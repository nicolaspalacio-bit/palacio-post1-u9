# Guia de Pruebas Automatizadas

Este documento detalla la suite completa de pruebas automatizadas del microservicio `recordatorios-citas-service`, incluyendo la descripcion tecnica de cada caso de prueba, colaboradores utilizados, aserciones y los comandos para su ejecucion en entornos Windows PowerShell y CI/CD con OpenJDK 21.

---

## 1. Resumen de la Suite de Pruebas

| Clase de Prueba | Tipo de Prueba | Anotaciones | Cantidad de Casos | Proposito Principal |
| :--- | :--- | :--- | :---: | :--- |
| `CitaReminderServiceImplTest` | Unitaria Aislada | `@ExtendWith(MockitoExtension.class)` | 8 | Valida la logica de orquestacion, despacho EMAIL/SMS, limites temporales, ausencia de citas, recordatorio urgente, fallos de red y excepciones de dominio sin levantar Spring. |
| `CitaRepositoryTest` | Persistencia (JPA) | `@DataJpaTest` | 6 | Valida los limites exactos de la ventana de 24h (segundo inicial, segundo final, exclusiones previa y posterior), filtro de no notificadas y persistencia de bandera en H2. |
| `CitaReminderServiceIntegrationTest` | Integracion Completa | `@SpringBootTest` | 5 | Valida la colaboracion de todas las capas en el ApplicationContext con H2 en memoria, Clock determinista y gateway SMS simulado con `@MockBean`. |
| `ReminderChannelFactoryImplTest` | Unitaria Aislada | `@ExtendWith(MockitoExtension.class)` | 2 | Valida la resolucion dinamica polimorfica de estrategias de canal (EMAIL y SMS) mediante Factory Method. |
| `SmsReminderChannelTest` | Unitaria Aislada | `@ExtendWith(MockitoExtension.class)` | 1 | Valida que la estrategia de SMS delega la transmision en el puerto `NotificadorSmsGateway`. |
| `EmailReminderChannelTest` | Unitaria Aislada | JUnit 5 puro | 1 | Valida la ejecucion de la estrategia de envio por correo electronico. |
| `TwilioNotificadorSmsGatewayTest` | Unitaria Aislada | JUnit 5 puro | 1 | Valida la ejecucion del adaptador de gateway SMS simulado. |
| `ClockConfigTest` | Unitaria Aislada | JUnit 5 puro | 1 | Valida la correcta inicializacion del bean del reloj del sistema para produccion. |
| `RecordatoriosCitasServiceApplicationTest` | Unitaria Aislada | JUnit 5 puro | 1 | Valida la instanciacion correcta de la clase raiz de Spring Boot. |

**Total de pruebas automatizadas:** 26 casos de prueba (100% en verde, 0 fallos, 0 errores, 0 omitidos).

---

## 2. Detalle de Casos de Prueba

### 2.1. Pruebas Unitarias de Negocio (`CitaReminderServiceImplTest`)

#### Test 1: `enviarRecordatoriosPendientes_citaCanalEmail_despachaPorEmailYPersisteBandera`
- **Escenario:** Cita medica existente en ventana de 24h con canal preferido `EMAIL`.
- **Dobles:** Mock `CitaRepository`, Mock `ReminderChannelFactory`, Mock `ReminderChannel`, Fake `Clock.fixed`.
- **Aserciones:** Retorno 1; `verify(reminderChannelFactory).obtenerCanal(EMAIL)`; `verify(channel).enviar(...)`; `verify(repo).save(cita)`; `assertTrue(cita.isRecordatorioEnviado())`.

#### Test 2: `enviarRecordatoriosPendientes_citaCanalSms_despachaPorSmsYPersisteBandera`
- **Escenario:** Cita medica existente en ventana de 24h con canal preferido `SMS`.
- **Dobles:** Mock `CitaRepository`, Mock `ReminderChannelFactory`, Mock `ReminderChannel`, Fake `Clock.fixed`.
- **Aserciones:** Retorno 1; `verify(reminderChannelFactory).obtenerCanal(SMS)`; `verify(channel).enviar(...)`; `verify(repo).save(cita)`; `assertTrue(cita.isRecordatorioEnviado())`.

#### Test 3: `enviarRecordatoriosPendientes_multiplesCitasEmailYSms_despachaPorCanalesCorrespondientes`
- **Escenario:** Citas mixtas (una EMAIL y una SMS) procesadas en el mismo lote.
- **Aserciones:** Retorno 2; se invocan ambos canales con sus destinatarios respectivos y se persisten ambas citas marcadas como enviadas.

#### Test 4: `enviarRecordatoriosPendientes_ventanaDeterministaSegunClockFijo_consultaLosLimitesExactos`
- **Escenario:** Auditoria de los limites temporales exactos consultados al repositorio mediante `ArgumentCaptor<LocalDateTime>`.
- **Aserciones:** `desde` es exactamente `2026-08-10T09:00:00` y `hasta` es exactamente `2026-08-11T09:00:00` (24 horas exactas segun el Fake).

#### Test 5: `enviarRecordatoriosPendientes_sinCitasPendientes_noConsultaLaFactoryNiPersiste`
- **Escenario:** Lista de citas vacia en la ventana consultada.
- **Aserciones:** Retorno 0; `verifyNoInteractions(reminderChannelFactory)`; `verify(citaRepository, never()).save(any())`.

