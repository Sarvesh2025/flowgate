# Flowgate

Java 21 API gateway (Spring Boot) with Redis + Postgres. Compose also runs Prometheus and Grafana.

```bash
export JAVA_HOME="$HOME/sdk/jdk-21"
export PATH="$JAVA_HOME/bin:$HOME/sdk/maven/bin:$PATH"

docker compose up -d   # needs Docker Desktop running

./scripts/mvn.sh -pl gateway spring-boot:run
./scripts/mvn.sh -pl upstreams/orders spring-boot:run
./scripts/mvn.sh -pl upstreams/payments spring-boot:run

cd frontend && npm install && npm run dev   # UI on http://localhost:3001
# login admin / admin123

curl -s localhost:8080/actuator/health
curl -s localhost:8080/api/v1/meta
curl -s localhost:8081/api/orders
curl -s localhost:8082/api/payments
```

Ports: gateway `8080` (replica `8083`), orders `8081`, payments `8082`, Next.js UI `3001`, Postgres `5432`, Redis `6379`, Toxiproxy Redis `16379` / API `8474`, Prometheus `9090`, Grafana `3000` (admin/admin), Jaeger `16686`.

Two instances share Redis (Phase 8). Alternate curls; remaining should drop across **both** ports:

```bash
./scripts/mvn.sh -pl gateway spring-boot:run
# other terminal:
SERVER_PORT=8083 FLOWGATE_INSTANCE=b ./scripts/mvn.sh -pl gateway spring-boot:run

for i in $(seq 1 12); do
  port=$((8080 + (i % 2) * 3))
  curl -sS -o /dev/null -w "${port} %{http_code} remaining=%header{x-ratelimit-remaining} inst=%header{x-flowgate-instance}\\n" \
    "http://localhost:${port}/api/v1/limited"
done
```

Redis-down (FAIL_CLOSED by default). Point the gateway at Toxiproxy, then cut:

```bash
FLOWGATE_REDIS_PORT=16379 FLOWGATE_INSTANCE=a ./scripts/mvn.sh -pl gateway spring-boot:run
./scripts/redis-cut.sh
curl -sD - http://localhost:8080/api/v1/limited   # 503, X-Flowgate-Redis: DOWN-CLOSED
./scripts/redis-heal.sh
```

Set a rule (or yaml `flowgate.ratelimit.redis-down-policy`) to `FAIL_OPEN` to keep serving from an in-process window while Redis is cut.

```bash
curl -s localhost:8080/actuator/prometheus | head
curl -sN localhost:8080/api/v1/metrics/stream
# Grafana: http://localhost:3000  (folder Flowgate)
# Jaeger:  http://localhost:16686  (service flowgate-gateway)
```

Reverse proxy (Phase 5): `GET /gw/orders` and `GET /gw/payments` rewrite to the mock upstreams. Headers: `X-Correlation-Id`, `X-Flowgate-Route`, `X-Flowgate-Upstream` (round-robin / canary), `X-Flowgate-Cache`.

```bash
curl -sD - http://localhost:8080/gw/orders
curl -sD - http://localhost:8080/gw/orders/ord-1
curl -sD - http://localhost:8080/gw/payments
```

Rate limit (Phase 2): `GET /api/v1/limited` — 10 requests per 1000 ms. Switch algorithm in `application.yml` (`flowgate.ratelimit.algorithm`), then restart.

| Algorithm | Use when |
|---|---|
| `TOKEN_BUCKET` | You want a steady rate and short bursts |
| `SLIDING_WINDOW_COUNTER` | Cheap and smoother than a clock minute |
| `SLIDING_WINDOW_LOG` | You need an exact count (more Redis memory) |
| `FIXED_WINDOW` | Simplest counter; can double at the window edge |

```bash
for i in $(seq 1 15); do
  curl -sS -o /dev/null -w "%{http_code} remaining=%header{x-ratelimit-remaining}\\n" \
    http://localhost:8080/api/v1/limited
done
```
