# Los cinco escenarios

Cómo demostrar cada uno en el APK. El **panel de simulación** se abre con el ícono de matraz de la
barra superior. Las capturas están en [`evidencias/`](evidencias/) (tomadas en emulador Pixel 9a).

Preparación común: abrir la app → elegir **Centinela 01**.

## 1. Misión normal

1. Pestaña **Misiones** → "Iniciar esta ronda" en *Ronda rápida de muelles* → "Enviar orden".
2. La franja superior muestra *Iniciar misión: pendiente* y luego *confirmada*.
3. Pestaña **Panel**: estado *Patrullando*, avance, puntos de control y el robot moviéndose en el mapa.
4. A los ~85 s: misión *Completada · 100 %*, estado *En base · cargando*.
5. Pestaña **Historial**: inicio, fin y orden registrados.

Evidencia: `01a-mision-en-curso.png`, `01b-mision-completada.png`.

## 2. Batería baja

1. Con una ronda en curso, panel de simulación → **Forzar batería baja**.
2. Aparece la alerta *Batería baja (19 %)* (advertencia), la batería se pinta en ámbar y la pestaña
   Alertas muestra el contador.
3. Acción disponible en la propia alerta: **Regresar a base** (pide confirmación). La misión pasa a
   *Abortada* y el robot a *Regresando a base*.
4. Variante: si no se hace nada, por debajo de 10 % el robot regresa solo y avisa con una alerta crítica.
5. En la base, *Iniciar misión* queda deshabilitada con el motivo "Batería insuficiente (mín. 25 %)"
   hasta que recarga.

Evidencia: `02-bateria-baja.png`.

## 3. Desconexión

1. Con una ronda en curso, panel de simulación → activar **Cortar el enlace** y cerrar el panel.
2. A los 3 s: indicador *Sin conexión*, franja roja "Sin datos del robot hace N s", datos atenuados
   con la etiqueta *Dato antiguo*, robot dibujado hueco en el mapa ("última posición conocida") y
   alerta *Conexión perdida*.
3. Las órdenes de misión quedan deshabilitadas ("Sin conexión: datos desactualizados"). Tocar
   **Detener**: queda *pendiente* y a los 5 s pasa a **sin respuesta** ("No se sabe si la orden llegó").
4. Panel de simulación → desactivar **Cortar el enlace**: vuelve *Conectado*, la telemetría salta al
   estado real (el robot siguió avanzando) y el historial registra "Conexión recuperada tras N s".

Evidencia: `03-desconexion.png`.

## 4. Incidente: obstáculo

1. Con una ronda en curso, panel de simulación → **Poner un obstáculo en el recorrido**.
2. Estado *Esperando decisión*, misión *Pausada*, alerta crítica con la etiqueta *Requiere decisión*.
3. El operador elige: **Reanudar por ruta alternativa**, **Regresar a base** o **Cancelar misión**.
   Al aceptarse la orden, la alerta queda cerrada con la decisión tomada.

Evidencia: `04-incidente-obstaculo.png`.

## 5. Escenario propio: persona detectada fuera de horario

1. Con una ronda en curso, panel de simulación → **Persona detectada fuera de horario**.
2. El robot se detiene a distancia (*Esperando decisión*), suma 1 a *Anomalías* y emite una alerta
   crítica. No se muestran ni guardan imágenes.
3. El operador elige: **Avisar a guardia** (deja asentado el aviso en el historial; el aviso real se
   hace por radio o teléfono), **Falsa alarma: reanudar** o **Regresar a base**.

Evidencia: `05-escenario-propio-persona.png`.

## Extra: orden rechazada

Panel de simulación → **Rechazar órdenes** → enviar cualquier orden: queda *Rechazada* con el motivo
en la franja superior, en la pestaña Control y en el historial. Sin forzarlo, el robot también
rechaza por su cuenta órdenes incoherentes con su estado real.

Otras capturas: `00-seleccion-robot.png`, `06-historial.png`, `07-control.png`.