#### Test 6: `enviarRecordatorioUrgente_citaExistente_usaCanalUrgenteInyectadoYPersisteBandera`
- **Escenario:** Recordatorio urgente para cita existente (ID 5L).
- **Aserciones:** `verify(canalUrgenteMock).enviar(eq(cita), contains("URGENTE"))`; `verify(repo).save(cita)`; `assertTrue(cita.isRecordatorioEnviado())`.

#### Test 7: `enviarRecordatorioUrgente_gatewayFalla_noMarcaComoEnviadoNiPersiste`
- **Escenario:** El canal urgente falla arrojando excepcion (`doThrow(new RuntimeException(...))`).
- **Aserciones:** Se propaga `RuntimeException`; `verify(citaRepository, never()).save(any())`; `assertFalse(cita.isRecordatorioEnviado())`.

#### Test 8: `enviarRecordatorioUrgente_citaInexistente_lanzaExcepcionYNoInvocaCanal`
- **Escenario:** Cita inexistente (ID 99L) en `enviarRecordatorioUrgente`.
- **Aserciones:** Lanza `CitaNoEncontradaException`; `verifyNoInteractions(canalUrgenteMock)`; `verify(citaRepository, never()).save(any())`.

---

### 2.2. Pruebas de Persistencia JPA en H2 (`CitaRepositoryTest`)

#### Test 9: `limitesExactos_citaEnLimiteInferiorExacto_seIncluyeEnResultado`
- Cita exactamente a las `09:00:00` (inicio de ventana). Se comprueba que `BETWEEN` en SQL/JPA la incluye.

#### Test 10: `limitesExactos_citaEnLimiteSuperiorExacto_seIncluyeEnResultado`
- Cita exactamente a las `09:00:00` del dia siguiente (24h exactas). Se comprueba que `BETWEEN` la incluye.

#### Test 11: `limitesExactos_citaUnSegundoAntesDeInicio_seExcluyeDelResultado`
- Cita a las `08:59:59`. Se comprueba que no es retornada en la consulta.

#### Test 12: `limitesExactos_citaUnSegundoDespuesDeFin_seExcluyeDelResultado`
- Cita a las `09:00:01` del dia siguiente. Se comprueba que no es retornada en la consulta.

#### Test 13: `citasPendientesVsEnviadas_soloRetornaLasNoNotificadas`
- Comprueba que una cita con `recordatorioEnviado = true` dentro de la ventana es descartada por el predicado `recordatorioEnviadoFalse`.

#### Test 14: `persistenciaBanderaNotificacion_alMarcarComoEnviada_sePersisteEnH2YDejaDeAparecer`
- Comprueba el ciclo completo de actualizacion en base de datos H2: la cita pasa de pendiente a notificada y la siguiente consulta derivada ya no la devuelve.

---

### 2.3. Pruebas de Integracion con Spring Boot (`CitaReminderServiceIntegrationTest`)

#### Test 15: `enviarRecordatoriosPendientes_contextoCompletoSpringConH2_persisteYDespachaEmailYSms`
- Despacho de citas EMAIL y SMS en H2 real, verificando la invocacion del `@MockBean NotificadorSmsGateway` y la actualizacion en base de datos.

#### Test 16: `enviarRecordatoriosPendientes_sinCitasPendientes_retornaCeroYNoLlamaGateway`
- Verificacion de ausencia de llamadas a la pasarela cuando la base de datos no contiene citas pendientes en la ventana.

#### Test 17: `enviarRecordatorioUrgente_citaExistente_despachaSmsYPersisteEstadoEnH2`
- Despacho urgente con persistencia verificada en la tabla `citas` de H2.

#### Test 18: `enviarRecordatorioUrgente_gatewayFalla_noPersisteBanderaEnH2`
- Simula falla en el `@MockBean NotificadorSmsGateway` y audita que en H2 la cita permanece con `recordatorioEnviado = false`.

#### Test 19: `enviarRecordatorioUrgente_citaInexistente_lanzaExcepcionYNoInvocaGateway`
- Comprueba que ante un ID desconocido se lanza `CitaNoEncontradaException` sin interactuar con el gateway.

---

### 2.4. Pruebas de Estrategias y Factoria

- **`ReminderChannelFactoryImplTest` (2 tests):** Comprueba resolucion de `EmailReminderChannel` y `SmsReminderChannel` segun el enum `CanalNotificacion`.
- **`SmsReminderChannelTest` (1 test):** Comprueba que la estrategia SMS delega en el gateway inyectado.
- **`EmailReminderChannelTest` (1 test):** Comprueba ejecucion de la estrategia EMAIL.
- **`TwilioNotificadorSmsGatewayTest` (1 test):** Comprueba ejecucion del adaptador de gateway.
- **`ClockConfigTest` (1 test):** Comprueba el bean de reloj del sistema.
- **`RecordatoriosCitasServiceApplicationTest` (1 test):** Comprueba instanciacion del servicio.

---

## 3. Comandos de Ejecucion

### En Windows PowerShell:
```powershell
# Configurar JDK 21
$env:JAVA_HOME = "C:\Program Files\Android\openjdk\jdk-21.0.8"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

# Ejecutar pruebas unitarias de servicio:
mvn test -Dtest=CitaReminderServiceImplTest

# Ejecutar pruebas de persistencia H2:
mvn test -Dtest=CitaRepositoryTest

# Ejecutar pruebas de integracion Spring Boot:
mvn test -Dtest=CitaReminderServiceIntegrationTest

# Ejecutar la suite completa (26 pruebas) y reporte JaCoCo:
mvn clean test

# Verificacion integral y empaquetado:
mvn clean verify
```
