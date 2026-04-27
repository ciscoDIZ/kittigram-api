# Kitties — Documentación de Negocio y API para Frontend

**Versión:** 1.0.0  
**Fecha:** 2026-04-25  
**Estado:** API en desarrollo con Swagger UI consolidado en `http://localhost:8080/swagger-ui`

---

## Tabla de Contenidos

1. [Visión General del Negocio](#visión-general-del-negocio)
2. [Roles y Permisos](#roles-y-permisos)
3. [Autenticación JWT](#autenticación-jwt)
4. [Flujos Principales](#flujos-principales)
5. [Referencia de Endpoints](#referencia-de-endpoints)
6. [Estados y Transiciones](#estados-y-transiciones)
7. [Motor de Análisis Automático](#motor-de-análisis-automático)
8. [Eventos Asíncronos (Kafka)](#eventos-asíncronos-kafka)
9. [Configuración y Entorno](#configuración-y-entorno)

---

## Visión General del Negocio

**Kitties** es una plataforma de adopción de gatos que conecta a:

- **Usuarios (Adoptantes):** Individuos que buscan adoptar gatos
- **Organizaciones (Refugios):** Entidades que publican gatos para adopción y gestiona el proceso
- **Sistema:** Facilita búsqueda, filtrado y un flujo de adopción automatizado con análisis inteligente

### Problemática que Resuelve

- Refugios necesitan una plataforma para gestionar gatos y solicitudes de adopción
- Adoptantes necesitan encontrar gatos disponibles cerca de ellos
- Ambos necesitan garantías: el adoptante completa formularios de evaluación, el refugio revisa automáticamente con IA

### Características Principales

1. **Catálogo de gatos público:** búsqueda por ciudad, nombre, edad
2. **Proceso de adopción multietapa:** solicitud → cuestionario → análisis automático → decisión → firma legal
3. **Análisis automático de candidatos:** 14 reglas de negocio detectan factores de riesgo
4. **Gestión de organizaciones:** membresía, roles, límites por plan
5. **Autenticación JWT:** sesiones cortas (15 min), renovación vía refresh token (7 días)

---

## Roles y Permisos

| Rol | Descripción | Permisos |
|-----|-------------|----------|
| **User** | Adoptante individual | • Registro y activación<br>• Ver catálogo público<br>• Crear solicitud adopción<br>• Rellenar cuestionarios y forms<br>• Ver mis solicitudes de adopción |
| **Organization** | Refugio / organización | • Crear y gestionar organización<br>• Invitar miembros (si plan lo permite)<br>• Publicar y editar gatos<br>• Subir imágenes de gatos<br>• Ver y procesar solicitudes de adopción<br>• Agendar entrevistas<br>• Aceptar/rechazar solicitudes |
| **Admin** | Administrador del sistema | • Todos los permisos de Organization<br>• Gestionar usuarios globalmente<br>• Auditoría y reportes |
| **Anónimo** | No autenticado | • Ver catálogo de gatos<br>• Ver detalle de gatos<br>• Registrarse |

### Límites por Plan de Organización

| Plan | Max Miembros | Descripción |
|------|--------------|-------------|
| **Free** | 1 | Una sola persona (founder) |
| **Basic** | 5 | Pequeño refugio |
| **Pro** | ∞ | Organización grande sin límites |

---

## Autenticación JWT

### Flujo General

```
User → POST /auth/login {email, password}
       ↓
       200 OK {accessToken, refreshToken, expiresIn=900}
       ↓
User almacena tokens
       ↓
Para cada request: Header "Authorization: Bearer {accessToken}"
```

### Características del Token

- **Issuer:** `https://www.kitti.es`
- **Duración AccessToken:** 900 segundos (15 min)
- **Duración RefreshToken:** 7 días
- **Algoritmo:** RS256 (RSA public/private key)
- **Claims:**
  - `sub`: User ID (Long)
  - `email`: User email (String)
  - `groups`: ["User"|"Organization"|"Admin"] — array con rol(es)

### Renovación y Revocación

```
POST /auth/refresh {refreshToken}
     → 200 OK {accessToken, refreshToken, expiresIn=900}
       (el token anterior se revoca automáticamente)

POST /auth/logout {refreshToken}
     → 204 No Content
       (revoca el refresh token, invalida todas las sesiones)
```

### Headers Requeridos

```
Authorization: Bearer {accessToken}
Content-Type: application/json (en requests con body)
```

---

## Flujos Principales

### Flujo A: Registro y Activación (User)

**Objetivo:** Un usuario se registra, recibe email de activación, y confirma su cuenta.

**Pasos:**

1. **POST `/users`** (sin auth)
   ```json
   {
     "email": "juan@ejemplo.com",
     "password": "MySecure123!",
     "name": "Juan",
     "surname": "García",
     "birthdate": "1990-05-15"
   }
   ```
   → 201 Created, status = `Pending` (no acceso todavía)

2. **Sistema envía email** con token de activación a juan@ejemplo.com
   ```
   Sujeto: Activa tu cuenta en Kitties
   Cuerpo: http://localhost:5173/activate?token={activationToken}
   ```

3. **Frontend redirige a página de activación** y llama:
   ```
   POST /users/activate
   {
     "token": "{activationToken}"
   }
   ```
   → 200 OK, status = `Active`

4. **Usuario ahora puede hacer login** y obtener JWT

---

### Flujo B: Login y Gestión de Sesión

**Objetivo:** Autenticar usuario y mantener sesión activa.

**Pasos:**

1. **POST `/auth/login`** (sin auth)
   ```json
   {
     "email": "juan@ejemplo.com",
     "password": "MySecure123!"
   }
   ```
   Respuesta:
   ```json
   {
     "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
     "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
     "expiresIn": 900
   }
   ```

2. **Frontend almacena ambos tokens** (localStorage o sessionStorage)

3. **Para cada request:** incluir header `Authorization: Bearer {accessToken}`

4. **Cuando accessToken expira (900s):** llamar
   ```
   POST /auth/refresh
   {
     "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
   }
   ```
   → 200 OK con nuevos tokens

5. **Al cerrar sesión:**
   ```
   POST /auth/logout
   {
     "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
   }
   ```
   → 204 No Content (refresh token revocado)

---

### Flujo C: Crear Organización (Organization Role)

**Objetivo:** Usuario con rol `Organization` crea un refugio y gestiona miembros.

**Pasos:**

1. **POST `/organizations`** (con token, rol=Organization)
   ```json
   {
     "name": "Refugio Gatuno Madrid",
     "description": "Somos un refugio de gatos en Madrid...",
     "address": "Calle Principal 123",
     "city": "Madrid",
     "region": "Comunidad de Madrid",
     "country": "España",
     "phone": "+34 912 345 678",
     "email": "info@refugio-gatuno.es"
   }
   ```
   → 201 Created
   ```json
   {
     "id": 42,
     "name": "Refugio Gatuno Madrid",
     "status": "Active",
     "plan": "Free",
     "maxMembers": 1,
     ...
   }
   ```
   El creador es automáticamente miembro con role=`Admin`

2. **Invitar miembros** (solo si plan lo permite):
   ```
   POST /organizations/42/members
   {
     "userId": 99,
     "role": "Staff"
   }
   ```
   → 201 Created (miembro invitado, status=Invited)

3. **Cambiar rol de miembro:**
   ```
   PATCH /organizations/42/members/99/role
   {
     "role": "Admin"
   }
   ```
   → 200 OK

4. **Remover miembro:**
   ```
   DELETE /organizations/42/members/99
   ```
   → 204 No Content (soft delete, status=Removed)

---

### Flujo D: Publicar Gato con Imágenes

**Objetivo:** Organización publica un gato disponible para adopción.

**Pasos:**

1. **POST `/cats`** (con token, rol=Organization)
   ```json
   {
     "name": "Misu",
     "age": 3,
     "sex": "Female",
     "description": "Gata tranquila y cariñosa, muy sociable",
     "neutered": true,
     "city": "Madrid",
     "region": "Comunidad de Madrid",
     "country": "España",
     "latitude": 40.4168,
     "longitude": -3.7038
   }
   ```
   → 201 Created (status = `Available`)
   ```json
   {
     "id": 1,
     "organizationId": 42,
     "status": "Available",
     "images": [],
     ...
   }
   ```

2. **Subir imagen (multipart/form-data):**
   ```
   POST /cats/1/images
   Header: Authorization: Bearer {token}
   Body: form-data
     - file: [binary image file, ≤5MB, JPEG or PNG]
   ```
   → 200 OK
   ```json
   {
     "id": 1,
     "images": [
       {
         "id": 1001,
         "url": "http://localhost:8080/api/storage/files/uuid-1.jpg",
         "order": 0
       }
     ]
   }
   ```

3. **Subir segunda imagen** (misma operación)

4. **Ver gato en catálogo público:**
   ```
   GET /cats/1
   ```
   → 200 OK (sin auth necesario)

---

### Flujo E: Proceso de Adopción Completo (8 Pasos)

**Objetivo:** Usuario solicita adoptar un gato, completa evaluación, sistema analiza, organización decide, firma legal.

```
PASO 1: Usuario ve gato público
  GET /cats?city=Madrid
  ↓ selecciona Misu (id=1)

PASO 2: Usuario crea solicitud de adopción
  POST /adoptions
  {
    "catId": 1,
    "organizationId": 42
  }
  ← 201 Created, status = Pending

PASO 3: Sistema verifica que no hay solicitud activa para ese gato
  (si hay otra en Pending/Reviewing/Accepted → 409 Conflict)

PASO 4: Usuario rellena cuestionario pre-adopción (32 campos)
  POST /adoptions/{adoptionId}/form
  {
    "hasPreviousCatExperience": true,
    "adultsInHousehold": 2,
    "hasChildren": false,
    "hasOtherPets": false,
    "hoursAlonePerDay": 6,
    "stableHousing": true,
    "housingType": "Apartment",
    "housingSize": 80,
    "hasOutdoorAccess": false,
    "isRental": false,
    "hasWindowsWithView": true,
    "hasVerticalSpace": true,
    "hasHidingSpots": true,
    "householdActivityLevel": "Moderate",
    "whyCatsNeedToPlay": "Para mantenerse en forma y estimular mentalmente",
    "dailyPlayMinutes": 30,
    "plannedEnrichment": "Torres de escalada, juguetes interactivos",
    "reactionToUnwantedBehavior": "Paciencia, redireccionamiento, nunca castigo físico",
    "hasScratchingPost": true,
    "willingToEnrichEnvironment": true,
    "motivationToAdopt": "Quiero brindar hogar a un gato necesitado",
    "understandsLongTermCommitment": true,
    "hasVetBudget": true,
    "allHouseholdMembersAgree": true,
    "anyoneHasAllergies": false
  }
  ← 201 Created
  ← Status automáticamente pasa a: Reviewing

PASO 5: Sistema analiza automáticamente el formulario
  - Motor de reglas evalúa 14 flags (Critical/Warning/Notice)
  - Calcula decisión: Approved / ReviewRequired / Rejected
  - Si rechazado: organización no debe gastar tiempo en entrevista

PASO 6: Organización decide (rechazar o aceptar)
  - Si rechazado automáticamente: email al usuario + status=Rejected
  - Si aprobado o con review: organización recibe notificación
  
  Organización acepta:
  PATCH /adoptions/{id}/status
  {
    "status": "Accepted"
  }
  ← 200 OK, ahora usuario puede agenda entrevista

PASO 7: Organización agenda entrevista
  POST /adoptions/{id}/interview
  {
    "scheduledAt": "2026-05-10T14:00:00Z",
    "notes": "Traer documentos de identidad"
  }
  ← 201 Created

PASO 8: Usuario firma formulario legal de adopción
  POST /adoptions/{id}/adoption-form
  {
    "fullName": "Juan García García",
    "idNumber": "12345678A",
    "phone": "+34 612 345 678",
    "address": "Calle Principal 123, Apt 4B",
    "city": "Madrid",
    "postalCode": "28001",
    "acceptsVetVisits": true,
    "acceptsFollowUpContact": true,
    "acceptsReturnIfNeeded": true,
    "acceptsTermsAndConditions": true,
    "additionalNotes": "Tenemos veterinario de confianza"
  }
  ← 201 Created, status = FormCompleted
  ← Contrato firmado digitalmente
```

---

### Flujo F: Motor de Análisis Automático

**Objetivo:** El sistema evalúa automáticamente cada cuestionario y acepta/rechaza candidatos.

**Reglas de Negocio (14 flags):**

#### CRITICAL (1 flag → Rechazo automático)

| Flag | Condición |
|------|-----------|
| `PHYSICAL_PUNISHMENT` | Campo `reactionToUnwantedBehavior` contiene: pegar, golpe, castigo físico, bofetada, palo, hit, smack, beat |
| `ABANDONMENT_HISTORY` | Campo `previousPetsHistory` contiene: abandone, tire, solte, deje en la calle |
| `RENTAL_NO_PERMISSION` | `isRental=true` AND `rentalPetsAllowed` != true |
| `ALLERGY_CONFIRMED` | `anyoneHasAllergies=true` |

#### WARNING (3+ flags → Rechazo; 1-2 → ReviewRequired)

| Flag | Condición |
|------|-----------|
| `INSUFFICIENT_PLAY_TIME` | `dailyPlayMinutes < 15` |
| `TOO_MANY_HOURS_ALONE` | `hoursAlonePerDay > 10` AND `hasOtherPets=false` |
| `NO_ENRICHMENT_SPACE` | `hasVerticalSpace=false` AND `hasHidingSpots=false` |
| `YOUNG_CHILDREN_NO_EXPERIENCE` | `hasChildren=true` AND `hasPreviousCatExperience!=true` AND `childrenAges` contiene 0-3 |
| `UNSTABLE_HOUSING` | `stableHousing=false` |
| `SUPERFICIAL_MOTIVATION` | `motivationToAdopt` contiene: es bonito, es mono, de regalo, para los niños, me parece gracioso, capricho |

#### NOTICE (Informativos, no afectan decisión)

| Flag | Condición |
|------|-----------|
| `NO_WINDOW_VIEW` | `hasWindowsWithView=false` |
| `SMALL_HOUSING` | `housingSize < 40` |
| `NO_PREVIOUS_EXPERIENCE` | `hasPreviousCatExperience!=true` |
| `NO_SCRATCHING_POST` | `hasScratchingPost!=true` |

**Lógica de Decisión:**

```
if (criticalFlags >= 1) → REJECTED
else if (warningFlags >= 3) → REJECTED
else if (warningFlags >= 1) → REVIEW_REQUIRED
else → APPROVED
```

**Resultado:** Email automático al usuario (rechazado, requiere revisión, aprobado)

---

## Referencia de Endpoints

Acceder a `http://localhost:8080/swagger-ui` para documentación interactiva.

### User Service (Puerto 8081)

| Método | Ruta | Auth | Descripción |
|--------|------|------|-------------|
| POST | `/users` | ✗ | Registro de nuevo usuario |
| POST | `/users/activate` | ✗ | Activar con token de email |
| GET | `/users/{email}` | ✓ | Ver mi perfil (self) |
| GET | `/users/active` | ✓ | Listar todos los usuarios activos |
| PUT | `/users/{email}` | ✓ | Actualizar perfil |
| PUT | `/users/{email}/deactivate` | ✓ | Desactivar cuenta (self) |
| PUT | `/users/{email}/activate` | ✓ | Activar usuario (admin/self) |

### Auth Service (Puerto 8082)

| Método | Ruta | Auth | Descripción |
|--------|------|------|-------------|
| POST | `/auth/login` | ✗ | Obtener access + refresh token |
| POST | `/auth/refresh` | ✗ | Renovar access token |
| POST | `/auth/logout` | ✗ | Revocar refresh token |

### Cat Service (Puerto 8084)

| Método | Ruta | Auth | Descripción |
|--------|------|------|-------------|
| GET | `/cats` | ✗ | Buscar gatos (`?city=Madrid&name=Misu`) |
| GET | `/cats/{id}` | ✗ | Detalle de gato |
| POST | `/cats` | ✓ | Crear gato (Organization) |
| PUT | `/cats/{id}` | ✓ | Editar gato (owner) |
| DELETE | `/cats/{id}` | ✓ | Borrar gato (owner) |
| POST | `/cats/{id}/images` | ✓ | Subir imagen (multipart) |
| DELETE | `/cats/{catId}/images/{imageId}` | ✓ | Borrar imagen |

### Adoption Service (Puerto 8086)

| Método | Ruta | Auth | Role | Descripción |
|--------|------|------|------|-------------|
| POST | `/adoptions` | ✓ | User | Crear solicitud |
| GET | `/adoptions/my` | ✓ | User | Mis solicitudes |
| GET | `/adoptions/organization` | ✓ | Org | Solicitudes para mi org |
| GET | `/adoptions/{id}` | ✓ | — | Detalle (participantes) |
| PATCH | `/adoptions/{id}/status` | ✓ | Org | Cambiar status |
| POST | `/adoptions/{id}/form` | ✓ | User | Enviar cuestionario pre-adopción |
| POST | `/adoptions/{id}/interview` | ✓ | Org | Agendar entrevista |
| POST | `/adoptions/{id}/adoption-form` | ✓ | User | Firmar formulario legal |

### Organization Service (Puerto 8088)

| Método | Ruta | Auth | Role | Descripción |
|--------|------|------|------|-------------|
| POST | `/organizations` | ✓ | Org/Admin | Crear organización |
| GET | `/organizations/mine` | ✓ | — | Mi organización |
| GET | `/organizations/{id}` | ✓ | — | Detalle (solo miembros) |
| PUT | `/organizations/{id}` | ✓ | Org Admin | Editar |
| GET | `/organizations/{id}/members` | ✓ | Org Admin | Listar miembros |
| POST | `/organizations/{id}/members` | ✓ | Org Admin | Invitar miembro |
| PATCH | `/organizations/{id}/members/{userId}/role` | ✓ | Org Admin | Cambiar rol |
| DELETE | `/organizations/{id}/members/{userId}` | ✓ | Org Admin | Remover miembro |

### Storage Service (Puerto 8083)

| Método | Ruta | Auth | Descripción |
|--------|------|------|-------------|
| POST | `/storage/upload` | ✓ | Subir archivo (multipart, ≤5MB, JPEG/PNG) |
| DELETE | `/storage/{key}` | — | Borrar archivo |
| GET | `/storage/files/{key}` | ✗ | Descargar/servir archivo |

---

## Estados y Transiciones

### User Status Diagram

```
[Pending] ← user creado, email no confirmado
    ↓
[Active] ← token de activación confirmado (puede hacer login)
    ↓
[Inactive] ← usuario desactiva su cuenta (soft delete)
    ↓
[Banned] ← admin banea usuario
```

### Adoption Request Status Diagram

```
[Pending] ← solicitud creada
    ↓ (usuario rellena cuestionario)
[Reviewing] ← análisis automático en curso / completado
    ↓ (sistema decide)
    ├─→ [Rejected] ← flags críticos o 3+ warnings
    │    └─→ email a usuario con razón
    │
    └─→ [Accepted] ← aprobado o requiere revisión (org decide)
         ↓ (usuario rellena formulario legal)
         [FormCompleted] ← contrato firmado
         ↓ (flujo de pago / trámites administrativos)
         [PaymentPending] → [PaymentFailed] o [Completed]
```

---

## Motor de Análisis Automático

El `form-analysis-service` escucha en Kafka cuando se envía un formulario pre-adopción y ejecuta las 14 reglas automáticamente. Resultado se publica de vuelta y:

- El adoption-service cambia status automáticamente
- El notification-service envía email al adoptante
- La organización ve el resultado en su dashboard

**Inputs al motor:** Todos los 32 campos del `AdoptionRequestForm`  
**Outputs:** `decision` (Approved/ReviewRequired/Rejected), lista de flags, razón si rechazo

---

## Eventos Asíncronos (Kafka)

### Topic: `user-registered`

**Producido por:** user-service  
**Consumido por:** notification-service

**Evento:**
```json
{
  "userId": 1,
  "email": "juan@ejemplo.com",
  "name": "Juan",
  "activationToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Acción:** Enviar email de activación con link `{frontend_url}/activate?token={token}`

---

### Topic: `adoption-form-submitted`

**Producido por:** adoption-service  
**Consumido por:** form-analysis-service

**Evento:**
```json
{
  "adoptionRequestId": 1,
  "adopterId": 5,
  "adopterEmail": "juan@ejemplo.com",
  "organizationId": 42,
  "catId": 1,
  "hasPreviousCatExperience": true,
  "dailyPlayMinutes": 30,
  ... (todos los 32 campos)
}
```

**Acción:** Evaluar reglas, persistir análisis, publicar resultado

---

### Topic: `adoption-form-analysed`

**Producido por:** form-analysis-service  
**Consumido por:** adoption-service, notification-service

**Evento:**
```json
{
  "adoptionRequestId": 1,
  "decision": "Approved",
  "rejectionReason": null,
  "criticalFlags": 0,
  "warningFlags": 0,
  "noticeFlags": 2,
  "adopterEmail": "juan@ejemplo.com"
}
```

**Acciones:**
- adoption-service: cambia status según decisión
- notification-service: envía email (Approved/ReviewRequired/Rejected) con detalles

---

## Configuración y Entorno

### Swagger UI Consolidado

URL única para ver toda la documentación:  
**`http://localhost:8080/swagger-ui`** → dropdown con 6 servicios

Las rutas OpenAPI internas (`/api/openapi/{servicio}`) son proxeadas automáticamente por el gateway.

### Variables de Entorno Clave

```env
# PostgreSQL
DB_USER=kitties
DB_PASSWORD=kitties
DB_HOST=localhost
DB_PORT=5432
DB_NAME=kitties

# JWT
JWT_PRIVATE_KEY_LOCATION=privateKey.pem
JWT_PUBLIC_KEY_LOCATION=publicKey.pem

# MinIO / S3
MINIO_ROOT_USER=kitties
MINIO_ROOT_PASSWORD=change_me_min16chars
MINIO_DEFAULT_BUCKETS=kitties

# Kafka
KAFKA_HOST=localhost
KAFKA_PORT=9092

# Frontend URL (CORS)
CORS_ORIGIN=http://localhost:5173
```

### Puertos

| Servicio | Puerto |
|----------|--------|
| Gateway | 8080 |
| User Service | 8081 |
| Auth Service | 8082 |
| Storage Service | 8083 |
| Cat Service | 8084 |
| Notification Service | 8085 |
| Adoption Service | 8086 |
| Form Analysis Service | 8087 |
| Organization Service | 8088 |

---

## Testing e Integración

### Ejemplo: Flujo Completo (cURL)

```bash
# 1. Registrarse
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "email": "juan@ejemplo.com",
    "password": "MySecure123!",
    "name": "Juan",
    "surname": "García"
  }'

# (Email recibido con token: 550e8400-e29b-41d4-a716-446655440000)

# 2. Activarse
curl -X POST http://localhost:8080/api/users/activate \
  -H "Content-Type: application/json" \
  -d '{"token": "550e8400-e29b-41d4-a716-446655440000"}'

# 3. Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "juan@ejemplo.com",
    "password": "MySecure123!"
  }' | jq '.accessToken'

# (Respuesta: {accessToken: "eyJ...", refreshToken: "uuid", expiresIn: 900})

# 4. Ver gatos disponibles
curl http://localhost:8080/api/cats?city=Madrid

# 5. Adoptar un gato (con token)
curl -X POST http://localhost:8080/api/adoptions \
  -H "Authorization: Bearer {accessToken}" \
  -H "Content-Type: application/json" \
  -d '{
    "catId": 1,
    "organizationId": 42
  }'
```

---

## Notas para el Desarrollo Frontend

1. **Siempre renovar token 1-2 min antes de expirar** (900s = 15 min)
2. **Rate limiting:** 10 req/min en login, 20 req/min en refresh, 5 req/min en upload
3. **Multipart upload:** usar `FormData` en JavaScript, no JSON
4. **Imágenes:** máximo 5MB, solo JPEG/PNG
5. **Errores de validación:** respuesta 400 con detalles de campos inválidos
6. **CORS:** configurado para `http://localhost:5173`, cambiar en prod
7. **Campos nullable:** revisar documentación Swagger para qué campos son opcionales
8. **Soft deletes:** usuarios/miembros nunca se borran, se marcan como `Inactive`/`Removed`

---

## Roadmap Futuro

- [ ] Pagos integrados (adopciones con costo)
- [ ] Notificaciones push (socket.io o SSE)
- [ ] Geolocalización avanzada (radio de búsqueda)
- [ ] Sistema de reseñas (feedback post-adopción)
- [ ] Reportes PDF (contrato firmado)
- [ ] Integración con redes sociales (login social)

---

**Documento generado:** 2026-04-25  
**Próxima actualización:** Según cambios en API o negocio
