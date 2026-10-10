# Recordatorios Citas Service

Repositorio del post-contenido de la Unidad 9 de Patrones de Diseño de Software.

## Parte 1 - Pruebas Unitarias con JUnit 5 y Mockito

`CitaReminderServiceImplTest` aisla `CitaReminderServiceImpl` de sus tres colaboradores:
- `CitaRepository` (Mock)
- `ReminderChannelFactory` / `ReminderChannel` (Mock)
- `Clock` (Fake con `Clock.fixed`)