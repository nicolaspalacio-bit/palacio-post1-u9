package com.clinica.recordatorios.channel;

import com.clinica.recordatorios.domain.CanalNotificacion;
import com.clinica.recordatorios.domain.Cita;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@DisplayName("Pruebas Unitarias de EmailReminderChannel (Estrategia EMAIL)")
class EmailReminderChannelTest {

    @Test
    @DisplayName("Estrategia EMAIL: procesa y despacha el correo sin lanzar excepciones")
    void enviar_citaValida_completaSinLanzarExcepcion() {
        EmailReminderChannel channel = new EmailReminderChannel();
        Cita cita = new Cita(1L, "Ana Torres", "ana@correo.com",
                LocalDateTime.of(2026, 8, 10, 12, 0), CanalNotificacion.EMAIL, false);

        assertDoesNotThrow(() -> channel.enviar(cita, "Mensaje de prueba"));
    }
}
