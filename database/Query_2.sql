-- =====================================================================
-- FERIAPP - Prueba geoespacial con coordenadas reales (5 puestos)
-- Script independiente: inserta solo lo mínimo necesario
-- (5 usuarios + 5 comerciantes + 5 puestos) para probar que PostGIS
-- funciona bien.
-- =====================================================================
/*
-- -----------------------------------------------------------------
-- 1. USUARIO: 5 comerciantes de prueba
-- -----------------------------------------------------------------
INSERT INTO usuario (nombre, apellido, telefono, contrasena, rol) VALUES
('Maria',  'Quispe', '70011001', 'clave123', 'comerciante'), -- id 1
('Juan',   'Mamani', '70011002', 'clave123', 'comerciante'), -- id 2
('Rosa',   'Choque', '70011003', 'clave123', 'comerciante'), -- id 3
('Pedro',  'Flores', '70011004', 'clave123', 'comerciante'), -- id 4
('Elena',  'Condori','70011005', 'clave123', 'comerciante'); -- id 5

-- -----------------------------------------------------------------
-- 2. COMERCIANTE: 5 filas, una por cada usuario
-- -----------------------------------------------------------------
INSERT INTO comerciante (id_usuario, nombre_comercial, descripcion) VALUES
(1, 'Frutas Doña Maria', 'Puesto de prueba 1'),
(2, 'Verduras Don Juan',  'Puesto de prueba 2'),
(3, 'Carniceria Rosa',    'Puesto de prueba 3'),
(4, 'Modas Pedro',        'Puesto de prueba 4'),
(5, 'Calzados Elena',     'Puesto de prueba 5');

-- -----------------------------------------------------------------
-- 3. PUESTO: 5 puestos con tus coordenadas reales
-- -----------------------------------------------------------------
INSERT INTO puesto (id_comerciante, nombre_puesto, descripcion, ubicacion) VALUES
(1, 'Puesto 1', 'Punto de referencia 1', ST_SetSRID(ST_MakePoint(-68.173642, -16.494905), 4326)),
(2, 'Puesto 2', 'Punto de referencia 2', ST_SetSRID(ST_MakePoint(-68.183180, -16.492273), 4326)),
(3, 'Puesto 3', 'Punto de referencia 3', ST_SetSRID(ST_MakePoint(-68.172727, -16.488300), 4326)),
(4, 'Puesto 4', 'Punto de referencia 4', ST_SetSRID(ST_MakePoint(-68.190695, -16.487053), 4326)),
(5, 'Puesto 5', 'Punto central',         ST_SetSRID(ST_MakePoint(-68.180061, -16.490633), 4326));


-- -----------------------------------------------------------------
-- 4. Verificar que las coordenadas se guardaron bien
-- -----------------------------------------------------------------
SELECT id_puesto, nombre_puesto, ST_AsText(ubicacion) AS coordenadas
FROM puesto
ORDER BY id_puesto;

-- -----------------------------------------------------------------
-- 5. Distancia real (en metros) entre cada puesto y el puesto 5
--    (el del centro) - confirma que PostGIS calcula bien
-- -----------------------------------------------------------------


SELECT
    p.nombre_puesto,
    ROUND(ST_Distance(
        p.ubicacion::geography,
        (SELECT ubicacion FROM puesto WHERE id_puesto = 5)::geography
    )::numeric, 1) AS distancia_al_centro_metros
FROM puesto p
ORDER BY distancia_al_centro_metros;
*/
-- -----------------------------------------------------------------
-- 6. Puestos dentro de 1 km del puesto central (id 5) - simula una
--    búsqueda de "puestos cercanos" como la usará la app
-- -----------------------------------------------------------------
SELECT nombre_puesto,
       ROUND(ST_Distance(
           ubicacion::geography,
           (SELECT ubicacion FROM puesto WHERE id_puesto = 5)::geography
       )::numeric, 1) AS distancia_metros
FROM puesto
WHERE ST_DWithin(
        ubicacion::geography,
        (SELECT ubicacion FROM puesto WHERE id_puesto = 5)::geography,
        1000
      )
ORDER BY distancia_metros;

-- =====================================================================
-- FIN
-- =====================================================================