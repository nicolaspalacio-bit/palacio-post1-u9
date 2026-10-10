package com.clinica.recordatorios.channel.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@DisplayName("Pruebas Unitarias de TwilioNotificadorSmsGateway")
class TwilioNotificadorSmsGatewayTest {

    @Test
    @DisplayName("Gateway SMS: ejecuta el envio de SMS simulado sin lanzar excepciones")
    void enviarSms_numeroYTextoValidos_ejecutaSinExcepcion() {
        TwilioNotificadorSmsGateway gateway = new TwilioNotificadorSmsGateway();
        assertDoesNotThrow(() -> gateway.enviarSms("3001234567", "Recordatorio de cita médica"));
    }
}
