package com.clinica.recordatorios.repository;

import com.clinica.recordatorios.domain.CanalNotificacion;
import com.clinica.recordatorios.domain.Cita;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@DisplayName("Pruebas de Persistencia JPA en H2 para CitaRepository")
class CitaRepositoryTest {

    @Autowired
    private CitaRepository citaRepository;

    @Autowired
    private TestEntityManager entityManager;

    private final LocalDateTime INICIO_VENTANA = LocalDateTime.of(2026, 8, 10, 9, 0, 0);
    private final LocalDateTime FIN_VENTANA = LocalDateTime.of(2026, 8, 11, 9, 0, 0);

    @Test
    @DisplayName("Límite inferior exacto: una cita en el segundo inicial de la ventana (09:00:00) se incluye")
    void limitesExactos_citaEnLimiteInferiorExacto_seIncluyeEnResultado() {
        Cita citaLimiteInferior = new Cita(null, "Paciente Límite Inferior", "3001112233",
                INICIO_VENTANA, CanalNotificacion.SMS, false);
        citaRepository.save(citaLimiteInferior);

        List<Cita> resultado = citaRepository
                .findByFechaHoraCitaBetweenAndRecordatorioEnviadoFalse(INICIO_VENTANA, FIN_VENTANA);

        assertEquals(1, resultado.size());
        assertEquals("Paciente Límite Inferior", resultado.get(0).getPacienteNombre());
    }

    @Test
    @DisplayName("Límite superior exacto: una cita en el segundo final de la ventana (24h después, 09:00:00) se incluye")
    void limitesExactos_citaEnLimiteSuperiorExacto_seIncluyeEnResultado() {
        Cita citaLimiteSuperior = new Cita(null, "Paciente Límite Superior", "paciente@correo.com",
                FIN_VENTANA, CanalNotificacion.EMAIL, false);
        citaRepository.save(citaLimiteSuperior);

        List<Cita> resultado = citaRepository
                .findByFechaHoraCitaBetweenAndRecordatorioEnviadoFalse(INICIO_VENTANA, FIN_VENTANA);

        assertEquals(1, resultado.size());
        assertEquals("Paciente Límite Superior", resultado.get(0).getPacienteNombre());
    }

    @Test
    @DisplayName("Fuera de ventana inferior: una cita un segundo antes del inicio (08:59:59) se excluye")
    void limitesExactos_citaUnSegundoAntesDeInicio_seExcluyeDelResultado() {
        Cita citaAntes = new Cita(null, "Paciente Antes", "3000000001",
                INICIO_VENTANA.minusSeconds(1), CanalNotificacion.SMS, false);
        citaRepository.save(citaAntes);

        List<Cita> resultado = citaRepository
                .findByFechaHoraCitaBetweenAndRecordatorioEnviadoFalse(INICIO_VENTANA, FIN_VENTANA);

        assertTrue(resultado.isEmpty(), "La cita previa al límite inicial no debe incluirse");
    }

    @Test
    @DisplayName("Fuera de ventana superior: una cita un segundo después del fin (09:00:01) se excluye")
    void limitesExactos_citaUnSegundoDespuesDeFin_seExcluyeDelResultado() {
        Cita citaDespues = new Cita(null, "Paciente Después", "despues@correo.com",
                FIN_VENTANA.plusSeconds(1), CanalNotificacion.EMAIL, false);
        citaRepository.save(citaDespues);

        List<Cita> resultado = citaRepository
                .findByFechaHoraCitaBetweenAndRecordatorioEnviadoFalse(INICIO_VENTANA, FIN_VENTANA);

        assertTrue(resultado.isEmpty(), "La cita posterior a las 24 horas no debe incluirse");
    }

    @Test
    @DisplayName("Filtro de estado: citas dentro de ventana pero ya notificadas (recordatorioEnviado=true) se excluyen")
    void citasPendientesVsEnviadas_soloRetornaLasNoNotificadas() {
        Cita citaPendiente = new Cita(null, "Cita Pendiente", "3004445566",
                INICIO_VENTANA.plusHours(5), CanalNotificacion.SMS, false);
        Cita citaYaEnviada = new Cita(null, "Cita Ya Enviada", "ya@correo.com",
                INICIO_VENTANA.plusHours(6), CanalNotificacion.EMAIL, true);

        citaRepository.save(citaPendiente);
        citaRepository.save(citaYaEnviada);

        List<Cita> resultado = citaRepository
                .findByFechaHoraCitaBetweenAndRecordatorioEnviadoFalse(INICIO_VENTANA, FIN_VENTANA);

        assertEquals(1, resultado.size());
        assertEquals("Cita Pendiente", resultado.get(0).getPacienteNombre());
        assertFalse(resultado.get(0).isRecordatorioEnviado());
    }

    @Test
    @DisplayName("Persistencia de bandera: actualizar recordatorioEnviado a true en H2 impacta la consulta derivada")
    void persistenciaBanderaNotificacion_alMarcarComoEnviada_sePersisteEnH2YDejaDeAparecer() {
        Cita cita = new Cita(null, "Roberto Castro", "roberto@mail.com",
                INICIO_VENTANA.plusHours(3), CanalNotificacion.EMAIL, false);
        Cita guardada = citaRepository.save(cita);
        Long id = guardada.getId();
        assertNotNull(id);

        // 1. Antes de actualizar, la cita aparece en pendientes
        List<Cita> antes = citaRepository
                .findByFechaHoraCitaBetweenAndRecordatorioEnviadoFalse(INICIO_VENTANA, FIN_VENTANA);
        assertEquals(1, antes.size());

        // 2. Modificar la bandera y persistir en H2
        guardada.setRecordatorioEnviado(true);
        citaRepository.saveAndFlush(guardada);
        entityManager.clear();

        // 3. Después de actualizar, la consulta de pendientes retorna vacío
        List<Cita> despues = citaRepository
                .findByFechaHoraCitaBetweenAndRecordatorioEnviadoFalse(INICIO_VENTANA, FIN_VENTANA);
        assertTrue(despues.isEmpty(), "Una cita marcada como enviada no debe volver a consultarse");

        // 4. Se comprueba el estado persistido en BD
        Optional<Cita> recuperada = citaRepository.findById(id);
        assertTrue(recuperada.isPresent());
        assertTrue(recuperada.get().isRecordatorioEnviado(), "La bandera en base de datos debe ser true");
    }
}
