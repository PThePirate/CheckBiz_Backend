-- =====================================================================
-- CHECKBIZ — Esquema de base de datos (PostgreSQL)
-- Base de datos: checkbiz_db
-- Generado a partir de CheckBiz_Especificacion_Pantallas.md
--
-- Cómo correrlo:
--   createdb checkbiz_db
--   psql -d checkbiz_db -f checkbiz_schema.sql
-- (o vía Docker, ver docker-compose.yml del backend)
--
-- Convenciones:
--   - UUID como PK en tablas de negocio (evita IDs adivinables/secuenciales
--     en URLs públicas, como el slug del perfil o el QR).
--   - SERIAL/INT en catálogos pequeños y estables (categorías, planes).
--   - CHECK en vez de ENUM: más fácil de alterar sin migraciones pesadas.
--   - Toda tabla de negocio lleva creado_en / actualizado_en.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";  -- para gen_random_uuid()

-- =====================================================================
-- MÓDULO E — ADMIN (independiente de la identidad ciudadana)
-- =====================================================================

-- Cuentas de administrador. Se crean por seed/script, nunca por registro
-- público. No tienen cédula ni flujo de KYC: son otro tipo de cuenta.
CREATE TABLE admins (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre          VARCHAR(100) NOT NULL,
    correo          VARCHAR(150) NOT NULL UNIQUE,
    password_hash   TEXT NOT NULL,
    rol             VARCHAR(20) NOT NULL DEFAULT 'admin'
                        CHECK (rol IN ('admin', 'moderador')),
    activo          BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Lista de veto permanente por cédula (E4). Se consulta ANTES de permitir
-- un registro nuevo, y también bloquea el login si alguien ya registrado
-- es vetado después.
CREATE TABLE veto_cedulas (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cedula          VARCHAR(10) NOT NULL UNIQUE,
    motivo          TEXT NOT NULL,
    vetado_por      UUID NOT NULL REFERENCES admins(id),
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Log de auditoría (E8) — toda acción sensible del admin queda registrada.
CREATE TABLE admin_logs_auditoria (
    id                  BIGSERIAL PRIMARY KEY,
    admin_id            UUID NOT NULL REFERENCES admins(id),
    accion              VARCHAR(60) NOT NULL,   -- ej. 'veto_cedula', 'aprobar_kyc'
    entidad_afectada    VARCHAR(60),            -- ej. 'usuarios', 'negocios'
    entidad_id          VARCHAR(60),
    detalle             JSONB,
    creado_en           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_veto_cedulas_cedula ON veto_cedulas(cedula);


-- =====================================================================
-- MÓDULO A/B — IDENTIDAD CIUDADANA (Cliente + Emprendedor unificados)
-- =====================================================================

-- Tabla central de identidad. 1 cédula = 1 fila = 1 cuenta.
-- rol_cliente y rol_emprendedor son capacidades acumulables, no cuentas
-- separadas (ver decisión de arquitectura acordada).
CREATE TABLE usuarios (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cedula                      VARCHAR(10) NOT NULL UNIQUE,
    nombre_completo             VARCHAR(150) NOT NULL,
    correo                      VARCHAR(150) NOT NULL UNIQUE,
    telefono                    VARCHAR(15) NOT NULL,
    password_hash               TEXT NOT NULL,

    -- Capacidades (roles) sobre la misma identidad
    rol_cliente                 BOOLEAN NOT NULL DEFAULT TRUE,
    rol_emprendedor             BOOLEAN NOT NULL DEFAULT FALSE,

    -- Esquema de identidad en 5 capas
    -- 1 Estructura (Módulo 10) · 2 OTP · 3 Foto · 4 SENESCYT/SRI · 5 Biometría (roadmap)
    kyc_layer                   SMALLINT NOT NULL DEFAULT 1
                                     CHECK (kyc_layer BETWEEN 1 AND 5),
    foto_verificacion_estado    VARCHAR(20) NOT NULL DEFAULT 'no_iniciada'
                                     CHECK (foto_verificacion_estado IN
                                         ('no_iniciada','en_revision','aprobada','rechazada')),
    senescyt_sri_estado         VARCHAR(20) NOT NULL DEFAULT 'no_verificado'
                                     CHECK (senescyt_sri_estado IN
                                         ('no_verificado','verificando','verificado','no_encontrado')),

    -- Estado de cuenta / cédula
    estado_cedula                VARCHAR(20) NOT NULL DEFAULT 'activa'
                                     CHECK (estado_cedula IN ('activa','vetada')),

    acepto_terminos              BOOLEAN NOT NULL DEFAULT FALSE,

    creado_en                    TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en                TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_usuarios_cedula ON usuarios(cedula);
CREATE UNIQUE INDEX idx_usuarios_correo ON usuarios(correo);

-- Códigos OTP (Capa 2). Se generan por intento de verificación; el más
-- reciente y no vencido es el válido.
CREATE TABLE otp_verificaciones (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id      UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    codigo          VARCHAR(6) NOT NULL,
    canal           VARCHAR(10) NOT NULL DEFAULT 'sms'
                        CHECK (canal IN ('sms','whatsapp')),
    intentos        SMALLINT NOT NULL DEFAULT 0,
    verificado      BOOLEAN NOT NULL DEFAULT FALSE,
    expira_en       TIMESTAMPTZ NOT NULL,
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_otp_usuario ON otp_verificaciones(usuario_id);

-- Foto con cédula (Capa 3) — cola de revisión manual en el piloto.
CREATE TABLE verificaciones_foto (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id      UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    foto_url        TEXT NOT NULL,
    estado          VARCHAR(20) NOT NULL DEFAULT 'en_revision'
                        CHECK (estado IN ('en_revision','aprobada','rechazada')),
    motivo_rechazo  TEXT,
    revisado_por    UUID REFERENCES admins(id),
    revisado_en     TIMESTAMPTZ,
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_verif_foto_estado ON verificaciones_foto(estado);
CREATE INDEX idx_verif_foto_usuario ON verificaciones_foto(usuario_id);

-- Firma del Contrato de Adhesión / Declaración Responsable.
-- Requisito legal (Ley de Comercio Electrónico): guardar IP + fecha/hora
-- exactas del momento de aceptación, no solo un booleano.
CREATE TABLE contratos_adhesion (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id          UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    tipo                VARCHAR(30) NOT NULL
                            CHECK (tipo IN ('adhesion_general','adhesion_emprendedor')),
    version_documento   VARCHAR(10) NOT NULL DEFAULT 'v1',
    ip_firma            VARCHAR(45) NOT NULL,
    user_agent          TEXT,
    firmado_en          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_contratos_usuario ON contratos_adhesion(usuario_id);

-- Reportes de usuario/estafa (A11) — puede derivar en veto (E4).
CREATE TABLE denuncias (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reportante_id       UUID NOT NULL REFERENCES usuarios(id),
    cedula_reportada    VARCHAR(10) NOT NULL,
    motivo              TEXT NOT NULL,
    estado              VARCHAR(20) NOT NULL DEFAULT 'abierta'
                            CHECK (estado IN ('abierta','revisada','archivada')),
    revisado_por        UUID REFERENCES admins(id),
    creado_en           TIMESTAMPTZ NOT NULL DEFAULT now()
);


-- =====================================================================
-- MÓDULO C/D — INSTITUCIONES (Universidades / Cámaras) — login propio
-- =====================================================================

-- Login institucional único para C (universidades) y D (cámaras).
-- Rol diferenciado, sin acceso a datos personales sensibles de usuarios.
CREATE TABLE cuentas_institucionales (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo                VARCHAR(25) NOT NULL
                            CHECK (tipo IN ('universidad','camara_impuestos','camara_negocio')),
    nombre_institucion  VARCHAR(150) NOT NULL,
    correo              VARCHAR(150) NOT NULL UNIQUE,
    password_hash       TEXT NOT NULL,
    logo_url            TEXT,
    activo              BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Catálogo de universidades para el carrusel de logos y para asociar
-- negocios/alumni, independiente de si esa universidad ya tiene login.
CREATE TABLE universidades (
    id                      SERIAL PRIMARY KEY,
    nombre                  VARCHAR(150) NOT NULL,
    logo_url                TEXT,
    cuenta_institucional_id UUID REFERENCES cuentas_institucionales(id),
    activa                  BOOLEAN NOT NULL DEFAULT TRUE
);

-- Verificación de alumni (para la insignia "Alumni Verificado").
CREATE TABLE alumni_verificacion (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id      UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    universidad_id  INT NOT NULL REFERENCES universidades(id),
    estado          VARCHAR(20) NOT NULL DEFAULT 'pendiente'
                        CHECK (estado IN ('pendiente','verificado','rechazado')),
    verificado_en   TIMESTAMPTZ,
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (usuario_id, universidad_id)
);

-- Cámaras de negocio / cámara de impuestos (para co-branding y panel D).
CREATE TABLE camaras (
    id                      SERIAL PRIMARY KEY,
    nombre                  VARCHAR(150) NOT NULL,
    tipo                    VARCHAR(20) NOT NULL
                                CHECK (tipo IN ('impuestos','negocio')),
    logo_url                TEXT,
    cuenta_institucional_id UUID REFERENCES cuentas_institucionales(id)
);


-- =====================================================================
-- MÓDULO B — NEGOCIO / MINI LANDING PAGE (pieza central del producto)
-- =====================================================================

CREATE TABLE categorias (
    id      SERIAL PRIMARY KEY,
    nombre  VARCHAR(60) NOT NULL UNIQUE,
    icono   VARCHAR(40),
    activa  BOOLEAN NOT NULL DEFAULT TRUE
);

-- El perfil público (Mini Landing Page, pantalla A6).
CREATE TABLE negocios (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id              UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    categoria_id            INT REFERENCES categorias(id),
    universidad_id          INT REFERENCES universidades(id),   -- para sello co-branded
    camara_id               INT REFERENCES camaras(id),          -- para sello co-branded

    nombre_comercial        VARCHAR(120) NOT NULL,
    slug                    VARCHAR(150) NOT NULL UNIQUE,        -- URL pública /negocio/:slug
    descripcion_corta       VARCHAR(280),
    ciudad                  VARCHAR(80),
    whatsapp                VARCHAR(15) NOT NULL,

    foto_portada_url        TEXT,
    logo_url                TEXT,
    video_presentacion_url  TEXT,                                -- solo plan Pro/Elite

    trust_score             SMALLINT NOT NULL DEFAULT 0
                                 CHECK (trust_score BETWEEN 0 AND 100),
    nivel_formalizacion     VARCHAR(20) NOT NULL DEFAULT 'semilla'
                                 CHECK (nivel_formalizacion IN ('semilla','asesoria','formalizado')),
    estado_publicacion      VARCHAR(20) NOT NULL DEFAULT 'borrador'
                                 CHECK (estado_publicacion IN ('borrador','publicado','suspendido')),

    creado_en               TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizado_en          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_negocios_slug ON negocios(slug);
CREATE INDEX idx_negocios_usuario ON negocios(usuario_id);
CREATE INDEX idx_negocios_categoria ON negocios(categoria_id);

-- Catálogo de productos/servicios (B4). Límite de 3 en plan gratuito;
-- ese límite se valida en la app según el plan activo, no aquí.
CREATE TABLE catalogo_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    negocio_id      UUID NOT NULL REFERENCES negocios(id) ON DELETE CASCADE,
    nombre          VARCHAR(120) NOT NULL,
    precio_referencial NUMERIC(10,2),
    foto_url        TEXT,
    orden           SMALLINT NOT NULL DEFAULT 0,
    activo          BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_catalogo_negocio ON catalogo_items(negocio_id);


-- =====================================================================
-- MÓDULO A — SOLICITUDES Y RESEÑAS (A7 / A8)
-- =====================================================================

CREATE TABLE solicitudes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    negocio_id      UUID NOT NULL REFERENCES negocios(id),
    cliente_id      UUID NOT NULL REFERENCES usuarios(id),
    descripcion     TEXT NOT NULL,
    fecha_estimada  DATE,
    estado          VARCHAR(20) NOT NULL DEFAULT 'enviada'
                        CHECK (estado IN ('enviada','en_conversacion','confirmada','cancelada')),
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    confirmada_en   TIMESTAMPTZ,
    actualizado_en  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_solicitudes_negocio ON solicitudes(negocio_id);
CREATE INDEX idx_solicitudes_cliente ON solicitudes(cliente_id);
CREATE INDEX idx_solicitudes_estado ON solicitudes(estado);

-- Reseña auditada: solo existe si nace de una solicitud CONFIRMADA
-- (1 reseña por solicitud — así se evita reseña sin compra real).
CREATE TABLE resenas (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    solicitud_id        UUID NOT NULL UNIQUE REFERENCES solicitudes(id),
    negocio_id          UUID NOT NULL REFERENCES negocios(id),
    cliente_id          UUID NOT NULL REFERENCES usuarios(id),
    estrellas           SMALLINT NOT NULL CHECK (estrellas BETWEEN 1 AND 5),
    comentario          TEXT,
    respuesta_negocio   TEXT,
    respondida_en       TIMESTAMPTZ,
    creado_en           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_resenas_negocio ON resenas(negocio_id);


-- =====================================================================
-- MÓDULO B — REPUTACIÓN, FORMALIZACIÓN Y ANALÍTICA
-- =====================================================================

-- Checklist de la Ruta de Formalización (B8) por negocio.
CREATE TABLE ruta_formalizacion (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    negocio_id      UUID NOT NULL REFERENCES negocios(id) ON DELETE CASCADE,
    nivel           VARCHAR(20) NOT NULL
                        CHECK (nivel IN ('semilla','asesoria','formalizado')),
    requisito       VARCHAR(150) NOT NULL,
    completado      BOOLEAN NOT NULL DEFAULT FALSE,
    completado_en   TIMESTAMPTZ
);

CREATE INDEX idx_ruta_negocio ON ruta_formalizacion(negocio_id);

-- Eventos de analítica (B7): visitas al perfil, clics a WhatsApp, etc.
-- Tabla de eventos crudos; los gráficos (línea de tiempo, comparativa
-- semanal) se agregan con GROUP BY sobre creado_en en la API.
CREATE TABLE analitica_eventos (
    id              BIGSERIAL PRIMARY KEY,
    negocio_id      UUID NOT NULL REFERENCES negocios(id) ON DELETE CASCADE,
    tipo_evento     VARCHAR(25) NOT NULL
                        CHECK (tipo_evento IN ('visita_perfil','clic_whatsapp','clic_solicitud','escaneo_qr')),
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_analitica_negocio_fecha ON analitica_eventos(negocio_id, creado_en);

-- QR de verificación física (B11) — uno por negocio, token no adivinable.
CREATE TABLE qr_verificacion (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    negocio_id      UUID NOT NULL UNIQUE REFERENCES negocios(id) ON DELETE CASCADE,
    codigo          VARCHAR(40) NOT NULL UNIQUE,
    escaneos_total  INT NOT NULL DEFAULT 0,
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now()
);


-- =====================================================================
-- INSIGNIAS / CERTIFICACIONES (B10)
-- =====================================================================

CREATE TABLE insignias (
    id           SERIAL PRIMARY KEY,
    nombre       VARCHAR(60) NOT NULL,
    descripcion  VARCHAR(200),
    icono        VARCHAR(40),
    tipo         VARCHAR(20) NOT NULL CHECK (tipo IN ('usuario','negocio'))
);

CREATE TABLE negocio_insignias (
    negocio_id   UUID NOT NULL REFERENCES negocios(id) ON DELETE CASCADE,
    insignia_id  INT NOT NULL REFERENCES insignias(id),
    obtenida_en  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (negocio_id, insignia_id)
);


-- =====================================================================
-- SUSCRIPCIONES / PLANES (B9)
-- =====================================================================

CREATE TABLE planes (
    id                              SERIAL PRIMARY KEY,
    nombre                          VARCHAR(30) NOT NULL UNIQUE
                                        CHECK (nombre IN ('basico','pro','elite')),
    precio_mensual                  NUMERIC(6,2) NOT NULL DEFAULT 0,
    precio_semestral                NUMERIC(6,2) NOT NULL DEFAULT 0,
    limite_catalogo                 SMALLINT NOT NULL DEFAULT 3,
    incluye_video                   BOOLEAN NOT NULL DEFAULT FALSE,
    incluye_analitica_avanzada      BOOLEAN NOT NULL DEFAULT FALSE,
    incluye_multiusuario            BOOLEAN NOT NULL DEFAULT FALSE,
    incluye_traduccion              BOOLEAN NOT NULL DEFAULT FALSE,
    incluye_certificado_pdf         BOOLEAN NOT NULL DEFAULT FALSE,
    incluye_whatsapp_business_api   BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE suscripciones (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    negocio_id      UUID NOT NULL UNIQUE REFERENCES negocios(id) ON DELETE CASCADE,
    plan_id         INT NOT NULL REFERENCES planes(id),
    ciclo           VARCHAR(10) NOT NULL CHECK (ciclo IN ('mensual','semestral')),
    estado          VARCHAR(15) NOT NULL DEFAULT 'activa'
                        CHECK (estado IN ('activa','vencida','cancelada')),
    inicia_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    vence_en        TIMESTAMPTZ
);


-- =====================================================================
-- NOTIFICACIONES (A10)
-- =====================================================================

CREATE TABLE notificaciones (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id      UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    tipo            VARCHAR(30) NOT NULL,   -- ej. 'kyc_aprobado', 'nueva_solicitud'
    titulo          VARCHAR(150) NOT NULL,
    mensaje         TEXT,
    leida           BOOLEAN NOT NULL DEFAULT FALSE,
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notificaciones_usuario ON notificaciones(usuario_id, leida);


-- =====================================================================
-- SESIONES (refresh tokens de auth — usuarios, admins e institucionales)
-- =====================================================================

CREATE TABLE sesiones (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id                  UUID REFERENCES usuarios(id) ON DELETE CASCADE,
    admin_id                    UUID REFERENCES admins(id) ON DELETE CASCADE,
    cuenta_institucional_id     UUID REFERENCES cuentas_institucionales(id) ON DELETE CASCADE,
    refresh_token_hash          TEXT NOT NULL UNIQUE,
    user_agent                  TEXT,
    ip                          VARCHAR(45),
    expira_en                   TIMESTAMPTZ NOT NULL,
    creado_en                   TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Exactamente un tipo de titular por sesión
    CONSTRAINT chk_sesion_un_titular CHECK (
        (usuario_id IS NOT NULL)::int +
        (admin_id IS NOT NULL)::int +
        (cuenta_institucional_id IS NOT NULL)::int = 1
    )
);


-- =====================================================================
-- SEED MÍNIMO (datos de arranque para que la app no cargue vacía)
-- =====================================================================

INSERT INTO categorias (nombre, icono) VALUES
    ('Diseño', 'palette'),
    ('Software', 'code'),
    ('Veterinaria', 'stethoscope'),
    ('Impresión 3D', 'box'),
    ('Legal', 'scale'),
    ('Limpieza', 'sparkles'),
    ('Tutorías', 'graduation-cap'),
    ('Mantenimiento', 'wrench');

INSERT INTO planes (nombre, precio_mensual, precio_semestral, limite_catalogo,
                     incluye_video, incluye_analitica_avanzada, incluye_multiusuario,
                     incluye_traduccion, incluye_certificado_pdf, incluye_whatsapp_business_api)
VALUES
    ('basico', 0,    0,   3,  FALSE, FALSE, FALSE, FALSE, FALSE, FALSE),
    ('pro',    4.99, 20,  10, TRUE,  TRUE,  FALSE, FALSE, FALSE, FALSE),
    ('elite',  9.99, 45,  30, TRUE,  TRUE,  TRUE,  TRUE,  TRUE,  TRUE);

INSERT INTO insignias (nombre, descripcion, icono, tipo) VALUES
    ('Emprendedor Verificado', 'Completó las 4 capas de verificación disponibles', 'badge-check', 'negocio'),
    ('Alumni Verificado', 'Egresado confirmado de una universidad aliada', 'graduation-cap', 'negocio'),
    ('Vendedor Confiable', 'Historial sostenido de solicitudes confirmadas', 'shield-check', 'negocio'),
    ('Potencial Exportable', 'Catálogo con traducción ES/EN habilitada', 'languages', 'negocio');

-- Un admin de arranque (CAMBIAR password_hash real antes de producción;
-- este es solo un placeholder para que exista la fila).
INSERT INTO admins (nombre, correo, password_hash, rol)
VALUES ('Admin CheckBiz', 'admin@checkbiz.ec', '<reemplazar_por_hash_bcrypt>', 'admin');
