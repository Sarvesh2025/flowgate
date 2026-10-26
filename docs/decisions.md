# Decision log

The human owns every choice. Entries below that say **proposed (used in Phase 1)** are defaults so the repo can boot. Confirm or reverse them before Phase 2.

## D-001 — Servlet Spring MVC + JPA, not WebFlux

- **Question:** Should the gateway be WebFlux (`WebFilter`) or Spring MVC (servlet filters)?
- **Options:**
  1. WebFlux + WebClient streaming. Matches “filter chain” language. JPA is blocking, so control-plane DB calls need `boundedElastic`. Interviewers often ask about that mix.
  2. Spring MVC + `OncePerRequestFilter` + JPA on Tomcat. Hibernate/JPA is the recruiter default. `WebClient` can still proxy later. Streaming has a few more caveats than WebFlux.
- **Choice (proposed, used in Phase 1):** option 2.
- **Why:** one programming model for JPA, security, and filters while you learn the domain. We can still call the chain a filter chain. Reverse this before rate limiting if you want a reactive gateway.
- **Interview:** “I chose MVC because the control plane is JPA/Flyway; the data plane will use WebClient for proxying. I know Cloud Gateway is WebFlux.”

## D-002 — Maven multi-module, Java 21, Spring Boot 3.4

- **Question:** Build tool and versions.
- **Options:** Maven vs Gradle; Java 17 vs 21.
- **Choice (proposed, used in Phase 1):** Maven, Java 21, Spring Boot 3.4.x.
- **Why:** Maven is what most Java interview loops expect. Java 21 is current LTS. Confirm if you prefer Gradle.

## D-003 — Compose starts only Postgres and Redis

- **Question:** Should `docker compose up` also start the Java apps?
- **Choice (proposed, used in Phase 1):** infra only. Apps run with `./scripts/mvn.sh` (Dockerized Maven) until we add Dockerfiles in a later slice.
- **Why:** fewer moving parts while you inspect Flyway and health.

## D-004 — Flyway schema exists before JPA entities

- **Question:** Hibernate `ddl-auto` vs Flyway as source of truth.
- **Choice:** Flyway owns tables. `ddl-auto=none`. Entities come in Phase 3.
- **Why:** recruiters want to see SQL you wrote, not Hibernate generating the schema.

## D-005 — Light tests and docs

- **Question:** How much automated testing and documentation?
- **Choice:** Not a test-heavy or docs-heavy repo. Prove slices with curl/Compose. At most a couple of later checks that prove Lua atomicity or two-instance sharing. One README to run the app. Decisions stay in this file. No ADR folder, no per-class unit tests.

## D-006 — Token bucket first

- **Question:** Which limiter to implement first?
- **Choice:** Token bucket (slice 6). Other algorithms in later slices, same `Decision` return type.

## D-007 — Default algorithm TOKEN_BUCKET

- **Question:** Which algorithm does `/api/v1/limited` use until you change yaml?
- **Choice:** `TOKEN_BUCKET`. Set `flowgate.ratelimit.algorithm: SLIDING_WINDOW_COUNTER` to switch. Restart the gateway after changing it.
