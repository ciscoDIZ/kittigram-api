# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# Kitties — Claude Code Rules

## Stack
- Java 21, Quarkus 3.34.3, Hibernate Reactive + Panache, SmallRye Mutiny
- PostgreSQL 16 (esquemas separados por servicio), MinIO (S3-compatible)
- Kafka (SmallRye Reactive Messaging), gRPC (auth↔user), SmallRye JWT (15 min)

## Servicios y puertos

Los 11 servicios son todos activos e intencionales. No eliminar ninguno de esta tabla; los puertos están verificados contra `application.properties` de cada módulo.

| Servicio             | HTTP | gRPC        | Rutas públicas (sin JWT)                        |
|----------------------|------|-------------|-------------------------------------------------|
| gateway-service      | 8080 | —           | proxy de todas las rutas públicas               |
| user-service         | 8081 | 9090 server | `POST /users`, `POST /users/activate`           |
| auth-service         | 8082 | —           | `POST /auth/login`, `POST /auth/refresh`        |
| storage-service      | 8083 | —           | `GET /storage/files/{key}`                      |
| cat-service          | 8084 | —           | `GET /cats`, `GET /cats/{id}`                   |
| notification-service | 8085 | —           | —                                               |
| adoption-service     | 8086 | —           | —                                               |
| form-analysis-service| 8087 | —           | —                                               |
| organization-service | 8088 | —           | —                                               |
| chat-service         | 8089 | —           | —                                               |
| schedule-service     | 8090 | —           | — (sin endpoints públicos; solo `/q/health`)    |

## Arquitectura — reglas duras
- Sin dependencias Maven entre módulos. Comunicación solo vía gRPC o Kafka.
- Sin relaciones JPA cross-service. Cada servicio tiene su propio esquema PostgreSQL.
- DTOs siempre (Records Java). Nunca exponer entidades Panache directamente.
- Patrón Repository, no Active Record.

## Patrones de código

### Entidades — Panache Active Entity (PanacheEntity)

Las entidades extienden `PanacheEntity`, que ya aporta `public Long id` con `@Id @GeneratedValue`. **No declarar `@Id` nunca.** Los campos son `public` (Panache los intercepta en bytecode). Timestamps con `@PrePersist` / `@PreUpdate`.

```java
@Entity
@Table(name = "adoption_requests", schema = "adoption")
public class AdoptionRequest extends PanacheEntity {

    @Column(nullable = false)
    public Long catId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public AdoptionStatus status;

    @Column(nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        status = AdoptionStatus.Pending;
    }
}
```

### Repositorios — PanacheRepository

`implements PanacheRepository<Entity>`, `@ApplicationScoped`. Usar los métodos de Panache (`list`, `find`, `count`, `persist`, `deleteById`) directamente. No extender `PanacheEntity` desde el repositorio.

```java
@ApplicationScoped
public class AdoptionRequestRepository implements PanacheRepository<AdoptionRequest> {

    public Uni<List<AdoptionRequest>> findByAdopterId(Long adopterId) {
        return list("adopterId", adopterId);
    }
}
```

### DTOs — Records Java

Records sin constructores especiales. Anotaciones de validación Jakarta en los de request; los de response sin anotaciones.

```java
// Request
public record AdoptionRequestCreateRequest(
        @NotNull Long catId,
        @NotNull Long organizationId
) {}

// Response
public record AdoptionRequestResponse(
        Long id,
        Long catId,
        AdoptionStatus status,
        LocalDateTime createdAt
) {}
```

### Enums

Valores en PascalCase, nunca SCREAMING_SNAKE_CASE:

```java
public enum AdoptionStatus { Pending, Reviewing, Accepted, Rejected, Completed }
```

## Reactividad (Mutiny)
- `@WithSession` para lecturas en Service; `@WithTransaction` para escrituras.
- `@WithSession` NO funciona en métodos que devuelven `Multi<T>` → poner `@WithSession` en el Repository y transformar en Service:
  ```java
  public Multi<T> search() {
      return repo.findAll()
          .onItem().transformToMulti(list -> Multi.createFrom().iterable(list));
  }
  ```
- `@Incoming` (Kafka) + `@WithTransaction` combinados directamente → fallo. Delegar la persistencia a un bean separado.

