package com.clinica.recordatorios.service;

import com.clinica.recordatorios.channel.ReminderChannel;
import com.clinica.recordatorios.channel.ReminderChannelFactory;
import com.clinica.recordatorios.domain.CanalNotificacion;
import com.clinica.recordatorios.domain.Cita;
import com.clinica.recordatorios.repository.CitaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias de CitaReminderServiceImpl con Mockito y Clock Fake")
class CitaReminderServiceImplTest {

    @Mock
    private CitaRepository citaRepository;

    @Mock
    private ReminderChannelFactory reminderChannelFactory;

    @Mock
    private ReminderChannel reminderChannelMock;

    @Mock
    private ReminderChannel canalUrgenteMock;

    // Fake determinista: deliberadamente NO se mockea con Mockito.
    // Clock.fixed(...) es una implementacion real y completa de java.time.Clock
    // que congela el instante sin efectos colaterales de red o SO.
    private final Clock clockFijo =
            Clock.fixed(Instant.parse("2026-08-10T09:00:00Z"), ZoneId.of("UTC"));

    private CitaReminderServiceImpl citaReminderService;

    @BeforeEach
    void setUp() {
        citaReminderService =
                new CitaReminderServiceImpl(citaRepository, reminderChannelFactory, canalUrgenteMock, clockFijo);
    }

    @Test
    @DisplayName("Despacho por EMAIL: verifica interaccion con Factory, Channel Mock y persistencia de bandera")
    void enviarRecordatoriosPendientes_citaCanalEmail_despachaPorEmailYPersisteBandera() {
        Cita cita = new Cita(1L, "Ana Gómez", "ana@correo.com",
                LocalDateTime.of(2026, 8, 10, 15, 0), CanalNotificacion.EMAIL, false);

        when(citaRepository.findByFechaHoraCitaBetweenAndRecordatorioEnviadoFalse(any(), any()))
                .thenReturn(List.of(cita));
        when(reminderChannelFactory.obtenerCanal(CanalNotificacion.EMAIL))
                .thenReturn(reminderChannelMock);

        int enviados = citaReminderService.enviarRecordatoriosPendientes();

        assertEquals(1, enviados);
        verify(reminderChannelFactory, times(1)).obtenerCanal(CanalNotificacion.EMAIL);
        verify(reminderChannelMock, times(1)).enviar(eq(cita), contains("Recordatorio"));
        verify(citaRepository, times(1)).save(cita);
        assertTrue(cita.isRecordatorioEnviado(), "La bandera recordatorioEnviado debe actualizarse a true");
    }

    @Test
    @DisplayName("Despacho por SMS: verifica interaccion con Factory, Channel Mock y persistencia de bandera")
    void enviarRecordatoriosPendientes_citaCanalSms_despachaPorSmsYPersisteBandera() {
        Cita cita = new Cita(2L, "Carlos Mendoza", "3001234567",
                LocalDateTime.of(2026, 8, 10, 16, 30), CanalNotificacion.SMS, false);

        when(citaRepository.findByFechaHoraCitaBetweenAndRecordatorioEnviadoFalse(any(), any()))
                .thenReturn(List.of(cita));
        when(reminderChannelFactory.obtenerCanal(CanalNotificacion.SMS))
                .thenReturn(reminderChannelMock);

        int enviados = citaReminderService.enviarRecordatoriosPendientes();

        assertEquals(1, enviados);
        verify(reminderChannelFactory, times(1)).obtenerCanal(CanalNotificacion.SMS);
        verify(reminderChannelMock, times(1)).enviar(eq(cita), contains("Recordatorio"));
        verify(citaRepository, times(1)).save(cita);
        assertTrue(cita.isRecordatorioEnviado(), "La bandera recordatorioEnviado debe actualizarse a true");
    }

    @Test
    @DisplayName("Despacho mixto: procesa citas por EMAIL y SMS invocando sus canales respectivos")
    void enviarRecordatoriosPendientes_multiplesCitasEmailYSms_despachaPorCanalesCorrespondientes() {
        Cita citaEmail = new Cita(10L, "Laura Morales", "laura@mail.com",
                LocalDateTime.of(2026, 8, 10, 11, 0), CanalNotificacion.EMAIL, false);
        Cita citaSms = new Cita(11L, "David Silva", "3119876543",
                LocalDateTime.of(2026, 8, 10, 17, 0), CanalNotificacion.SMS, false);

        ReminderChannel mockEmailChannel = mock(ReminderChannel.class);
        ReminderChannel mockSmsChannel = mock(ReminderChannel.class);

        when(citaRepository.findByFechaHoraCitaBetweenAndRecordatorioEnviadoFalse(any(), any()))
                .thenReturn(List.of(citaEmail, citaSms));
        when(reminderChannelFactory.obtenerCanal(CanalNotificacion.EMAIL)).thenReturn(mockEmailChannel);
        when(reminderChannelFactory.obtenerCanal(CanalNotificacion.SMS)).thenReturn(mockSmsChannel);

        int enviados = citaReminderService.enviarRecordatoriosPendientes();

        assertEquals(2, enviados);
        verify(mockEmailChannel, times(1)).enviar(eq(citaEmail), anyString());
        verify(mockSmsChannel, times(1)).enviar(eq(citaSms), anyString());
        verify(citaRepository, times(1)).save(citaEmail);
        verify(citaRepository, times(1)).save(citaSms);
        assertTrue(citaEmail.isRecordatorioEnviado());
        assertTrue(citaSms.isRecordatorioEnviado());
    }

