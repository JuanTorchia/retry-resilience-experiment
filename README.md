# Retry Lab

Reproducible lab for studying when retries improve resilience and when they amplify failure.

Languages:

- [English](#english)
- [Español](#español)

---

## English

This experiment is a local simulation. It does not represent production, real incidents, or the real capacity of any provider. It is meant to compare policies under controlled conditions and support sober technical conclusions.

### What It Tests

The Spring Boot app exposes `GET /api/work`. Each request calls a simulated downstream service with a configurable policy:

- `no-retry-low-timeout`: 1 attempt, low timeout. This is an intentionally bad configuration, not the fair baseline.
- `no-retry-standard-timeout`: 1 attempt, same per-attempt timeout as retry policies. This is the main baseline.
- `immediate-retry`: up to 3 attempts, no backoff.
- `exponential-backoff`: up to 3 attempts, 75 ms and 150 ms backoff.
- `jitter`: up to 3 attempts, exponential backoff with jitter.
- `circuit-breaker`: jitter + circuit breaker.
- `bulkhead`: jitter + downstream concurrency limit.

Downstream scenarios:

- `fixed-delay`: fixed 220 ms delay.
- `random-failures`: 120 ms delay plus simulated random failures.
- `progressive-degradation`: load-sensitive degradation; delay increases with every real downstream call.
- `latency-tail-spike`: most calls are fast, but every sixth call simulates a slow tail-latency spike.

### Metrics

The lab measures:

- total requests
- successful requests
- failed requests
- success rate
- error rate
- successful requests per second
- p95/p99 for all attempts
- p95/p99 for successful requests only
- real downstream calls
- retry amplification factor
- retry attempts per request
- timeout count
- circuit breaker and bulkhead rejections
- max inflight downstream and concurrency observation

The most important metric for avoiding self-deception is:

```text
retry_amplification_factor = downstream_calls / total_requests
```

Important methodological notes:

- `all_attempt_p95_ms` and `all_attempt_p99_ms` are capped by the caller timeout when attempts time out. They are not full downstream service-time percentiles.
- The lab uses `future.cancel(true)` to cancel timed-out attempts. Many HTTP, DB, or queue systems may keep doing downstream work after the client gives up. The experiment counts initiated downstream calls, but it may underestimate residual post-timeout work.
- `max_inflight_downstream` and `concurrency_observation` are observed concurrency signals. They do not prove CPU, network, connection pool, or database saturation by themselves.
- `successful_requests_per_second` measures useful work observed under this closed k6 load, not the general maximum capacity of the system.

### Run Locally

Requirements:

- Java 21
- Maven
- k6

```powershell
mvn test
mvn spring-boot:run
```

In another terminal:

```powershell
.\scripts\run-all.ps1 -Mode smoke
```

### Run With Docker Compose

Fast smoke run:

```powershell
.\scripts\run-lab.ps1 -Mode smoke
```

Editorial run:

```powershell
.\scripts\run-lab.ps1 -Mode editorial
```

`smoke` uses 5 VUs for 5 seconds per scenario. `editorial` uses 40 VUs for 60 seconds per scenario. The scripts clean `results/raw/*.json`, `results/comparison.csv`, and `results/comparison.md` before each matrix run.

There is no database: PostgreSQL does not add signal to this hypothesis.

### Results

- `results/raw/*-summary.json`: k6 summary for each run.
- `results/raw/*-app-metrics.json`: internal app metrics for each run.
- `results/comparison.csv`: mechanical comparison table for analysis.
- `results/comparison.md`: readable comparison table.

To publish exact numbers, run at least three editorial runs:

```powershell
.\scripts\run-lab.ps1 -Mode editorial
```

Then report median/range, or explicitly state that `results/comparison.*` is a representative run.

### Manual Endpoint Checks

```powershell
Invoke-RestMethod -Method Post http://localhost:18080/api/reset
Invoke-RestMethod "http://localhost:18080/api/work?policy=jitter&scenario=random-failures"
Invoke-RestMethod http://localhost:18080/api/metrics
```

### Expected Reading

Do not look for a universal winner. Look for the trade-off:

- During transient failures, retries can improve success rate.
- During sustained latency or load-sensitive degradation, retries can multiply real downstream calls, increase p95/p99, and consume downstream capacity.
- During tail latency, retries can turn some timeouts into successes, but the original slow attempt already consumed capacity.
- Circuit breakers and bulkheads do not "fix" the downstream. They change the failure mode to limit damage.
- Retries should be discussed separately from containment mechanisms. Circuit breakers and bulkheads may increase visible errors while reducing real downstream calls or concurrency.

### Limitations

- The downstream is simulated in the same process: useful for relative comparison, not for extrapolating absolute capacity.
- `random-failures` and `jitter` contain randomness: run more than once before making strong claims. The shape of the results should hold, not every exact number.
- k6 measures HTTP requests; attempt percentiles come from internal app instrumentation.
- The canonical metrics for the post are the internal app outputs in `results/*`; the k6 summary is supporting evidence.
- Timeouts cancel the caller-side attempt, but the cost of initiating the attempt already existed. That is part of the pressure the experiment is meant to expose.
- `progressive-degradation` does not represent the same external failure identically across all policies. It represents the feedback loop where more retries can accelerate degradation.

---

## Español

Laboratorio reproducible para medir cuándo un retry mejora resiliencia y cuándo amplifica una caída.

Este experimento es una simulación local. No representa producción, incidentes reales ni capacidad real de un proveedor. Sirve para comparar políticas bajo condiciones controladas y para escribir conclusiones técnicas sobrias.

### Qué prueba

La app Spring Boot expone `GET /api/work`. Cada request llama a un downstream simulado con una política configurable:

- `no-retry-low-timeout`: 1 intento, timeout bajo. Es una mala configuración intencional, no el baseline justo.
- `no-retry-standard-timeout`: 1 intento, mismo timeout por intento que las políticas con retry. Es el baseline principal.
- `immediate-retry`: hasta 3 intentos, sin backoff.
- `exponential-backoff`: hasta 3 intentos, backoff de 75 ms y 150 ms.
- `jitter`: hasta 3 intentos, exponential backoff con jitter.
- `circuit-breaker`: jitter + circuit breaker.
- `bulkhead`: jitter + límite de concurrencia hacia downstream.

Escenarios de downstream:

- `fixed-delay`: delay fijo de 220 ms.
- `random-failures`: delay de 120 ms y fallas aleatorias simuladas.
- `progressive-degradation`: degradación sensible a carga; el delay sube con cada llamada real al downstream.
- `latency-tail-spike`: la mayoría de llamadas son rápidas, pero cada sexta llamada simula un pico lento de cola.

### Métricas

El laboratorio mide:

- total requests
- successful requests
- failed requests
- success rate
- error rate
- successful requests per second
- p95/p99 de todos los intentos
- p95/p99 solo de requests exitosas
- cantidad de llamadas reales al downstream
- retry amplification factor
- retry attempts por request
- timeout count
- rechazos por circuit breaker y bulkhead
- max inflight downstream y observación de concurrencia

La métrica más importante para evitar autoengaño es:

```text
retry_amplification_factor = downstream_calls / total_requests
```

Notas metodológicas importantes:

- `all_attempt_p95_ms` y `all_attempt_p99_ms` quedan limitadas por el timeout del caller cuando un intento vence. No son percentiles completos del tiempo real de trabajo del downstream.
- El laboratorio usa `future.cancel(true)` para cancelar intentos vencidos. Muchos sistemas HTTP, DB o colas pueden seguir ejecutando trabajo downstream aunque el cliente abandone. Por eso el experimento cuenta llamadas iniciadas, pero puede subestimar trabajo residual post-timeout.
- `max_inflight_downstream` y `concurrency_observation` son señales de concurrencia observada. No prueban por sí solas saturación de CPU, red, pool de conexiones o base de datos.
- `successful_requests_per_second` mide trabajo útil observado bajo esta carga cerrada de k6, no capacidad máxima general del sistema.

### Ejecutar local

Requisitos:

- Java 21
- Maven
- k6

```powershell
mvn test
mvn spring-boot:run
```

En otra terminal:

```powershell
.\scripts\run-all.ps1 -Mode smoke
```

### Ejecutar con Docker Compose

Smoke rápido:

```powershell
.\scripts\run-lab.ps1 -Mode smoke
```

Corrida editorial:

```powershell
.\scripts\run-lab.ps1 -Mode editorial
```

`smoke` usa 5 VUs durante 5 segundos por escenario. `editorial` usa 40 VUs durante 60 segundos por escenario. Los scripts limpian `results/raw/*.json`, `results/comparison.csv` y `results/comparison.md` antes de cada matriz.

No hay base de datos: PostgreSQL no agrega señal a esta hipótesis.

### Resultados

- `results/raw/*-summary.json`: salida k6 por corrida.
- `results/raw/*-app-metrics.json`: métricas internas de la app por corrida.
- `results/comparison.csv`: tabla mecánica para análisis.
- `results/comparison.md`: tabla legible para revisión.

Para publicar números exactos, correr al menos tres veces:

```powershell
.\scripts\run-lab.ps1 -Mode editorial
```

Después reportar mediana/rango, o declarar explícitamente que `results/comparison.*` es una corrida representativa.

### Endpoint manual

```powershell
Invoke-RestMethod -Method Post http://localhost:18080/api/reset
Invoke-RestMethod "http://localhost:18080/api/work?policy=jitter&scenario=random-failures"
Invoke-RestMethod http://localhost:18080/api/metrics
```

### Lectura esperada

No busques "ganador universal". Buscá el trade-off:

- En fallas transitorias, retry puede subir el success rate.
- En lentitud sostenida o degradación sensible a carga, retry puede multiplicar llamadas reales, subir p95/p99 y consumir capacidad downstream.
- En tail latency, retries pueden convertir algunos timeouts en éxito, pero el intento lento original ya consumió capacidad.
- Circuit breaker y bulkhead no "arreglan" el downstream: cambian el modo de falla para limitar daño.
- Conviene separar retries de mecanismos de contención. Circuit breaker y bulkhead pueden subir errores visibles mientras reducen llamadas reales o concurrencia.

### Limitaciones

- Downstream simulado en el mismo proceso: útil para comparación relativa, no para extrapolar capacidad absoluta.
- Aleatoriedad en `random-failures` y `jitter`: correr más de una vez antes de escribir conclusiones fuertes. La forma de los resultados debería mantenerse, no cada número exacto.
- k6 mide requests HTTP; los percentiles de intentos vienen de instrumentación interna de la app.
- La métrica canónica para el post es la salida interna de la app en `results/*`; el resumen k6 queda como apoyo.
- Timeouts cancelan el intento desde el caller, pero el costo de iniciar el intento ya existió. Esa es parte de la presión que el experimento quiere hacer visible.
- `progressive-degradation` no representa una falla externa idéntica para todas las políticas. Representa el bucle de realimentación donde más retries pueden acelerar la degradación.
