# Kittigram API

Microservices backend for Kittigram, a cat adoption portal for shelters and veterinarians. Maven multi-module monorepo built with Quarkus 3.34.3 and Java 21.

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

Kittigram connects adopters with shelters and veterinarians. Each service owns its PostgreSQL schema and communicates via gRPC (synchronous) or Kafka (asynchronous). The gateway is the single entry point exposed to the internet; all other services run in a private network.

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

### cat-service — port 8083

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

### storage-service — port 8084

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

**Expenses:** veterinary costs billed to the organization; management fee retained by Kittigram.

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
                        │  cat-service         :8083                  │
                        │  storage-service     :8084                  │
                        │  notification-service :8085                 │
                        │  adoption-service    :8086                  │
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
| cat-service           | 8083  | —     |
| storage-service       | 8084  | —     |
| notification-service  | 8085  | —     |
| adoption-service      | 8086  | —     |
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

### Running services

Use `dev.sh` in the repository root to start services in Quarkus dev mode. Each service runs with `-am` so Maven builds reactor dependencies without a prior `mvn install`.

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
for svc in user-service cat-service gateway-service adoption-service; do
  cp auth-service/src/main/resources/publicKey.pem $svc/src/main/resources/publicKey.pem
done
```

| File             | Services                                                                          | Profile   |
|------------------|-----------------------------------------------------------------------------------|-----------|
| `privateKey.pem` | `auth-service`                                                                    | dev / test |
| `publicKey.pem`  | `auth-service`, `user-service`, `cat-service`, `gateway-service`, `adoption-service` | dev / test |

**In production** the keys are read from the filesystem, not from the classpath. Mount them as Docker Secrets or Kubernetes Secrets at `/run/secrets/` (or override with `JWT_PRIVATE_KEY_LOCATION` / `JWT_PUBLIC_KEY_LOCATION`).

**JWT configuration:** issuer `https://kittigram.ciscoadiz.org`, access token TTL 900 s, refresh token TTL 7 days. The `groups` claim carries the user role (`User`, `Organization`, `Admin`).

On Windows, run the commands above in Git Bash or WSL. To install OpenSSL via winget: `winget install ShiningLight.OpenSSL`.

### gRPC internal secret

`auth-service` and `user-service` communicate over gRPC protected by a shared secret injected as the `x-internal-token` header. Set `GRPC_INTERNAL_SECRET` in your `.env` (required in production; defaults to `kittigram-dev-secret` in dev).

---

## Testing

Unit tests use plain Mockito (`@ExtendWith(MockitoExtension.class)`), no containers. Integration tests use `@QuarkusTest` + RestAssured with `%test.*` profiles in the main config.

| Service               | Unit | Integration | Notes                                                |
|-----------------------|------|-------------|------------------------------------------------------|
| user-service          | 8    | 3           | SmallRye in-memory Kafka                             |
| auth-service          | 7    | 4           | `@InjectMock` on gRPC client                         |
| cat-service           | 9    | 5           | JWT test tokens via `quarkus-smallrye-jwt-build`     |
| storage-service       | 6    | 2           | Real MinIO via `QuarkusTestResourceLifecycleManager` |
| gateway-service       | —    | 4           | WireMock 1.6.1 DevService                            |
| notification-service  | 3    | 2           | MockMailbox + in-memory Kafka + Awaitility           |
| adoption-service      | 17   | 9           | RBAC + ownership checks covered                      |
| form-analysis-service | 8    | 3           | Rules engine + in-memory Kafka                       |

**Total: 93 tests** (58 unit + 35 integration)

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
| `DB_NAME`                   | `kittigram`              | PostgreSQL database                      |
| `GRPC_INTERNAL_SECRET`      | `kittigram-dev-secret`   | Shared secret for auth↔user gRPC channel; **required in prod** |
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

### Core

- [x] All services implemented with full adoption workflow
- [x] Integration and unit tests for all services (93 total)
- [x] Value Objects (`Email`, `ActivationToken`)
- [x] User roles (`User`, `Organization`, `Admin`)
- [x] Security audit completed (11 vulnerabilities found and fixed, score 5.5 → 8.5/10)
- [ ] **Flyway** — versioned SQL migrations per service, `migrate-at-start=true`, Hibernate in `validate` mode in production. Pattern to be defined in `user-service` first.
- [ ] **payment-service** — Stripe Connect for marketplace payments, Stripe Subscriptions for recurring billing, Quartz for reconciliation jobs.

### Communication

- [ ] **chat-service** — real-time WebSocket (org ↔ adopter), persistent history, typing indicators, read receipts. File attachments via `storage-service` in the future.
- [ ] **notification-service** — wire to adoption and chat events via Kafka.

### Intelligence

- [ ] **form-analysis-service** — LLM-based screening form analysis.

### Growth

- [ ] Senior cat program.
- [ ] Sponsorship program — recurring payments for people who cannot adopt.
- [ ] Post-adoption ratings — adopter rates shelter and vice versa.
- [ ] Post-adoption follow-up — shelter requests updates (photos, health status) after adoption.
- [ ] B2B2C — shelter and veterinary management software (freemium model).
- [ ] Extensible pricing.

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
- [ ] Rate limiting distributed with Redis (current limit is per JVM instance)
- [ ] Activation token expiry (`activationTokenExpiresAt`)
- [ ] Audit log for sensitive actions (login, status changes)
- [ ] HTTPS between services in production
- [ ] JWKS endpoint for zero-downtime key rotation
- [ ] Docker images run as non-root (`quarkus.jib.user=1001`)
- [ ] OWASP Dependency Check + Trivy in CI

### Technical

- [ ] **HTTPS** in gateway.
- [ ] **Observability** — OpenTelemetry distributed traces, metrics, and centralized logs.
- [ ] **kittigram-cli** — Quarkus + Picocli compiled to a native binary with GraalVM. Use cases: dev service launcher, stack installer, module scaffolding, health checks, migration generation.
- [ ] Value Objects for remaining services.
- [ ] Repository interfaces as ports (DIP) across all services.
- [ ] Pagination on cat listing.
- [ ] Production Docker Compose.
- [ ] CI/CD with GitHub Actions.

### Long term

- [ ] Blockchain for veterinary medical records (requires legal analysis Spain/EU).

---

## License

Private repository. All rights reserved.