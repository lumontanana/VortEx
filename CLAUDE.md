# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

This is a Spring Boot project being built out feature-by-feature via GitHub-issue tickets (see `docs/agents/issue-tracker.md`). It started from Spring Initializr; the first real feature — a booking/appointment scheduling system (parent spec: issue #1, tickets #2-#8) — is fully implemented. Future features should follow the same spec → tickets → branch-per-ticket workflow.

- Group/artifact: `com.vortex:vortexweb`
- Java version: 26
- Spring Boot version: 4.1.1
- Base package: `com.vortex.vortexweb`

### Package layout

- `com.vortex.vortexweb.security` — Spring Security config (`SecurityConfig`) and the seeded-admin-user properties (`AdminUserProperties`, bound from `vortex.admin.*`).
- `com.vortex.vortexweb.admin` — admin-only, authenticated controllers/views (protected by `SecurityConfig` under `/admin/**`).
- `com.vortex.vortexweb.availability` — `AvailabilityRule` and `BlockedPeriod` entities/repositories (no controllers here — admin management of them lives in `com.vortex.vortexweb.admin.AvailabilityAdminController`, following the "admin controllers live in `admin`" convention above).
- `com.vortex.vortexweb.booking` — the public-facing booking domain: `Appointment`/`AppointmentStatus`/`AppointmentRepository`, the `Slot` value type, `SlotService` (computes open slots from availability rules minus blocked periods minus taken appointments, sliced by `vortex.booking.default-duration-minutes`), and the public `BookingController` (unauthenticated, not under `/admin/**`).
- `com.vortex.vortexweb.notifications` — `NotificationService` interface (`send(to, subject, body)`) abstracting outbound client emails, with `EmailNotificationService` as the sole implementation (calls a transactional email provider's HTTP API via a `RestClient`, configured from `vortex.notifications.email.*`). Callers (e.g. `BookingController`, `AppointmentAdminController`) depend only on the interface; tests that don't care about emails should `@MockitoBean` it rather than let real submissions hit the (fake) configured provider URL.
- `com.vortex.vortexweb.admin.AppointmentAdminController` — lists `PENDING` and `CONFIRMED` appointments and lets the artist: confirm (→ `CONFIRMED`, rejected with 409 if it now overlaps another `CONFIRMED` appointment) or decline (→ `DECLINED`) a pending request; reschedule (moves `startTime`, rejected with 409 on overlap against *other* confirmed appointments), cancel (→ `CANCELLED`), or mark complete (→ `COMPLETED`, no email) a confirmed one. Also serves `/admin/appointments/schedule`: upcoming `CONFIRMED` appointments (future `startTime` only) and a history view (`COMPLETED`/`DECLINED`/`CANCELLED`). Lives in `admin` (not `booking`) per the "admin controllers live in `admin`" convention, even though `Appointment` itself is a `booking` type.
- `com.vortex.vortexweb.home` — the public homepage (spec: issue #16). `HomeController` serves `GET /` as one page with anchor-linked sections (hero/about/portfolio/contact), all content bound from `ArtistProfileProperties` (`vortex.artist.*`) rather than hardcoded — same "content as `@ConfigurationProperties`" pattern as the admin/booking properties. Portfolio is CSS-drawn placeholder tiles (no real photos yet). Styling lives in `src/main/resources/static/style.css`, plain CSS with no build step.

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
- Outbound HTTP to a real provider (currently just the email provider) is stubbed in tests with **WireMock** (`wiremock-standalone`, test-scoped) bound to a dynamic port via `@DynamicPropertySource`, not `MockRestServiceServer` — Spring Boot's auto-configured `RestClient.Builder` bean is `@Scope("prototype")`, so a test can't reliably bind a mock server to the same builder instance a production `@Bean` used to build its `RestClient`. A real embedded server sidesteps that entirely. See `BookingNotificationTests` for the pattern.
- The JDK's default `HttpClient`-backed request factory negotiates HTTP/2 against WireMock's embedded Jetty and gets `RST_STREAM`'d. Tests that talk to a WireMock server must force HTTP/1.1 via `spring.http.clients.imperative.factory=simple` (a `@DynamicPropertySource` entry) — this is a test-only workaround for the local plaintext HTTP/2 negotiation, not something production (HTTPS providers with proper ALPN) needs.
- `EmailNotificationService.send()` catches and logs (not rethrows) any exception from the outbound call. Found by actually running the app: `vortex.notifications.email.base-url` is a placeholder (`https://email-provider.invalid`) until a real provider is configured, and before this fix a failed/unreachable send took the whole request down with a 500 *after* the underlying write (appointment save, confirm, decline, etc.) had already committed — so a real booking would succeed in the DB but look like an error to the user. Every `NotificationService` caller relies on `send()` never throwing; don't reintroduce a propagating exception here without updating all call sites.
- When running the app manually (`spring-boot:run`) *and* the test suite at the same time, both connect to the same docker-compose Postgres container (Boot's docker-compose support reuses an already-running container instead of starting a new one). Manually-created data can collide with fixed test fixtures using "tomorrow's date" — stop the manually-run app before running tests if you hit unexpected 409s in booking/availability tests.
- `vortex.artist.*` (name/bio/instagram-url/email) in `application.properties` are placeholders — swap in the real artist name, bio copy, and contact/social links before launch. The portfolio section on the homepage is CSS-drawn placeholder tiles for the same reason.
- For UI-facing tickets (e.g. the homepage), the automated test suite only covers rendered *content* (MockMvc never applies CSS or lays out a page). Before calling such a ticket done, also run the app and drive it with an ad-hoc Playwright script (see the pattern used for the booking flow and the homepage) to screenshot both a desktop and a mobile viewport — this is a one-time manual verification step, not a committed test or a new project dependency.
- CI (`.github/workflows/ci.yml`) runs `./mvnw test` on `ubuntu-latest` for every PR and push to `main`. GitHub-hosted runners come with Docker already running, so `spring-boot-docker-compose` starting Postgres for tests works there the same as locally — no extra CI-specific database setup needed. No branch protection rule requiring this check has been configured yet; that's a separate, deliberate step (Settings → Branches) if you want it enforced rather than just visible.

## Branch workflow

Work on tickets happens on branches, never directly on `main`:

1. Branch off `main`, named `<issue-number>-<short-slug>` (e.g. `3-artist-managed-availability`) — matches GitHub's own `gh issue develop` convention, so it stays traceable to the ticket. For work with no tracked issue (infra, fixes found while testing), use a descriptive slug instead (e.g. `ci-github-actions`).
2. Implement the ticket on that branch, committing as work progresses.
3. Before opening a PR, run `./mvnw test` locally and confirm it's green.
4. Push the branch and open a PR with `gh pr create`, including `Closes #<issue-number>` in the body so merging auto-closes the ticket.
5. CI (`.github/workflows/ci.yml`) runs `./mvnw test` on every PR and on push to `main` — wait for it to go green (`gh pr checks`) in addition to your own local run.
6. Merge the PR once tests are green — no separate manual review step is required for this workflow.

## Agent skills

### Issue tracker

GitHub Issues via the `gh` CLI, repo `lumontanana/VortEx`. See `docs/agents/issue-tracker.md`.

### Domain docs

Single-context: `CONTEXT.md` + `docs/adr/` at the repo root (created lazily as decisions are made). See `docs/agents/domain.md`.
