# Registro de decisiones y limitaciones

## Decisiones

1. **Órdenes discretas, sin joystick.** La ronda es autónoma; el operador supervisa y decide, no
   conduce. Teleoperar con la latencia de una red Wi‑Fi de depósito sería además menos seguro.
2. **Telemetría integrada al Panel.** Supervisión y telemetría son la misma tarea ("¿cómo está el
   robot?"), así que comparten vista en lugar de obligar a cambiar de pestaña.
3. **Detener siempre a mano y sin confirmación.** Está en la franja superior de todas las pestañas.
   Frenar por error cuesta poco (se reanuda); no poder frenar a tiempo cuesta mucho.
4. **Sin conexión solo quedan las órdenes de seguridad.** Con datos viejos no tiene sentido pausar o
   iniciar; detener y regresar sí se pueden intentar, sabiendo que pueden quedar sin respuesta.
5. **"Confirmada" ≠ "ejecutada".** La respuesta del robot solo indica que aceptó la orden. El estado
   real siempre se lee de la telemetría, y la interfaz lo dice de forma explícita.
6. **Doble validación.** `CommandRules` habilita o no los botones; el robot (simulador) vuelve a
   validar contra su estado real y puede rechazar. Las reglas de la app son una ayuda, no la autoridad.
7. **Sin enlace, el robot pausa la ronda y espera** ([#4](https://github.com/fieldcheck-uade/robot-hmi-app/issues/4)).
   Un corte breve (menos de 10 s) no interrumpe nada. Si dura más, el robot se detiene en el lugar
   (*Esperando decisión*) y, al reconectar, el operador decide si reanuda, regresa o cancela. Con
   batería crítica vuelve solo a la base. Se descartaron las otras dos opciones: **seguir la ronda**
   deja un robot andando entre personas sin que nadie lo supervise, y **regresar a la base**
   recorre la nave sin supervisión justo cuando el operador no puede ver ni frenar al robot.
   Pausar pierde cobertura de vigilancia mientras dura el corte, pero es el estado más predecible.
   Lo ocurrido durante el corte se entrega al reconectar.
8. **La decisión cierra la alerta.** Si una alerta pedía una decisión y el robot acepta reanudar,
   regresar o cancelar, la alerta queda reconocida con la decisión anotada.
9. **Un solo ViewModel** para toda la sesión: todas las pestañas muestran el mismo robot y comparten
   estado; partirlo habría duplicado la combinación de flujos.
10. **Room para el historial**, inyección de dependencias manual (`AppContainer`) y navegación por
    pestañas con estado simple: el tamaño de la app no justifica Hilt ni Navigation.
11. **Controles de simulación en una interfaz aparte** (`SimulationControls`), accesibles solo a
    través del ViewModel, para que quede claro qué desaparece con un robot real.
12. **Tema oscuro único**, pensado para uso nocturno.

## Limitaciones

- El robot es simulado: movimiento en línea recta entre puntos, sin dinámica ni navegación real.
- Tiempos y consumo de batería están acelerados para que una ronda dure 1–2 minutos en la demo.
- El contrato JSON está documentado pero no se serializa: el Repository simulado usa los modelos
  Kotlin directamente. No es un protocolo de Unitree.
- Los tres robots comparten el mismo plano y las mismas rondas; solo cambia la batería inicial.
- Alertas y órdenes de la sesión se pierden al desconectar o cerrar la app; el historial persiste.
- La simulación corre dentro del proceso de la app: si Android lo cierra en segundo plano, el robot
  simulado se reinicia.
- "Avisar a guardia" solo deja constancia en el historial; no envía ningún aviso.
- Sin autenticación de operadores ni roles.
- Probado en emulador (Pixel 9a, vertical). No hay diseño específico para tablet ni horizontal.
- Tests: unitarios del simulador y de las reglas de habilitación. Sin tests de UI.
