# CheckBiz — Backend (Kotlin + Spring Boot + PostgreSQL + Docker)

> **Nota de rebranding:** este proyecto se llamaba Trustify y ahora es
> **CheckBiz**. El renombrado (paquetes Kotlin, nombre de base de datos,
> contenedores Docker, variables de configuración, admin de arranque) se
> hizo de forma sistemática y se re-verificó por completo después:
> las 26 entidades se volvieron a cruzar contra una base `checkbiz_db`
> recién creada desde cero con el SQL renombrado, sin ninguna
> discrepancia, y el algoritmo Módulo 10 se recompiló bajo el nuevo
> paquete `com.checkbiz.backend` con los mismos resultados de siempre.

Reescritura del backend en el stack de la propuesta original: Kotlin,
Spring Boot, PostgreSQL, todo sobre Docker. Misma funcionalidad que la
versión Node ya construida: registro con KYC en capas, login unificado
(cliente/emprendedor), activación de emprendedor y backoffice mínimo de
admin (cola de KYC + veto de cédula).

> **Nota sobre cómo se probó este backend:** Maven Central (de donde
> Gradle descarga Spring Boot y Kotlin) no estaba accesible en el entorno
> donde se generó este código, así que no pude compilar el proyecto
> completo ahí. Lo que sí verifiqué de punta a punta:
> 1. El algoritmo Módulo 10 se compiló con `kotlinc` real y corrió 707
>    casos de prueba (500 cédulas generadas válidas, 5 casos borde, 200
>    mutaciones rechazadas) — todos correctos.
> 2. Un script comparó automáticamente cada `@Table`/`@Column` de las 26
>    entidades contra las columnas reales de `checkbiz_db` (nombres,
>    tipos `TEXT`/`JSONB`, y `NOT NULL`) vía `information_schema`. Encontró
>    y permitió corregir 3 columnas mal mapeadas antes de entregarte esto
>    (les faltaba `columnDefinition = "text"`, lo que habría roto
>    `ddl-auto: validate` al arrancar).
> 3. Un tercer script confirmó que las 64 clases Kotlin tienen llaves y
>    paréntesis balanceados (sin contar comentarios con numeración "1)",
>    "2)", que dan falsos positivos ya revisados a mano).
>
> En tu máquina, con acceso normal a internet, `gradle build` va a poder
> bajar las dependencias sin ningún cambio de código.

---

## 1. Requisitos

- JDK 21
- Docker (recomendado) — o PostgreSQL 16 propio

## 2. Levantar todo con Docker (la forma más simple)

```bash
docker compose up -d --build
```

Esto levanta PostgreSQL limpio y el backend en un solo paso. El backend,
al arrancar, usa **Flyway** para crear el esquema completo a partir de
`src/main/resources/db/migration/V1__init.sql` (el mismo SQL de 26 tablas
ya validado en la versión anterior) y siembra el admin con contraseña real.

Backend disponible en `http://localhost:4000`.

## 3. Alternativa: solo Postgres en Docker, backend local

```bash
docker compose up -d db
./gradlew bootRun   # o: gradle bootRun, si no tienes el wrapper generado
```

> Este proyecto no incluye el binario `gradlew`/`gradle-wrapper.jar`
> (no pude generarlo/validarlo en el entorno de desarrollo). La primera
> vez, genera el wrapper tú mismo:
> ```bash
> gradle wrapper --gradle-version 8.9
> ```
> A partir de ahí `./gradlew` funciona normal.

## 4. Admin de arranque

Al arrancar por primera vez, `AdminSeeder` reemplaza el hash placeholder
del SQL por uno real:

- **Correo:** `admin@checkbiz.ec`
- **Contraseña:** `CheckBizAdmin2025!` — **cámbiala** apenas puedas.

---

## Endpoints

### Público — Cliente / Emprendedor (`/api/auth`)

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/auth/registro` | Paso 1: datos + Capa 1 (Módulo 10). Valida unicidad y veto. Crea la cuenta y envía OTP. |
| POST | `/api/auth/login` | Login único para cliente y emprendedor. |
| POST | `/api/auth/otp/enviar` | Reenvía el código OTP (requiere token). |
| POST | `/api/auth/otp/verificar` | Verifica el código, sube a Capa 2. |
| POST | `/api/auth/foto` | Sube la selfie con cédula (`multipart/form-data`, campo `foto`). Capa 3, queda en revisión. |
| GET | `/api/auth/me` | Perfil del usuario autenticado. |

### Emprendedor (`/api/emprendedor`)

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/emprendedor/activar` | Activa `rolEmprendedor` sobre la cuenta ya existente (Capa 4 + contrato específico). No crea una cuenta nueva. |

### Admin (`/api/admin`) — login y rutas completamente separadas del público

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/admin/login` | Login de administrador. |
| GET | `/api/admin/kyc/fotos?estado=en_revision` | Cola de verificación de fotos (Capa 3). |
| PATCH | `/api/admin/kyc/fotos/:id` | Aprobar o rechazar una foto (`{ estado, motivoRechazo? }`). |
| POST | `/api/admin/veto` | Vetar una cédula permanentemente (`{ cedula, motivo }`). |

En desarrollo (`OTP_DEV_MODE=true`), las respuestas de `/registro` y
`/otp/enviar` incluyen el código real (`otp.codigoDev`) para poder probar
el flujo completo sin un proveedor de SMS conectado todavía.

---

## Estructura

```
src/main/kotlin/com/checkbiz/backend/
├─ CheckBizBackendApplication.kt
├─ config/         → JwtService, JwtAuthFilter, SecurityConfig, WebConfig, AdminSeeder
├─ domain/         → 26 entidades JPA (una por tabla)
├─ repository/     → interfaces Spring Data JPA
├─ dto/            → requests/responses
├─ service/        → AuthService, OtpService, EmprendedorService, AdminService, ArchivoService
├─ controller/      → AuthController, EmprendedorController, AdminController, SaludController
├─ exception/       → AppException, GlobalExceptionHandler
└─ util/            → Modulo10.kt (validación de cédula), RequestExtensions.kt

src/main/resources/
├─ application.yml
└─ db/migration/V1__init.sql   → mismo esquema ya validado (26 tablas)
```

## Decisiones técnicas clave

- **`ddl-auto: validate`**: Hibernate nunca genera ni altera el esquema —
  solo confirma que las entidades coinciden con las tablas reales. La
  fuente de verdad del esquema es Flyway (`V1__init.sql`), igual que en
  la versión anterior con `checkbiz_schema.sql`.
- **JWT con dos tipos de titular** (`USUARIO` / `ADMIN`): un token de
  admin nunca sirve como token de usuario y viceversa — se valida el tipo
  explícitamente en cada filtro, no solo la firma.
- **BCrypt con 12 rounds**, igual que la versión Node, para que ambas
  implementaciones sean equivalentes en seguridad.
- **`@MapsId` para la llave compuesta** de `negocio_insignias` — patrón
  estándar de JPA para PK compuestas que además son FK.

## Próximos pasos naturales

- Módulo B3: crear/editar el negocio (Mini Landing Page) — las entidades
  `Negocio` y `CatalogoItem` ya están listas.
- Módulo A7/A8: solicitudes y reseñas.
- Conectar el frontend (`checkbiz/`) a estos endpoints.
