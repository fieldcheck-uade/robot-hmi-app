# Centinela Go2 — HMI/HRI para rondas de vigilancia

Trabajo práctico grupal de Desarrollo de Aplicaciones I (UADE): interfaz de operación y supervisión
para un **Unitree Go2** que hace rondas de vigilancia nocturna en un centro logístico.
La app funciona con un **robot simulado**: no necesita robot, servidor ni internet.

| | |
|---|---|
| Consigna | [`TP-HMI-Ottoman.pdf`](TP-HMI-Ottoman.pdf) |
| Preentrega (caso de uso, robot, telemetría, comandos) | [`docs/preentrega.md`](docs/preentrega.md) |
| Arquitectura | [`docs/arquitectura.md`](docs/arquitectura.md) |
| Contrato de mensajes (JSON) | [`docs/contrato-mensajes.md`](docs/contrato-mensajes.md) |
| Los cinco escenarios y cómo demostrarlos | [`docs/escenarios.md`](docs/escenarios.md) |
| Riesgos y medidas | [`docs/riesgos.md`](docs/riesgos.md) |
| Guía para el Figma | [`docs/figma.md`](docs/figma.md) |
| Decisiones y limitaciones | [`docs/decisiones.md`](docs/decisiones.md) |

## Compilar y ejecutar

Requiere Android SDK (compileSdk 37) y JDK 17 o superior. Abrir la carpeta en Android Studio o:

```bash
./gradlew assembleDebug        # genera app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # tests del simulador y de las reglas de habilitación
./gradlew installDebug         # instala en el emulador o dispositivo conectado
```

minSdk 26 (Android 8.0). La app no pide ningún permiso.

## Estructura

```
app/src/main/kotlin/ar/edu/uade/robothmi/
├── model/        Modelos de dominio (Telemetry, Alert, CommandRecord…) y plano del lugar
├── domain/       CommandRules: condiciones de habilitación de cada orden
├── data/
│   ├── RobotRepository.kt   Interfaz del Repository + SimulationControls
│   ├── local/               Room: historial de eventos
│   └── sim/                 RobotSimulator (modelo del robot) y SimulatedRobotRepository
└── ui/
    ├── RobotViewModel.kt    UiState (StateFlow) + UiEvent
    ├── components/          Componentes Compose reutilizables y mapa
    ├── screens/             Selección, Panel, Misiones, Control, Alertas, Historial
    └── theme/
```

## Pendiente del grupo

Cada pendiente tiene su issue en GitHub:

- [#2](https://github.com/fieldcheck-uade/robot-hmi-app/issues/2) Prototipo navegable en **Figma** (guía en `docs/figma.md`).
- [#3](https://github.com/fieldcheck-uade/robot-hmi-app/issues/3) **Video** de demostración (guion en `docs/escenarios.md`).
- [#4](https://github.com/fieldcheck-uade/robot-hmi-app/issues/4) Decidir qué hace el robot cuando pierde la conexión.
- [#5](https://github.com/fieldcheck-uade/robot-hmi-app/issues/5) Revisión del equipo de lo mergeado en el PR #1.
- [#6](https://github.com/fieldcheck-uade/robot-hmi-app/issues/6) Publicar el APK para la entrega.
