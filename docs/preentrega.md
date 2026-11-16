# Preentrega — Centinela Go2

> Los datos técnicos del Go2 de este documento son los que publica el fabricante según nuestro
> relevamiento; **hay que verificarlos contra las fuentes de la sección 7 antes de entregar**, porque
> cambian según la versión (Air / Pro / EDU) y la revisión del producto.

## 1. Problema

Un centro logístico de mediana escala (nave de ~60 × 40 m con muelles de carga, estanterías y un
depósito de mercadería de alto valor) queda sin actividad entre las 22:00 y las 06:00. En ese horario
hay que verificar periódicamente que los portones y muelles estén cerrados, que no haya personas
dentro y que no haya anomalías (objetos caídos, pasillos bloqueados, temperatura fuera de rango).

**Cómo se resuelve hoy.** Un guardia hace la ronda a pie cada una o dos horas y marca puntos de
control con un lector. Entre rondas la supervisión depende de cámaras fijas con puntos ciegos entre
estanterías. Los problemas: las rondas son repetitivas y se saltean o acortan, el guardia que recorre
deja la garita sin atención, queda solo frente a una posible intrusión, y el registro de la ronda es
pobre (solo "pasé por el punto").

**Con el robot.** El Go2 hace la ronda de forma autónoma y repetible; el guardia permanece en la
garita, supervisa desde la app y solo interviene cuando el robot pide una decisión o cuando hay que
ir físicamente a verificar algo.

## 2. Entorno y recorrido

```
 0 m                                                          60 m
  ┌────────────────────────────────────────────────────────────┐
  │  MUELLES DE CARGA      (M1)──────────────(M3)────(Portón E)│
  ├────┬───────────────────────────────────────────────────┬───┤
  │    │  ┌───────────────┐      ┌──────────────┐          │   │
  │(Acc│  │ ESTANTERÍAS A │ pas. │ ESTANTERÍAS B│          │   │
  │eso)│  │               │ cen- │              │          │   │
  │    │  └───────────────┘ tral └──────────────┘ ┌────────┴───┤
  ├────┤                                          │ DEPÓSITO   │
  │BASE│─────────────(Pasillo central)────────────│ ALTO VALOR │
  │ ▣  │                                          │    (D)     │
  └────┴──────────────────────────────────────────┴────────────┘
 40 m
  ▣ base de carga = punto seguro      ( ) punto de control
```

Rondas definidas en la app:

| Ronda | Puntos de control |
|---|---|
| Perimetral completa | Acceso peatonal → Muelle 1 → Muelle 3 → Portón este → Depósito de alto valor → Pasillo central |
| Rápida de muelles | Muelle 1 → Muelle 3 |
| Verificación de depósito de alto valor | Pasillo central → Depósito de alto valor |

Toda ronda sale de la base de carga y termina en ella. La base es además el **punto seguro**: un
lugar fuera de las vías de circulación donde el robot puede quedar detenido sin estorbar.

## 3. Actores

| Actor | Rol | Qué necesita |
|---|---|---|
| Operador (guardia en garita) | Usa la app: lanza rondas, supervisa, decide ante incidentes | Estado del robot de un vistazo, alertas claras, saber si una orden llegó |
| Supervisor de seguridad | Revisa lo ocurrido en el turno | Historial de rondas, alertas y órdenes |
| Personal presente (limpieza, mantenimiento, choferes nocturnos) | No usa la app; comparte el espacio con el robot | Que el robot sea previsible, mantenga distancia y se pueda detener |
| Tercero no autorizado | Es detectado por el robot | — (ver privacidad en `riesgos.md`) |

**HRI con las personas presentes.** El robot circula a paso de persona (1,5 m/s como máximo en la
ronda), se detiene a distancia cuando detecta a alguien y no se acerca ni lo sigue. La decisión de
qué hacer con una detección es siempre humana: el robot avisa y espera.

**Cuándo debe intervenir un humano:** obstáculo que el robot no puede rodear, detección de una
persona, batería baja, pérdida de conexión prolongada, y cualquier orden rechazada o sin respuesta.

## 4. Misión

1. El operador elige el robot y la ronda, y confirma el inicio.
2. El robot sale de la base, recorre los puntos de control en orden y se detiene unos segundos en
   cada uno para inspeccionarlo.
3. La app muestra progreso, posición y variables del servicio en tiempo real.
4. Al terminar, el robot vuelve a la base y queda cargando; la ronda queda en el historial.

## 5. Elección del robot: Unitree Go2 (versión EDU)

**Por qué un robot y no más cámaras fijas o más guardias.** Las cámaras fijas no cubren los pasillos
entre estanterías sin multiplicar la instalación, y no "van a ver" algo puntual. Sumar guardias
resuelve la cobertura pero no el carácter repetitivo ni el riesgo de la ronda en solitario. Un robot
móvil agrega un sensor que se desplaza y deja al guardia en la garita.

