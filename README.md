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
