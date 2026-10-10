package com.clinica.recordatorios;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@DisplayName("Pruebas Unitarias de RecordatoriosCitasServiceApplication")
class RecordatoriosCitasServiceApplicationTest {

    @Test
    @DisplayName("Main: verifica que la clase principal se puede instanciar correctamente")
    void instanciaAplicacion_seCreaSinErrores() {
        assertDoesNotThrow(RecordatoriosCitasServiceApplication::new);
    }
}
