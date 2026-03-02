# Momo Login Payload Benchmark (3 runs)

- Scenario: Kotlin JVM client + Python backend
- Payload: login JSON (redacted sample schema)

## Per-run

- Run 1: saving=7.357%, latency_gain=20.833%, rps_gain=26.261%
- Run 2: saving=7.357%, latency_gain=19.551%, rps_gain=24.024%
- Run 3: saving=7.357%, latency_gain=4.377%, rps_gain=4.534%

## Average

- saving_pct_avg: 7.357%
- json_mean_ms_avg: 0.315 ms
- msgpack_mean_ms_avg: 0.267 ms
- latency_gain_pct_avg: 14.92%
- rps_gain_pct_avg: 18.273%
