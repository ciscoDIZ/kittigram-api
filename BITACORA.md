# Kittigram - Bitácora de Desarrollo

## Contexto del Proyecto

Kittigram es un portal donde los usuarios pueden subir perfiles de gatos para adopción. La arquitectura está diseñada como un **monorepo multi-módulo Maven** con estructura de microservicios desde el primer momento, de forma que cada módulo sea independiente y pueda separarse en un servicio autónomo en el futuro.

---

## Decisiones de Arquitectura

### Filosofía general
- Cada servicio es **idempotente** desde el principio
- Sin dependencias entre módulos Maven (solo se comunican vía gRPC o mensajería)
- Sin relaciones JPA entre entidades (cada servicio gestiona su propio dominio)
- Cada servicio tiene su propio esquema PostgreSQL
- Base de datos PostgreSQL compartida, un esquema por servicio
- Programación **reactiva** con Mutiny (`Uni<T>` para valores únicos, `Multi<T>` para flujos)
- Patrón **Repository** (no Active Record) para separar responsabilidades
- DTOs siempre, nunca se exponen entidades directamente
- Arquitectura hexagonal **implícita** — no se explicita con packages `domain/application/infrastructure` porque añade burocracia sin valor en un contexto Jakarta EE / Quarkus

### Mapa de puertos
```
5432  → PostgreSQL
8080  → gateway-service HTTP  ← punto de entrada público
8081  → user-service HTTP
8082  → auth-service HTTP
8083  → storage-service HTTP
8084  → cat-service HTTP
8085  → notification-service HTTP
8086  → adoption-service HTTP
8087  → form-analysis-service HTTP
8088  → organization-service HTTP
8008  → Kafka UI (provectuslabs/kafka-ui)
9000  → MinIO API S3
9001  → MinIO consola web
9090  → user-service gRPC server
9091  → auth-service gRPC server (no lo usa realmente)
9092  → Kafka broker
2181  → Zookeeper
```

### Stack tecnológico
- **Framework**: Quarkus 3.34.3
- **Java**: 21
- **BD**: PostgreSQL 16
- **ORM**: Hibernate Reactive + Panache
- **REST**: Quarkus REST (RESTEasy Reactive)
- **gRPC**: Quarkus gRPC
- **Mensajería**: SmallRye Reactive Messaging + Apache Kafka (confluentinc/cp-kafka:7.5.0)
- **JWT**: SmallRye JWT
- **Email**: Quarkus Mailer (MailHog en dev)
- **Imágenes**: Quarkiverse Amazon S3 + MinIO (dev) / Cloudflare R2 (prod)
- **Contenedores**: Jib (sin Dockerfile)

---

## Estructura del Proyecto

```
kittigram/
├── pom.xml                  ← padre agregador
├── docker-compose.yml       ← PostgreSQL + MinIO + Kafka + Zookeeper + Kafka UI
├── init.sql                 ← CREATE SCHEMA users, auth, cats, adoption, form_analysis
├── BITACORA.md
├── user-service/
├── auth-service/
├── storage-service/
├── cat-service/
├── gateway-service/
├── notification-service/
├── adoption-service/
├── form-analysis-service/
└── organization-service/
```

### pom.xml raíz (padre)
- `<packaging>pom</packaging>`
- Gestiona versiones vía `<dependencyManagement>` con el BOM de Quarkus y el BOM de Amazon Services
- Dependencias comunes mínimas: solo `quarkus-arc`, `quarkus-container-image-jib`, `quarkus-junit`
- Plugins comunes: `maven-compiler-plugin`, `maven-surefire-plugin`, `maven-failsafe-plugin`, `quarkus-maven-plugin`
- El `quarkus-maven-plugin` en el padre permite ejecutar `mvn quarkus:dev -pl <modulo>` desde la raíz

### Módulos
```xml
<modules>
    <module>user-service</module>
    <module>auth-service</module>
    <module>storage-service</module>
    <module>cat-service</module>
    <module>gateway-service</module>
    <module>notification-service</module>
    <module>adoption-service</module>
    <module>form-analysis-service</module>
    <module>organization-service</module>
</modules>
```

---

## Problemas Conocidos y Soluciones

### 1. Hibernate Reactive: No current Mutiny.Session
**Error**: `IllegalStateException: No current Mutiny.Session found`

**Causa**: Con Hibernate Reactive, toda operación de BD necesita una sesión activa en el contexto de Vert.x.

**Solución**:
- `@WithTransaction` en métodos de escritura del Service
- `@WithSession` en métodos de lectura del Service que devuelven `Uni<T>`
- `@WithSession` **NO** funciona en métodos que devuelven `Multi<T>` → error de compilación
- Para métodos que devuelven `Multi<T>` en el Service, la sesión se abre en el Repository con `@WithSession` en métodos que devuelven `Uni<List<T>>`

**Patrón correcto para Multi en Service**:
```java
public Multi<CatSummaryResponse> search(...) {
    Uni<List<Cat>> catsUni = catRepository.findAvailable(); // @WithSession en repo
    return catsUni
            .onItem().transformToMulti(list -> Multi.createFrom().iterable(list))
            .onItem().transform(catMapper::toSummaryResponse);
}
```

**Patrón correcto en Repository**:
```java
@WithSession
public Uni<List<Cat>> findAvailable() {
    return find("status", CatStatus.Available).list();
}
```

### 2. Deserialización de Records con Jackson
**Error**: Los campos de records Java llegaban como `null` al deserializar JSON.

**Solución**: Añadir `@JsonProperty` en los campos del record:
```java
public record RefreshRequest(
        @JsonProperty("refreshToken") String refreshToken
) {}
```

O registrar el módulo `jackson-module-parameter-names`:
```java
@Singleton
public class JacksonConfig implements ObjectMapperCustomizer {
    @Override
    public void customize(ObjectMapper objectMapper) {
        objectMapper.registerModule(new ParameterNamesModule());
    }
}
```

### 3. gRPC: puerto en conflicto
**Problema**: `auth-service` también intentaba levantar servidor gRPC en el puerto 9090 (ya usado por `user-service`).

**Solución**: Asignar puerto diferente en `auth-service/application.properties`:
```properties
quarkus.grpc.server.port=9091
```

### 4. S3AsyncClient no inyectable
**Error**: `UnsatisfiedResolutionException: Unsatisfied dependency for type S3AsyncClient`

**Solución**: Añadir el cliente HTTP asíncrono de Netty:
```xml
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>netty-nio-client</artifactId>
</dependency>
```
Y en `application.properties`:
```properties
quarkus.s3.async-client.type=netty
```

### 5. Palabras reservadas en HQL
**Error**: `Non-boolean expression used in predicate context`

**Causa**: `order` es palabra reservada en HQL/SQL.

**Solución**: Renombrar el campo en la entidad:
```java
@Column(name = "image_order", nullable = false)
public Integer imageOrder;
```

### 6. Proto en multi-módulo
**Problema**: Las clases generadas por protobuf solo están disponibles en el módulo donde está el `.proto`.

**Solución**: Copiar el fichero `.proto` en `src/main/proto/` de cada servicio que lo necesite. El módulo `proto/` existe como fuente de verdad del contrato, pero cada servicio tiene su propia copia para que Quarkus genere las clases.

### 7. @WithTransaction pierde parámetros
**Problema**: Con `@WithTransaction` en el proxy CDI, los parámetros del método podían llegar como `null`.