**Por qué Go2 y no G1.** La tarea es recorrer e inspeccionar, no manipular: no hacen falta brazos ni
manos. Un cuadrúpedo es más estable, más bajo (pasa bajo cintas y entre pallets), tiene mejor
autonomía para la tarea y cuesta bastante menos que un humanoide. El G1 se justificaría si hubiera
que abrir puertas con picaporte o manipular objetos.

**Por qué la versión EDU.** Es la que admite desarrollo propio (SDK y ROS 2) y cómputo adicional a
bordo; las versiones Air y Pro están pensadas para control desde la app del fabricante.

| Característica (Go2, según fabricante — verificar) | Valor aproximado |
|---|---|
| Peso | ~15 kg |
| Sensores de serie | LiDAR 4D (L1) de 360° × 90°, cámara frontal gran angular, IMU |
| Velocidad | ~2,5 m/s (Air) a ~3,7 m/s (EDU); la ronda usa 1,5 m/s |
| Autonomía | 1–2 h con batería estándar (8000 mAh); 2–4 h con batería de larga duración (15000 mAh) |
| Carga útil | ~7–8 kg |
| Movilidad | Escalones y desniveles moderados (~15 cm), pendientes hasta ~30–40° |
| Conectividad | Wi‑Fi y Bluetooth; 4G según versión |
| Desarrollo | `unitree_sdk2` (C++/Python) sobre DDS, compatible con ROS 2 (EDU) |

**Restricciones.** Autonomía limitada (hay que planificar recargas entre rondas); no es apto para
lluvia intensa ni polvo abundante sin protección adicional; no abre puertas; la cámara de serie tiene
rendimiento limitado con poca luz.

| Capacidad | ¿Disponible de serie? |
|---|---|
| Caminar, evitar obstáculos, telemetría básica (batería, IMU, estado) | Sí |
| Control por SDK de alto nivel (velocidad, posturas) | Sí (EDU) |
| Navegación autónoma por puntos en un mapa de la nave | **Requiere desarrollo**: mapeo y localización (SLAM) y planificador sobre ROS 2 |
| Detección de personas | **Requiere desarrollo y cómputo**: modelo de visión en un módulo tipo Jetson |
| Visión nocturna confiable | **Requiere equipamiento**: cámara térmica o iluminación IR |
| Medir temperatura ambiente | **Requiere equipamiento**: sensor adicional |
| Regreso y acople automático a la base de carga | **Requiere equipamiento**: base de carga automática y su integración |
| Parada de emergencia remota certificada | **Requiere equipamiento**; la app no la reemplaza |

## 6. Telemetría y comandos

**Telemetría (1 mensaje por segundo):**

| Grupo | Datos |
|---|---|
| Conectividad | Estado del enlace, señal (%), latencia (ms) |
| Batería | Nivel (%), cargando sí/no |
| Estado operativo | En espera, patrullando, en pausa, esperando decisión, regresando a base, detenido |
| Misión / progreso | Ronda, estado, avance (%), puntos de control hechos/total, próximo punto |
| Ubicación | Posición (x, y en metros), zona, si está en el punto seguro |
| Movimiento | Velocidad (m/s), rumbo (°) |
| Alertas | Lista con severidad, hora y si requieren decisión |
| Última actualización | Hora del último dato y su antigüedad |
| **Servicio (3 variables propias)** | Puntos inspeccionados en el turno · anomalías detectadas · temperatura ambiente |

**Comandos:**

| Orden | Habilitada cuando… | El robot la rechaza si… |
|---|---|---|
| Iniciar misión | Conectado, en espera, en la base, batería ≥ 25 %, sin misión activa | No se cumple lo anterior (estado real) |
| Pausar misión | Está patrullando | No hay misión en ejecución |
| Reanudar misión | Hay una misión pausada | No hay misión pausada |
| Cancelar misión | Hay una misión activa | No hay misión activa |
| Regresar a punto seguro | No está en la base ni regresando (disponible también sin conexión) | Ya está en la base o regresando |
| Detención operativa | Se está moviendo, en pausa o esperando; **siempre** disponible sin conexión | Ya está detenido |
| Reconocer alerta | La alerta no está reconocida y hay conexión | — |

Cada orden pasa por los estados **pendiente → confirmada / rechazada / sin respuesta** (5 s de
espera). "Confirmada" significa que el robot la aceptó, no que ya la ejecutó: la ejecución se
verifica en la telemetría. Iniciar, cancelar y regresar piden confirmación; la detención no.

Riesgos: ver [`riesgos.md`](riesgos.md). Flujos del Figma: ver [`figma.md`](figma.md).

## 7. Fuentes técnicas

- Unitree Robotics — página de producto del Go2: <https://www.unitree.com/go2>
- Unitree — centro de documentación para desarrolladores del Go2: <https://support.unitree.com/home/en/developer>
- `unitree_sdk2` (SDK oficial): <https://github.com/unitreerobotics/unitree_sdk2>
- `unitree_ros2` (integración con ROS 2): <https://github.com/unitreerobotics/unitree_ros2>
- Unitree — página de producto del G1 (para la comparación): <https://www.unitree.com/g1>
