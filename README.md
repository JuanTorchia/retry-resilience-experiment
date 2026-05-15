# Retry Lab

Laboratorio reproducible para medir cuándo un retry mejora resiliencia y cuándo amplifica una caída.

El experimento es una simulación local. No representa producción, incidentes reales ni capacidad real de un proveedor. Sirve para comparar políticas bajo las mismas condiciones controladas.

## Qué prueba

La app Spring Boot expone `GET /api/work`. Cada request llama a un downstream simulado con una política configurable:

- `no-retry-low-timeout`: 1 intento, timeout bajo.
- `no-retry-standard-timeout`: 1 intento, mismo timeout que las politicas con retry.
- `immediate-retry`: hasta 3 intentos, sin backoff.
- `exponential-backoff`: hasta 3 intentos, backoff 75 ms y 150 ms.
- `jitter`: hasta 3 intentos, exponential backoff con jitter.
- `circuit-breaker`: jitter + circuit breaker.
- `bulkhead`: jitter + límite de concurrencia hacia downstream.

Escenarios de downstream:

- `fixed-delay`: delay fijo de 220 ms.
- `random-failures`: delay de 120 ms y fallas aleatorias simuladas.
- `progressive-degradation`: el delay sube con cada llamada real al downstream.

## Métricas

El laboratorio mide:

- total requests
- successful requests
- failed requests
- error rate
- successful requests per second
- p95/p99 de todos los intentos
- p95/p99 solo de requests exitosas
- cantidad de llamadas reales al downstream
- retry amplification factor
- max inflight downstream y observación de saturación/cola

La métrica más importante para evitar autoengaño es `retry_amplification_factor = downstream_calls / total_requests`.

## Ejecutar local

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
.\scripts\run-all.ps1 -BaseUrl http://localhost:18080 -Vus 40 -Duration 30s
```

Resultados:

- `results/raw/*-summary.json`: salida k6 por corrida.
- `results/raw/*-app-metrics.json`: métricas internas de la app por corrida.
- `results/comparison.csv`: tabla mecánica para análisis.
- `results/comparison.md`: tabla legible para revisión.

## Ejecutar con Docker Compose

```powershell
docker compose up -d --build retry-lab
.\scripts\run-all.ps1 -UseDockerK6 -Vus 40 -Duration 30s
docker compose down
```

Si la primera corrida falla porque la app todavía está arrancando, repetí el `run-all.ps1`. No hay base de datos: PostgreSQL no agrega señal a esta hipótesis.

## Endpoint manual

```powershell
Invoke-RestMethod -Method Post http://localhost:18080/api/reset
Invoke-RestMethod "http://localhost:18080/api/work?policy=jitter&scenario=random-failures"
Invoke-RestMethod http://localhost:18080/api/metrics
```

## Lectura esperada

No busques “ganador universal”. Buscá el trade-off:

- En fallas transitorias, retry puede subir el success rate.
- En lentitud sostenida o degradación progresiva, retry puede multiplicar llamadas reales, subir p95/p99 y consumir capacidad downstream.
- Circuit breaker y bulkhead no “arreglan” el downstream: cambian el modo de falla para limitar daño.

## Limitaciones

- Downstream simulado en el mismo proceso: útil para comparación relativa, no para extrapolar capacidad absoluta.
- Aleatoriedad en `random-failures` y `jitter`: correr más de una vez antes de escribir conclusiones fuertes.
- k6 mide requests HTTP; los percentiles de intentos vienen de instrumentación interna de la app.
- Timeouts cancelan el intento desde el cliente, pero el costo de iniciar el intento ya existió. Esa es parte de la presión que el experimento quiere hacer visible.

## Nota metodologica

`no-retry-low-timeout` existe para mostrar una trampa de configuracion: un timeout demasiado bajo puede hacer fallar todo aunque el downstream responda cerca del presupuesto. Para comparar retries de forma justa, usar `no-retry-standard-timeout` como baseline, porque comparte el mismo timeout por intento que `immediate-retry`, `exponential-backoff`, `jitter`, `circuit-breaker` y `bulkhead`.
