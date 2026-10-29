# Flowgate

Java 21 API gateway (Spring Boot) with Redis + Postgres. Phase 1: infra and mock upstreams only.

```bash
export JAVA_HOME="$HOME/sdk/jdk-21"
export PATH="$JAVA_HOME/bin:$HOME/sdk/maven/bin:$PATH"

docker compose up -d   # needs Docker Desktop running

./scripts/mvn.sh -pl gateway spring-boot:run
./scripts/mvn.sh -pl upstreams/orders spring-boot:run
./scripts/mvn.sh -pl upstreams/payments spring-boot:run

curl -s localhost:8080/actuator/health
curl -s localhost:8080/api/v1/meta
curl -s localhost:8081/api/orders
curl -s localhost:8082/api/payments
```

Ports: gateway `8080`, orders `8081`, payments `8082`, Postgres `5432`, Redis `6379`.

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
