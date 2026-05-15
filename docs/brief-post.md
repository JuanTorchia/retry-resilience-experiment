# Brief del Post

## Tesis sugerida

Los retries no son una mejora automática. En fallas transitorias pueden comprar disponibilidad a costa de más llamadas downstream. En degradación sostenida pueden amplificar presión y no recuperar throughput útil. La decisión correcta no sale de mirar solo p95: hay que mirar success rate, amplification factor, presupuesto de timeout, max inflight y tipo de falla.

## Qué se probó

Se construyó una simulación local con Java 21, Spring Boot 3, Maven, Docker Compose y k6. La app expone un endpoint que llama a un downstream simulado bajo distintas políticas de timeout, retry, backoff, jitter, circuit breaker y bulkhead.

Políticas:

- `no-retry-low-timeout`: ejemplo de mala configuración, no baseline justo.
- `no-retry-standard-timeout`: baseline principal; un intento con el mismo timeout por intento que las políticas con retry.
- `immediate-retry`: hasta tres intentos, sin backoff.
- `exponential-backoff`: hasta tres intentos, con pausas crecientes.
- `jitter`: backoff exponencial con variación aleatoria.
- `circuit-breaker`: jitter más apertura del circuito ante fallas.
- `bulkhead`: jitter más límite de concurrencia hacia downstream.

Escenarios:

- `fixed-delay`: dependencia lenta pero estable.
- `random-failures`: errores transitorios simulados.
- `progressive-degradation`: la dependencia se vuelve cada vez más lenta a medida que recibe llamadas.
- `latency-tail-spike`: la mayoría de llamadas son rápidas, pero aparece cola lenta predecible.

## Qué NO se puede concluir

Este experimento no mide producción, una red real, un proveedor real, pools de conexiones reales ni capacidad absoluta. No permite afirmar “esta política escala a X RPS” ni “este timeout sirve para cualquier sistema”.

Tampoco permite comparar librerías de resiliencia. El objetivo no es benchmarkear Resilience4j, Spring Boot o k6. El objetivo es aislar el efecto metodológico de retries, timeouts y límites de concurrencia.

## Resultados fuertes

Son resultados defendibles si aparecen de forma consistente en corridas `editorial`:

- `random-failures`: retries pueden mejorar success rate, pero suben `downstream_calls` y `retry_amplification_factor`.
- `progressive-degradation`: retries pueden bajar error rate aparente durante un tramo, pero consumen más intentos y pueden no recuperar throughput útil.
- `latency-tail-spike`: un retry puede rescatar requests que cayeron en una cola lenta, pero el intento original ya ejerció presión downstream.
- `circuit-breaker` y `bulkhead`: pueden aumentar rechazos visibles mientras reducen presión o concurrencia, lo cual puede ser deseable para proteger el sistema.

## Trampas de interpretación

- Comparar retries contra `no-retry-low-timeout` es injusto: muestra mala configuración, no resiliencia.
- Mirar solo p95 de requests exitosas oculta intentos fallidos, timeouts y presión real sobre downstream.
- Mirar solo success rate oculta amplificación. Un resultado con más éxitos puede estar comprándolos con demasiadas llamadas extra.
- Mirar solo error rate penaliza circuit breakers y bulkheads, porque hacen visible una falla que antes se convertía en cola.
- Promediar latencia borra el fenómeno que importa. Usar p95/p99.

## Tablas y gráficos recomendados

- Tabla principal por política y escenario: success rate, error rate, successful RPS, amplification factor, downstream calls, max inflight, timeout count.
- Gráfico de barras: `retry_amplification_factor` por política.
- Gráfico de barras o líneas: success rate vs amplification factor en `random-failures`.
- Gráfico separado para `progressive-degradation`: successful RPS y max inflight.
- Tabla corta de `circuit_breaker_rejected` y `bulkhead_rejected` para explicar que rechazo explícito no siempre es peor que cola implícita.

## Conclusión sobria para equipos backend

Usaría retries solo con presupuesto total de tiempo, timeout por intento, límite bajo de intentos y backoff con jitter. Mediría intentos por request y llamadas downstream por request como métricas de primer nivel. Para dependencias compartidas o críticas agregaría circuit breaker o bulkhead, aceptando que pueden aumentar errores visibles para reducir daño sistémico.

La pregunta correcta no es “¿retry sí o no?”. Es “¿qué tipo de falla estoy intentando absorber, cuánto presupuesto tengo y qué presión extra puedo permitirme ejercer sobre la dependencia?”.

## Cómo regenerar evidencia

Smoke:

```powershell
.\scripts\run-lab.ps1 -Mode smoke
```

Editorial:

```powershell
.\scripts\run-lab.ps1 -Mode editorial
```

Los números pueden variar por CPU, Docker, JVM warmup y aleatoriedad. La interpretación debería mantenerse si el fenómeno es real. Si cambia con facilidad, esa inestabilidad también es un resultado editorial honesto.
