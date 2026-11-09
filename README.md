# Flowgate

Java 21 / Spring Boot API gateway: Redis Lua rate limits, Postgres control plane, JWT + hashed API keys, reverse proxy with canary / Resilience4j, Prometheus + Grafana + Jaeger, Next.js dashboard.

Clone and run (needs Docker Desktop + JDK 21, or the bundled `./scripts/mvn.sh`):

```bash
git clone https://github.com/Sarvesh2025/flowgate.git
cd flowgate

export JAVA_HOME="${JAVA_HOME:-$HOME/sdk/jdk-21}"
export PATH="$JAVA_HOME/bin:$HOME/sdk/maven/bin:$PATH"

docker compose up -d
./scripts/mvn.sh -pl gateway spring-boot:run
./scripts/mvn.sh -pl upstreams/orders spring-boot:run
./scripts/mvn.sh -pl upstreams/payments spring-boot:run
cd frontend && npm install && npm run dev
```

Login at http://127.0.0.1:3001 — `admin` / `admin123`.

```bash
curl -s localhost:8080/api/v1/meta
curl -sD - localhost:8080/api/v1/limited | head
curl -sD - localhost:8080/gw/orders | head
```

Ports: gateway `8080` (replica `8083`), orders `8081`, payments `8082`, UI `3001`, Postgres `5432`, Redis `6379`, Toxiproxy `16379` / `8474`, Prometheus `9090`, Grafana `3000` (admin/admin), Jaeger `16686`.

Two instances share Redis:

```bash
SERVER_PORT=8083 FLOWGATE_INSTANCE=b ./scripts/mvn.sh -pl gateway spring-boot:run

for i in $(seq 1 12); do
  port=$((8080 + (i % 2) * 3))
  curl -sS -o /dev/null -w "${port} %{http_code} remaining=%header{x-ratelimit-remaining} inst=%header{x-flowgate-instance}\\n" \
    "http://localhost:${port}/api/v1/limited"
done
```

Redis-down (FAIL_CLOSED). Gateway must use Toxiproxy:

```bash
FLOWGATE_REDIS_PORT=16379 ./scripts/mvn.sh -pl gateway spring-boot:run
./scripts/redis-cut.sh
curl -sD - http://localhost:8080/api/v1/limited   # 503, X-Flowgate-Redis: DOWN-CLOSED
./scripts/redis-heal.sh
```

Optional limiter load (`k6` on the PATH): `k6 run scripts/k6-limit.js`

Images (Compose stays infra-only; routes are `localhost` upstreams). Package first, then:

```bash
./scripts/mvn.sh -DskipTests package
docker build -f docker/Dockerfile.gateway -t flowgate-gateway .
docker build -f docker/Dockerfile.orders -t flowgate-orders .
docker build -f docker/Dockerfile.payments -t flowgate-payments .
docker build -f docker/Dockerfile.ui -t flowgate-ui .
```
