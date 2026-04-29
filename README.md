# Kitties API

Microservices backend for Kitties, a cat adoption platform for shelters and veterinarians. Maven multi-module monorepo built with Quarkus 3.34.3 and Java 21.

---

## Table of Contents

- [Overview](#overview)
- [Services](#services)
- [Architecture](#architecture)
  - [DMZ Deployment](#dmz-deployment)
  - [Port Map](#port-map)
- [Tech Stack](#tech-stack)
- [Development](#development)
  - [Prerequisites](#prerequisites)
  - [Infrastructure](#infrastructure)
  - [Running services](#running-services)
  - [Security keys](#security-keys)
- [Testing](#testing)
- [Known Patterns](#known-patterns)
- [Environment Variables](#environment-variables)
- [Roadmap](#roadmap)

---

## Overview

Kitties connects adopters with shelters and veterinarians. Each service owns its PostgreSQL schema and communicates via gRPC (synchronous) or Kafka (asynchronous). The gateway is the single entry point exposed to the internet; all other services run in a private network.

---

## Services

### gateway-service — port 8080

Single entry point deployed in the DMZ. Validates JWT before routing to internal services. Public routes bypass JWT validation.

**Public routes (no JWT required):**
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/users` (registration)
- `POST /api/users/activate` (account activation)
- `GET /api/cats`, `GET /api/cats/{id}`
- `GET /api/storage/files/{key}`

**Rate-limited routes** (SmallRye Fault Tolerance `@RateLimit`, per JVM instance):
- `POST /api/auth/login` — 10 req/min
- `POST /api/auth/refresh` — 20 req/min
- `POST /api/storage/upload` — 5 req/min

Exceeding a limit returns HTTP **429 Too Many Requests**.

---

### user-service — port 8081

User account management. Exposes a gRPC server consumed by `auth-service`.

**Endpoints:**
- `POST /users` — Register (status: Pending, sends activation email via Kafka)
- `POST /users/activate` — Activate account, body `{"token":"..."}` (public)
- `GET /users/{email}` — Get by email (JWT required)
- `PUT /users/{email}` — Update (JWT, owner only)

**User lifecycle:** `Pending` → `Active` → `Inactive` / `Banned`

**Activation flow:** registration publishes a `user-registered` Kafka event → `notification-service` sends an email with a link to `FRONTEND_URL/activate?token=...` → frontend calls `POST /users/activate` with the token in the request body (token never exposed in API server logs).

**gRPC:**
```proto
service UserService {
  rpc ValidateCredentials(ValidateCredentialsRequest) returns (ValidateCredentialsResponse);
  rpc GetUserById(GetUserByIdRequest) returns (GetUserResponse);
}
```

---

### auth-service — port 8082

Issues and rotates JWT tokens. Validates credentials via gRPC against `user-service`. The gRPC channel is protected with a shared-secret header (`x-internal-token`). Implements an `IdentityProvider` interface designed for future migration to Keycloak without service-layer changes.

- Access token: 15 min (RSA-2048 signed)
- Refresh token: 7 days (stateful, stored in DB, revocable)
- JWT `groups` claim carries the user role (`User`, `Organization`, `Admin`) for RBAC enforcement downstream

**Endpoints:**
- `POST /auth/login` — Returns access + refresh token
- `POST /auth/refresh` — Rotates both tokens
- `POST /auth/logout` — Revokes refresh token

---

### cat-service — port 8084

Cat profiles for adoption. Images are proxied through the gateway; bucket URLs are never exposed directly.

**Endpoints:**
- `GET /cats?city=X&name=Y` — Search (public, no breed search by design)
- `GET /cats/{id}` — Detail with images (public)
- `POST /cats` — Create profile (JWT)
- `PUT /cats/{id}` — Update (JWT, owner only)
- `DELETE /cats/{id}` — Delete with images (JWT, owner only)
- `POST /cats/{id}/images` — Upload image
- `DELETE /cats/{catId}/images/{imageId}` — Delete image

---

### storage-service — port 8083

S3-compatible file storage. Backend is MinIO in development and Cloudflare R2 in production.

**Endpoints:**
- `POST /storage/upload` — Upload file (jpg/png, max 5 MB)
- `DELETE /storage/{key}` — Delete file
- `GET /storage/files/{key}` — Serve file (proxied through gateway)

---

### notification-service — port 8085

Consumes Kafka events and sends transactional emails via SMTP.

**Topics consumed:**
- `user-registered` → account activation email (link points to `FRONTEND_URL/activate?token=...`)
- `adoption-form-analysed` → adoption result email (accepted / rejected) to the adopter's email from the JWT claim

---

### adoption-service — port 8086

End-to-end adoption workflow. Adopters submit requests and complete screening forms; organizations manage the process, schedule interviews, and record expenses.

**Adoption lifecycle:** `Pending` → `Reviewing` → `Accepted` → `FormCompleted` → `AwaitingPayment` → `Completed` / `Rejected`

**Expenses:** veterinary costs billed to the organization; management fee retained by Kitties.

**Endpoints:**

| Method | Path | Role | Notes |
|--------|------|------|-------|
| `POST` | `/adoptions` | `User` | Create request |
| `GET` | `/adoptions/{id}` | Any (JWT) | Caller must be adopter **or** organization of that request |
| `GET` | `/adoptions/my` | `User` | My requests as adopter |
| `GET` | `/adoptions/organization` | `Organization` | Requests for my organization |
| `PATCH` | `/adoptions/{id}/status` | `Organization` | Update status; org owner only |
| `POST` | `/adoptions/{id}/form` | `User` | Submit screening form |
| `POST` | `/adoptions/{id}/interview` | `Organization` | Schedule interview |
| `POST` | `/adoptions/{id}/adoption-form` | `User` | Submit legal contract |

Roles are enforced via `@RolesAllowed` (SmallRye JWT `groups` claim). Ownership is verified at the service layer; mismatches return **403**.

**Kafka topics:**
- `adoption-form-submitted` (outgoing) — screening form data for analysis
- `adoption-form-analysed` (incoming) — analysis decision (ACCEPTED / REJECTED)

**Intake flow** (added 2026-04-28, lives under `intake/` package alongside the adoption aggregate):

| Method  | Path                                  | Role           | Notes                                                                |
|---------|---------------------------------------|----------------|----------------------------------------------------------------------|
| `POST`  | `/intake-requests`                    | `User`         | User asks an organization to take in a cat (surrender)               |
| `GET`   | `/intake-requests/mine`               | `User`         | My pending/approved/rejected intakes                                 |
| `GET`   | `/intake-requests/organization`       | `Organization` | Intakes addressed to my organization                                 |
| `PATCH` | `/intake-requests/{id}/approve`       | `Organization` | Approves the surrender (only Pending → Approved)                     |
| `PATCH` | `/intake-requests/{id}/reject`        | `Organization` | Rejects with reason; response includes alternative organizations in the same region (looked up via `organization-service` internal endpoint, tolerates lookup failures) |

---

### chat-service — port 8089

Conversations between an adopter and an organization, opened after an intake is approved. REST-only in v1 (WebSocket is upcoming). Each conversation is bound to one intake request.

**Endpoints:**

| Method  | Path                          | Role             | Notes                                                |
|---------|-------------------------------|------------------|------------------------------------------------------|
| `GET`   | `/chats/mine`                 | `User`           | My conversations                                     |
| `GET`   | `/chats/organization`         | `Organization`   | Conversations for my organization                    |
| `GET`   | `/chats/{id}/messages`        | Any (participant)| Message history; participant check at service layer  |
| `POST`  | `/chats/{id}/messages`        | Any (participant)| Send message; bumps `lastMessageAt`                  |
| `POST`  | `/chats/{id}/block`           | `Organization`   | Block the user on this org (idempotent)              |
| `DELETE`| `/chats/{id}/block`           | `Organization`   | Unblock (idempotent)                                 |

**Internal (service-to-service, `X-Internal-Token`)**:
- `POST /chats/internal/conversations` — open a conversation `(intakeRequestId, userId, organizationId)`. Intended caller is `adoption-service` after an intake approval.

**Moderation:** organizations can block a user via `(organizationId, userId)` pair (scope is the pair, not the conversation). Blocked users keep read access but their `POST /chats/{id}/messages` returns **403**. Global user bans (`UserStatus.Banned`) are deferred until a real case appears.

---

## Architecture

### DMZ Deployment

Three isolated networks. Only the gateway is reachable from the internet.

```
                        ┌─────────────────────────────────────────────┐
  Internet              │  PUBLIC NETWORK                             │
  ──────────►  :443 ──► │  gateway-service :8080                      │
                        └──────────────┬──────────────────────────────┘
                                       │  JWT-validated requests
                        ┌──────────────▼──────────────────────────────┐
                        │  PRIVATE NETWORK                            │
                        │                                             │
                        │  user-service        :8081  gRPC :9090      │
                        │  auth-service        :8082  gRPC :9091      │
                        │  cat-service         :8084                  │
                        │  storage-service     :8083                  │
                        │  notification-service :8085                 │
                        │  adoption-service    :8086                  │
                        │  form-analysis-service :8087                │
                        │  organization-service :8088                 │
                        │  chat-service        :8089                  │
                        └──────────────┬──────────────────────────────┘
                                       │
                        ┌──────────────▼──────────────────────────────┐
                        │  DATA NETWORK                               │
                        │                                             │
                        │  PostgreSQL  :5432                          │
                        │  Kafka       :9092                          │
                        │  MinIO       :9000 / :9001                  │
                        └─────────────────────────────────────────────┘
```

Implemented via Docker networks (dev/staging) or Kubernetes NetworkPolicy (production).

### Port Map

| Service               | HTTP  | gRPC  |
|-----------------------|-------|-------|
| gateway-service       | 8080  | —     |
| user-service          | 8081  | 9090  |
| auth-service          | 8082  | 9091  |
| cat-service           | 8084  | —     |
| storage-service       | 8083  | —     |
| notification-service  | 8085  | —     |
| adoption-service      | 8086  | —     |
| form-analysis-service | 8087  | —     |
| organization-service  | 8088  | —     |
| chat-service          | 8089  | —     |
| PostgreSQL            | 5432  | —     |
| MinIO API             | 9000  | —     |
| MinIO Console         | 9001  | —     |
| Kafka                 | 9092  | —     |
| Zookeeper             | 2181  | —     |
| MailHog SMTP          | 1025  | —     |
| MailHog UI            | 8025  | —     |

---

## Tech Stack

| Layer       | Technology                                                         |
|-------------|--------------------------------------------------------------------|
| Framework   | Quarkus 3.34.3                                                     |
| Language    | Java 21                                                            |
| Database    | PostgreSQL 16                                                      |
| ORM         | Hibernate Reactive + Panache                                       |
| REST        | Quarkus REST (RESTEasy Reactive)                                   |
| gRPC        | Quarkus gRPC                                                       |
| Messaging   | Apache Kafka + SmallRye Reactive Messaging                         |
| Auth        | SmallRye JWT (RSA-2048 key pair)                                   |
| Storage     | Quarkiverse Amazon S3 — MinIO (dev) / Cloudflare R2 (prod)        |
| Email       | Quarkus Mailer — MailHog (dev) / SMTP (prod)                      |
| Containers  | Jib (no Dockerfile required)                                       |
| Reactive    | Mutiny (`Uni<T>` / `Multi<T>`)                                     |

---

## Development

### Prerequisites

- Java 21
- Maven 3.9+
- Docker + Docker Compose

### Infrastructure

Copy the environment template and fill in real values before starting:

```bash
cp .env.example .env
# edit .env — all values are required, no hardcoded fallbacks in docker-compose
```

Then start the stack:

```bash
docker compose up -d
```

Starts PostgreSQL, MinIO, Kafka, Zookeeper, and MailHog. Schemas are created automatically by `init.sql` on first run. The `.env` file is git-ignored; never commit it.

> **MinIO bucket**: `minio/minio:latest` does not auto-create buckets from `MINIO_DEFAULT_BUCKETS` (that is a `bitnami/minio` feature). `storage-service` creates the bucket automatically via `BucketInitializer` on startup — no manual action required.

### Running services

Use `dev.sh` in the repository root to start services in Quarkus dev mode. Each service runs with `-am` so Maven builds reactor dependencies without a prior `mvn install`.

> **storage-service credential note**: Quarkus dev mode runs with the module directory as its working directory, so the root `.env` is not loaded automatically. A symlink fixes this — run once after cloning:
> ```bash
> ln -sf ../.env storage-service/.env
> ```
> The symlink is git-ignored.

```bash
# Start all services
./dev.sh

# Start gateway + specific services
./dev.sh user-service,auth-service
```

`dev.sh` validates service names, never starts `gateway-service` twice, captures all PIDs, and sends SIGTERM to all processes on Ctrl+C.

To start a single service manually:

```bash
mvn compile quarkus:dev -pl <service> -am
```

### Security keys

JWT requires an RSA-2048 key pair. Key files (`*.pem`) are excluded from version control.

```bash
# Generate private key (PKCS8, required by SmallRye JWT)
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
  -out auth-service/src/main/resources/privateKey.pem

# Extract public key
openssl rsa -pubout \
  -in  auth-service/src/main/resources/privateKey.pem \
  -out auth-service/src/main/resources/publicKey.pem

# Distribute public key to verifying services
for svc in user-service cat-service gateway-service adoption-service organization-service chat-service; do
  cp auth-service/src/main/resources/publicKey.pem $svc/src/main/resources/publicKey.pem
done
```

| File             | Services                                                                          | Profile   |
|------------------|-----------------------------------------------------------------------------------|-----------|
| `privateKey.pem` | `auth-service`                                                                    | dev / test |
| `publicKey.pem`  | `auth-service`, `user-service`, `cat-service`, `gateway-service`, `adoption-service`, `organization-service`, `chat-service` | dev / test |

**In production** the keys are read from the filesystem, not from the classpath. Mount them as Docker Secrets or Kubernetes Secrets at `/run/secrets/` (or override with `JWT_PRIVATE_KEY_LOCATION` / `JWT_PUBLIC_KEY_LOCATION`).

**JWT configuration:** issuer `https://www.kitti.es`, access token TTL 900 s, refresh token TTL 7 days. The `groups` claim carries the user role (`User`, `Organization`, `Admin`).

On Windows, run the commands above in Git Bash or WSL. To install OpenSSL via winget: `winget install ShiningLight.OpenSSL`.

### gRPC internal secret

`auth-service` and `user-service` communicate over gRPC protected by a shared secret injected as the `x-internal-token` header. Set `GRPC_INTERNAL_SECRET` in your `.env` (required in production; defaults to `kitties-dev-secret` in dev).

---

## Testing

Unit tests use plain Mockito (`@ExtendWith(MockitoExtension.class)`), no containers. Integration tests use `@QuarkusTest` + RestAssured with `%test.*` profiles in the main config.

| Service               | Unit | Integration | Notes                                                |
|-----------------------|------|-------------|------------------------------------------------------|
| user-service          | 8    | 3           | SmallRye in-memory Kafka                             |
| auth-service          | 7    | 4           | `@InjectMock` on gRPC client                         |
| cat-service           | 9    | 5           | JWT test tokens via `quarkus-smallrye-jwt-build`     |
| storage-service       | 9    | 3           | Real MinIO via `QuarkusTestResourceLifecycleManager` |
| gateway-service       | 2    | 23          | WireMock 1.6.1 DevService; Mockito para unit tests   |
| notification-service  | 3    | 2           | MockMailbox + in-memory Kafka + Awaitility           |
| adoption-service      | 28   | 19          | RBAC + ownership; intake flow + rejection alternatives |
| form-analysis-service | 8    | 3           | Rules engine + in-memory Kafka                       |
| organization-service  | 16   | 17          | Plan-based member limits; @TestSecurity RBAC; @InternalOnly by-region |
| chat-service          | 17   | 16          | Conversations, messages, ban, internal create        |
| gateway-service       | 2    | 26          | + internal path 404 + chat routing                   |

**Total: ~225 tests**

**End-to-end tests** run against the full live stack (all services + Docker infra):

| Suite              | Tests | Coverage |
|--------------------|-------|----------|
| `StorageE2E`       | 6     | Upload JPEG, serve public, 401, 400 invalid type, delete, rate limit 429 |
| `SecurityE2E`      | 2     | Magic byte upload rejection (400), X-Content-Type-Options nosniff on gateway responses |

```bash
# Run e2e suite (requires full stack running)
mvn test -Pe2e -pl e2e-tests

# Start full stack and run e2e automatically
./dev.sh --e2e
```

**Rate-limit isolation in e2e tests**: tests that hit rate-limited endpoints must send a unique `X-Forwarded-For` header per test run (e.g. `"test-" + System.currentTimeMillis()`) to avoid bucket contamination between consecutive runs within the 60-second window.

```bash
mvn verify -pl <service>
```

---

## Known Patterns

### Reactive session management

Hibernate Reactive requires all DB operations on the Vert.x event loop thread.

```java
@WithSession  // reads
public Uni<T> find() { ... }

@WithTransaction  // writes
public Uni<T> save() { ... }
```

### Repository pattern

All services use `PanacheRepository` (not Active Record). Repository interfaces (ports) are being extracted so services depend on abstractions, enabling DIP compliance and pure unit testing.

### No JPA relations

Entities do not use `@OneToMany` / `@ManyToOne`. Cross-entity joins are resolved explicitly in the service layer.

### Value Objects

Domain concepts with format constraints (`Email`, `ActivationToken`) are implemented as immutable `final` classes with a private constructor and a static `of()` factory — not records, because records cannot enforce a truly private canonical constructor. Format validation only; business rule validation stays in the service layer.

```java
public final class Email {
    private final String value;
    private Email(String value) { this.value = value; }

    public static Email of(String raw) {
        if (raw == null || !raw.contains("@"))
            throw new IllegalArgumentException("Invalid email");
        return new Email(raw.toLowerCase());
    }

    public String value() { return value; }
}
```

---

## Environment Variables

Copy `.env.example` to `.env` and fill in all values. Variables marked **required** have no fallback in docker-compose or in the `%prod` Quarkus profile.

**Infrastructure (docker-compose)**

| Variable                | Required | Description                      |
|-------------------------|----------|----------------------------------|
| `POSTGRES_USER`         | ✅       | PostgreSQL username               |
| `POSTGRES_PASSWORD`     | ✅       | PostgreSQL password               |
| `POSTGRES_DB`           | ✅       | PostgreSQL database name          |
| `MINIO_ROOT_USER`       | ✅       | MinIO access key                  |
| `MINIO_ROOT_PASSWORD`   | ✅       | MinIO secret key (min 16 chars)   |
| `MINIO_DEFAULT_BUCKETS` | ✅       | MinIO bucket name                 |

**Services (application.properties)**

| Variable                    | Dev default              | Description                              |
|-----------------------------|--------------------------|------------------------------------------|
| `DB_USER`                   | —                        | PostgreSQL username (injected at runtime)|
| `DB_PASSWORD`               | —                        | PostgreSQL password                      |
| `DB_HOST`                   | `localhost`              | PostgreSQL host                          |
| `DB_PORT`                   | `5432`                   | PostgreSQL port                          |
| `DB_NAME`                   | `kitties`               | PostgreSQL database                      |
| `GRPC_INTERNAL_SECRET`      | `kitties-dev-secret`    | Shared secret for auth↔user gRPC channel; **required in prod** |
| `STORAGE_SERVICE_URL`       | `http://localhost:8084`  | Storage service URL                      |
| `USER_SERVICE_HOST`         | `localhost`              | User service host (gRPC)                 |
| `KAFKA_HOST`                | `localhost`              | Kafka broker host                        |
| `KAFKA_PORT`                | `9092`                   | Kafka broker port                        |
| `MAIL_HOST`                 | `localhost`              | SMTP host                                |
| `MAIL_PORT`                 | `1025`                   | SMTP port                                |
| `FRONTEND_URL`              | `http://localhost:5173`  | Frontend base URL (used in activation emails) |

**Production only**

| Variable                    | Default mount path                  | Description                            |
|-----------------------------|-------------------------------------|----------------------------------------|
| `JWT_PRIVATE_KEY_LOCATION`  | `/run/secrets/privateKey.pem`       | RSA private key path (auth-service)    |
| `JWT_PUBLIC_KEY_LOCATION`   | `/run/secrets/publicKey.pem`        | RSA public key path (all other services) |

**Dev tools:**

| Tool          | URL                        |
|---------------|----------------------------|
| MinIO Console | http://localhost:9001       |
| MailHog       | http://localhost:8025       |
| Kafka UI      | http://localhost:8008       |

---

## Roadmap

> **Business model**: Kitties is B2B2C — shelters are the paying customer, adopters are the end user. The shelter dashboard and workflow tooling are the core product; the adoption portal is the channel that gives shelters value.

---

### Priority 1 — Foundation (prerequisite for everything)

- [x] All services implemented with full adoption workflow
- [x] Integration and unit tests for all services (143 total)
- [x] Value Objects (`Email`, `ActivationToken`)
- [x] User roles (`User`, `Organization`, `Admin`)
- [x] Security audit completed (11 vulnerabilities found and fixed, score 5.5 → 8.5/10)
- [x] JaCoCo configured across all modules (quarkus-jacoco + maven plugin in root pom)
- [x] gateway-service instruction coverage at 100% (574/574); branch coverage 93.5% (4 unreachable branches in Vert.x WebClient code)
- [ ] **CI/CD with GitHub Actions** — no pipeline exists; tests only run locally. Minimum: compile + test on push, JaCoCo report as PR artifact, OWASP Dependency Check + Trivy.
- [x] **Flyway** — versioned SQL migrations per service, `migrate-at-start=true`, Hibernate in `validate` mode in production. All 6 database-backed services have V1 migrations (auth, user, cat, adoption, organization, form-analysis).
- [ ] **Production Docker Compose** — prerequisite for any real deployment.
- [ ] **Observability** — OpenTelemetry distributed traces, metrics, and centralized logs. No correlation IDs between services today; debugging a gateway → auth → adoption flow requires grepping logs manually.
- [ ] **Coverage baselines for remaining modules** — JaCoCo is installed; run `mvn test -pl <module>` and review `target/site/jacoco/index.html` per service.

### Priority 2 — Shelter Dashboard (core revenue)

Shelters distrust platforms that automate them out of control. The value proposition is giving them better tools to manage their own work, not replacing it. This is what justifies a subscription.

- [ ] Shelter management dashboard — adoption pipeline view, cat inventory, case history per animal.
- [ ] Multi-user per shelter — shelter admin can invite volunteers with limited roles.
- [ ] Shelter analytics — adoption rates, average time to adopt, rejection reasons.
- [ ] Post-adoption follow-up — shelter requests updates (photos, health status) from adopters.
- [ ] Post-adoption ratings — adopter rates shelter and vice versa.
- [ ] **payment-service** — Stripe Connect (marketplace between shelters and adopters), Stripe Subscriptions (sponsorship recurring billing), platform fee charged to adopter at adoption confirmation, Quartz for reconciliation. Introduce only once at least one monetization path is validated.
- [ ] Extensible pricing (freemium tier for small shelters, paid tiers for volume and analytics).

### Priority 3 — Intelligence

- [ ] **form-analysis-service** — LLM-based screening form analysis. Architecture (async Kafka + external call) is already in place; the engine swap is the only change needed.

### Priority 4 — Communication

- [ ] **Notifications MVP** — extend `notification-service` to cover all adoption state transitions (already partially wired). Shelters and adopters receive email on every status change. This is sufficient for MVP.
- [x] **chat-service** (REST bases) — port 8089. Conversation model (one per intake), REST endpoints for list/send/history, internal endpoint for adoption-service to open conversations, in-chat user blocking (org → user pair). Persistent history.
- [ ] **chat-service** real-time transport — WebSocket on top of the existing REST surface (or long-poll if WebSocket gives trouble). REST currently requires polling.
- [ ] **Auto-open conversation on intake approval** — wire `IntakeRequestService.approve(...)` in adoption-service to call `POST /chats/internal/conversations` (REST client + `kitties.internal.secret`).
- [ ] **Global user ban** (`UserStatus.Banned`) — deferred until first real case of cross-conversation abuse. In-chat ban (org-scoped) is already live.

### Priority 5 — Adopter Growth & Self-funding

These features make the portal self-sustaining without depending solely on shelter subscriptions.

- [ ] **Senior cat sponsorship** — recurring monthly payment to sponsor a senior cat's shelter costs. Sponsors get updates (photos, health status) from the shelter. Technically: Stripe Subscriptions + `Sponsorship` entity linked to `Cat`. First monetization feature to build: low friction, high emotional value, predictable revenue.
- [ ] **Management fee on adoptions** — small platform fee charged to the adopter at adoption confirmation (Airbnb model). Shelters do not pay; the fee covers operational costs. Requires payment-service to be live first.
- [ ] **Starter kits for first-time adopters** — curated product bundle (food, litter, toys, vet guide) offered at checkout. *Operational model TBD*: own inventory vs. dropshipping partner. Build catalog and checkout only after the supply chain is defined.
- [ ] Post-adoption ratings — adopter rates shelter and vice versa.
- [ ] Post-adoption follow-up — shelter requests updates after adoption (partially covered by the sponsorship updates flow).
- [ ] **kitties-cli** — Quarkus + Picocli native binary. Useful once operational complexity justifies it.

### Security

- [x] IDOR protection on `GET /adoptions/{id}` (ownership enforced)
- [x] RBAC via JWT `groups` claim + `@RolesAllowed` on all adoption endpoints
- [x] gRPC internal channel protected with shared-secret interceptors
- [x] Kafka `EXTERNAL` listener restricted to `127.0.0.1`
- [x] Bean Validation on all input DTOs (`@Valid` + Jakarta constraints)
- [x] Rate limiting on login, refresh, and upload endpoints (SmallRye Fault Tolerance)
- [x] Activation token moved from query param to POST body
- [x] Credentials removed from docker-compose (`.env` + `.env.example`)
- [x] JWT keys externalized in `%prod` profile (mounted secrets, not classpath)
- [x] MIME magic byte validation on upload — rejects files whose bytes don't match declared Content-Type (JPEG/PNG spoofing prevention)
- [x] `X-Content-Type-Options: nosniff` injected on all gateway responses (MIME sniffing prevention)
- [ ] **Fix `IpRateLimiter` cross-endpoint bucket sharing** — refresh and upload (IP key) share the same `Deque<Long>`; two uploads consume from the same window as two refreshes for the same IP.
- [ ] Rate limiting distributed with Redis — current limit is per JVM instance; multi-replica deployments multiply the effective limit.
- [ ] Activation token expiry (`activationTokenExpiresAt`)
- [ ] Audit log for sensitive actions (login, status changes)
- [ ] HTTPS between services in production
- [ ] JWKS endpoint for zero-downtime key rotation
- [ ] Docker images run as non-root (`quarkus.jib.user=1001`)
- [ ] OWASP Dependency Check + Trivy in CI

### Technical debt

- [ ] **HTTPS** in gateway.
- [ ] Value Objects for remaining services.
- [ ] Repository interfaces as ports (DIP) across all services.
- [ ] Pagination on cat listing.

---

## License

Private repository. All rights reserved.