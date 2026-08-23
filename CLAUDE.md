# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

This is a Spring Boot project being built out feature-by-feature via GitHub-issue tickets (see `docs/agents/issue-tracker.md`). It started from Spring Initializr; the first real feature — a booking/appointment scheduling system (parent spec: issue #1) — is in progress, ticket by ticket.

- Group/artifact: `com.vortex:vortexweb`
- Java version: 26
- Spring Boot version: 4.1.1
- Base package: `com.vortex.vortexweb`

### Package layout

- `com.vortex.vortexweb.security` — Spring Security config (`SecurityConfig`) and the seeded-admin-user properties (`AdminUserProperties`, bound from `vortex.admin.*`).
- `com.vortex.vortexweb.admin` — admin-only, authenticated controllers/views (protected by `SecurityConfig` under `/admin/**`).
- `com.vortex.vortexweb.availability` — `AvailabilityRule` and `BlockedPeriod` entities/repositories (no controllers here — admin management of them lives in `com.vortex.vortexweb.admin.AvailabilityAdminController`, following the "admin controllers live in `admin`" convention above).

## Stack

- **Spring Web MVC** (`spring-boot-starter-webmvc`) — controllers/REST endpoints
- **Thymeleaf** (`spring-boot-starter-thymeleaf`) — server-rendered HTML views
- **Spring Security** (`spring-boot-starter-security`) — protects `/admin/**`; a single admin user is seeded in-memory from `vortex.admin.username`/`vortex.admin.password` (no self-registration, no DB-backed users)
- **Spring Data JPA** (`spring-boot-starter-data-jpa`) — persistence, backed by **PostgreSQL** (runtime driver only; no JDBC/datasource config present yet)
- **Spring RestClient** (`spring-boot-starter-restclient`) — outbound HTTP calls to other services
- **Lombok** — enabled via annotation processor in the compiler plugin config
- **Spring Boot DevTools** and **spring-boot-docker-compose** — both optional, runtime-scoped; docker-compose support auto-starts the Postgres container in `compose.yaml` during local dev runs *and* during tests (see Notes below)

## Commands

Use the Maven wrapper (`mvnw`/`mvnw.cmd`) — no globally installed Maven is required.

```bash
# Run the app locally (auto-starts Postgres via compose.yaml through spring-boot-docker-compose)
./mvnw spring-boot:run

# Build (compiles, runs tests, packages jar)
./mvnw package

# Compile only
./mvnw compile

# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=VortexwebApplicationTests

# Run a single test method
./mvnw test -Dtest=VortexwebApplicationTests#contextLoads

# Build a container image (OCI) via Spring Boot's buildpacks support
./mvnw spring-boot:build-image
```

On Windows PowerShell, use `mvnw.cmd` instead of `./mvnw`.

## Local infrastructure

`compose.yaml` defines a single `postgres:latest` service (db `mydatabase`, user `myuser`, password `secret`, no fixed host port). With `spring-boot-docker-compose` on the classpath, Spring Boot starts/stops this container automatically when the app runs via `spring-boot:run` or from an IDE — no manual `docker compose up` needed for local development.

## Notes

- The parent POM (`spring-boot-starter-parent`) is overridden with empty `<license>`/`<developers>`/`<scm>` blocks to prevent unwanted inheritance — keep these empty unless intentionally setting project metadata.
- Lombok's annotation processor is wired explicitly into both the `default-compile` and `default-testCompile` executions of `maven-compiler-plugin` — if adding other annotation processors, they need to be added to both executions the same way.
- `spring.docker.compose.skip.in-tests` is explicitly set to `false` in `application.properties`. Spring Boot's docker-compose support defaults to *skipping* itself during `@SpringBootTest` runs, which otherwise breaks every test (JPA/Postgres autoconfiguration is active from the classpath even before any entities exist, so tests fail to find a datasource). This project's testing strategy relies on real HTTP + real Postgres integration tests, so this must stay `false`.
- Docker Desktop must be running for both `spring-boot:run` and `./mvnw test` — both start the `postgres` service from `compose.yaml` automatically, but only if the Docker daemon itself is already up.
- Primary test seam (established by the admin-authentication ticket, `AdminAuthenticationTests`): `@SpringBootTest` + `@AutoConfigureMockMvc`, exercising the real controller/security-filter-chain/DB stack with nothing internal mocked. Use `spring-security-test`'s `formLogin()`/`authenticated()`/`unauthenticated()` helpers for auth flows.
- `spring.jpa.hibernate.ddl-auto=update` is set in `application.properties`. There's no Flyway/Liquibase migration tool yet — this is the pragmatic default for a greenfield app with no other schema consumers. Revisit (switch to a real migration tool) before this app has real production data to lose.

## Branch workflow

Work on tickets happens on branches, never directly on `main`:

1. Branch off `main`, named `<issue-number>-<short-slug>` (e.g. `3-artist-managed-availability`) — matches GitHub's own `gh issue develop` convention, so it stays traceable to the ticket.
2. Implement the ticket on that branch, committing as work progresses.
3. Before opening a PR, run `./mvnw test` locally and confirm it's green — there's no CI configured yet, so this local run is the only gate.
4. Push the branch and open a PR with `gh pr create`, including `Closes #<issue-number>` in the body so merging auto-closes the ticket.
5. Merge the PR once tests are green — no separate manual review step is required for this workflow.

## Agent skills

### Issue tracker

GitHub Issues via the `gh` CLI, repo `lumontanana/VortEx`. See `docs/agents/issue-tracker.md`.

### Domain docs

Single-context: `CONTEXT.md` + `docs/adr/` at the repo root (created lazily as decisions are made). See `docs/agents/domain.md`.
