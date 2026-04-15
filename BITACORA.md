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
├── init.sql                 ← CREATE SCHEMA users, auth, cats
├── BITACORA.md
├── user-service/
├── auth-service/
├── storage-service/
├── cat-service/
├── gateway-service/
└── notification-service/
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
- `GET /users/activate?token=xxx` → 200 (**público**, activa cuenta con token UUID)
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
quarkus.s3.aws.credentials.static-provider.secret-access-key=${MINIO_ROOT_PASSWORD:kittigram123}
bucket.name=${MINIO_DEFAULT_BUCKETS:kittigram}
quarkus.s3.async-client.type=netty
```

**Notas importantes**:
- El bucket MinIO debe estar en modo **público** o usar URLs prefirmadas
- Para producción: Cloudflare R2 (sin egress), compatible con API S3
- Para dev: MinIO en Docker
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

### notification-service
**Puerto**: 8085
**Sin BD** (solo envía emails)

**Dependencias**:
- `quarkus-rest-jackson`
- `quarkus-messaging-kafka` (consumidor Kafka)
- `quarkus-mailer`
- `quarkus-container-image-jib`

**Estructura**:
```
consumer/UserRegisteredConsumer.java  ← @Incoming("user-registered")
event/UserRegisteredEvent.java        ← mismo record que user-service
```

**Flujo**:
1. `user-service` publica `UserRegisteredEvent` en topic `user-registered`
2. `notification-service` consume el mensaje vía `@Incoming("user-registered")`
3. Deserializa con `ObjectMapper` (llega como `String`)
4. Envía email HTML de activación via `ReactiveMailer`

**Configuración Kafka**:
```properties
kafka.bootstrap.servers=${KAFKA_HOST:localhost}:${KAFKA_PORT:9092}
mp.messaging.incoming.user-registered.connector=smallrye-kafka
mp.messaging.incoming.user-registered.topic=user-registered
mp.messaging.incoming.user-registered.value.deserializer=org.apache.kafka.common.serialization.StringDeserializer
mp.messaging.incoming.user-registered.group.id=notification-service
```

**Email de activación**:
- Subject: "Activa tu cuenta en Kittigram 🐱"
- Contiene enlace: `http://localhost:8080/api/users/activate?token={activationToken}`
- MailHog en dev (puerto 1025 SMTP, 8025 web UI)

**Notas importantes**:
- El mensaje Kafka llega como `String` (no como el tipo directamente) → se deserializa manualmente con `ObjectMapper`
- El `notification-service` tiene su propia copia del record `UserRegisteredEvent` (sin dependencias entre módulos Maven)

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

El proyecto tiene control de versiones Git con historial atómico que refleja el orden de desarrollo:

```
feat(gateway-service): reverse proxy implementation
feat(gateway-service): Vert.x WebClient config
feat(gateway-service): JWT auth filter + routing config
chore(gateway-service): scaffold
docs: add BITACORA.md with full project context
feat(security): JWT authentication
feat(cat-service): application layer
feat(cat-service): domain layer
feat(storage-service): S3/MinIO file storage
feat(auth-service): application layer
feat(auth-service): domain layer
feat(user-service): gRPC server
feat(user-service): application layer
feat(user-service): domain layer
chore: project scaffold
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
| storage-service | 2 | 6 | **8** |
| gateway-service | 4 | — | **4** |
| notification-service | 2 | 3 | **5** |
| **Total** | **20** | **33** | **53** |

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
7. **`adoption-service`** → proceso de adopción, historial, reportes
8. **`docker-compose.yml` de producción** → con todos los servicios
9. **cat-service**: tests de imagen (upload/delete) con WireMock para StorageClient

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
notification-service   ✅ (email activación via Kafka)
ban-service            📋 (baneo temporal/permanente, desbaneo via @Scheduled)
adoption-service       📋 (proceso adopción, historial, reportes)
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

## Historial de Sesiones

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