**Solución**: Extraer el valor del parámetro antes de llamar al service, o asegurarse de que la deserialización funciona correctamente (ver problema #2).

### 9. init.sql de docker-compose no se re-ejecuta con volumen existente
**Problema**: Al añadir nuevos esquemas a `init.sql`, el script no se vuelve a ejecutar si el volumen de PostgreSQL ya existe.

**Solución**: Recrear el volumen desde cero:
```bash
docker compose down -v
docker compose up -d
```

### 10. @Incoming + @WithTransaction son incompatibles
**Problema**: Combinar `@Incoming` con `@WithTransaction` en el mismo método lanza error en runtime porque SmallRye y la interceptión CDI de transacciones entran en conflicto.

**Solución**: Usar `Panache.withTransaction()` de forma programática dentro del cuerpo del método:
```java
@Incoming("some-topic")
public Uni<Void> onMessage(String payload) {
    return Panache.withTransaction(() -> /* persistencia */);
}
```

### 11. Panache.withTransaction() falla en tests con InMemoryConnector
**Problema**: Al usar `InMemoryConnector` en tests, `Panache.withTransaction()` falla porque no hay datasource real disponible en el contexto del test de mensajería.

**Solución**: Extraer la persistencia a un bean separado (`FormAnalysisPersistenceService`) anotado con `@WithTransaction`, y mockear ese bean en los tests con `@InjectMock`. Esto separa la responsabilidad de persistencia de la de consumo de mensajes y permite testar el consumer de forma aislada.

### 12. GlobalExceptionMapper no captura jakarta.ws.rs.NotFoundException
**Problema**: Las rutas no encontradas devolvían una respuesta genérica sin pasar por el mapper personalizado.

**Solución**: Añadir explícitamente `jakarta.ws.rs.NotFoundException` al bloque de mapeo en `GlobalExceptionMapper`.

### 13. Conflicto de rutas /adoptions/my con /adoptions/{id}
**Problema**: El path literal `/adoptions/my` podía colisionar con el path parametrizado `/adoptions/{id}` si JAX-RS resolvía primero el parámetrico.

**Solución**: JAX-RS resuelve correctamente dando prioridad a los segmentos literales sobre los parametrizados. No requiere cambios, solo confirmar el comportamiento.

### 14. Template Qute no se inyecta con @InjectMocks de Mockito
**Problema**: Al usar `@InjectMocks` en tests unitarios de consumers que inyectan `@Location("...") Template template`, Mockito no sabe cómo inyectar el `Template` ni encadenar `TemplateInstance`.

**Solución**: Declarar explícitamente `@Mock Template myTemplate` y `@Mock TemplateInstance myTemplateInstance`, y configurar el comportamiento encadenado:
```java
@Mock Template activationEmail;
@Mock TemplateInstance templateInstance;

when(activationEmail.data(anyString(), any())).thenReturn(templateInstance);
when(templateInstance.data(anyString(), any())).thenReturn(templateInstance);
when(templateInstance.createUni()).thenReturn(Uni.createFrom().item("html content"));
```

### 15. UserRegisteredEventDeserializer no encontrado en classpath
**Problema**: Al arrancar `notification-service`, Kafka lanzaba `ClassNotFoundException` buscando un deserializador personalizado que no existe en el módulo.

**Solución**: Configurar `StringDeserializer` y deserializar manualmente con `ObjectMapper` dentro del consumer (patrón ya establecido en el proyecto):
```properties
mp.messaging.incoming.user-registered.value.deserializer=org.apache.kafka.common.serialization.StringDeserializer
```

### 16. adopterEmail propagado desde JWT en lugar de llamar a user-service vía gRPC
**Problema**: `adoption-service` necesitaba el email del adoptante para incluirlo en el evento `AdoptionFormSubmittedEvent`, pero no tenía acceso al servicio de usuarios.

**Solución**: Extraer el email directamente del JWT en el `Resource` con `jwt.getClaim("email")` y pasarlo al service como parámetro. Evita añadir una llamada gRPC innecesaria dado que el token ya contiene el dato.

### 17. AdoptionFormSubmittedEvent y AdoptionFormAnalysedEvent sin adopterEmail
**Problema**: `notification-service` no sabía a quién enviar el email de resultado de análisis porque los eventos no incluían el email del adoptante.

**Solución**: Añadir `adopterEmail` a ambos events y propagarlo por todo el flujo: `AdoptionResource` → `AdoptionService` → `AdoptionFormSubmittedEvent` → `form-analysis-service` → `AdoptionFormAnalysedEvent` → `notification-service`.

### 18. Tests rotos por cambio de firma en AdoptionMapper y AdoptionService
**Problema**: Al añadir `adopterEmail` como tercer argumento en `AdoptionMapper.toEntity()` y `AdoptionService.createAdoptionRequest()`, todos los tests existentes que llamaban a esos métodos fallaron por firma incorrecta.

**Solución**: Actualizar los constructores y llamadas en `AdoptionServiceTest` y `AdoptionResourceTest` para incluir el nuevo parámetro.

### 19. AdoptionRequestResponse faltaba el campo adopterEmail
**Problema**: El record DTO de respuesta no incluía `adopterEmail` aunque la entidad sí lo tenía, causando que el campo no se expusiera en la API.

**Solución**: Añadir `adopterEmail` al record `AdoptionRequestResponse` y actualizar `AdoptionMapper.toResponse()` para mapearlo.

### 20. existsActiveByCatId falla con "No current Mutiny.Session found" en tests de integración
**Problema**: El método `existsActiveByCatId()` en `AdoptionRequestRepository` llama a `count()` de Panache, que necesita sesión activa. En los tests de integración, el método se ejecutaba fuera del contexto transaccional del service.

**Solución**: Añadir `@WithSession` al método `existsActiveByCatId` en `AdoptionRequestRepository`:
```java
@WithSession
public Uni<Boolean> existsActiveByCatId(Long catId) {
    return count("catId = ?1 and status not in (?2, ?3)", catId,
            AdoptionStatus.Rejected, AdoptionStatus.Completed)
            .map(count -> count > 0);
}
```

### 21. FormAnalysisRulesTest roto por find & replace accidental
**Problema**: Un find & replace para añadir `adopterEmail` a los constructores del test introdujo el campo dos veces en algunos constructores, causando errores de compilación.

**Solución**: Reescribir el fichero manualmente revisando cada constructor para que contenga un único `adopterEmail`.

### 22. Plantillas Qute: chain data().render() no mockeable con @InjectMocks
**Problema**: En `AdoptionFormAnalysedConsumer`, el chain de Qute termina en `.render()` (que devuelve `Uni<String>`), pero al usar `@InjectMocks` el `Template` es `null` y el chain no se puede configurar.

**Solución**: Mismo patrón que el Problema #14 pero con `.render()` en lugar de `.createUni()`:
```java
@Mock Template adoptionAcceptedEmail;
@Mock TemplateInstance templateInstance;

when(adoptionAcceptedEmail.data(anyString(), any())).thenReturn(templateInstance);
when(templateInstance.data(anyString(), any())).thenReturn(templateInstance);
when(templateInstance.render()).thenReturn(Uni.createFrom().item("<html>..."));
```

### 23. Rate limiter e2e: bucket de IP contaminado entre ejecuciones
**Problema**: Los tests e2e de upload fallaban con 429 en la primera petición de una nueva ejecución. `IpRateLimiter` usa `X-Forwarded-For` como clave de bucket. La ejecución anterior (test de rate limit, Order 6) llenaba el bucket dentro de la ventana de 60 s. La siguiente ejecución usaba la misma IP real (`127.0.0.1`) → todos los uploads siguientes devolvían 429 sin llegar al upstream.

**Solución**: Definir `static final String TEST_IP = "test-" + System.currentTimeMillis()` en la clase de test y enviarlo como header `X-Forwarded-For` en cada request que consuma del mismo bucket de rate limit. El gateway lee este header con prioridad sobre la IP del socket.

```java
private static final String TEST_IP = "test-" + System.currentTimeMillis();

given()
    .header("X-Forwarded-For", TEST_IP)
    .header("Authorization", "Bearer " + token)
    .multiPart("file", ...)
    .post("/api/storage/upload");
```

### 24. MinIO oficial no auto-crea buckets con MINIO_DEFAULT_BUCKETS
**Problema**: `minio/minio:latest` ignora la variable `MINIO_DEFAULT_BUCKETS`. Esta es una feature de `bitnami/minio`, no de la imagen oficial. El `storage-service` devolvía 500 en todos los uploads válidos porque el bucket no existía: el `S3AsyncClient` lanzaba `NoSuchBucketException` → `GlobalExceptionMapper` → HTTP 500.

**Nota**: los uploads con tipo inválido devolvían 400 correctamente porque la validación de tipo se hace antes de llamar a S3.

**Solución**: Añadir `BucketInitializer` con `@Observes StartupEvent` que hace `headBucket` y si recibe `NoSuchBucketException` crea el bucket con `createBucket`. Se usa `.get()` bloqueante porque el evento de startup es síncrono.

```java
@ApplicationScoped
public class BucketInitializer {
    @Inject S3AsyncClient s3;
    @ConfigProperty(name = "bucket.name") String bucketName;

    void onStart(@Observes StartupEvent ev) {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucketName).build()).get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof NoSuchBucketException) {
                s3.createBucket(CreateBucketRequest.builder().bucket(bucketName).build()).get();
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
```

### 25. Quarkus dev cwd = directorio del módulo, no la raíz del proyecto
**Problema**: Al arrancar con `mvn compile quarkus:dev -pl storage-service -am` desde la raíz, el proceso Java del servicio corre con `cwd = storage-service/`. Quarkus carga el `.env` del `cwd`, no de la raíz del proyecto. Los defaults de `application.properties` usaban `${MINIO_ROOT_PASSWORD:kittigram123}` pero el `.env` raíz tenía `MINIO_ROOT_PASSWORD=change_me_min16chars`. El `S3AsyncClient` fallaba en auth (403 silencioso de MinIO) → `S3Exception` → `GlobalExceptionMapper` → HTTP 500 en todos los uploads.

**Diagnóstico**: la health check `GET /q/health/live` también devolvía 500 (con el formato del `GlobalExceptionMapper`) porque el módulo `quarkus-smallrye-health` no estaba en el POM del `storage-service`, por lo que la ruta `/q/health/live` no estaba registrada y lanzaba `NotFoundException` → capturada por `ExceptionMapper<Throwable>`.

**Solución**:
1. Symlink: `ln -sf ../.env storage-service/.env` (gitignored por la regla `.env` en `.gitignore` raíz).
2. Actualizar el default de `application.properties` para que coincida con el valor del `.env` de referencia:
```properties
quarkus.s3.aws.credentials.static-provider.secret-access-key=${MINIO_ROOT_PASSWORD:change_me_min16chars}
```

El symlink es la solución duradera: Quarkus encuentra el `.env` en su `cwd` y carga las credenciales reales. El default actualizado actúa como fallback para entornos sin el symlink. Si en el futuro se añade otro servicio con credenciales externas, aplicar el mismo patrón.

### 8. PanacheRepository no tiene stream() en modo reactivo
**Problema**: `find("catId", catId).stream()` no compila — `stream()` no existe en `PanacheQuery` reactivo.

**Solución**: Usar `list()` y transformar a `Multi` manualmente:
```java
public Multi<CatImage> findByCatId(Long catId) {
    return find("catId", catId).list()
            .onItem().transformToMulti(list -> Multi.createFrom().iterable(list));
}
```
No añadir `@WithSession` si la sesión ya está abierta en el Service llamante.

---

## Servicios Implementados

### user-service
**Puerto**: 8081
**Esquema BD**: `users`
**gRPC server**: 9090

**Dependencias**:
- `quarkus-rest-jackson`
- `quarkus-hibernate-reactive-panache`
- `quarkus-reactive-pg-client`
- `quarkus-messaging`
- `quarkus-messaging-kafka` (productor Kafka)
- `quarkus-mailer`
- `quarkus-grpc`
- `quarkus-elytron-security-common` (BcryptUtil para hash de passwords)
- `quarkus-smallrye-jwt` (verificación JWT)
- `quarkus-container-image-jib`

**Entidades**:
- `User` → tabla `users.users`
  - id, email, passwordHash, name, surname, birthdate, status, activationToken, createdAt, updatedAt
  - `UserStatus`: Pending, Active, Inactive, Banned
  - `@PrePersist` y `@PreUpdate` para timestamps

**Estructura**:
```
domain/Email.java               ← Value Object (clase final, of() factory)
domain/ActivationToken.java     ← Value Object (clase final, of() factory)
entity/User.java
entity/UserStatus.java
event/UserRegisteredEvent.java  ← record publicado a Kafka
repository/UserRepository.java
service/UserService.java
mapper/UserMapper.java
dto/UserCreateRequest.java
dto/UserUpdateRequest.java
dto/UserResponse.java
resource/UserResource.java
grpc/UserGrpcService.java       ← servidor gRPC
exception/UserNotFoundException.java
exception/ErrorResponse.java
exception/GlobalExceptionMapper.java
```

**Endpoints REST**:
- `POST /users` → 201 Created + Location header (**público**, registro)
- `GET /users/{email}` → 200 (**requiere JWT**)
- `GET /users/active` → Multi stream (**requiere JWT**)
- `POST /users/activate` → 200 (**público**, body `{"token":"..."}`, activa cuenta con token UUID)
- `PUT /users/{email}` → 200 (**requiere JWT**, solo el propio usuario)
- `PUT /users/{email}/activate` → 200 (**requiere JWT**, solo el propio usuario)
- `PUT /users/{email}/deactivate` → 200 (**requiere JWT**, solo el propio usuario)

**Autorización**: la clase está anotada `@Authenticated`. El `PUT` verifica que el claim `email` del JWT coincide con el `{email}` del path. Si no coincide → 403.

**gRPC**:
```proto
service UserService {
    rpc ValidateCredentials(ValidateCredentialsRequest) returns (ValidateCredentialsResponse);
    rpc GetUserById(GetUserByIdRequest) returns (GetUserResponse);
}
```
- `@WithSession` en los métodos del `UserGrpcService`

**Eventos Kafka**:
- `user-registered` (outgoing): publicado en `createUser()`. Payload: `UserRegisteredEvent(userId, email, name, activationToken)`
- Serializer: `ObjectMapperSerializer` (JSON)
- Config: `mp.messaging.outgoing.user-registered.*`

**Notas importantes**:
- La tabla se llama `users` (no `User` para evitar conflicto con palabra reservada en PostgreSQL)
- El hash de password usa `BcryptUtil.bcryptHash()` y `BcryptUtil.matches()`
- El borrado es **lógico** (status → Inactive), no físico

---

### auth-service
**Puerto**: 8082
**Esquema BD**: `auth`
**gRPC client**: llama a user-service en 9090

**Dependencias**:
- `quarkus-rest-jackson`
- `quarkus-hibernate-reactive-panache`
- `quarkus-reactive-pg-client`
- `quarkus-messaging`
- `quarkus-grpc`
- `quarkus-smallrye-jwt`
- `quarkus-smallrye-jwt-build`
- `jackson-module-parameter-names`
- `quarkus-container-image-jib`

**Entidades**:
- `RefreshToken` → tabla `auth.refresh_tokens`
  - id, token (UUID), userId, email, expiresAt, revoked, createdAt
  - `isExpired()` e `isValid()` como métodos de dominio en la entidad

**Estructura**:
```
entity/RefreshToken.java
repository/RefreshTokenRepository.java
service/AuthService.java
service/JwtTokenService.java    ← genera y firma el access token (extraído para testabilidad)
dto/AuthRequest.java
dto/AuthResponse.java
dto/RefreshRequest.java         ← @JsonProperty en campo
dto/LogoutRequest.java          ← @JsonProperty en campo
resource/AuthResource.java
grpc/UserServiceClient.java     ← cliente gRPC
config/JacksonConfig.java       ← registra ParameterNamesModule
exception/InvalidCredentialsException.java
exception/InvalidTokenException.java
exception/ErrorResponse.java
exception/GlobalExceptionMapper.java
```

**Endpoints REST**:
- `POST /auth/login` → 200 + accessToken + refreshToken
- `POST /auth/refresh` → 200 + nuevo accessToken + nuevo refreshToken
- `POST /auth/logout` → 204 No Content

**Flujo de autenticación**:
1. Cliente → `POST /auth/login`
2. `auth-service` llama a `user-service` vía gRPC `ValidateCredentials`
3. `user-service` valida email + bcrypt password
4. `auth-service` genera JWT (15 min) + UUID refresh token (7 días)
5. Guarda refresh token en BD
6. Devuelve ambos tokens

**JWT**:
- Issuer: `https://kittigram.ciscoadiz.org`
- Subject: `String.valueOf(userId)` (el id numérico del usuario)
- Claim adicional: `email`
- Access token: 900 segundos
- Firmado con **clave privada RSA** en `src/main/resources/privateKey.pem`
- Configuración: `smallrye.jwt.sign.key.location=privateKey.pem`

**Notas importantes**:
- `saveToken()` en repository con `@WithTransaction` para persistir refresh tokens
- `refresh()` en service sin `@WithTransaction` (la transacción la gestiona el repository)
- El `LogoutRequest` y `RefreshRequest` necesitan `@JsonProperty` en sus campos

---

### storage-service
**Puerto**: 8083
**Sin BD** (no necesita persistencia propia)

**Dependencias**:
- `quarkus-rest-jackson`
- `quarkus-rest`
- `quarkus-amazon-s3` (io.quarkiverse.amazonservices)
- `software.amazon.awssdk:netty-nio-client`
- `quarkus-container-image-jib`
- BOM: `quarkus-amazon-services-bom` (en el padre)

**Estructura**:
```
provider/StorageProvider.java       ← interfaz
provider/S3StorageProvider.java     ← implementación S3/MinIO/R2
service/StorageService.java         ← validación + lógica
resource/StorageResource.java       ← endpoints REST
dto/UploadResponse.java
exception/InvalidFileException.java
exception/ErrorResponse.java
exception/GlobalExceptionMapper.java
```

**Endpoints REST**:
- `POST /storage/upload` → multipart/form-data → 200 + {key, url}
- `DELETE /storage/{key}` → 204
- `GET /storage/files/{key}` → sirve el archivo desde S3 con su Content-Type original (**público**)

**Validaciones**:
- Tipos permitidos: `image/jpeg`, `image/png`
- Tamaño máximo: 5MB

**Interfaz StorageProvider**:
```java
public interface StorageProvider {
    Uni<String> upload(String key, byte[] data, String contentType);
    Uni<Void> delete(String key);
    String getUrl(String key);
}
```

**URLs de imágenes**: `S3StorageProvider.getUrl()` devuelve `{storage.public.url}/storage/files/{key}`,
donde `storage.public.url=http://localhost:8080/api` en dev. Las URLs apuntan al gateway,
no a MinIO directamente, desacoplando las URLs públicas del almacenamiento interno.

**Configuración MinIO** (`application.properties`):
```properties
quarkus.s3.endpoint-override=http://localhost:9000
quarkus.s3.path-style-access=true
quarkus.s3.aws.region=us-east-1
quarkus.s3.aws.credentials.type=static
quarkus.s3.aws.credentials.static-provider.access-key-id=${MINIO_ROOT_USER:kittigram}
quarkus.s3.aws.credentials.static-provider.secret-access-key=${MINIO_ROOT_PASSWORD:change_me_min16chars}
bucket.name=${MINIO_DEFAULT_BUCKETS:kittigram}
quarkus.s3.async-client.type=netty
```

**Notas importantes**:
- El bucket MinIO se crea automáticamente al arrancar el servicio via `BucketInitializer` (`@Observes StartupEvent`). `minio/minio:latest` NO crea buckets via `MINIO_DEFAULT_BUCKETS` — eso es una feature de `bitnami/minio`.
- El symlink `storage-service/.env → ../.env` es necesario en dev porque Quarkus carga el `.env` desde el `cwd` del proceso, que es el directorio del módulo, no la raíz del proyecto.
- Para producción: Cloudflare R2 (sin egress), compatible con API S3
- Las URLs son permanentes (el contenido es público por naturaleza)
- `@MultipartForm` está deprecado en Quarkus REST reactivo, usar `@RestForm` directamente

---

### cat-service
**Puerto**: 8084
**Esquema BD**: `cats`
**REST client**: llama a storage-service en 8083

**Dependencias**:
- `quarkus-rest-jackson`
- `quarkus-hibernate-reactive-panache`
- `quarkus-reactive-pg-client`
- `quarkus-messaging`
- `quarkus-rest-client-jackson`
- `quarkus-smallrye-jwt` (verificación JWT)
- `quarkus-container-image-jib`

**Entidades**:
- `Cat` → tabla `cats.cats`
  - id, name, age, sex (CatSex), description, neutered, status (CatStatus)
  - userId (referencia sin FK a user-service)
  - city, region, country, latitude, longitude
  - profileImageUrl (desnormalizado para rendimiento en listados)
  - createdAt, updatedAt
  - `CatSex`: Male, Female
  - `CatStatus`: Available, InProcess, Adopted
  - `@PrePersist` setea status = Available

- `CatImage` → tabla `cats.cat_images`
  - id, catId, key (nombre en bucket), url, imageOrder, createdAt
  - `imageOrder` (no `order` → palabra reservada en HQL)

**Estructura**:
```
entity/Cat.java
entity/CatSex.java
entity/CatStatus.java
entity/CatImage.java
repository/CatRepository.java
repository/CatImageRepository.java
service/CatService.java
mapper/CatMapper.java
client/StorageClient.java           ← REST client hacia storage-service
client/dto/StorageResponse.java
dto/CatCreateRequest.java
dto/CatUpdateRequest.java
dto/CatResponse.java                ← con List<CatImageResponse>
dto/CatSummaryResponse.java         ← sin imágenes, para listados
dto/CatImageResponse.java
resource/CatResource.java
exception/CatNotFoundException.java
exception/ErrorResponse.java
exception/GlobalExceptionMapper.java
```

**Endpoints REST**:
- `GET /cats?city=X&name=Y` → Multi stream de CatSummaryResponse (**público**)
- `GET /cats/{id}` → CatResponse con imágenes (**público**)
- `POST /cats` → 201 (**requiere JWT**, userId extraído del token)
- `PUT /cats/{id}` → 200 (**requiere JWT**, solo el dueño del gato)
- `DELETE /cats/{id}` → 204 (**requiere JWT**, solo el dueño del gato)
- `POST /cats/{id}/images` → multipart, sube a storage-service (**requiere JWT**, solo el dueño)
- `DELETE /cats/{catId}/images/{imageId}` → 204 (**requiere JWT**, solo el dueño)

**Autorización**: la clase está anotada `@Authenticated`. El `userId` se extrae con `Long.parseLong(jwt.getSubject())`. El service verifica propiedad en toda operación de escritura:
```java
private void requireOwner(Cat cat, Long userId) {
    if (!cat.userId.equals(userId)) {
        throw new ForbiddenException("Access denied");
    }
}
```

**Diseño de búsqueda**:
- Solo por ciudad y/o nombre (decisión ética: no fomentar búsqueda por raza)
- Listado devuelve `CatSummaryResponse` (sin imágenes) para evitar N+1
- `profileImageUrl` desnormalizado en `Cat` para mostrar miniatura en listado
- Detalle `GET /cats/{id}` carga las imágenes completas

**StorageClient**:
```java
@RegisterRestClient(configKey = "storage-service")
@Path("/storage")
public interface StorageClient {
    @POST @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    Uni<StorageResponse> upload(@RestForm("file") FileUpload file);

    @DELETE @Path("/{key}")
    Uni<Void> delete(@PathParam("key") String key);
}
```
Configuración:
```properties
quarkus.rest-client.storage-service.url=${STORAGE_SERVICE_URL:http://localhost:8083}
```

**Notas importantes**:
- Sin relaciones JPA, todo se carga explícitamente
- `imageOrder` siempre es 0 por ahora (pendiente implementar reordenación)
- Al subir primera imagen → se actualiza `profileImageUrl` en Cat
- Al borrar imagen de perfil → pendiente actualizar `profileImageUrl` con la siguiente imagen

---

### adoption-service
**Puerto**: 8086
**Esquema BD**: `adoption`

**Dependencias**:
- `quarkus-rest-jackson`
- `quarkus-hibernate-reactive-panache`
- `quarkus-reactive-pg-client`
- `quarkus-messaging-kafka` (productor + consumidor)
- `quarkus-smallrye-jwt` (verificación JWT)
- `quarkus-container-image-jib`

**Entidades**:
- `AdoptionRequest` → tabla `adoption.adoption_requests`
  - id, catId, adopterId, organizationId, status (AdoptionStatus), rejectionReason, createdAt, updatedAt
  - `AdoptionStatus`: Pending, Reviewing, Accepted, Rejected, FormCompleted, AwaitingPayment, Completed
- `AdoptionRequestForm` → formulario de screening del candidato (40+ campos)
  - hasPreviousCatExperience, adultsInHousehold, hasChildren, childrenAges, hasOtherPets
  - housingType (HousingType enum), housingSize, hasOutdoorAccess, isRental, rentalPetsAllowed
  - householdActivityLevel (ActivityLevel enum), dailyPlayMinutes, motivationToAdopt
  - understandsLongTermCommitment, hasVetBudget, allHouseholdMembersAgree, anyoneHasAllergies
- `Interview` → tabla `adoption.interviews`
  - adoptionRequestId, scheduledAt, location, notes
- `AdoptionForm` → contrato legal de adopción
  - adoptionRequestId, adopterName, adopterDni, catName, signedAt, terms
- `Expense` → gastos asociados a la adopción
  - adoptionRequestId, amount, description, recipient (ExpenseRecipient)

**Estructura**:
```
entity/AdoptionRequest.java
entity/AdoptionStatus.java
entity/AdoptionRequestForm.java
entity/AdoptionForm.java
entity/Interview.java
entity/Expense.java
entity/ExpenseRecipient.java
entity/ActivityLevel.java
entity/HousingType.java
repository/AdoptionRequestRepository.java
repository/AdoptionRequestFormRepository.java
repository/AdoptionFormRepository.java
repository/InterviewRepository.java
repository/ExpenseRepository.java
service/AdoptionService.java
mapper/AdoptionMapper.java
event/AdoptionFormSubmittedEvent.java  ← publicado a Kafka (screening data)
event/AdoptionFormAnalysedEvent.java   ← consumido de Kafka (decisión externa)
dto/AdoptionRequestCreateRequest.java
dto/AdoptionRequestResponse.java
dto/AdoptionStatusUpdateRequest.java
dto/AdoptionRequestFormCreateRequest.java
dto/AdoptionRequestFormResponse.java
dto/AdoptionFormCreateRequest.java
dto/AdoptionFormResponse.java
dto/InterviewCreateRequest.java
dto/InterviewResponse.java
dto/ExpenseResponse.java
exception/AdoptionRequestNotFoundException.java
exception/CatNotAvailableException.java
exception/InvalidAdoptionStatusException.java
exception/AdoptionFormAlreadySubmittedException.java
exception/ErrorResponse.java
exception/GlobalExceptionMapper.java
```

**Flujo de adopción**:
1. Adoptante → `POST /adoptions` (crea solicitud, verifica que no haya solicitud activa para ese gato)
2. Adoptante → `POST /adoptions/{id}/request-form` (formulario de screening; estado → Reviewing; publica `adoption-form-submitted` a Kafka)
3. Servicio externo analiza el formulario → publica `adoption-form-analysed` (ACCEPTED/REJECTED)
4. Organización → `PUT /adoptions/{id}/status` (puede aceptar/rechazar manualmente)
5. Organización → `POST /adoptions/{id}/interview` (agenda entrevista; requiere estado Accepted)
6. Adoptante → `POST /adoptions/{id}/adoption-form` (contrato legal; estado → FormCompleted; único, no repetible)

**Kafka**:
- `adoption-form-submitted` (outgoing): 40+ campos del formulario de screening para análisis externo
- `adoption-form-analysed` (incoming): `{ adoptionRequestId, decision: "ACCEPTED"|"REJECTED", rejectionReason }` — deserialización manual con `ObjectMapper` (mismo patrón que `notification-service`)

**Autorización**:
- `requireAdopter()`: verifica que `adopterId` del token == `adopterId` de la solicitud
- `requireOrganizationOwner()`: verifica que `organizationId` del token == `organizationId` de la solicitud
- `requireStatus()`: verifica que el estado actual es el esperado; lanza `InvalidAdoptionStatusException` si no

**Notas importantes**:
- `existsActiveByCatId()` en el repository evita solicitudes duplicadas para el mismo gato
- Dentro de `submitRequestForm()`, el cambio de estado en `AdoptionRequest` se persiste con `subscribe().with(...)` porque la transacción ya está abierta por el método principal
- El `ObjectMapper` en `onFormAnalysed()` se instancia manualmente (patrón idéntico al `notification-service`)

---

### form-analysis-service
**Puerto**: 8087
**Esquema BD**: `form_analysis`

**Dependencias**:
- `quarkus-rest-jackson`
- `quarkus-hibernate-reactive-panache`
- `quarkus-reactive-pg-client`
- `quarkus-messaging-kafka` (consumidor + productor)
- `quarkus-container-image-jib`

**Entidades**:
- `FormAnalysis` → tabla `form_analysis.form_analyses`
  - id, adoptionRequestId, decision (AnalysisDecision), rejectionReason, createdAt
  - `AnalysisDecision`: ACCEPTED, REJECTED
- `FormFlag` → tabla `form_analysis.form_flags`
  - id, formAnalysisId, category, description, severity (FlagSeverity)
  - `FlagSeverity`: CRITICAL, WARNING, NOTICE

**Estructura**:
```
entity/FormAnalysis.java
entity/FormFlag.java
entity/AnalysisDecision.java
entity/FlagSeverity.java
repository/FormAnalysisRepository.java
repository/FormFlagRepository.java
rules/FormAnalysisRules.java        ← lógica de reglas por categorías
service/FormAnalysisService.java    ← consumer + producer Kafka
service/FormAnalysisPersistenceService.java  ← persistencia aislada con @WithTransaction
event/AdoptionFormSubmittedEvent.java  ← consumido de Kafka
event/AdoptionFormAnalysedEvent.java   ← publicado a Kafka
exception/ErrorResponse.java
exception/GlobalExceptionMapper.java
```

**Flujo**:
1. `adoption-service` publica `AdoptionFormSubmittedEvent` en topic `adoption-form-submitted`
2. `FormAnalysisService` consume el evento vía `@Incoming("adoption-form-submitted")`
3. `FormAnalysisRules` evalúa el formulario y genera flags clasificados
4. Se determina la decisión: REJECTED si hay algún flag CRITICAL, ACCEPTED en caso contrario
5. `FormAnalysisPersistenceService` persiste la decisión y los flags en BD (separado para permitir tests)
6. Se publica `AdoptionFormAnalysedEvent` en topic `adoption-form-analysed`

**Reglas de análisis** (`FormAnalysisRules`):
- **Critical**: experiencia inexistente con mascotas + niños pequeños, vivienda de alquiler sin permiso de mascotas, ningún miembro del hogar de acuerdo
- **Warning**: sin acceso exterior ni espacio suficiente, sin presupuesto veterinario, alguien con alergias
- **Notice**: compromiso a largo plazo no confirmado del todo, actividad baja con muchos niños

**Kafka**:
- `adoption-form-submitted` (incoming): recibe el formulario de screening completo
- `adoption-form-analysed` (outgoing): publica la decisión con `adoptionRequestId`, `decision` y `rejectionReason` opcional

**Notas importantes**:
- `@Incoming` + `@WithTransaction` son incompatibles → persistencia extraída a `FormAnalysisPersistenceService` (ver Problema #10 y #11)
- En tests, `FormAnalysisPersistenceService` se mockea con `@InjectMock` para aislar el consumer

---

### notification-service
**Puerto**: 8085
**Sin BD** (solo envía emails)

**Dependencias**:
- `quarkus-rest-jackson`
- `quarkus-messaging-kafka` (consumidor Kafka)
- `quarkus-mailer`
- `quarkus-container-image-jib`

**Dependencias** (adicionales):
- `quarkus-qute` (plantillas HTML)
- `quarkus-rest-qute`

**Estructura**:
```
consumer/UserRegisteredConsumer.java         ← @Incoming("user-registered")
consumer/AdoptionFormAnalysedConsumer.java   ← @Incoming("adoption-form-analysed")
event/UserRegisteredEvent.java               ← mismo record que user-service
event/AdoptionFormAnalysedEvent.java         ← mismo record que adoption/form-analysis-service
resources/templates/
    activation-email.html                    ← plantilla Qute email activación
    adoption-accepted-email.html             ← plantilla Qute formulario aceptado
    adoption-rejected-email.html             ← plantilla Qute formulario rechazado
```

**Flujo**:
1. `user-service` publica `UserRegisteredEvent` en topic `user-registered`
2. `notification-service` consume el mensaje vía `@Incoming("user-registered")`
3. Deserializa con `ObjectMapper` (llega como `String`)
4. Renderiza plantilla Qute y envía email HTML via `ReactiveMailer`

**Flujo adicional (adopción)**:
1. `form-analysis-service` publica `AdoptionFormAnalysedEvent` en topic `adoption-form-analysed`
2. `AdoptionFormAnalysedConsumer` consume el evento
3. Envía email de resultado (aceptado o rechazado) al adoptante con plantilla Qute correspondiente

**Configuración Kafka**:
```properties
kafka.bootstrap.servers=${KAFKA_HOST:localhost}:${KAFKA_PORT:9092}
mp.messaging.incoming.user-registered.connector=smallrye-kafka
mp.messaging.incoming.user-registered.topic=user-registered
mp.messaging.incoming.user-registered.value.deserializer=org.apache.kafka.common.serialization.StringDeserializer
mp.messaging.incoming.user-registered.group.id=notification-service
mp.messaging.incoming.adoption-form-analysed.connector=smallrye-kafka
mp.messaging.incoming.adoption-form-analysed.topic=adoption-form-analysed
mp.messaging.incoming.adoption-form-analysed.value.deserializer=org.apache.kafka.common.serialization.StringDeserializer
mp.messaging.incoming.adoption-form-analysed.group.id=notification-service
```

**Emails**:
- Activación: "Activa tu cuenta en Kittigram 🐱" → enlace `http://localhost:8080/api/users/activate?token={activationToken}`
- Adopción aceptada: notifica al adoptante que puede continuar el proceso
- Adopción rechazada: notifica al adoptante con el motivo de rechazo
- MailHog en dev (puerto 1025 SMTP, 8025 web UI)

**Notas importantes**:
- El mensaje Kafka llega como `String` → se deserializa manualmente con `ObjectMapper`
- El `notification-service` tiene su propia copia de los events (sin dependencias entre módulos Maven)
- Las plantillas Qute se inyectan con `@Location("nombre-plantilla.html") Template miTemplate`
- Para tests unitarios: mockear `Template` y `TemplateInstance` explícitamente y encadenar `data()` (ver Problema #14)

---

### gateway-service
**Puerto**: 8080 (punto de entrada público)
**Sin BD**

**Dependencias**:
- `quarkus-rest`
- `quarkus-rest-client-jackson`
- `quarkus-smallrye-jwt` (validación JWT centralizada)
- `quarkus-container-image-jib`

**Estructura**:
```
filter/JwtAuthFilter.java       ← intercepta todas las peticiones
proxy/ProxyService.java         ← resuelve destino y hace proxy con Vert.x WebClient
resource/GatewayResource.java   ← GET/POST/PUT/DELETE en /api/{path:.+}
config/WebClientConfig.java     ← produce WebClient singleton
```

**Enrutado por prefijo**:
- `/api/auth/**` → auth-service (8082)
- `/api/users/**` → user-service (8081)
- `/api/cats/**` → cat-service (8084)
- `/api/storage/**` → storage-service (8083)

La ruta interna se reescribe eliminando el prefijo `/api`: `/api/cats/1` → `/cats/1`.

**Rutas públicas** (sin token requerido):
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/users` (registro)
- `POST /api/users/activate` (activación de cuenta)
- `GET /api/cats` y `GET /api/cats/{id}`

**Notas importantes**:
- El proxy propaga `Authorization` y `Content-Type` al servicio destino
- `JwtAuthFilter` usa `@ServerRequestFilter` de RESTEasy Reactive (devuelve `Uni<Response>`)
- `ProxyService` usa `Vert.x WebClient` (no los REST clients de Quarkus) para poder hacer
  proxy genérico sin definir cada endpoint individualmente
- El proxy pasa `byte[]` crudos con el `Content-Type` original (incluido el multipart boundary),
  haciendo el forwarding transparente a cualquier tipo de body (JSON, multipart, etc.)
- `GET /api/storage/files/*` es público (imágenes accesibles sin autenticación)
- CORS configurado para `localhost:5173` (Vite) con credentials y max-age=86400

---

## Seguridad JWT

### Claves RSA
Par de claves RSA-2048 generadas con OpenSSL (PKCS8 para la privada):
- `auth-service/src/main/resources/privateKey.pem` → firma de tokens
- `user-service/src/main/resources/publicKey.pem` → verificación
- `cat-service/src/main/resources/publicKey.pem` → verificación

Los ficheros `*.pem` están excluidos de control de versiones (`.gitignore`). Cada desarrollador debe generarlos localmente. Ver instrucciones en el README → sección *Generating the RSA key pair*.

### Configuración por servicio

**auth-service** (firma):
```properties
smallrye.jwt.sign.key.location=privateKey.pem
mp.jwt.verify.issuer=https://kittigram.ciscoadiz.org
smallrye.jwt.new-token.issuer=https://kittigram.ciscoadiz.org
smallrye.jwt.new-token.lifespan=900
```

**user-service / cat-service** (verificación):
```properties
mp.jwt.verify.issuer=https://kittigram.ciscoadiz.org
mp.jwt.verify.publickey.location=publicKey.pem
```

### Estructura del token
- `sub`: userId (Long como String)
- `email`: email del usuario
- `iss`: `https://kittigram.ciscoadiz.org`
- Expiración: 900 segundos

---

## Repositorio Git

### Convenciones de Commits

El proyecto usa **Conventional Commits** con scope obligatorio (excepto en commits transversales). Los mensajes están en **inglés**, en **imperativo**, sin punto final.

#### Formato

```
<type>(<scope>): <descripción>
```

#### Tipos permitidos

| Tipo | Uso |
|------|-----|
| `feat` | Nueva funcionalidad (endpoints, entidades, lógica de negocio, Kafka, etc.) |
| `fix` | Corrección de bugs |
| `test` | Añadir o modificar tests (unitarios o integración) |
| `refactor` | Reestructuración sin cambiar comportamiento |
| `docs` | Documentación (BITACORA.md, README.md) |
| `chore` | Tareas de mantenimiento (scaffold, docker-compose, dependencias) |
| `build` | Configuración de build (pom.xml, dependencias Maven) |
| `security` | Cambios de seguridad transversales (sin scope de servicio) |

#### Scope

El scope es el **nombre del servicio** afectado:
- `user-service`, `auth-service`, `storage-service`, `cat-service`
- `gateway-service`, `notification-service`, `adoption-service`, `form-analysis-service`

Para cambios transversales que afectan a varios servicios, se puede omitir el scope o usar un concepto (`security`, `deps`).

#### Ejemplos del historial real

**Nuevas funcionalidades (feat):**
```
feat(user-service): domain layer
feat(user-service): application layer
feat(user-service): gRPC server
feat(auth-service): domain layer
feat(cat-service): application layer
feat(storage-service): S3/MinIO file storage
feat(gateway-service): JWT auth filter + routing config
feat(gateway-service): Vert.x WebClient config
feat(gateway-service): reverse proxy implementation
feat(notification-service): email verification on registration
feat(adoption-service): bootstrap microservice and define domain entities
feat(adoption-service): implement core business logic and kafka integration
feat(form-analysis-service): implement automated form analysis rules
feat(security): JWT authentication
```

**Tests:**
```
test(user-service): add unit tests for user service
test(auth-service): add integration tests for login, refresh and logout endpoints
test(cat-service): add integration tests for cat CRUD endpoints
test(storage-service): add integration tests for file upload with MinIO testcontainer
test(gateway-service): add integration tests for routing and JWT filter
test(notification-service): add integration tests for activation email consumer
test(notification-service): implement unit tests for UserRegisteredConsumer
test(adoption-service): implement unit tests for adoption service logic
test(rules): Add unit tests for FormAnalysisRules
```

**Refactors:**
```
refactor(auth): Delegate access token generation to JwtTokenService
refactor(user-service): introduce email and activation token value objects
refactor(adoption-service): restructure adoption form for legal contracts
```

**Fixes:**
```
fix(user-service): align test assertions with actual HTTP response codes
fix(gateway-service): multipart support in proxy
fix(gateway-service): public file routes, logging, CORS for Vite
fix(adoption-service): map JAX-RS NotFoundException to 404 status
fix(adoption-service): use programmatic transaction for form analysis consumer
```

**Documentación:**
```
docs: add BITACORA.md with full project context
docs: document integration tests in BITACORA and README
docs: document unit tests and JwtTokenService refactor
docs: session 2026-04-14 — testing strategy, Value Objects, DDD decisions
docs(README): add table of contents
docs(BITACORA): gateway-service implementation details
```

**Chore/Build:**
```
chore: project scaffold
chore(gateway-service): scaffold
chore: add Kafka, Zookeeper and Kafka UI to docker-compose
chore: add MailHog for email testing
build(deps): Add Mockito JUnit Jupiter dependency
build(form-analysis-service): inherit configuration from parent pom
```

#### Buenas prácticas

1. **Commits atómicos**: un commit = un cambio lógico completo
2. **Scope por servicio**: siempre incluir el servicio modificado
3. **Agrupación por capa**: `domain layer` → `application layer` → `integration`
4. **Tests separados**: commits de tests aparte de los de implementación
5. **Primera letra minúscula** en la descripción (excepto nombres propios)
6. **Sin punto final** en el mensaje
7. **Imperativo**: "add", "implement", "fix", no "added", "implements", "fixes"

#### Patrones comunes de agrupación

Para un nuevo servicio:
```
chore(<servicio>): scaffold
feat(<servicio>): domain layer
feat(<servicio>): application layer
feat(<servicio>): REST API / gRPC server
test(<servicio>): add unit tests
test(<servicio>): add integration tests
```

Para una nueva feature en servicio existente:
```
feat(<servicio>): <descripción de la feature>
test(<servicio>): add tests for <feature>
docs: document <feature> in BITACORA
```

---

## docker-compose.yml

Incluye: PostgreSQL, MinIO, MailHog, Zookeeper, Kafka, Kafka UI.

Kafka config relevante:
- `KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092` → para conexiones desde host
- `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1` → cluster de un solo nodo
- Kafka UI en `http://localhost:8008` (puerto interno 8080 mapeado a 8008 para evitar conflicto con gateway)

## init.sql

```sql
CREATE SCHEMA IF NOT EXISTS users;
CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS cats;
```

---

## Value Objects y DDD Táctico

### Decisión: Value Objects como clases finales, no records

Los Value Objects se implementan como **clases finales con constructor privado y método estático `of()`**. No se usan records porque los records no pueden tener un constructor canónico verdaderamente privado, lo que impediría garantizar las invariantes de construcción.

```java
public final class Email {
    private final String value;

    private Email(String value) {
        this.value = value;
    }

    public static Email of(String value) {
        if (value == null || !value.contains("@")) {
            throw new IllegalArgumentException("Invalid email: " + value);
        }
        return new Email(value.toLowerCase());
    }

    public String value() { return value; }
}
```

### Responsabilidades

| Capa | Responsabilidad |
|------|-----------------|
| Value Object | Validación de **formato** únicamente |
| Service | Validación de **reglas de negocio** (duplicados, tokens expirados, etc.) |
| Mapper | Transformación pura (sin validación, sin lógica de negocio) |
| GlobalExceptionMapper | Traducción de excepciones de dominio a respuestas HTTP |

### Ubicación en el proyecto

Los VOs viven en un package `domain/` dentro de cada servicio:
```
user-service/src/main/java/org/ciscoadiz/user/domain/
    Email.java
    ActivationToken.java
```

### Principios SOLID aplicados
- **SRP**: cada capa tiene una única razón de cambio
- **OCP**: VOs inmutables, extensión sin modificación
- **ISP**: interfaces de repository enfocadas (un método = un contrato)
- **DIP**: Services dependen de interfaces de Repository (ports), no de implementaciones

### Repository como puerto (DIP)

Los Services dependerán de interfaces, no de las implementaciones `PanacheRepository`:
```java
// Puerto (en domain/ o repository/)
public interface UserRepository {
    Uni<User> findByEmail(String email);
    Uni<User> findByActivationToken(String token);
    Uni<User> persist(User user);
}

// Adaptador (implementación)
@ApplicationScoped
public class UserRepositoryImpl extends PanacheRepository<User> implements UserRepository { ... }
```

---

## Tests de Integración

Cada servicio tiene su propio conjunto de tests de integración con `@QuarkusIntegrationTest` / `@QuarkusTest`. La estrategia varía por servicio según sus dependencias externas.

### Convención de configuración de tests

**No se crea un `application.properties` separado para tests.** Toda la configuración específica de test se añade al `application.properties` principal usando el perfil `%test.`:

```properties
# Perfil test: init script para DevServices PostgreSQL
%test.quarkus.datasource.devservices.init-script-path=init-test.sql

# Perfil test: conector Kafka en memoria en lugar del real
%test.mp.messaging.connector.smallrye-kafka.connector=smallrye-in-memory
```

El fichero `src/test/resources/init-test.sql` crea el schema necesario para los tests de integración:
```sql
CREATE SCHEMA IF NOT EXISTS users;
```

### Recuento de tests (estado actual)

| Servicio | Integración | Unitarios | Total |
|---|---|---|---|
| user-service | 3 | 8 | **11** |
| auth-service | 4 | 7 | **11** |
| cat-service | 5 | 9 | **14** |
| storage-service | 3 | 9 | **12** |
| gateway-service | 23 | 2 | **25** |
| notification-service | 2 | 3 | **5** |
| adoption-service | 9 | 17 | **26** |
| form-analysis-service | 3 | 8 | **11** |
| organization-service | 11 | 14 | **25** |
| **Total** | **63** | **77** | **143** |

E2E adicionales: `StorageE2E` (6 tests), `SecurityE2E` (2 tests) — requieren stack completo.

### Tests unitarios

#### user-service — UserServiceTest
- **Framework**: Mockito (`@ExtendWith(MockitoExtension.class)`)
- **Mocks**: `UserRepository`, `UserMapper`, Kafka `Emitter<UserRegisteredEvent>`, `ReactiveMailer`
- **Tests**: registro con emisión de evento, activación de cuenta, desactivación, email duplicado (409), token inválido (400), usuario no encontrado (404)

#### auth-service — AuthServiceTest
- **Framework**: Mockito (`@ExtendWith(MockitoExtension.class)`)
- **Mocks**: `UserServiceClient` (gRPC), `RefreshTokenRepository`, `JwtTokenService`
- **Tests**: autenticación con credenciales válidas, credenciales inválidas (InvalidCredentialsException), refresh exitoso, token no encontrado, token expirado, token revocado, logout exitoso, logout con token inexistente

**Nota de diseño**: para poder mockear la generación de JWT en los tests, se extrajo la lógica de firma a `JwtTokenService` (ver refactor en sección auth-service).

---

### Tests de integración

#### user-service
- **Framework**: `@QuarkusTest` + `RestAssured`
- **PostgreSQL**: DevServices (Testcontainer automático de Quarkus)
- **Kafka**: `InMemoryConnectorLifecycleManager` implementa `QuarkusTestResourceLifecycleManager` para sustituir el conector Kafka por el conector en memoria de SmallRye
- **Tests**: registro de usuario (201 + Location), activación por token válido, activación con token inválido (400)

```java
@QuarkusTestResource(InMemoryConnectorLifecycleManager.class)
@QuarkusTest
class UserResourceTest { ... }
```

#### auth-service
- **Framework**: `@QuarkusTest` + `RestAssured`
- **PostgreSQL**: DevServices
- **gRPC mock**: `@InjectMock` sobre `UserServiceClient` para aislar auth-service de user-service
- **Tests**: login exitoso, credenciales inválidas (401), refresh inválido (401), logout (204)

```java
@InjectMock
UserServiceClient userServiceClient;
```

#### cat-service
- **Framework**: `@QuarkusTest` + `RestAssured`
- **PostgreSQL**: DevServices
- **JWT de prueba**: `@io.smallrye.jwt.build.Jwt` para generar tokens firmados con la clave de test
- **Tests**: búsqueda pública, 404 en gato inexistente, creación sin token (401), creación autenticada (201)
- **Pendiente**: tests de imagen (upload) con WireMock para el StorageClient

#### storage-service
- **Framework**: `@QuarkusTest` + `RestAssured`
- **MinIO**: `MinioTestResource` implementa `QuarkusTestResourceLifecycleManager`, levanta un contenedor MinIO real y crea el bucket automáticamente antes del test
- **Tests**: subida de JPG exitosa, rechazo de tipo de fichero inválido (400)
- **Nota**: durante el desarrollo de los tests se descubrió que `InvalidFileException` no tenía mapper → se añadió `GlobalExceptionMapper` y `ErrorResponse` al servicio

```java
public class MinioTestResource implements QuarkusTestResourceLifecycleManager {
    private static final GenericContainer<?> minio = new GenericContainer<>("minio/minio")
            .withEnv("MINIO_ROOT_USER", "kittigram")
            .withEnv("MINIO_ROOT_PASSWORD", "kittigram123")
            .withCommand("server /data")
            .withExposedPorts(9000);
    // crear bucket tras arrancar el contenedor
}
```

#### gateway-service
- **Framework**: `@QuarkusTest` + `RestAssured`
- **Servicios internos**: WireMock DevService de Quarkus simula todos los servicios internos (user, auth, cat, storage) en un único servidor stub
- **Tests**: routing de login a auth-service, JWT filter bloquea endpoints protegidos (401), ruta pública de cats (200), ruta desconocida (401)

#### notification-service
- **Framework**: `@QuarkusTest`
- **Kafka**: canal en memoria (`smallrye.messaging.connector.smallrye-in-memory`)
- **Email**: `MockMailbox` (inyectado vía `@Inject io.quarkus.mailer.MockMailbox`)
- **Asincronía**: `Awaitility` para esperar el procesamiento del evento antes de hacer las aserciones
- **Tests**: verifica subject del email, verifica que el enlace de activación contiene el token

```java
@Test
void whenUserRegistered_thenActivationEmailSent() {
    // publicar evento al canal en memoria
    InMemoryConnector.sink("user-registered").send(...);
    // esperar con Awaitility hasta recibir el email
    await().atMost(5, SECONDS).until(() -> mailbox.getTotalMessagesSent() == 1);
    // assertar subject y enlace
}
```

### Dependencias de test añadidas

| Servicio | Dependencias añadidas |
|---|---|
| user-service | `quarkus-test-vertx`, `smallrye-reactive-messaging-in-memory` |
| auth-service | `quarkus-junit5-mockito` |
| cat-service | `quarkus-test-security`, `quarkus-smallrye-jwt-build` |
| storage-service | `testcontainers` (MinIO container manual) |
| gateway-service | `quarkus-wiremock` |
| notification-service | `quarkus-mailer` (MockMailbox), `awaitility` |

---

## Pendiente / Deuda Técnica

### Funcionalidad pendiente
1. **Paginación** → listado de gatos necesita paginación
2. **Orden de imágenes** → `imageOrder` siempre es 0, falta endpoint para reordenar
3. **Actualizar profileImageUrl al borrar imagen de perfil** → pendiente seleccionar la siguiente
4. **Tareas programadas** → `@Scheduled` para limpieza de usuarios inactivos
5. **Mensajería asíncrona** → borrado de imágenes via mensajería (ahora es síncrono)
6. **`ban-service`** → sistema de baneo temporal/permanente con desbaneo automático via `@Scheduled`
7. **`docker-compose.yml` de producción** → con todos los servicios
8. **cat-service**: tests de imagen (upload/delete) con WireMock para StorageClient

### Deuda técnica
- `@JsonProperty` en todos los records de todos los servicios para deserialización correcta
- Validación de entrada (campos obligatorios, formatos)

### Servicios futuros planificados
```
user-service           ✅
auth-service           ✅
storage-service        ✅
cat-service            ✅
gateway-service        ✅
notification-service   ✅ (email activación + notificaciones adopción via Kafka, plantillas Qute)
adoption-service       ✅ (proceso adopción, formulario screening, entrevistas, contratos, gastos)
form-analysis-service  ✅ (análisis automático de formularios, reglas Critical/Warning/Notice)
ban-service            📋 (baneo temporal/permanente, desbaneo via @Scheduled)
```

---

## Patrones Recurrentes

### Patrón de sesión reactiva
```java
// Lectura en Service (Uni)
@WithSession
public Uni<T> findSomething() { ... }

// Escritura en Service
@WithTransaction
public Uni<T> saveSomething() { ... }

// Lectura en Repository para Multi en Service
// (sin @WithSession — la sesión la gestiona el Service llamante)
public Multi<T> findAll(Long id) {
    return find("...", id).list()
            .onItem().transformToMulti(list -> Multi.createFrom().iterable(list));
}

// En Service para devolver Multi
public Multi<T> search() {
    return repository.findAll() // Uni<List<T>>
            .onItem().transformToMulti(list -> Multi.createFrom().iterable(list))
            .onItem().transform(mapper::toDto);
}
```

### Patrón GlobalExceptionMapper
Cada servicio tiene su propio mapper con logging:
```java
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {
    @Override
    public Response toResponse(Throwable exception) {
        Log.errorf(exception, "Exception caught: %s", exception.getMessage());
        // mapear excepciones conocidas a códigos HTTP
    }
}
```

### Patrón ErrorResponse
```java
public record ErrorResponse(int status, String message, LocalDateTime timestamp) {
    public ErrorResponse(int status, String message) {
        this(status, message, LocalDateTime.now());
    }
}
```

---

## Comandos Útiles

```bash
# Compilar todo desde la raíz
mvn install -DskipTests

# Arrancar un servicio en dev
mvn quarkus:dev -pl user-service
mvn quarkus:dev -pl auth-service
mvn quarkus:dev -pl storage-service
mvn quarkus:dev -pl cat-service

# Levantar infraestructura
docker compose up -d

# Ver esquemas en PostgreSQL
docker exec -it kittigram-postgres-1 psql -U kittigram -d kittigram -c "\dn"

# Ver tablas de un esquema
docker exec -it kittigram-postgres-1 psql -U kittigram -d kittigram -c "\dt cats.*"
```

---

## Variables de Entorno

| Variable | Valor dev | Descripción |
|----------|-----------|-------------|
| DB_USER | kittigram | Usuario PostgreSQL |
| DB_PASSWORD | kittigram | Password PostgreSQL |
| DB_HOST | localhost | Host PostgreSQL |
| DB_PORT | 5432 | Puerto PostgreSQL |
| DB_NAME | kittigram | Base de datos |
| MINIO_ROOT_USER | kittigram | Usuario MinIO / Access Key S3 |
| MINIO_ROOT_PASSWORD | kittigram123 | Password MinIO / Secret Key S3 |
| MINIO_DEFAULT_BUCKETS | kittigram | Nombre del bucket |
| STORAGE_SERVICE_URL | http://localhost:8083 | URL del storage-service |
| USER_SERVICE_HOST | localhost | Host del user-service (para gRPC) |
| KAFKA_HOST | localhost | Host del broker Kafka |
| KAFKA_PORT | 9092 | Puerto del broker Kafka |
| MAIL_HOST | localhost | Host SMTP (MailHog en dev) |
| MAIL_PORT | 1025 | Puerto SMTP |
| MAIL_FROM | kittigram@ciscoadiz.org | Remitente de los emails |

---

## Auditorías de Seguridad

### Auditoría 2026-04-17 — Revisión estática completa

**Tipo:** Análisis estático de código (SAST) — revisión manual de todos los microservicios  
**Alcance:** auth-service, user-service, cat-service, adoption-service, gateway-service, notification-service  
**Informe completo:** `audit-report.pdf` en la raíz del repositorio  
**Puntuación:** 5,5 → **8,5 / 10**

#### Hallazgos

| ID | Severidad | Descripción | Rama | Estado |
|----|-----------|-------------|------|--------|
| C1 | 🔴 Crítica | IDOR en `GET /adoptions/{id}` — cualquier usuario autenticado podía leer solicitudes de otros | `fix/adoption-idor` | ✅ Corregido |
| H1 | 🟠 Alta | Refresh token registrado en logs en texto plano (`RefreshTokenRepository`) | `fix/high-severity-bundle` | ✅ Corregido |
| H2 | 🟠 Alta | Canal gRPC `auth-service` ↔ `user-service` sin autenticación | `fix/high-severity-bundle` | ✅ Corregido |
| H3 | 🟠 Alta | Kafka listener `EXTERNAL` escuchando en `0.0.0.0:9092` | `fix/high-severity-bundle` | ✅ Corregido |
| M1 | 🟡 Media | Sin Bean Validation en ningún DTO de entrada ni recurso JAX-RS | `fix/dto-input-validation` | ✅ Corregido |
| M2 | 🟡 Media | Sin rate limiting en `/auth/login`, `/auth/refresh`, `/storage/upload` | `fix/rate-limiting-gateway` | ✅ Corregido |
| M3 | 🟡 Media | RBAC sin implementar: claim `groups` vacío, sin `@RolesAllowed` | `fix/rbac-roles-allowed` | ✅ Corregido |
| M4 | 🟡 Media | Token de activación de cuenta expuesto en query param `GET /activate?token=` | `fix/activation-token-query-param` | ✅ Corregido |
| M5 | 🟡 Media | Gateway sin handler `PATCH` y path stripping incorrecto para `/api/adoptions` | `fix/gateway-patch-proxy` | ✅ Corregido |
| M6 | 🟡 Media | Credenciales de PostgreSQL y MinIO hardcodeadas en `docker-compose.yml` | `fix/default-credentials` | ✅ Corregido |
| M7 | 🟡 Media | Claves JWT (`privateKey.pem`, `publicKey.pem`) empaquetadas en el classpath del artefacto | `fix/jwt-keys-external-prod` | ✅ Corregido |

#### Correcciones aplicadas

- **C1:** Añadido helper `requireParticipant(adoption, callerId)` en `AdoptionService`; el endpoint ahora requiere que el caller sea adoptante u organización de esa solicitud.
- **H1:** Eliminada la línea `Log.infof("Looking for token: '%s'", token)` en `RefreshTokenRepository`.
- **H2:** Interceptores gRPC con shared-secret (`x-internal-token`): `GrpcAuthInterceptor` (server) en user-service y `GrpcClientAuthInterceptor` (client) en auth-service. Secreto configurable vía `GRPC_INTERNAL_SECRET`.
- **H3:** `KAFKA_LISTENERS: INTERNAL://0.0.0.0:29092,EXTERNAL://127.0.0.1:9092` — listener externo restringido a localhost.
- **M1:** `quarkus-hibernate-validator` añadido a 4 servicios. Todos los DTOs anotados con restricciones Jakarta Validation. `@Valid` en todos los parámetros de cuerpo de los recursos.
- **M2:** `quarkus-smallrye-fault-tolerance` en gateway. `RateLimitedProxy` con `@RateLimit`: login (10/min), refresh (20/min), upload (5/min). `RateLimitExceptionMapper` → HTTP 429.
- **M3:** Cadena completa de roles: `UserGrpcService` → proto → `AuthService` → `RefreshToken.role` → `JwtTokenService.groups` → `@RolesAllowed` en `AdoptionResource`.
- **M4:** `GET /users/activate?token=` cambiado a `POST /users/activate` con body `{"token":"..."}`. URL del email apunta al frontend (`FRONTEND_URL`). Endpoint añadido a rutas públicas del gateway.
- **M5:** Handler `@PATCH` añadido a `GatewayResource`. Path stripping corregido a `path.replaceFirst("^/api", "")`.
- **M6:** Credenciales reemplazadas por `${POSTGRES_USER}`, `${POSTGRES_PASSWORD}`, `${MINIO_ROOT_USER}`, etc. Creado `.env.example`. Añadido `.env` a `.gitignore`.
- **M7:** Perfil `%prod` en los 5 servicios con JWT: lee claves desde `/run/secrets/` (Docker/K8s Secrets). Dev y test sin cambios. Variables `JWT_PRIVATE_KEY_LOCATION` y `JWT_PUBLIC_KEY_LOCATION`.

#### Recomendaciones pendientes (no implementadas)

1. **Rate limiting distribuido con Redis** — el actual es por instancia JVM, no funciona en despliegues multi-réplica
2. **Caducidad de tokens de activación** — añadir `activationTokenExpiresAt` en `User`, actualmente son indefinidos
3. **Log de auditoría** — registrar login, cambios de estado de adopción, desactivaciones
4. **HTTPS entre microservicios** en producción
5. **JWKS endpoint** para rotación de claves JWT sin interrumpir sesiones activas
6. **Imágenes Docker con usuario no root** — `quarkus.jib.user=1001`
7. **OWASP Dependency Check + Trivy** en el pipeline de CI
8. **Política de complejidad de contraseñas** — actualmente solo `@Size(min=8)`

#### Tests tras las correcciones
```
42 tests unitarios — BUILD SUCCESS (auth-service, user-service, cat-service, adoption-service)
```

---

## Historial de Sesiones

### Sesión 2026-04-28/29 — Reestructuración adopción + chat-service

**Contexto**: aplicar la decisión de negocio del 2026-04-27 (solo las protectoras publican gatos; los usuarios entran por un flujo de "ingreso" previo). Plan acordado en 3 fases (1 cat-lockdown, 2a/b/c intake en adoption-service, 3 chat-service nuevo).

**Bloque 1 — Fase 1 (`feat/cat-publishing-org-only`, mergeada)**
- `CatResource`: `POST /cats` y mutaciones restringidas a `@RolesAllowed("Organization")`. `ownerOrgId` viene del JWT, no del body.
- Sin migración de datos legados (decisión: BD aún no en prod, se destruye y recrea).

**Bloque 2 — Fase 2a (`feat/adoption-intake-flow`, mergeada)**
- Nuevo agregado `IntakeRequest` (paquete `adoption-service/intake/`, deliberadamente separado del agregado `AdoptionRequest`). Estados `Pending`/`Approved`/`Rejected`.
- Endpoints: `POST /intake-requests` (User), `GET /intake-requests/mine` (User), `GET /intake-requests/organization` (Organization, id desde JWT — alineado con el patrón de `AdoptionResource`, no con el path literal del plan), `PATCH /intake-requests/{id}/approve|reject`.
- Migración Flyway V2 con `intake_requests`. `GlobalExceptionMapper` extendido con `IntakeRequestNotFoundException` (404) y `InvalidIntakeStatusException` (409).
- Tests: 10 unit (Mockito) + 10 integración (`@QuarkusTest` + `@TestSecurity`).

**Bloque 3 — Fase 2b (`feat/intake-org-lookup`, mergeada)**
- `organization-service`: clonado el patrón `@InternalOnly` + `InternalTokenFilter` de `cat-service`. Nuevo `OrganizationInternalResource` con `GET /organizations/internal/by-region/{region}` que devuelve un DTO público mínimo (`OrganizationPublicMinimalResponse`).
- `gateway-service`: bloqueo explícito de `^/api/[^/]+/internal(/.*)?$` con 404 antes de auth (defensa en profundidad — afecta también a `/api/cats/internal/*`).
- `adoption-service`: `quarkus-rest-client-jackson` añadido. `OrganizationClient` (`@RegisterRestClient`) listo para consumir el endpoint con header `X-Internal-Token`. Properties `kitties.internal.secret` y `quarkus.rest-client.organization-service.url`.
- Tests: 4 nuevos en organization-service, 3 en gateway-service. El cliente sin consumidor no se testea aquí (se cubre en 2c).

**Bloque 4 — Fase 2c (`feat/intake-rejection-alternatives`, mergeada)**
- `IntakeRequestService.reject(...)` ahora devuelve `IntakeRejectionResponse(intake, alternatives)`. Tras persistir el rechazo llama a `OrganizationClient.findByRegion(region, secret)`, filtra la org que rechazó y devuelve la lista.
- **Tolerancia a fallos**: si el cliente HTTP falla (`organization-service` caído), se devuelve la rejection con `alternatives = []` y un `Log.warnf`. La rejection no debe revertirse por un fallo de lookup.
- Tests: 2 nuevos en service (alternativas filtradas + tolerancia a fallo). El happy path end-to-end del resource necesita 2 contextos de seguridad distintos (User para POST, Organization para PATCH) en el mismo test → no factible con `@TestSecurity`; cobertura suficiente vía service test mockeando el cliente.

**Bloque 5 — Fase 3 bases (`feat/chat-service`, PR pendiente)**
- Módulo Maven nuevo en puerto **8089**, esquema `chat`. Añadido al pom raíz y al routing del gateway (`/api/chats/*` → chat-service).
- Dominio: `Conversation` (1 por intake aprobada, UNIQUE `intake_request_id`) + `Message` con `SenderType` (User/Organization).
- REST público (JWT, role-aware): `GET /chats/mine` (User), `GET /chats/organization` (Organization), `GET /chats/{id}/messages`, `POST /chats/{id}/messages`. Check de participación y `lastMessageAt` bumping en send.
- REST interno: `POST /chats/internal/conversations` (`@InternalOnly`) — bases listas para que `adoption-service.IntakeRequestService.approve(...)` abra la conversación; el cableado cross-service queda como deuda.
- **Ban en chat (opción A de moderación)**: tabla `chat.blocked_participants(organization_id, user_id)`, scope = pareja (no por conversación). `POST /chats/{id}/block` y `DELETE /chats/{id}/block` (Organization, idempotentes). `sendMessage` desde User → 403 si la pareja está bloqueada; lectura sigue abierta. Las opciones B (ban global en user-service) y C (`moderation-service` propio) descartadas por ahora — quedan en deuda para cuando aparezca un caso real.
- Tests: 33 verdes (17 service + 12 resource + 4 internal).

**Decisiones recurrentes a lo largo del plan**:
- Cada fase en rama propia y PR independiente. Ninguna fase posterior se empezó sin que la anterior estuviese mergeada a `main`.
- Path de endpoints alineado con patrones existentes (e.g. `/intake-requests/organization` en vez del literal `/organizations/{id}/intake-requests` del plan) cuando el patrón vigente era más limpio.
- Tolerancia a fallos en lookups cross-service (no romper la operación principal por un fallo de un servicio satélite).

**Deuda registrada**:
- WebSocket en chat-service (`feat/chat-websocket` futuro): hoy solo REST.
- Auto-creación de `Conversation` desde `IntakeRequestService.approve(...)`: `OrganizationClient` patrón replicable, falta `ChatClient` + invocación.
- Ban global de usuario (`UserStatus.Banned`): pendiente del primer caso real.

**Estado al cierre**: fases 1, 2a, 2b, 2c mergeadas a `main`. Fase 3 (`feat/chat-service`) lista para PR. Sin tests rotos en ningún módulo afectado.

---

### Sesión 2026-04-23/24 — Seguridad + Flyway

**Contexto**: dos ramas paralelas de trabajo: controles de seguridad en `security` y migraciones de base de datos en `feat/flyway-migrations`.

**Bloque 1 — Seguridad (rama `security`, mergeada a `main`)**
- `StorageService`: validación de magic bytes en upload. Rechaza ficheros cuyo contenido no coincide con el `Content-Type` declarado (JPEG/PNG spoofing). Tipos soportados: `image/jpeg` (`FF D8 FF`) y `image/png` (`89 50 4E 47`).
- `GatewayResource`: cabecera `X-Content-Type-Options: nosniff` inyectada en todas las respuestas del gateway via `ContainerResponseFilter`.
- Tests: `StorageServiceTest` ampliado (9 tests); `GatewayResourceTest` añade aserción de la cabecera (25 tests totales en gateway).
- E2E: `SecurityE2E` (nuevo) — 2 tests: rechazo de upload con bytes inválidos (400) y verificación de `nosniff` en respuesta del gateway.

**Bloque 2 — Flyway (rama `feat/flyway-migrations`)**
- Patrón aplicado en los 6 servicios con BD PostgreSQL:
  - `quarkus-flyway` + `quarkus-jdbc-postgresql` en `pom.xml` (solo JDBC para Flyway; el cliente reactivo se mantiene).
  - `application.properties`: Flyway desactivado globalmente, activado solo en `%prod` con `migrate-at-start=true` y schema propio. Hibernate en `validate` en prod.
  - Fichero `V1__init_<schema>.sql` en `src/main/resources/db/migration/`.
- Estado por servicio:
  - `auth-service`: ya tenía V1 migration de sesión anterior; configuración completada.
  - `user-service`: ya tenía V1 migration (referencia del patrón).
  - `cat-service`: deps ya añadidas en commit previo; añadidos config + V1 migration (`cats`, `cat_images`).
  - `adoption-service`: deps + config + V1 migration (`adoption_requests`, `adoption_forms`, `expenses`, `interviews`).
  - `organization-service`: deps + config + V1 migration (`organizations`, `organization_members` con UNIQUE en `(organization_id, user_id)`).
  - `form-analysis-service`: deps + config + V1 migration (`form_analyses`, `form_flags`).
- Todos los módulos compilan limpio tras los cambios.

**Problemas encontrados**:
- Ninguno relevante. Patrón bien definido desde `user-service`; aplicación mecánica a los demás servicios.

**Estado al cierre**: 143 tests (77 unit + 66 integration). Flyway completamente desplegado en todos los servicios con BD. Dos nuevos controles de seguridad implementados y cubiertos con tests.

---

### Sesión 2026-04-18 (Bloque 2 — organization-service)

**Bloque 1 — Nuevo organization-service (puerto 8088)**
- Nuevo microservicio para gestión de protectoras. Multi-usuario desde el primer día con límite de miembros por plan: `Free(1)`, `Basic(5)`, `Pro(-1 = ilimitado)`.
- Entidades: `Organization` + `OrganizationMember`. Enums en PascalCase: `OrganizationStatus`, `OrganizationPlan`, `MemberRole`, `MemberStatus`.
- `@PrePersist` en `Organization` asigna `status=Active`, `plan=Free`, `maxMembers=plan.maxMembers` si no se especifican.
- `OrganizationMemberRepository`: usa `.firstResult().onItem().transform(Optional::ofNullable)` — `firstResultOptional()` no existe en Hibernate Reactive Panache.

**Bloque 2 — API y reglas de negocio**
- 8 endpoints: crear organización, `GET /organizations/mine`, perfil por id, actualizar, listar miembros, invitar, cambiar rol, eliminar miembro.
- `POST /organizations`: solo roles `Organization` o `Admin`. El creador se añade automáticamente como miembro `Admin`.
- Invitar miembro verifica `count >= maxMembers` (se omite si `maxMembers == -1`).
- `requireAdmin` privado en `OrganizationService`: helper centralizado para verificar que el caller es ADMIN de la org.

**Bloque 3 — Migración cat-service: `userId` → `organizationId`**
- `Cat.java`: `userId` renombrado a `organizationId` (`@Column(name="organization_id")`).
- `CatMapper`: `toEntity` ya no recibe `Long userId`; `organizationId` viene del request body.
- `CatService`/`CatResource`: endpoints mutables reciben `organizationId` como query param; ownership check compara `cat.organizationId == requestedOrganizationId`.
- `CatRepository.findByUserId` → `findByOrganizationId`.

**Bloque 4 — Gateway wiring**
- `ProxyService`: añadida rama `if (path.startsWith("/api/organizations")) return organizationServiceUrl`.
- `application.properties`: `quarkus.rest-client.organization-service.url` + `%test.*` apuntando a WireMock.

**Problemas encontrados**:
- `firstResultOptional()` no existe en Hibernate Reactive Panache → sustituido por `.firstResult().onItem().transform(Optional::ofNullable)`.
- `@TestSecurity` aplica a todo el método de test: no se puede mezclar una request autenticada y una sin autenticar en el mismo método. Solución: separar en dos tests (`testNonMemberCannotUpdateOrganization` y `testUpdateOrganizationUnauthorized`).
- `init-test.sql` (Testcontainers init script) se ejecuta antes de que Hibernate cree las tablas → INSERTs fallaban. Solución: `init-test.sql` solo crea el esquema; datos de prueba en `import-test.sql` gestionado por `quarkus.hibernate-orm.sql-load-script` (se ejecuta después del DDL).

**Estado al cierre**: 25 tests en organization-service (14 unit + 11 integración), 138 tests totales — BUILD SUCCESS.

---

### Sesión 2026-04-18

**Bloque 1 — Cobertura JaCoCo en todos los módulos**
- `quarkus-jacoco` añadido como dependencia de test en el POM raíz: todos los módulos la heredan.
- `jacoco-maven-plugin` configurado en el POM raíz con `exclClassLoaders=*QuarkusClassLoader` (evita doble instrumentación de clases cargadas por Quarkus) y `append=true` para fusionar `.exec` de tests unitarios y de integración.
- `dataFile` apunta a `jacoco-quarkus.exec` en todos los módulos.

**Bloque 2 — Fix test roto en gateway-service**
- `testAdoptionsPathStrippedCorrectly` devolvía 401 en lugar de 200. Dos causas:
  1. `quarkus.http.auth.proactive=true` (defecto Quarkus): valida el token Bearer antes de llegar a JAX-RS; `test-token` no es un JWT válido → 401 inmediato. Corregido con `%test.quarkus.http.auth.proactive=false`.
  2. URL de `adoption-service` no mapeada a WireMock en perfil test → error de conexión a `localhost:8086`. Corregido añadiendo `%test.quarkus.rest-client.adoption-service.url`.

**Bloque 3 — Cobertura gateway-service: 65% → 100% instrucciones**
- `gateway-service/pom.xml`: añadidas dependencias `quarkus-junit5-mockito` y `mockito-junit-jupiter` para unit tests con Mockito.
- `application.properties`: límites de rate limit reducidos en perfil test (`%test.rate-limit.auth.login=2`, etc.) para disparar 429 con solo 3 peticiones.
- `GatewayResourceTest`: ampliado de 6 a 22 tests de integración con WireMock. Nuevos tests cubren: `refresh`, `logout`, POST/PUT/PATCH/DELETE genéricos, `storage/upload`, 429 por rate limit (login, refresh, upload), ramas de `clientIp()` (X-Forwarded-For, en blanco, sin header), body JSON inválido, ruta desconocida con Bearer → 404, `Authorization: Basic` → 401.
- `IpRateLimiterTest` (nuevo): unit test puro que cubre el `while` de limpieza de timestamps expirados en `IpRateLimiter`.
- `GatewayResourceUnitTest` (nuevo): unit test con Mockito que cubre la rama `return "unknown"` de `clientIp()` (inaccesible vía HTTP porque `RoutingContext` siempre tiene `remoteAddress`).

**Problemas encontrados en esta sesión**:
- Tests contaminados por `IpRateLimiter`: el limiter comparte una sola `Deque<Long>` por clave IP para todos los endpoints (login, refresh, upload). Varios tests usando `127.0.0.1` agotaban el bucket entre sí. Solución: `X-Forwarded-For` único por test con IPs estáticas generadas a partir de `System.currentTimeMillis()`.
- `testLoginRateLimitExceeded` fallaba al parsear JSON del 429: `RateLimitExceptionMapper` serializa `Map.of(...)` pero sin configuración Jackson explícita puede producir `{key=value}` en lugar de JSON. Solución: aserción con `containsString` sobre el body raw en vez de JSON path.
- `testMalformedAuthHeaderReturnsUnauthorized` usaba `GET /api/cats/1` que coincide con `PUBLIC_PATTERNS` → el filtro JWT lo dejaba pasar. Cambiado a `GET /api/adoptions/99` (ruta protegida).
- JaCoCo advierte "Unsupported class file major version 69" para proxies de Mockito generados con Java 21. Es una limitación conocida de JaCoCo 0.8.12, no afecta a la cobertura del código de producción.

**Cobertura final gateway-service**: 574/574 instrucciones (100%), 58/62 ramas (93.5%). Las 4 ramas no alcanzadas son genuinamente inalcanzables vía HTTP: `r.body() == null` en `ProxyService` (Vert.x siempre devuelve `Buffer`) y `rc == null` / `rc.remoteAddress() == null` en `clientIp()`.

**Estado al cierre**: 113 tests — BUILD SUCCESS en todos los módulos.

---

### Sesión 2026-04-16

**Bloque 1 — adoption-service completado**
- Tests unitarios (20) e integración (9) implementados: 29 en total, BUILD SUCCESS.
- `AdoptionService` cubierto con Mockito: flujo completo de creación, envío de formulario, análisis externo, entrevista, contrato.
- Tests de integración con `@QuarkusTest`: PostgreSQL DevServices, Kafka InMemoryConnector, JWT de test.
- `GlobalExceptionMapper` actualizado para capturar `jakarta.ws.rs.NotFoundException` (ver Problema #12).
- Rutas de `adoption-service` (8086) añadidas al `gateway-service`.

**Bloque 2 — form-analysis-service**
- Nuevo microservicio creado desde cero con su propio esquema BD (`form_analysis`).
- Entidades: `FormAnalysis`, `FormFlag`. Enums: `AnalysisDecision` (ACCEPTED/REJECTED), `FlagSeverity` (CRITICAL/WARNING/NOTICE).
- `FormAnalysisRules`: motor de reglas con tres categorías de severidad para evaluar el formulario de screening.
- `FormAnalysisService`: consumer de `adoption-form-submitted`, aplica reglas, persiste resultados, publica `adoption-form-analysed`.
- `FormAnalysisPersistenceService`: persistencia extraída en bean separado con `@WithTransaction` para compatibilidad con `@Incoming` (ver Problema #10 y #11).
- 11 tests: 8 unitarios (reglas + service mockeando persistencia) + 3 integración (flujo end-to-end con InMemoryConnector).

**Bloque 3 — notification-service refactorizado**
- Integración de plantillas Qute (`quarkus-qute`): el email de activación se renderiza con una plantilla HTML en lugar de construir el HTML en Java.
- Nuevo `AdoptionFormAnalysedConsumer`: consume `adoption-form-analysed` y envía email de resultado (aceptado/rechazado) con su plantilla Qute correspondiente. 3 plantillas HTML en total.
- Tests unitarios actualizados para mockear `Template` y `TemplateInstance` con cadena `data()` (ver Problema #14).

**Bloque 4 — Propagación de adopterEmail por el flujo de adopción**
- `adopterEmail` añadido a `AdoptionRequest` (entidad), `AdoptionRequestResponse` (DTO) y `AdoptionMapper`. El email se extrae del JWT en `AdoptionResource` con `jwt.getClaim("email")` evitando llamadas gRPC a `user-service` (ver Problema #16).
- `AdoptionFormSubmittedEvent` y `AdoptionFormAnalysedEvent` actualizados con `adopterEmail` para que `notification-service` sepa a quién enviar el email de resultado (ver Problema #17).
- `form-analysis-service` propagado el campo a través de su propio flujo de eventos.
- `notification-service`: `AdoptionFormAnalysedConsumer` usa `adopterEmail` del evento como destinatario del email.
- Tests de `AdoptionServiceTest` y `AdoptionResourceTest` actualizados para la nueva firma (ver Problema #18).
- `existsActiveByCatId` en `AdoptionRequestRepository` anotado con `@WithSession` para corregir fallo en integración (ver Problema #20).
- `FormAnalysisRulesTest` reescrito para corregir duplicación accidental de `adopterEmail` (ver Problema #21).

**Problemas encontrados en esta sesión**:
- `init.sql` no se re-ejecuta si el volumen Docker ya existe → `docker compose down -v` (Problema #9)
- `@Incoming` + `@WithTransaction` incompatibles → `Panache.withTransaction()` programático (Problema #10)
- `Panache.withTransaction()` falla en tests con InMemoryConnector → bean de persistencia separado (Problema #11)
- `GlobalExceptionMapper` no capturaba `jakarta.ws.rs.NotFoundException` (Problema #12)
- Conflicto aparente de rutas `/adoptions/my` vs `/adoptions/{id}` → JAX-RS lo resuelve correctamente (Problema #13)
- Template Qute no inyectable con `@InjectMocks` → mocks explícitos de `Template` y `TemplateInstance` (Problema #14)
- `UserRegisteredEventDeserializer` no encontrado → `StringDeserializer` + `ObjectMapper` manual (Problema #15)
- `adopterEmail` obtenido del JWT en lugar de gRPC → evita complejidad innecesaria (Problema #16)
- Eventos Kafka sin `adopterEmail` → propagado por todo el flujo hasta `notification-service` (Problema #17)
- Tests rotos por nuevo parámetro `adopterEmail` en firma → actualización de constructores (Problema #18)
- `AdoptionRequestResponse` sin `adopterEmail` → añadido al record y al mapper (Problema #19)
- `existsActiveByCatId` sin sesión activa en tests de integración → `@WithSession` en repositorio (Problema #20)
- `FormAnalysisRulesTest` con `adopterEmail` duplicado por find & replace → reescritura manual (Problema #21)
- Chain `data().render()` de Qute no mockeable con `@InjectMocks` → mocks explícitos con `.render()` (Problema #22)

**Estado al cierre**: 8 servicios implementados. 93 tests — BUILD SUCCESS en todos los módulos.

---

### Sesión 2026-04-15

**Bloque 1 — Roles de usuario**
- `user-service`: añadido `UserRole` enum (`User`, `Organization`, `Admin`). Los nuevos usuarios se crean con rol `User` por defecto. El rol se refleja en `UserCreateRequest`, `UserResponse` y `UserMapper`. Tests unitarios actualizados.

**Bloque 2 — adoption-service (bootstrap y dominio)**
- Bootstrap del módulo: `adoption-service` añadido al POM raíz. Esquema `adoption` añadido a `init.sql`.
- Entidades de dominio: `AdoptionRequest`, `Expense`, `AdoptionStatus`, `ExpenseRecipient`.
- Configuración PostgreSQL, JWT y Jib. Dockerfiles multi-stage para JVM y native.
- `notification-service`: corregido deserializador a `StringDeserializer` para eventos `user-registered`.

**Bloque 3 — adoption-service (capa de dominio completa)**
- Ciclo de vida de estados extendido: añadidos `rejectionReason`, `Reviewing`, `AwaitingPayment`.
- Entidades `AdoptionRequestForm` (formulario screening, 40+ campos) con enums `ActivityLevel` y `HousingType`.
- Entidades `Interview` y `AdoptionForm` (contrato legal).
- Repositories para todas las entidades. DTOs completos (create/response) para cada recurso.
- Mapper `AdoptionMapper`.
- Custom exceptions: `AdoptionRequestNotFoundException`, `CatNotAvailableException`, `InvalidAdoptionStatusException`, `AdoptionFormAlreadySubmittedException`. `GlobalExceptionMapper` y `ErrorResponse`.

**Bloque 4 — adoption-service (capa de servicio y Kafka)**
- `AdoptionService` completo con toda la lógica de negocio:
  - `createAdoptionRequest`: evita solicitudes duplicadas para el mismo gato con `existsActiveByCatId()`
  - `submitRequestForm`: valida estado Pending, persiste formulario, cambia estado a Reviewing, publica `adoption-form-submitted` a Kafka
  - `scheduleInterview`: valida estado Accepted, agenda entrevista
  - `submitAdoptionForm`: valida estado Accepted, evita duplicados, cambia estado a FormCompleted
  - `onFormAnalysed` (`@Incoming`): consume decisión externa (ACCEPTED/REJECTED) y actualiza estado
- Eventos Kafka: `AdoptionFormSubmittedEvent` (outgoing, 40+ campos) y `AdoptionFormAnalysedEvent` (incoming).
- `AdoptionRequestFormRepository` extraído para el servicio.

**Estado al cierre**: 7 servicios implementados. adoption-service sin tests por ahora.

---

### Sesión 2026-04-14

**Bloque 1 — Storage y gateway**
- `storage-service`: nuevo endpoint `GET /storage/files/{key}` que sirve objetos desde S3/MinIO con su `Content-Type` original. `S3StorageProvider.getUrl()` ahora devuelve la URL pública a través del gateway, desacoplando URLs de imagen del almacenamiento interno.
- `gateway-service`: rutas públicas para ficheros (`GET /api/storage/files/*`), logging de requests, CORS configurado para Vite (`localhost:5173`) con credentials y `max-age=86400`.

**Bloque 2 — Verificación de email**
- `user-service`: estado `Pending` añadido a `UserStatus`. Los nuevos usuarios se crean con `activationToken` UUID. Nuevo endpoint público `GET /users/activate?token=`.
- `notification-service`: consume el evento `user-registered` de Kafka y envía email HTML de activación via `ReactiveMailer` + MailHog en dev.
- `docker-compose`: MailHog añadido (SMTP 1025, UI 8025). Kafka con dos listeners separados: `INTERNAL` (kafka:29092, para contenedores Docker) y `EXTERNAL` (localhost:9092, para servicios del host).

**Bloque 3 — Tests de integración** (todos los servicios)
- Estrategia: `@QuarkusTest` + RestAssured. PostgreSQL vía DevServices, Kafka in-memory, MinIO Testcontainer real, WireMock para gateway, `@InjectMock` para gRPC, MockMailbox + Awaitility para notificaciones.
- `storage-service`: durante los tests se descubrió que faltaba `GlobalExceptionMapper` → añadido.
- Total inicial: ~20 tests de integración.

**Bloque 4 — Tests unitarios y refactor**
- `auth-service`: `JwtTokenService` extraído de `AuthService` para hacer la firma JWT inyectable y mockeable. `AuthServiceTest` con Mockito cubre 7 escenarios.
- `user-service`: `Email` y `ActivationToken` introducidos como Value Objects en `domain/`. `UserServiceTest` con Mockito cubre 8 escenarios.
- Configuración de tests consolidada: perfiles `%test.*` en el `application.properties` principal, sin fichero separado.

**Bloque 5 — Decisiones de arquitectura**
- Arquitectura hexagonal implícita: no se explicita con packages `domain/application/infrastructure`.
- Value Objects: clases finales con constructor privado y `of()` como factory (no records).
- Repository como puerto (DIP): Services dependerán de interfaces, no de `PanacheRepository` directamente.
- GitKraken AI configurado (250k tokens/semana, Gemini Flash) con Conventional Commits y Commit Composer.

**Estado al cierre**: 53 tests en total (20 integración + 33 unitarios) distribuidos en 6 servicios.
---

### Sesión 2026-04-18

**Contexto**: corrección de la suite e2e de `storage-service` (6 tests, todos fallaban).

**Bloque 1 — Rate limiter contaminando ejecuciones e2e**
- El test `upload_rateLimitExceeded_returns429` (Order 6) llenaba el bucket de IP dentro de la ventana de 60 s. La siguiente ejecución encontraba el bucket lleno y fallaba con 429 desde el primer upload.
- Fix: añadir `X-Forwarded-For: TEST_IP` (único por ejecución, generado con `System.currentTimeMillis()`) a todos los requests de upload en `StorageE2E`. El gateway lee `X-Forwarded-For` con prioridad sobre la IP del socket (ver Problema #23).

**Bloque 2 — 500 en upload válido: credenciales MinIO incorrectas**
- Con el rate limiter corregido, el test 1 (upload JPEG) devolvía 500. Los tests 2 (serve) y 5 (delete) dependían del test 1 y también fallaban. El test 4 (tipo inválido → 400) pasaba porque la validación de tipo ocurre antes de llamar a S3.
- Causa raíz: doble problema encadenado:
  1. **MinIO no crea el bucket automáticamente** con `MINIO_DEFAULT_BUCKETS` en la imagen oficial — el bucket `kittigram` no existía (ver Problema #24).
  2. **Credenciales incorrectas**: el proceso Quarkus de `storage-service` corre con `cwd = storage-service/` y no carga el `.env` raíz. Usaba el default `kittigram123` pero MinIO esperaba `change_me_min16chars` (ver Problema #25).
- Fixes aplicados:
  - Symlink `storage-service/.env → ../.env` para que Quarkus cargue las credenciales reales.
  - Default de `MINIO_ROOT_PASSWORD` actualizado en `application.properties` a `change_me_min16chars`.
  - `BucketInitializer` (`@Observes StartupEvent`) que crea el bucket si no existe, usando `S3AsyncClient.get()` bloqueante.
- El bucket existente en MinIO se creó manualmente con `docker exec kittigram-minio-1 mc mb local/kittigram`. Quarkus recargó `application.properties` automáticamente en dev mode (hot reload).

**Bloque 3 — Actualización de documentación**
- `CLAUDE.md`: tres gotchas nuevos (rate limit e2e, MinIO sin auto-bucket, Quarkus dev cwd).
- `BITACORA.md` y `README.md`: problemas #23–25 documentados, sección storage-service y guía de setup actualizadas.
- Memorias de Claude Code actualizadas con los aprendizajes de infraestructura.

**Estado al cierre**: 6/6 tests de `StorageE2E` verdes. Stack completo operativo.

---

### Sesión 2026-04-17

**Contexto**: primera ejecución real del stack completo + suite e2e.

**Bloque 1 — Infraestructura**
- PostgreSQL: contraseña del contenedor desincronizada con el valor por defecto de la app (`kittigram`). Corregido con `ALTER USER` y `.env` actualizado.
- Kafka: listener `EXTERNAL` estaba vinculado a `127.0.0.1` dentro del contenedor. Docker reenvía tráfico a la IP del contenedor, no a su loopback → todos los servicios recibían disconnects en bootstrap. Cambiado a `0.0.0.0:9092`.
- `dev.sh`: faltaba `form-analysis-service` en `KNOWN_SERVICES`. Sin él, el topic `adoption-form-analysed` nunca recibía mensajes y `notification-service` no enviaba emails de adopción.

**Bloque 2 — Bugs de producción (detectados por e2e)**

*auth-service*
- Token rotation no implementada: `AuthService.refresh()` generaba nuevos tokens pero nunca revocaba el antiguo. El token viejo permanecía válido indefinidamente. Fix: `token.revoked = true` + `persist` antes de `generateTokens`.

*gateway-service*
- `JwtAuthFilter` tenía lista de rutas públicas hardcodeada sin `POST:/api/auth/logout` → logout devolvía 401 sin Bearer token.
- `ProxyService` lanzaba NPE al recibir respuesta sin body (ej. 204 No Content de logout): `r.body().getBytes()` sin guardar nulo.
- Rate limiter (`@RateLimit` de SmallRye) era global por instancia JVM, no por cliente. Reemplazado por `IpRateLimiter` con ventana deslizante por email (login) o IP (refresh, upload). Los límites pasan a leerse de `application.properties` vía `@ConfigProperty`.

**Bloque 3 — Fixes de tests e2e**
- `MailHogClient`: emails HTML llegan en Quoted-Printable. El regex de extracción de token fallaba por soft line breaks (`=\n`) y `=3D`. Fix: strip + decode antes de aplicar el patrón.
- `AdoptionJourneyE2E`: `Map.of` no acepta valores `null` → eliminadas las entradas opcionales nulas del formulario de screening.
- `ActivationRequest` record sin `@JsonProperty` → Jackson no deserializaba el campo `token` correctamente.

**Bloque 4 — Tooling**
- `CLAUDE.md` reescrito como referencia autocontenida (stack, puertos, patrones, gotchas, comportamiento esperado). Eliminada dependencia de `~/.agents/technologies/kittigram.md` que no existía.

**Estado al cierre**: 33/33 tests e2e verdes. 6 commits atómicos mergeados a `main`.
