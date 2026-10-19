-- =====================================================================
-- FERIAPP - Script de creación de base de datos (estructura, sin datos)
-- Proyecto de grado: Aplicación móvil para la búsqueda de productos
-- a través de un mapa virtual en la Feria 16 de Julio

-- -----------------------------------------------------------------
-- 1. EXTENSIÓN POSTGIS
-- -----------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS postgis;

-- -----------------------------------------------------------------
-- 2. TABLA: usuario
-- Cuentas con acceso al sistema: comerciante y administrador.
-- El consumidor NO tiene fila aquí (usa la app sin registrarse).
-- El administrador se crea directamente con un INSERT manual.
-- -----------------------------------------------------------------
CREATE TABLE usuario (
    id_usuario      SERIAL PRIMARY KEY,
    nombre          VARCHAR(80)  NOT NULL,
    apellido        VARCHAR(80)  NOT NULL,
    telefono        VARCHAR(20)  NOT NULL UNIQUE,
    contrasena      VARCHAR(255) NOT NULL,
    rol             VARCHAR(20)  NOT NULL CHECK (rol IN ('comerciante', 'administrador')),
    fecha_registro  TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------
-- 3. TABLA: categoria
-- -----------------------------------------------------------------
CREATE TABLE categoria (
    id_categoria     SERIAL PRIMARY KEY,
    nombre_categoria VARCHAR(80) NOT NULL UNIQUE,
    descripcion      VARCHAR(255)
);

-- -----------------------------------------------------------------
-- 4. TABLA: comerciante
-- Datos comerciales de un usuario con rol 'comerciante'
-- -----------------------------------------------------------------
CREATE TABLE comerciante (
    id_comerciante    SERIAL PRIMARY KEY,
    id_usuario        INTEGER NOT NULL UNIQUE REFERENCES usuario(id_usuario) ON DELETE CASCADE,
    nombre_comercial  VARCHAR(100) NOT NULL,
    descripcion       VARCHAR(255),
    estado            VARCHAR(20) NOT NULL DEFAULT 'activo' CHECK (estado IN ('activo', 'inactivo'))
);

-- -----------------------------------------------------------------
-- 5. TABLA: puesto
-- Puesto físico dentro de la feria. La ubicación va aquí mismo como
-- columna geométrica (PostGIS), no en una tabla aparte.
-- -----------------------------------------------------------------
CREATE TABLE puesto (
    id_puesto       SERIAL PRIMARY KEY,
    id_comerciante  INTEGER NOT NULL UNIQUE REFERENCES comerciante(id_comerciante) ON DELETE CASCADE,
    nombre_puesto   VARCHAR(100) NOT NULL,
    descripcion     VARCHAR(255),
    ubicacion       GEOMETRY(Point, 4326) NOT NULL,
    estado          VARCHAR(20) NOT NULL DEFAULT 'activo' CHECK (estado IN ('activo', 'inactivo'))
);

-- Índice espacial para búsquedas por cercanía (también útil desde
-- otras herramientas que consulten la BD directamente)
CREATE INDEX idx_puesto_ubicacion ON puesto USING GIST (ubicacion);

-- -----------------------------------------------------------------
-- 6. TABLA: producto
-- -----------------------------------------------------------------
CREATE TABLE producto (
    id_producto     SERIAL PRIMARY KEY,
    id_categoria    INTEGER NOT NULL REFERENCES categoria(id_categoria) ON DELETE RESTRICT,
    nombre_producto VARCHAR(120) NOT NULL,
    descripcion     VARCHAR(255),
    precio          NUMERIC(10,2) NOT NULL CHECK (precio > 0),
    disponible      BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_registro  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_producto_categoria ON producto (id_categoria);
CREATE INDEX idx_producto_nombre ON producto (nombre_producto);

-- -----------------------------------------------------------------
-- 7. TABLA: producto_puesto
-- Relación N:M entre producto y puesto. Un puesto puede ofrecer
-- varios productos, pero como máximo 3 (ver trigger más abajo).
-- -----------------------------------------------------------------
CREATE TABLE producto_puesto (
    id_producto_puesto SERIAL PRIMARY KEY,
    id_producto        INTEGER NOT NULL REFERENCES producto(id_producto) ON DELETE CASCADE,
    id_puesto          INTEGER NOT NULL REFERENCES puesto(id_puesto) ON DELETE CASCADE,
    UNIQUE (id_producto, id_puesto)
);

CREATE INDEX idx_productopuesto_puesto ON producto_puesto (id_puesto);
CREATE INDEX idx_productopuesto_producto ON producto_puesto (id_producto);

-- -----------------------------------------------------------------
-- 8. TABLA: imagen_producto
-- Máximo 3 imágenes por producto (ver trigger más abajo)
-- -----------------------------------------------------------------
CREATE TABLE imagen_producto (
    id_imagen    SERIAL PRIMARY KEY,
    id_producto  INTEGER NOT NULL REFERENCES producto(id_producto) ON DELETE CASCADE,
    ruta_imagen  TEXT NOT NULL
);

CREATE INDEX idx_imagenproducto_producto ON imagen_producto (id_producto);

-- -----------------------------------------------------------------
-- 9. TABLA: imagen_puesto
-- Exactamente 1 imagen por puesto: id_puesto es UNIQUE, así la BD
-- misma impide una segunda fila para el mismo puesto.
-- -----------------------------------------------------------------
CREATE TABLE imagen_puesto (
    id_imagen   SERIAL PRIMARY KEY,
    id_puesto   INTEGER NOT NULL UNIQUE REFERENCES puesto(id_puesto) ON DELETE CASCADE,
    ruta_imagen TEXT NOT NULL
);

-- -----------------------------------------------------------------
-- 10. TABLA: busquedas
-- Registra las búsquedas del consumidor (sin cuenta, por eso no
-- lleva id_usuario). id_categoria y id_producto son opcionales para
-- poder calcular "categoría más buscada" sin obligar a que la
-- búsqueda haya encontrado un producto exacto.
-- -----------------------------------------------------------------
CREATE TABLE busquedas (
    id_busqueda      SERIAL PRIMARY KEY,
    id_categoria     INTEGER REFERENCES categoria(id_categoria) ON DELETE SET NULL,
    id_producto      INTEGER REFERENCES producto(id_producto) ON DELETE SET NULL,
    termino_busqueda VARCHAR(120) NOT NULL,
    fecha_busqueda   TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_busquedas_categoria ON busquedas (id_categoria);
CREATE INDEX idx_busquedas_fecha ON busquedas (fecha_busqueda);

-- =====================================================================
-- 11. TRIGGERS DE REGLAS DE NEGOCIO
-- =====================================================================

-- Un puesto no puede tener más de 3 productos asociados
CREATE OR REPLACE FUNCTION chk_max_productos_por_puesto()
RETURNS TRIGGER AS $$
BEGIN
    IF (SELECT COUNT(*) FROM producto_puesto WHERE id_puesto = NEW.id_puesto) >= 3 THEN
        RAISE EXCEPTION 'El puesto % ya tiene el máximo de 3 productos asociados', NEW.id_puesto;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_max_productos_por_puesto
BEFORE INSERT ON producto_puesto
FOR EACH ROW EXECUTE FUNCTION chk_max_productos_por_puesto();

-- Un producto no puede tener más de 3 imágenes
CREATE OR REPLACE FUNCTION chk_max_imagenes_por_producto()
RETURNS TRIGGER AS $$
BEGIN
    IF (SELECT COUNT(*) FROM imagen_producto WHERE id_producto = NEW.id_producto) >= 3 THEN
        RAISE EXCEPTION 'El producto % ya tiene el máximo de 3 imágenes', NEW.id_producto;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_max_imagenes_por_producto
BEFORE INSERT ON imagen_producto
FOR EACH ROW EXECUTE FUNCTION chk_max_imagenes_por_producto();

SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
ORDER BY table_name;
