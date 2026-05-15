# Brief del Post

## Tesis

Un retry no es gratis. Puede convertir fallas transitorias en éxitos, pero frente a un downstream lento o en caída puede multiplicar carga, empeorar p95/p99 y esconder el problema si solo miramos el success rate final.

## Qué se probó

Se construyó una simulación local con Java 21, Spring Boot 3, Maven, Docker Compose y k6. La app expone un endpoint que llama a un downstream simulado bajo distintas políticas de timeout, retry, backoff, jitter, circuit breaker y bulkhead.

Escenarios simulados:

- Delay fijo.
- Fallas aleatorias.
- Degradación progresiva.

Políticas comparadas:

- Sin retries con timeout bajo.
- Sin retries con timeout comparable al resto de politicas.
- Retry inmediato sin backoff.
- Retry con exponential backoff.
- Retry con jitter.
- Circuit breaker.
- Bulkhead.

## Qué mirar

La comparación no debe quedarse en “cuántos requests terminaron OK”. Hay que mirar:

- Error rate.
- Successful requests per second.
- p95/p99 de todos los intentos.
- p95/p99 solo de requests exitosas.
- Llamadas reales al downstream.
- Retry amplification factor.
- Max inflight downstream como proxy de presión, saturación o cola.

## Trampas de métricas

- Comparar una politica sin retry con timeout bajo contra retries con timeout mayor mezcla dos variables. El baseline justo es `no-retry-standard-timeout`; `no-retry-low-timeout` queda solo como ejemplo de mala configuracion.
- Mirar solo el success rate puede hacer que una política con retries parezca mejor aunque haya duplicado o triplicado la presión sobre el downstream.
- Mirar solo latencia de requests exitosas oculta los intentos fallidos y timeouts que consumieron recursos.
- Promediar latencia suaviza el daño en cola. Para este tema importan p95 y p99.
- Un circuit breaker puede subir errores visibles a corto plazo, pero reducir daño sistémico. No es un fracaso automático.
- Un bulkhead puede mostrar `retry_amplification_factor` menor a 1 porque rechaza antes de llamar al downstream. Eso no significa que resolvio el problema: significa que cambio carga invisible por errores visibles.

## Resultados principales de la corrida editorial

Matriz ejecutada con Docker Compose + k6, `VUS=40`, `Duration=30s`.

- En `fixed-delay`, `no-retry-standard-timeout` tuvo 4400 exitosos, 0 fallidos, `success rps=142.66` y `amplification=1`. `immediate-retry`, `exponential-backoff`, `jitter` y `circuit-breaker` quedaron practicamente iguales. Ahi el retry no agrego valor porque el primer intento ya entraba en presupuesto.
- En `fixed-delay`, `no-retry-low-timeout` fallo 4816 de 4865 requests (`error rate=98.99%`). Ese escenario no demuestra que retry sea mejor: demuestra que el timeout era incorrecto para un downstream de ~220ms.
- En `random-failures`, el baseline sin retry tuvo `error rate=34.81%`, 4554 exitosos y `amplification=1`. `immediate-retry` bajo el error rate a `3.84%`, subio exitosos a 5060 y aumento llamadas reales al downstream a 7761 (`amplification=1.475`). Ahi retry compra disponibilidad a cambio de ~47.5% mas llamadas.
- En `random-failures`, `jitter` tambien bajo el error rate (`3.93%`) pero tuvo menor throughput exitoso que retry inmediato (`137.68` vs `162.71` successful rps) y mayor latencia exitosa p95 (`563ms` vs `360ms`). Con esta simulacion concreta, jitter no fue "gratis"; suavizo el patron pero agrego espera visible.
- En `progressive-degradation`, `no-retry-standard-timeout` ya estaba muy mal: `error rate=98.48%`, solo 59 exitosos y `amplification=1`. `immediate-retry` mantuvo apenas 60 exitosos pero aumento llamadas al downstream de 3880 a 4380 y `amplification=2.92`. En degradacion sostenida, retry amplifico presion sin recuperar throughput util.
- En `progressive-degradation`, `jitter` bajo llamadas frente a retry inmediato (3474 vs 4380) pero siguio con `error rate=94.99%` y solo 60 exitosos. Mitiga algo de presion, no arregla una dependencia degradada.
- En `bulkhead-fixed-delay`, hubo 2114 exitosos y 11994 fallidos, con `amplification=0.15` y `max inflight=16`. El bulkhead protegió el downstream limitando concurrencia, pero el costo fue rechazo masivo arriba.

## Qué no se puede concluir

Este experimento no prueba que una política sea universalmente correcta. No mide una arquitectura real, una red real, pools reales de base de datos ni comportamiento de un proveedor externo. Es una simulación controlada para razonar sobre trade-offs.

Tampoco permite afirmar valores absolutos de capacidad. Los números sirven para comparar políticas bajo la misma carga y el mismo escenario.

## Decisión técnica después de verlo

La decisión razonable no es “usar retries” o “no usar retries”. Es:

- Retry solo en errores plausiblemente transitorios.
- Timeout explícito por intento y presupuesto total por request.
- Backoff con jitter por defecto para evitar sincronización.
- Límite de intentos bajo.
- Circuit breaker o bulkhead cuando el downstream es compartido o crítico.
- Métricas por intento, no solo por request final.

## Señales que miraría en producción

- Ratio de intentos por request.
- Downstream calls por request entrante.
- p95/p99 por intento y por request final.
- Tasa de timeouts por endpoint y por dependencia.
- Estado del circuit breaker.
- Rechazos de bulkhead.
- Tamaño de cola o uso de pool hacia downstream.
- Correlación entre retry rate y caída de throughput exitoso.

## Cómo usar los resultados

Primero generar `results/comparison.csv` y `results/comparison.md` con:

```powershell
.\scripts\run-all.ps1 -BaseUrl http://localhost:18080 -Vus 40 -Duration 30s
```

Después escribir el post con los números reales obtenidos. Si un número no sale del experimento, no incluirlo como evidencia.
