# Guia de Pruebas Automatizadas

Este documento detalla la suite completa de pruebas automatizadas del microservicio `recordatorios-citas-service`, incluyendo la descripcion tecnica de cada caso de prueba, colaboradores utilizados, aserciones y los comandos para su ejecucion local.

---

## 1. Resumen de la Suite de Pruebas

| Clase de Prueba | Tipo de Prueba | Anotaciones | Cantidad de Casos | Proposito Principal |
| :--- | :--- | :--- | :---: | :--- |
| `CitaReminderServiceImplTest` | Unitaria Aislada | `@ExtendWith(MockitoExtension.class)` | 6 | Valida la logica de negocio, orquestacion de canales, calculo de ventanas y manejo de excepciones sin levantar Spring. |
| `CitaRepositoryTest` | Persistencia (JPA) | `@DataJpaTest` | 1 | Valida la consulta derivada de Spring Data JPA contra base de datos relacional H2 en memoria. |
| `CitaReminderServiceIntegrationTest` | Integracion Completa | `@SpringBootTest` | 1 | Valida la colaboracion de todas las capas dentro del ApplicationContext real con Clock determinista y gateway mockeado. |

**Total de pruebas automatizadas:** 8 casos de prueba (100% en verde).

---

## 2. Detalle de Casos de Prueba

### 2.1. Pruebas Unitarias (`CitaReminderServiceImplTest`)

#### Test 1: `enviarRecordatoriosPendientes_citaEnVentana_usaElCanalPreferido`
- **Escenario:** Cita medica existente con fecha dentro de las 24 horas y canal preferido `EMAIL`.
- **Dobles de prueba:**
  - `CitaRepository`: Mock que retorna la cita en `findByFechaHoraCitaBetween...`.
  - `ReminderChannelFactory`: Mock que retorna `reminderChannelMock` al solicitar canal `EMAIL`.
  - `ReminderChannel`: Mock que registra la invocacion.
  - `Clock`: Fake fijo a `2026-08-10T09:00:00Z`.
- **Aserciones:**
  - Retorno del metodo igual a `1`.
  - `verify(reminderChannelMock, times(1)).enviar(eq(cita), anyString())`.
  - `verify(citaRepository, times(1)).save(cita)`.
  - `assertTrue(cita.isRecordatorioEnviado())`.

#### Test 2: `enviarRecordatoriosPendientes_ventanaDeterministaSegunClockFijo_consultaLosLimitesExactos`
- **Escenario:** Verificacion milimetrica de los limites temporales pasados a la consulta del repositorio.
- **Mecanismo:** Empleo de `ArgumentCaptor<LocalDateTime>` para capturar los argumentos `desde` y `hasta`.
- **Aserciones:**
  - `desde` capturado es exactamente `2026-08-10T09:00:00`.
  - `hasta` capturado es exactamente `2026-08-11T09:00:00` (24 horas exactas).

#### Test 3: `enviarRecordatoriosPendientes_sinCitasPendientes_noConsultaLaFactory`
- **Escenario:** No existen citas pendientes en la ventana de 24 horas.
- **Aserciones:**
  - Retorno del metodo igual a `0`.
  - `verifyNoInteractions(reminderChannelFactory)`.
  - `verify(citaRepository, never()).save(any())`.

#### Test 4: `enviarRecordatorioUrgente_citaExistente_usaCanalUrgenteInyectado`
- **Escenario:** Se solicita el envio de recordatorio urgente para una cita existente (ID 5L, SMS).
- **Aserciones:**
  - `verify(canalUrgenteMock, times(1)).enviar(eq(cita), contains("URGENTE"))`.
  - `verify(citaRepository).save(cita)`.
  - `assertTrue(cita.isRecordatorioEnviado())`.

#### Test 5: `enviarRecordatorioUrgente_gatewayFalla_noMarcaComoEnviado`
- **Escenario:** Simula una caida o falla de red del proveedor de SMS (`doThrow(new RuntimeException(...))`).
- **Aserciones:**
  - El metodo propaga `RuntimeException`.
  - `verify(citaRepository, never()).save(any())` (la cita no se marca como enviada si el despacho falla).
  - *Nota:* Esta prueba era imposible de ejecutar antes del refactor DIP.

#### Test 6: `enviarRecordatorioUrgente_citaInexistente_lanzaCitaNoEncontradaException`
- **Escenario:** Se consulta un ID que no existe en el repositorio (ID 99L).
- **Aserciones:**
  - El metodo arroja `CitaNoEncontradaException`.
  - `verifyNoInteractions(canalUrgenteMock)`.

---

### 2.2. Pruebas de Persistencia (`CitaRepositoryTest`)

#### Test 7: `findByFechaHoraCitaBetweenAndRecordatorioEnviadoFalse_soloRetornaCitasPendientesEnVentana`
- **Escenario:** Se persisten 3 citas en la base de datos H2:
  1. Cita dentro de ventana, `recordatorioEnviado = false` (esperada).
  2. Cita dentro de ventana, `recordatorioEnviado = true` (descartada por ya enviada).
  3. Cita fuera de ventana, `recordatorioEnviado = false` (descartada por fecha).
- **Aserciones:**
  - La consulta retorna exactamente `1` registro.
  - El registro devuelto corresponde al paciente de la cita pendiente en ventana.

---

### 2.3. Pruebas de Integracion Completa (`CitaReminderServiceIntegrationTest`)

#### Test 8: `enviarRecordatoriosPendientes_contextoCompletoDeSpring_persisteYNotificaCitasEnVentana`
- **Escenario:** Flujo end-to-end dentro del contenedor Spring Boot con base de datos H2 real y gateway mockeado (`@MockBean`).
- **Aserciones:**
  - El servicio retorna `1` recordatorio enviado.
  - El gateway mockeado recibe la invocacion `enviarSms(...)`.
  - La base de datos H2 almacena la entidad con `recordatorioEnviado = true`.

---

## 3. Comandos de Ejecucion

### En Windows PowerShell:
```powershell
# Ejecutar solo pruebas unitarias:
mvn test -Dtest=CitaReminderServiceImplTest

# Ejecutar solo pruebas de persistencia H2:
mvn test -Dtest=CitaRepositoryTest

# Ejecutar la suite completa de pruebas y generar reporte de cobertura:
mvn clean test
```

### En Bash (Linux / macOS):
```bash
# Ejecutar solo pruebas unitarias:
mvn test -Dtest=CitaReminderServiceImplTest

# Ejecutar la suite completa y empaquetar el microservicio:
mvn clean verify
```