## Gotchas conocidos
- Records Java: añadir `@JsonProperty` en cada campo, o registrar `ParameterNamesModule` en JacksonConfig.
- `"order"` es palabra reservada HQL → usar `imageOrder` con `@Column(name = "image_order")`.
- Copiar el `.proto` en `src/main/proto/` de cada servicio que lo necesite. No compartir módulo Maven.
- Borrado de usuarios: lógico (status → Inactive), nunca físico.
- `ProxyService` explota con NPE si la respuesta upstream no tiene body (ej. 204). Siempre guardar `r.body() != null`.
- `JwtAuthFilter` tiene una lista explícita de rutas públicas (`PUBLIC_EXACT`). Añadir ahí cualquier endpoint nuevo que no requiera Bearer token.
- **Auth servicio-a-servicio (interno)**: usar el secreto compartido `kitties.internal.secret` (mismo valor en todos los servicios; en dev `kitties-dev-secret`, en prod via env `KITTIES_INTERNAL_SECRET`). Para gRPC: metadata `x-internal-token` validada por interceptors (`GrpcAuthInterceptor` en el server, `GrpcClientAuthInterceptor` en el client). Para HTTP: header `X-Internal-Token` validado por un `ContainerRequestFilter` con la annotation `@InternalOnly` (referencia: `cat-service/security/InternalTokenFilter.java`). Nunca exponer endpoints `@InternalOnly` por el gateway.
- El rate limiter (`IpRateLimiter`) usa email como clave para login y IP para el resto. No cambiar a global o los tests se contaminarán entre sí.
- **Tests e2e + rate limiter**: enviar siempre `X-Forwarded-For: TEST_IP` (único por ejecución, ej. `"test-" + System.currentTimeMillis()`) en los requests que consuman del mismo bucket de rate limit. Sin esto los tests se contaminan entre ejecuciones dentro de la ventana de 60 s.
- **`minio/minio:latest` NO crea buckets automáticamente** con `MINIO_DEFAULT_BUCKETS` (eso es una feature de `bitnami/minio`). Usar un `BucketInitializer` (`@Observes StartupEvent`) que haga `headBucket` → `createBucket` con `S3AsyncClient.get()` (bloqueante en startup está bien).
- **Quarkus dev cwd = directorio del módulo**, no la raíz del proyecto. El `.env` raíz NO se carga cuando se arranca con `mvn -pl <módulo>`. Solución: symlink `<módulo>/.env → ../.env` (ya existe en `storage-service`). Si los defaults en `application.properties` no coinciden con el `.env` raíz, el servicio usará credenciales incorrectas.
- Kafka EXTERNAL listener debe vincularse a `0.0.0.0` dentro del contenedor, no a `127.0.0.1` (Docker no redirige al loopback del contenedor).
- `MailHogClient.extractActivationToken` espera el body decodificado de Quoted-Printable. Los emails HTML llegan con soft line breaks (`=\n`) y `=3D` en lugar de `=`.

## Comandos habituales

```bash
# Compilar módulo
mvn compile -pl <módulo>

# Tests de un módulo (unit + integración)
mvn test -pl <módulo>

# Una sola clase de test
mvn test -pl <módulo> -Dtest=CatServiceTest

# Un solo método
mvn test -pl <módulo> -Dtest=CatServiceTest#createCat_shouldPersist

# Arrancar un servicio en dev mode
mvn compile quarkus:dev -pl <módulo> -am

# Arrancar todos los servicios (gateway incluido)
./dev.sh

# Arrancar gateway + servicios concretos
./dev.sh user-service,auth-service

# Arrancar todo y correr e2e al terminar
./dev.sh --e2e

# Solo e2e (requiere stack completo corriendo)
mvn test -Pe2e -pl e2e-tests
```

## Estructura de paquetes (igual en todos los servicios)

```
src/main/java/org/ciscoadiz/<servicio>/
  entity/      PanacheEntity subclasses
  repository/  PanacheRepository implementations (@ApplicationScoped)
  service/     Business logic (@WithSession / @WithTransaction)
  resource/    JAX-RS endpoints (@Path, @RolesAllowed)
  dto/         Java Records (request / response)
  mapper/      Manual entity ↔ DTO conversion
  exception/   Domain exceptions + ExceptionMappers
  client/      HTTP/gRPC clients to other services (if needed)
  config/      @ConfigProperty beans
```

## Comportamiento esperado
- **Antes de comenzar cualquier desarrollo**: crear una rama con el formato `<tipo>/<descripción>`, donde `<tipo>` sigue la convención de conventional commits (`feat`, `fix`, `chore`, `docs`, `refactor`, `test`). Ejemplos: `feat/flyway-migrations`, `fix/rate-limiter-bucket`.
- **Compilar solo el módulo afectado**: `mvn compile -pl <módulo>` o `mvn test -pl <módulo>`.
- **Tests e2e**: `mvn test -Pe2e -pl e2e-tests` (requiere stack completo corriendo).
- **Si un fix falla dos veces seguidas por el mismo motivo → parar y preguntar al usuario.**
- Cambios atómicos: modificar solo lo necesario. No reescribir ficheros completos salvo que sea inevitable.
- No añadir comentarios salvo que el WHY sea no obvio.
- No añadir atribución `Co-Authored-By: Anthropic` en commits.