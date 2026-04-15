# 🐱 Kittigram API

Microservices backend for Kittigram, a cat adoption platform. Built with Quarkus, PostgreSQL, Kafka, and MinIO.

---

## Table of Contents

- [Architecture](#architecture)
  - [Port Map](#port-map)
- [Tech Stack](#tech-stack)
- [Services](#services)
  - [gateway-service](#gateway-service)
  - [user-service](#user-service)
  - [auth-service](#auth-service)
  - [storage-service](#storage-service)
  - [cat-service](#cat-service)
  - [notification-service](#notification-service)
- [Testing](#testing)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Infrastructure](#infrastructure)
  - [Database Setup](#database-setup)
  - [Build](#build)
  - [Run a service](#run-a-service)
- [Security](#security)
- [Environment Variables](#environment-variables)
- [Development Tools](#development-tools)
- [Known Patterns](#known-patterns)
- [Roadmap](#roadmap)

---

## Architecture

Kittigram follows a **microservices architecture** organized as a Maven multi-module monorepo. Each service is independently deployable, has its own PostgreSQL schema, and communicates with other services via gRPC or Kafka.

```
kittigram/
├── user-service/         → User management, activation, gRPC server
├── auth-service/         → JWT authentication, refresh tokens, gRPC client
├── storage-service/      → File storage via S3/MinIO/Cloudflare R2
├── cat-service/          → Cat profiles and image management
├── gateway-service/      → API Gateway, JWT validation, CORS, routing
└── notification-service/ → Email notifications via Kafka + MailHog/SMTP
```

### Port Map

| Service              | HTTP  | gRPC  |
|----------------------|-------|-------|
| gateway-service      | 8080  | —     |
| user-service         | 8081  | 9090  |
| auth-service         | 8082  | 9091  |
| storage-service      | 8083  | —     |
| cat-service          | 8084  | —     |
| notification-service | 8085  | —     |
| PostgreSQL           | 5432  | —     |
| MinIO API            | 9000  | —     |
| MinIO Console        | 9001  | —     |
| Kafka                | 9092  | —     |
| Zookeeper            | 2181  | —     |
| MailHog SMTP         | 1025  | —     |
| MailHog UI           | 8025  | —     |

---

## Tech Stack

| Layer           | Technology                                      |
|-----------------|-------------------------------------------------|
| Framework       | Quarkus 3.34.3                                  |
| Language        | Java 21                                         |
| Database        | PostgreSQL 16                                   |
| ORM             | Hibernate Reactive + Panache                    |
| REST            | Quarkus REST (RESTEasy Reactive)                |
| gRPC            | Quarkus gRPC                                    |
| Messaging       | Apache Kafka + SmallRye Reactive Messaging      |
| Auth            | SmallRye JWT (RSA key pair)                     |
| Storage         | Quarkiverse Amazon S3 + MinIO (dev) / Cloudflare R2 (prod) |
| Email           | Quarkus Mailer + MailHog (dev)                  |
| Containers      | Jib (no Dockerfile required)                    |
| Reactive        | Mutiny (`Uni<T>` / `Multi<T>`)                  |

---

## Services

### gateway-service
Single entry point for all API traffic. Responsibilities:
- JWT validation (public routes bypass validation)
- Request routing to internal services via Vert.x WebClient
- CORS configuration for the frontend

**Public routes:**
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/users` (registration)
- `GET /api/cats` (listing)
- `GET /api/cats/{id}` (detail)
- `GET /api/storage/files/{key}` (image proxy)

---

### user-service
Manages user accounts and exposes a gRPC server for credential validation.

**Endpoints:**
- `POST /users` — Register a new user (status: Pending, sends activation email via Kafka)
- `GET /users/activate?token=xxx` — Activate account (public)
- `GET /users/{email}` — Get user by email (JWT required)
- `PUT /users/{email}` — Update user (JWT, owner only)

**gRPC:**
```proto
service UserService {
  rpc ValidateCredentials(ValidateCredentialsRequest) returns (ValidateCredentialsResponse);
  rpc GetUserById(GetUserByIdRequest) returns (GetUserResponse);
}
```

**User statuses:** `Pending` (tras registro) → `Active` (tras activar email) → `Inactive` / `Banned`

---

### auth-service
Handles JWT issuance and refresh token management. Calls `user-service` via gRPC to validate credentials.

**Endpoints:**
- `POST /auth/login` — Returns access token (15 min) + refresh token (7 days)
- `POST /auth/refresh` — Rotates tokens
- `POST /auth/logout` — Revokes refresh token

**JWT claims:** `sub` (userId), `email`, `iss`

---

### storage-service
Manages file storage with S3-compatible backends. Images are served through the gateway, never exposing the bucket URL directly.

**Endpoints:**
- `POST /storage/upload` — Upload image (jpg/png, max 5MB)
- `DELETE /storage/{key}` — Delete image
- `GET /storage/files/{key}` — Serve image (proxied through gateway)

**Backends:** MinIO (dev), Cloudflare R2 (prod)

---

### cat-service
Manages cat profiles and their associated images. Calls `storage-service` synchronously for image uploads.

**Endpoints:**
- `GET /cats?city=X&name=Y` — Search available cats (public)
- `GET /cats/{id}` — Cat detail with images (public)
- `POST /cats` — Create cat profile (JWT required)
- `PUT /cats/{id}` — Update cat profile (JWT, owner only)
- `DELETE /cats/{id}` — Delete cat and images (JWT, owner only)
- `POST /cats/{id}/images` — Upload image
- `DELETE /cats/{catId}/images/{imageId}` — Delete image

**Design decisions:**
- Search by city/name only (no breed search — ethical decision)
- Listing returns `CatSummaryResponse` (no images) to avoid N+1
- `profileImageUrl` denormalized on `Cat` for efficient listing
- No JPA relations between entities

---

### notification-service
Consumes Kafka events and sends transactional emails via SMTP.

**Kafka topics consumed:**
- `user-registered` → Sends account activation email

---

## Testing

**Unit tests** use plain Mockito (`@ExtendWith(MockitoExtension.class)`) with no container dependencies:

| Service      | Tests | Coverage                                                        |
|--------------|-------|-----------------------------------------------------------------|
| user-service | 8     | Registration, activation, deactivation, duplicate email, errors |
| auth-service | 7     | Authenticate, refresh (valid/expired/revoked), logout           |
| cat-service  | 9     | CRUD, owner checks, image management                            |
| storage-service | 6  | Upload validation, file type enforcement                        |
| notification-service | 3 | Event consumption, email content                           |

**Integration tests** use `@QuarkusTest` + RestAssured. No separate `application.properties` for tests — `%test.*` profiles are used in the main config file:

```properties
%test.quarkus.datasource.devservices.init-script-path=init-test.sql
```

| Service              | Tests | DB             | External deps                                      |
|----------------------|-------|----------------|----------------------------------------------------|
| user-service         | 3     | DevServices PG | SmallRye in-memory connector (Kafka)               |
| auth-service         | 4     | DevServices PG | `@InjectMock` on gRPC client                       |
| cat-service          | 5     | DevServices PG | JWT test token via `quarkus-smallrye-jwt-build`    |
| storage-service      | 2     | —              | Real MinIO container via `QuarkusTestResourceLifecycleManager` |
| gateway-service      | 4     | —              | WireMock 1.6.1 DevService (stubs all internal services) |
| notification-service | 2     | —              | MockMailbox + in-memory Kafka + Awaitility         |

**Total: 53 tests** (20 integration + 33 unit)

```bash
# Run tests for a specific service
mvn verify -pl user-service
mvn verify -pl auth-service
mvn verify -pl storage-service
mvn verify -pl cat-service
mvn verify -pl gateway-service
mvn verify -pl notification-service
```

---

## Getting Started

### Prerequisites
- Java 21
- Maven 3.9+
- Docker + Docker Compose
- Node.js 22+ (frontend only)

### Infrastructure

Start all required services:

```bash
docker compose up -d
```

This starts PostgreSQL, MinIO, Kafka, Zookeeper, and MailHog.

### Database Setup

Schemas are created automatically by `init.sql` on first run:

```sql
CREATE SCHEMA IF NOT EXISTS users;
CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS cats;
```

### Build

```bash
mvn install -DskipTests
```

### Run a service

```bash
mvn quarkus:dev -pl user-service
mvn quarkus:dev -pl auth-service
mvn quarkus:dev -pl storage-service
mvn quarkus:dev -pl cat-service
mvn quarkus:dev -pl gateway-service
mvn quarkus:dev -pl notification-service
```

---

## Security

### JWT
- RSA-2048 key pair (PKCS8)
- Private key in `auth-service` for signing
- Public key in `user-service`, `cat-service`, and `gateway-service` for verification
- Issuer: `https://kittigram.ciscoadiz.org`
- Access token TTL: 900 seconds
- Refresh token TTL: 7 days (stored in DB, revocable)

> ⚠️ Keys are versioned because this is a private repository.

---

## Environment Variables

| Variable               | Default         | Description                        |
|------------------------|-----------------|------------------------------------|
| `DB_USER`              | `kittigram`     | PostgreSQL username                |
| `DB_PASSWORD`          | `kittigram`     | PostgreSQL password                |
| `DB_HOST`              | `localhost`     | PostgreSQL host                    |
| `DB_PORT`              | `5432`          | PostgreSQL port                    |
| `DB_NAME`              | `kittigram`     | PostgreSQL database                |
| `MINIO_ROOT_USER`      | `kittigram`     | MinIO access key                   |
| `MINIO_ROOT_PASSWORD`  | `kittigram123`  | MinIO secret key                   |
| `MINIO_DEFAULT_BUCKETS`| `kittigram`     | MinIO bucket name                  |
| `STORAGE_SERVICE_URL`  | `http://localhost:8083` | Storage service URL        |
| `USER_SERVICE_HOST`    | `localhost`     | User service host (gRPC)           |
| `KAFKA_HOST`           | `localhost`     | Kafka broker host                  |
| `KAFKA_PORT`           | `9092`          | Kafka broker port                  |
| `MAIL_HOST`            | `localhost`     | SMTP host                          |
| `MAIL_PORT`            | `1025`          | SMTP port                          |

---

## Development Tools

| Tool         | URL                        | Description              |
|--------------|----------------------------|--------------------------|
| MinIO Console| http://localhost:9001       | Object storage UI        |
| MailHog      | http://localhost:8025       | Email testing UI         |
| Kafka UI     | http://localhost:8008       | Kafka topic browser      |

---

## Known Patterns

### Reactive Session Management
Hibernate Reactive requires all DB operations to run on the Vert.x event loop thread.

```java
// Reads in Service
@WithSession
public Uni<T> findSomething() { ... }

// Writes in Service
@WithTransaction
public Uni<T> saveSomething() { ... }

// Repository methods for Multi in Service
@WithSession
public Uni<List<T>> findAll() {
    return find("...").list();
}
```

### Repository Pattern
All services use `PanacheRepository` (not Active Record) to keep domain logic separate from persistence. Repository interfaces (ports) are being extracted so Services depend on abstractions, enabling full DIP compliance and easier unit testing.

### No JPA Relations
Entities do not use `@OneToMany` or `@ManyToOne`. Cross-entity queries are done explicitly in the service layer.

### Value Objects
Domain concepts with format constraints (e.g. `Email`, `ActivationToken`) are implemented as **immutable final classes** with a private constructor and a `static of()` factory method — not records, because records cannot enforce a truly private canonical constructor.

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

Value Objects are responsible for **format validation only**. Business rule validation (duplicate checks, expiry) stays in the Service layer. They live in a `domain/` package within each service.

---

## Roadmap

- [x] Integration tests for all services
- [x] Unit tests for all Service classes (Mockito)
- [x] Value Objects introduced (`Email`, `ActivationToken` in user-service)
- [ ] Value Objects for remaining services
- [ ] Repository interfaces as ports (DIP)
- [ ] Hexagonal architecture remains implicit (no explicit package restructure planned)
- [ ] Input validation on all endpoints
- [ ] Pagination on cat listing
- [ ] Image reordering
- [ ] Scheduled tasks (inactive user cleanup, unban)
- [ ] Async image deletion via Kafka
- [ ] `ban-service`
- [ ] `adoption-service`
- [ ] Production docker-compose
- [ ] CI/CD with GitHub Actions

---

## License

Private repository. All rights reserved.