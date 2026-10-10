package com.clinica.recordatorios;

import com.clinica.recordatorios.channel.gateway.NotificadorSmsGateway;
import com.clinica.recordatorios.domain.CanalNotificacion;
import com.clinica.recordatorios.domain.Cita;
import com.clinica.recordatorios.repository.CitaRepository;
import com.clinica.recordatorios.service.CitaNoEncontradaException;
import com.clinica.recordatorios.service.CitaReminderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@DisplayName("Pruebas de Integración con @SpringBootTest, H2 y Gateway SMS Simulado")
class CitaReminderServiceIntegrationTest {

    @TestConfiguration
    static class ClockDeterministaConfig {
        @Bean
        @Primary
        public Clock clock() {
            // Reloj Fake determinista configurado para el contexto de integración
            return Clock.fixed(Instant.parse("2026-08-10T09:00:00Z"), ZoneId.of("UTC"));
        }
    }

    @Autowired
    private CitaReminderService citaReminderService;

    @Autowired
    private CitaRepository citaRepository;

    @MockBean
    private NotificadorSmsGateway notificadorSmsGateway;

    @BeforeEach
    void setUp() {
        citaRepository.deleteAll();
        reset(notificadorSmsGateway);
    }

    @Test
    @DisplayName("Integración por lotes: despacha citas EMAIL y SMS en ventana de 24h, llamando gateway SMS y persistiendo banderas en H2")
    void enviarRecordatoriosPendientes_contextoCompletoSpringConH2_persisteYDespachaEmailYSms() {
        // Cita 1: SMS dentro de ventana
        Cita citaSms = citaRepository.save(new Cita(null, "Carla Ríos", "3011112222",
                LocalDateTime.of(2026, 8, 10, 18, 0), CanalNotificacion.SMS, false));

        // Cita 2: EMAIL dentro de ventana
        Cita citaEmail = citaRepository.save(new Cita(null, "Mario Vargas", "mario@correo.com",
                LocalDateTime.of(2026, 8, 11, 8, 30), CanalNotificacion.EMAIL, false));

        // Cita 3: Fuera de ventana (más de 24 horas después del reloj fijo)
        citaRepository.save(new Cita(null, "Fuera de Ventana", "3000000000",
                LocalDateTime.of(2026, 8, 11, 10, 0), CanalNotificacion.SMS, false));

        int enviados = citaReminderService.enviarRecordatoriosPendientes();

        assertEquals(2, enviados, "Debe procesar exactamente las 2 citas dentro de la ventana de 24 horas");
        verify(notificadorSmsGateway, times(1)).enviarSms(eq("3011112222"), contains("Recordatorio"));

        // Comprobación de persistencia real en base de datos H2
        Cita citaSmsPersistida = citaRepository.findById(citaSms.getId()).orElseThrow();
        Cita citaEmailPersistida = citaRepository.findById(citaEmail.getId()).orElseThrow();
        assertTrue(citaSmsPersistida.isRecordatorioEnviado(), "La cita SMS debe estar marcada como enviada en H2");
        assertTrue(citaEmailPersistida.isRecordatorioEnviado(), "La cita EMAIL debe estar marcada como enviada en H2");
    }

    @Test
    @DisplayName("Integración sin citas pendientes: no invoca al gateway SMS y retorna 0")
    void enviarRecordatoriosPendientes_sinCitasPendientes_retornaCeroYNoLlamaGateway() {
        int enviados = citaReminderService.enviarRecordatoriosPendientes();

        assertEquals(0, enviados);
        verifyNoInteractions(notificadorSmsGateway);
    }

    @Test
    @DisplayName("Integración urgente: cita existente despacha por gateway SMS y persiste recordatorioEnviado=true en H2")
    void enviarRecordatorioUrgente_citaExistente_despachaSmsYPersisteEstadoEnH2() {
        Cita cita = citaRepository.save(new Cita(null, "Elena Morales", "3123456789",
                LocalDateTime.of(2026, 8, 10, 10, 15), CanalNotificacion.SMS, false));

        citaReminderService.enviarRecordatorioUrgente(cita.getId());

        verify(notificadorSmsGateway, times(1)).enviarSms(eq("3123456789"), contains("URGENTE"));

        Cita persistida = citaRepository.findById(cita.getId()).orElseThrow();
        assertTrue(persistida.isRecordatorioEnviado(), "El recordatorio urgente debe persistir la bandera como true en H2");
    }

    @Test
    @DisplayName("Integración urgente con fallo en gateway: si el gateway de SMS falla, la cita en H2 NO se marca como enviada")
    void enviarRecordatorioUrgente_gatewayFalla_noPersisteBanderaEnH2() {
        Cita cita = citaRepository.save(new Cita(null, "Paciente Error", "3009998877",
                LocalDateTime.of(2026, 8, 10, 10, 0), CanalNotificacion.SMS, false));

        doThrow(new RuntimeException("Error simulado en proveedor SMS"))
                .when(notificadorSmsGateway).enviarSms(anyString(), anyString());

        assertThrows(RuntimeException.class,
                () -> citaReminderService.enviarRecordatorioUrgente(cita.getId()));

        Cita enBaseDeDatos = citaRepository.findById(cita.getId()).orElseThrow();
        assertFalse(enBaseDeDatos.isRecordatorioEnviado(),
                "Al fallar el despacho urgente, la cita en base de datos H2 debe permanecer con recordatorioEnviado = false");
    }

    @Test
    @DisplayName("Integración urgente cita inexistente: lanza CitaNoEncontradaException y no invoca al gateway SMS")
    void enviarRecordatorioUrgente_citaInexistente_lanzaExcepcionYNoInvocaGateway() {
        assertThrows(CitaNoEncontradaException.class,
                () -> citaReminderService.enviarRecordatorioUrgente(999999L));

        verifyNoInteractions(notificadorSmsGateway);
    }
}
