# Guía de incorporación — Kitties Backend

Esta guía cubre los conceptos y patrones que debes entender antes de tocar código de producción. No es una lista de reglas a memorizar — es una explicación del *porqué* detrás de cada decisión. Un error en esta stack puede funcionar perfectamente en local y explotar bajo carga real sin dejar rastro obvio.

Tiempo estimado de lectura: 45 minutos.

---

## Índice

1. [El model de concurrencia: Vert.x y el event loop](#1-el-modelo-de-concurrencia-vertx-y-el-event-loop)
2. [Mutiny: Uni y Multi](#2-mutiny-uni-y-multi)
3. [Hibernate Reactive: sesiones y transacciones](#3-hibernate-reactive-sesiones-y-transacciones)
4. [Autenticación interna: @InternalOnly](#4-autenticación-interna-internalonly)
5. [Gotchas explicados](#5-gotchas-explicados)
6. [Checklist antes de tu primer PR](#6-checklist-antes-de-tu-primer-pr)

---

## 1. El modelo de concurrencia: Vert.x y el event loop

### El problema que resuelve

En un servidor tradicional (Spring MVC), cada request HTTP ocupa un thread del pool mientras espera una respuesta de la base de datos, de otro servicio, o de cualquier operación de I/O. Si tienes 200 requests concurrentes esperando, necesitas 200 threads activos.

Los threads son caros: cada uno consume ~1 MB de stack, y el sistema operativo paga un coste en cada cambio de contexto entre ellos. En la práctica, un servidor con este modelo se queda sin threads antes de quedarse sin CPU o memoria.

```
Modelo bloqueante — 3 requests concurrentes:

thread-1: ──── request-A ──── [espera BD: 50ms] ──── response-A ────
thread-2: ──── request-B ──── [espera BD: 50ms] ──── response-B ────
thread-3: ──── request-C ──── [espera BD: 50ms] ──── response-C ────

Los 3 threads están bloqueados durante 50ms. No pueden hacer nada más.
```

### La solución: event loop

Quarkus corre sobre **Vert.x**, que usa un número reducido de threads llamados **event loop threads** (por defecto: número de cores × 2). En lugar de bloquear un thread esperando, la operación registra un callback y devuelve el thread al pool inmediatamente. Cuando llega la respuesta, el event loop retoma el trabajo.

```
Modelo reactivo — 3 requests con 1 event loop thread:

event-loop: ─ A_inicio ─ B_inicio ─ C_inicio ─ A_callback ─ B_callback ─ C_callback ─

Las 3 requests se procesan con 1 solo thread. El thread nunca espera.
```

Con 4-8 threads de event loop, el servidor puede gestionar miles de requests concurrentes.

### La regla crítica: nunca bloquear el event loop

Si bloqueas el event loop thread (con una llamada síncrona, un `Thread.sleep`, un `await().indefinitely()`), no solo paras esa request — paras **todas las requests** que ese thread estaba gestionando.

```java
// ❌ MAL — bloquea el event loop
@GET
@Path("/cats")
public List<Cat> getCats() {
    return catRepository.listAll().await().indefinitely(); // bloquea el thread
}

// ✅ BIEN — devuelve el control al event loop inmediatamente
@GET
@Path("/cats")
public Uni<List<Cat>> getCats() {
    return catRepository.listAll(); // el thread queda libre hasta que llegue el resultado
}
```

Quarkus detecta operaciones bloqueantes en el event loop y lanza una advertencia:
```
You have attempted to perform a blocking operation on the IO thread.
```
Si ves este warning, es un bug — no lo ignores.

---

## 2. Mutiny: Uni y Multi

Mutiny es la librería reactiva de Quarkus. Reemplaza `CompletableFuture`, callbacks anidados y código bloqueante con una API legible.

### Uni\<T\> — exactamente un resultado

Representa una operación que producirá **un valor** (o un error) en el futuro. Es el equivalente reactivo de un método que devuelve `T`.

```java
Uni<Cat> cat = catRepository.findById(1L);
// El Uni no hace nada hasta que alguien se suscribe.
// Quarkus se suscribe automáticamente cuando el resource devuelve el Uni.
```

Encadenar operaciones:

```java
Uni<CatResponse> response = catRepository.findById(id)           // Uni<Cat>
        .onItem().ifNull().failWith(() -> new NotFoundException())
        .onItem().transform(cat -> catMapper.toResponse(cat));    // Uni<CatResponse>
```

### Multi\<T\> — cero o más resultados

Representa una operación que producirá **varios valores** a lo largo del tiempo. Útil para streams, exports o datasets grandes.

```java
Multi<Cat> cats = catRepository.streamAll(); // emite un Cat cada vez
```

### Cuándo usar cada uno

| Situación | Tipo |
|---|---|
| Buscar por ID, crear, actualizar, borrar | `Uni<T>` |
| Endpoint REST que devuelve lista | `Uni<Page<T>>` (con paginación) |
| Export, job interno, stream real | `Multi<T>` |
| Lista pequeña y acotada por diseño | `Uni<List<T>>` es aceptable |

> **Por qué no `Uni<List<T>>` para endpoints públicos:** una lista sin paginar carga todos los registros en memoria de una vez. Con 50 registros de prueba funciona. Con 50.000 en producción, agota la memoria del servidor. Añade siempre `page` y `size` como parámetros antes de devolver colecciones.

### Operadores más usados

```java
// Transformar el valor
uni.onItem().transform(value -> ...)          // síncrono
uni.onItem().transformToUni(value -> ...)     // cuando la transformación devuelve otro Uni

// Encadenar sin usar el valor anterior
uni.chain(() -> otroUni)

// Manejar errores
uni.onFailure().recoverWithItem(fallback)
uni.onFailure().invoke(e -> Log.error("...", e))

// Convertir lista en Multi
uni.onItem().transformToMulti(list -> Multi.createFrom().iterable(list))
```

### Lo que Uni NO hace hasta que alguien se suscribe

Un `Uni` es una **descripción** de una operación, no la operación en sí. No ejecuta nada hasta que hay un suscriptor. En el contexto de Quarkus REST, el framework se suscribe automáticamente cuando el resource devuelve el `Uni`. En tests o código no-REST, necesitas suscribirte explícitamente.

```java
Uni<Cat> uni = catRepository.findById(1L);
// Hasta aquí, no se ha ejecutado ninguna query.

uni.subscribe().with(
    cat -> System.out.println(cat),
    error -> System.err.println(error)
);
// Ahora sí se ejecuta.
```

---

## 3. Hibernate Reactive: sesiones y transacciones

### Por qué Hibernate Reactive y no JDBC

JDBC es bloqueante. Una query con JDBC bloquea el thread hasta que llega la respuesta de la base de datos. En un event loop, eso bloquea todas las requests en curso. Hibernate Reactive usa el **reactive PostgreSQL client** para hacer las queries sin bloquear ningún thread.

### @WithSession y @WithTransaction

Hibernate Reactive necesita una **sesión** abierta para cualquier operación con la base de datos. La sesión es la conexión lógica con la BD dentro de la que viven las entidades gestionadas.

```java
@WithSession          // abre una sesión para esta operación (lecturas)
public Uni<Cat> findById(Long id) { ... }

@WithTransaction      // abre sesión + transacción (escrituras)
public Uni<Cat> save(Cat cat) { ... }
```

**Regla práctica:**
- Lecturas → `@WithSession` en el Service
- Escrituras → `@WithTransaction` en el Service
- Dentro de `Panache.withTransaction(() -> ...)`, no añadas `@WithSession` ni `@WithTransaction` en los métodos que llama — ya tienen sesión activa

### El gotcha de @WithSession con Multi

`@WithSession` **no funciona** en métodos que devuelven `Multi<T>`. La sesión se cierra antes de que el Multi emita todos sus elementos.

```java
// ❌ MAL — la sesión se cierra antes de que Multi emita
@WithSession
public Multi<Cat> findAll() {
    return list("status", Active).onItem()
            .transformToMulti(list -> Multi.createFrom().iterable(list));
}

// ✅ BIEN — @WithSession en el repositorio, transformación en el service
// Repository:
@WithSession
public Uni<List<Cat>> findAllActive() {
    return list("status", Active);
}

// Service:
public Multi<Cat> streamAllActive() {
    return repository.findAllActive()
            .onItem().transformToMulti(list -> Multi.createFrom().iterable(list));
}
```

### @Incoming (Kafka) + @WithTransaction = fallo

Un consumer Kafka anotado directamente con `@WithTransaction` falla porque Mutiny no puede propagar el contexto de transacción a través del canal de mensajería.

```java
// ❌ MAL
@Incoming("adoption-form-submitted")
@WithTransaction
public Uni<Void> consume(String message) { ... }

// ✅ BIEN — delegar la persistencia a un bean separado
@Incoming("adoption-form-submitted")
public Uni<Void> consume(String message) {
    return persistenceService.save(message); // el bean tiene @WithTransaction
}
```

### PanacheEntity vs PanacheRepository

Este proyecto usa el patrón **Repository**, no Active Record:

```java
// ❌ No hacer esto (Active Record — prohibido en este proyecto)
cat.persist();

// ✅ Correcto (Repository pattern)
catRepository.persist(cat);
```

Las entidades extienden `PanacheEntity` que ya provee `public Long id`. **Nunca declarar `@Id` manualmente.**

Los campos de las entidades son `public` (Panache los intercepta a nivel de bytecode):

```java
@Entity
@Table(name = "cats", schema = "cat")
public class Cat extends PanacheEntity {
    public String name;          // public — Panache intercepta el acceso
    public CatStatus status;
    public LocalDateTime createdAt;
}
```

---

## 4. Autenticación interna: @InternalOnly

### El problema

Algunos endpoints deben ser accesibles únicamente por otros servicios del sistema — nunca por usuarios finales ni por el gateway. Por ejemplo, `schedule-service` dispara jobs de retención de datos llamando a `adoption-service`, o `user-service` llama a `chat-service` durante el borrado de un usuario.

Si estos endpoints fueran accesibles públicamente, cualquiera podría borrar datos o disparar operaciones privilegiadas con una simple petición HTTP.

### Cómo funciona

El mecanismo es un `@NameBinding` de JAX-RS: una anotación que enlaza un `ContainerRequestFilter` exclusivamente a los recursos o métodos marcados con ella. El filtro valida el header `X-Internal-Token` contra el secreto compartido `kitties.internal.secret`.

```
petición entrante a /adoptions/internal/retention/run
        │
        ▼
InternalTokenFilter.filter()     ← solo se ejecuta en endpoints @InternalOnly
  lee header X-Internal-Token
  compara con kitties.internal.secret
  ✗ → 401 Unauthorized (la petición muere aquí)
  ✓ → continúa al resource
        │
        ▼
AdoptionInternalResource.runRetention()
```

### El secreto compartido

`kitties.internal.secret` tiene el mismo valor en todos los servicios:
- **Desarrollo:** `kitties-dev-secret` (por defecto, no requiere configuración)
- **Producción:** inyectado vía variable de entorno `KITTIES_INTERNAL_SECRET`

### Usar @InternalOnly en un resource

```java
@Path("/adoptions/internal")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@InternalOnly          // toda la clase queda protegida
public class AdoptionInternalResource {

    @POST
    @Path("/retention/run")
    public Uni<Response> runRetention() { ... }
}
```

### Llamar a un endpoint interno desde otro servicio

```java
@RegisterRestClient(configKey = "adoption-service")
@Path("/adoptions/internal")
public interface AdoptionInternalClient {

    @POST
    @Path("/retention/run")
    Uni<Response> triggerRetention(@HeaderParam("X-Internal-Token") String token);
}

// En el servicio llamante:
@ConfigProperty(name = "kitties.internal.secret")
String internalSecret;

adoptionInternalClient.triggerRetention(internalSecret);
```

### La regla más importante

**Los endpoints `@InternalOnly` nunca se exponen por el gateway.** El gateway (puerto 8080) no debe hacer proxy de rutas `/*/internal/*`. Estos endpoints son accesibles únicamente desde la red privada de contenedores.

Si añades un endpoint interno nuevo, verifica que no está en la lista de rutas del gateway.

> Para la guía completa de cómo añadir el patrón a un nuevo servicio, ver [CLAUDE.md — Autenticación interna](CLAUDE.md#autenticación-interna-servicio-a-servicio).

---

## 5. Gotchas explicados

Estos son los errores más comunes en este stack. No los memorices — entiende el porqué.

### Records Java necesitan @JsonProperty

Los Records de Java no exponen los nombres de los parámetros del constructor en tiempo de ejecución por defecto. Jackson no sabe cómo deserializarlos.

```java
// ❌ Jackson no puede deserializar esto
public record CreateCatRequest(String name, String breed) {}

// ✅ Opción A — @JsonProperty en cada campo
public record CreateCatRequest(
        @JsonProperty("name") String name,
        @JsonProperty("breed") String breed
) {}

// ✅ Opción B — registrar ParameterNamesModule en JacksonConfig (ya configurado en el proyecto)
```

### "order" es palabra reservada en HQL

HQL (el dialecto de Hibernate) reserva la palabra `order` para `ORDER BY`. Si tienes un campo llamado `order` en una entidad, las queries fallarán con un error de parseo críptico.

```java
// ❌ Rompe las queries HQL
public int order;

// ✅ Nombre alternativo en Java, nombre real en la columna
@Column(name = "image_order")
public int imageOrder;
```

### Los .proto se copian en cada servicio

No existe un módulo Maven compartido para los ficheros `.proto`. Copia el `.proto` en `src/main/proto/` de cada servicio que lo necesite. La regla de arquitectura prohíbe dependencias Maven entre módulos para evitar acoplamiento en compilación.

### El borrado de usuarios es siempre lógico, nunca físico

`UserStatus` cambia a `Inactive`. La fila nunca se elimina directamente. El borrado físico (anonimización RGPD) lo gestiona el job de erasure de `schedule-service` con un período de gracia de 30 días y registro de auditoría inmutable.

### ProxyService explota con NPE en respuestas sin body

Si un servicio upstream devuelve 204 (sin body) y el `ProxyService` intenta leer el body sin comprobarlo, lanza `NullPointerException`.

```java
// ✅ Siempre comprobar antes de leer el body
if (r.body() != null) {
    // procesar body
}
```

### JwtAuthFilter requiere registro explícito de rutas públicas

El filtro JWT tiene una lista explícita de rutas que no requieren token (`PUBLIC_EXACT`). Si añades un endpoint nuevo que debe ser público (sin JWT), añádelo a esa lista. Si no lo haces, el endpoint devolverá 401 aunque no tenga `@RolesAllowed`.

### Rate limiter: usar X-Forwarded-For en tests e2e

El rate limiter usa la IP como clave para la mayoría de endpoints. Los tests e2e que golpean endpoints con rate limit deben enviar un header `X-Forwarded-For` único por ejecución para evitar que los tests se contaminen entre sí dentro de la ventana de 60 segundos.

```java
// En tests e2e
String testIp = "test-" + System.currentTimeMillis();
given().header("X-Forwarded-For", testIp).when().post("/api/auth/login")...
```

### Quarkus dev usa el directorio del módulo como cwd

Cuando arrancas un servicio con `mvn quarkus:dev -pl <módulo>`, el working directory es el directorio del módulo, no la raíz del proyecto. El fichero `.env` de la raíz no se carga automáticamente.

```bash
# Crear symlink una vez tras clonar (ya existe en storage-service)
ln -sf ../.env <módulo>/.env
```

### Kafka EXTERNAL listener debe escuchar en 0.0.0.0

Docker no redirige al loopback del contenedor. Si el listener externo de Kafka escucha en `127.0.0.1`, los servicios en otros contenedores no pueden conectar.

---

## 6. Checklist antes de tu primer PR

Antes de abrir un pull request, verifica estos puntos:

**Reactividad**
- [ ] Ningún método del resource, service o repository devuelve un tipo bloqueante (`List<T>`, `Optional<T>`, `void`)
- [ ] No hay ningún `.await().indefinitely()` ni `Thread.sleep` en código de producción
- [ ] Los métodos que devuelven `Multi<T>` no tienen `@WithSession` (el gotcha de sesión)
- [ ] Los consumers `@Incoming` de Kafka delegan la persistencia a un bean separado

**Base de datos**
- [ ] Las escrituras tienen `@WithTransaction`, las lecturas tienen `@WithSession`
- [ ] Dentro de `Panache.withTransaction(() -> ...)` no se añaden anotaciones de sesión redundantes
- [ ] Los endpoints que devuelven colecciones tienen paginación (`page`, `size`)

**Seguridad**
- [ ] Los endpoints internos están anotados con `@InternalOnly`
- [ ] Ningún endpoint `@InternalOnly` está registrado en las rutas del gateway
- [ ] Los endpoints nuevos que deben ser públicos están en la lista `PUBLIC_EXACT` del `JwtAuthFilter`

**Código**
- [ ] Los DTOs son Records Java con `@JsonProperty` o con `ParameterNamesModule` configurado
- [ ] Las entidades no declaran `@Id` (lo provee `PanacheEntity`)
- [ ] Los campos de entidad son `public`
- [ ] Los valores de enums están en PascalCase (`Pending`, no `PENDING`)
- [ ] Ningún nombre de campo de entidad es una palabra reservada HQL (`order`, `group`, `select`...)

**Arquitectura**
- [ ] No hay dependencias Maven entre módulos
- [ ] No hay relaciones JPA entre entidades de distintos servicios
- [ ] La comunicación entre servicios es solo vía HTTP interno (`@InternalOnly`), gRPC o Kafka

**Commits**
- [ ] Un commit por capa o servicio (no un commit gigante con todo)
- [ ] El commit está en la rama correcta (no en `main` ni en la rama de otro)

---

*Para cualquier duda sobre patrones del proyecto, consulta [CLAUDE.md](CLAUDE.md). Para la arquitectura general y los servicios, consulta [README.md](README.md).*