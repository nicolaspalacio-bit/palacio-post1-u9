package com.clinica.recordatorios.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("Pruebas Unitarias de ClockConfig")
class ClockConfigTest {

    @Test
    @DisplayName("ClockConfig: provee una instancia funcional de java.time.Clock para produccion")
    void clockBean_proveeClockValido() {
        ClockConfig config = new ClockConfig();
        Clock clock = config.clock();
        assertNotNull(clock);
        assertNotNull(clock.instant());
        assertNotNull(clock.getZone());
    }
}
