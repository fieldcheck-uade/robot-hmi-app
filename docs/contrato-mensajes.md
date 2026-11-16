# Contrato de mensajes propuesto

> **Es una propuesta del grupo, no un protocolo oficial de Unitree.** Describe los mensajes que
> intercambiarían la app y un puente a bordo del robot (por ejemplo un nodo ROS 2 que traduzca a
> `unitree_sdk2`). El Repository simulado se comporta según este contrato, pero no serializa JSON:
> trabaja con los modelos Kotlin equivalentes (`Telemetry`, `CommandRecord`, `Alert`).

Convenciones: JSON en UTF‑8; tiempos en milisegundos desde época Unix (`ts`); posiciones en metros
sobre el plano de la nave (origen arriba a la izquierda); rumbo en grados, 0 = norte, sentido horario.

## 1. Telemetría (robot → app, 1 Hz)

```json
{
  "type": "telemetry",
  "robot_id": "go2-01",
  "seq": 1842,
  "ts": 1794833562000,
  "connectivity": { "signal_pct": 78, "latency_ms": 56 },
  "battery": { "level_pct": 91.6, "charging": false },
  "op_state": "patrolling",
  "mission": {
    "mission_id": "ronda-perimetral",
    "name": "Ronda perimetral completa",
    "state": "running",
    "progress": 0.16,
    "checkpoints_done": 1,
    "checkpoints_total": 6,
    "next_checkpoint": "Muelle 1"
  },
  "location": { "x": 8.4, "y": 12.1, "zone": "Pasillo perimetral", "at_safe_point": false },
  "motion": { "speed_mps": 1.48, "heading_deg": 25.0 },
  "service": {
    "checkpoints_inspected_shift": 7,
    "anomalies_detected": 0,
    "ambient_temp_c": 19.4
  }
}
```

- `op_state`: `idle` · `patrolling` · `paused` · `waiting_operator` · `returning` · `stopped`
- `mission.state`: `running` · `paused` · `completed` · `cancelled` · `aborted`; `mission` es `null`
  si no se inició ninguna en la sesión.
- `seq` crece de a uno: un salto indica mensajes perdidos. Si la app no recibe telemetría durante
  3 s considera el enlace perdido y marca los datos como antiguos.

## 2. Comando (app → robot)

```json
{
  "type": "command",
  "command_id": 37,
  "robot_id": "go2-01",
  "ts": 1794833570120,
  "command": "start_mission",
  "params": { "mission_id": "ronda-perimetral" }
}
```

| `command` | `params` |
|---|---|
| `start_mission` | `{ "mission_id": "…" }` |
| `pause_mission`, `resume_mission`, `cancel_mission` | `{}` |
| `return_to_safe_point` | `{}` |
| `operational_stop` | `{}` |
| `ack_alert` | `{ "alert_id": 12 }` |

## 3. Respuesta a un comando (robot → app)

Confirmada (el robot **aceptó** la orden; la ejecución se observa después en la telemetría):

```json
{
  "type": "command_response",
  "command_id": 37,
  "ts": 1794833570910,
  "status": "confirmed"
}
```

Rechazada:

```json
{
  "type": "command_response",
  "command_id": 38,
  "ts": 1794833601455,
  "status": "rejected",
  "reason_code": "battery_too_low",
  "reason": "Batería insuficiente (19 %)"
}
```

No existe un mensaje para **sin respuesta**: es un estado que asigna la app cuando pasan 5 s sin
`command_response`. En ese caso no se sabe si la orden llegó; la app no la reenvía sola.

`reason_code` propuestos: `unknown_mission`, `mission_already_active`, `not_idle`,
`not_at_safe_point`, `battery_too_low`, `no_running_mission`, `no_paused_mission`,
`no_active_mission`, `already_at_safe_point`, `already_returning`, `already_stopped`, `busy`.

## 4. Eventos (robot → app)

Alerta:

```json
{
  "type": "alert",
  "alert_id": 12,
  "ts": 1794833618000,
  "alert_type": "obstacle",
  "severity": "critical",
  "title": "Obstáculo bloquea el recorrido",
  "detail": "Pallet atravesado en Muelles de carga. El robot no encontró un desvío y espera una decisión.",
  "requires_decision": true
}
```

- `alert_type`: `low_battery` · `critical_battery` · `obstacle` · `intruder`
- `severity`: `info` · `warning` · `critical`
- La alerta de conexión perdida no viaja por este canal: la genera la app.
- Por privacidad, la alerta `intruder` no incluye imágenes ni datos que identifiquen a la persona.

Cambio de misión:

```json
{
  "type": "mission_event",
  "ts": 1794833690000,
  "mission_id": "ronda-muelles",
  "event": "completed",
  "detail": "Ronda rápida de muelles: 2/2 puntos inspeccionados."
}
```

`event`: `started` · `paused` · `resumed` · `completed` · `cancelled` · `aborted` · `stopped` · `arrived_safe_point`

Los eventos generados durante un corte de enlace se guardan en el robot y se entregan al reconectar,
con su `ts` original.