    @Test
    @DisplayName("Ventana de 24 horas: verifica con ArgumentCaptor los limites exactos calculados con Clock Fake")
    void enviarRecordatoriosPendientes_ventanaDeterministaSegunClockFijo_consultaLosLimitesExactos() {
        when(citaRepository.findByFechaHoraCitaBetweenAndRecordatorioEnviadoFalse(any(), any()))
                .thenReturn(List.of());

        citaReminderService.enviarRecordatoriosPendientes();

        ArgumentCaptor<LocalDateTime> desdeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> hastaCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(citaRepository).findByFechaHoraCitaBetweenAndRecordatorioEnviadoFalse(
                desdeCaptor.capture(), hastaCaptor.capture());

        // Con Clock.fixed("2026-08-10T09:00:00Z"), el límite inicial es exacto a las 09:00
        // y el límite final de la ventana de 24 horas es exactamente 2026-08-11T09:00:00.
        assertEquals(LocalDateTime.of(2026, 8, 10, 9, 0), desdeCaptor.getValue());
        assertEquals(LocalDateTime.of(2026, 8, 11, 9, 0), hastaCaptor.getValue());
    }

    @Test
    @DisplayName("Ausencia de citas pendientes: no interactua con la Factory ni realiza guardados en el repositorio")
    void enviarRecordatoriosPendientes_sinCitasPendientes_noConsultaLaFactoryNiPersiste() {
        when(citaRepository.findByFechaHoraCitaBetweenAndRecordatorioEnviadoFalse(any(), any()))
                .thenReturn(List.of());

        int enviados = citaReminderService.enviarRecordatoriosPendientes();

        assertEquals(0, enviados);
        verifyNoInteractions(reminderChannelFactory);
        verify(citaRepository, never()).save(any());
    }

    @Test
    @DisplayName("Recordatorio urgente: cita existente usa canal inyectado, envia mensaje de emergencia y persiste bandera")
    void enviarRecordatorioUrgente_citaExistente_usaCanalUrgenteInyectadoYPersisteBandera() {
        Cita cita = new Cita(5L, "Luis Peña", "3001234567",
                LocalDateTime.of(2026, 8, 10, 10, 30), CanalNotificacion.SMS, false);
        when(citaRepository.findById(5L)).thenReturn(Optional.of(cita));

        citaReminderService.enviarRecordatorioUrgente(5L);

        verify(canalUrgenteMock, times(1)).enviar(eq(cita), contains("URGENTE"));
        verify(citaRepository, times(1)).save(cita);
        assertTrue(cita.isRecordatorioEnviado(), "La cita debe guardarse con bandera recordatorioEnviado = true");
    }

    @Test
    @DisplayName("Fallo en gateway urgente: la cita NO debe guardarse como enviada ni mutar su bandera")
    void enviarRecordatorioUrgente_gatewayFalla_noMarcaComoEnviadoNiPersiste() {
        Cita cita = new Cita(6L, "Marta Ruiz", "3009876543",
                LocalDateTime.of(2026, 8, 10, 10, 30), CanalNotificacion.SMS, false);
        when(citaRepository.findById(6L)).thenReturn(Optional.of(cita));
        doThrow(new RuntimeException("Gateway de SMS no disponible"))
                .when(canalUrgenteMock).enviar(any(), anyString());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> citaReminderService.enviarRecordatorioUrgente(6L));

        assertEquals("Gateway de SMS no disponible", ex.getMessage());
        verify(citaRepository, never()).save(any());
        assertFalse(cita.isRecordatorioEnviado(), "La bandera recordatorioEnviado debe permanecer false tras el fallo");
    }

    @Test
    @DisplayName("Cita inexistente en urgente: lanza CitaNoEncontradaException y comprueba que NO se invoque el canal urgente")
    void enviarRecordatorioUrgente_citaInexistente_lanzaExcepcionYNoInvocaCanal() {
        when(citaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(CitaNoEncontradaException.class,
                () -> citaReminderService.enviarRecordatorioUrgente(99L));

        verifyNoInteractions(canalUrgenteMock);
        verify(citaRepository, never()).save(any());
    }
}
