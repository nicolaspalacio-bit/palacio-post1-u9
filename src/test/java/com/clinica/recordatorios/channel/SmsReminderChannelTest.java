package com.clinica.recordatorios.channel;

import com.clinica.recordatorios.channel.gateway.NotificadorSmsGateway;
import com.clinica.recordatorios.domain.CanalNotificacion;
import com.clinica.recordatorios.domain.Cita;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias de SmsReminderChannel (Estrategia SMS)")
class SmsReminderChannelTest {

    @Mock
    private NotificadorSmsGateway gateway;

    @Test
    @DisplayName("Estrategia SMS: delega el envio al NotificadorSmsGateway con el contacto y texto correctos")
    void enviar_citaValida_delegaEnGateway() {
        SmsReminderChannel channel = new SmsReminderChannel(gateway);
        Cita cita = new Cita(1L, "Pedro Gómez", "3001234567",
                LocalDateTime.of(2026, 8, 10, 10, 0), CanalNotificacion.SMS, false);

        channel.enviar(cita, "Mensaje de prueba");

        verify(gateway, times(1)).enviarSms("3001234567", "Mensaje de prueba");
    }
}
