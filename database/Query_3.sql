-- =====================================================================
-- FERIAPP - Completar datos de prueba sobre los 5 puestos ya existentes
-- (usuario/comerciante/puesto con id 1-5, ya insertados por
--  feriapp_prueba_geoespacial.sql). Agrega categorías, productos,
--  relaciones producto_puesto, imágenes y búsquedas.
-- =====================================================================
/*
-- -----------------------------------------------------------------
-- 1. CATEGORIA: 10 categorías (completas, aunque solo 5 tengan
--    productos vendidos en un puesto por ahora)
-- -----------------------------------------------------------------
INSERT INTO categoria (nombre_categoria, descripcion) VALUES
('Frutas',       'Frutas frescas de temporada'),         -- id 1
('Verduras',     'Verduras y hortalizas frescas'),        -- id 2
('Carnes',       'Carne de res, pollo y cerdo'),          -- id 3
('Ropa',         'Prendas de vestir para toda la familia'), -- id 4
('Calzado',      'Zapatos y zapatillas'),                 -- id 5
('Abarrotes',    'Productos de primera necesidad'),       -- id 6
('Bebidas',      'Bebidas embotelladas y gaseosas'),      -- id 7
('Limpieza',     'Artículos de limpieza del hogar'),      -- id 8
('Electronica',  'Accesorios y aparatos electrónicos'),   -- id 9
('Hogar',        'Artículos para el hogar');              -- id 10

-- -----------------------------------------------------------------
-- 2. PRODUCTO: 15 productos para los 5 puestos existentes (3 cada
--    uno) + 5 productos sueltos en las otras categorías, sin puesto
--    asignado todavía (útiles para probar búsquedas "sin resultado
--    en un puesto específico")
-- -----------------------------------------------------------------
INSERT INTO producto (id_categoria, nombre_producto, descripcion, precio) VALUES
(1, 'Manzana',         'Manzana roja, precio por kilo', 8.50),   -- id 1  -> puesto 1
(1, 'Platano',         'Plátano de exportación, por kilo', 6.00),-- id 2  -> puesto 1
(1, 'Naranja',         'Naranja jugosa, por kilo', 5.50),        -- id 3  -> puesto 1
(2, 'Papa',            'Papa blanca, por arroba', 45.00),        -- id 4  -> puesto 2
(2, 'Cebolla',         'Cebolla roja, por kilo', 4.50),          -- id 5  -> puesto 2
(2, 'Tomate',          'Tomate fresco, por kilo', 6.00),         -- id 6  -> puesto 2
(3, 'Carne de res',    'Carne de res, corte surtido, por kilo', 38.00), -- id 7 -> puesto 3
(3, 'Pollo entero',    'Pollo entero fresco, por kilo', 18.00),  -- id 8  -> puesto 3
(3, 'Chuleta de cerdo','Chuleta de cerdo, por kilo', 30.00),     -- id 9  -> puesto 3
(4, 'Polera',          'Polera de algodón unisex', 35.00),       -- id 10 -> puesto 4
(4, 'Pantalon jean',   'Pantalón jean para adulto', 90.00),      -- id 11 -> puesto 4
(4, 'Chompa',          'Chompa de lana', 70.00),                 -- id 12 -> puesto 4
(5, 'Zapatillas',      'Zapatillas deportivas', 150.00),         -- id 13 -> puesto 5
(5, 'Zapatos formales','Zapatos de vestir', 180.00),              -- id 14 -> puesto 5
(5, 'Sandalias',       'Sandalias de verano', 60.00),             -- id 15 -> puesto 5
(6,  'Arroz',          'Arroz por kilo', 7.00),                   -- id 16 -> sin puesto
(7,  'Gaseosa 2L',     'Gaseosa sabor cola, 2 litros', 12.00),    -- id 17 -> sin puesto
(8,  'Detergente',     'Detergente en polvo, 1kg', 15.00),       -- id 18 -> sin puesto
(9,  'Audifonos',      'Audífonos con cable 3.5mm', 25.00),      -- id 19 -> sin puesto
(10, 'Sarten',         'Sartén antiadherente 24cm', 55.00);      -- id 20 -> sin puesto

-- -----------------------------------------------------------------
-- 3. PRODUCTO_PUESTO: 3 productos por cada uno de los 5 puestos
--    (respeta el máximo de 3 ya validado por el trigger)
-- -----------------------------------------------------------------
INSERT INTO producto_puesto (id_producto, id_puesto) VALUES
(1,1), (2,1), (3,1),      -- Puesto 1 - Frutas
(4,2), (5,2), (6,2),      -- Puesto 2 - Verduras
(7,3), (8,3), (9,3),      -- Puesto 3 - Carnes
(10,4), (11,4), (12,4),   -- Puesto 4 - Ropa
(13,5), (14,5), (15,5);   -- Puesto 5 - Calzado

-- -----------------------------------------------------------------
-- 4. IMAGEN_PUESTO: exactamente 1 imagen por cada uno de los 5 puestos
-- -----------------------------------------------------------------
INSERT INTO imagen_puesto (id_puesto, ruta_imagen) VALUES
(1, '/images/puestos/puesto1.jpg'),
(2, '/images/puestos/puesto2.jpg'),
(3, '/images/puestos/puesto3.jpg'),
(4, '/images/puestos/puesto4.jpg'),
(5, '/images/puestos/puesto5.jpg');

-- -----------------------------------------------------------------
-- 5. IMAGEN_PRODUCTO: 1 imagen por cada uno de los 20 productos
-- -----------------------------------------------------------------
INSERT INTO imagen_producto (id_producto, ruta_imagen)
SELECT id_producto, '/images/productos/producto' || id_producto || '.jpg'
FROM producto;
*/
-- -----------------------------------------------------------------
-- 6. BUSQUEDAS: búsquedas ficticias del consumidor (sin cuenta),
--    cubriendo varias categorías para poder probar estadísticas
-- -----------------------------------------------------------------
INSERT INTO busquedas (id_categoria, id_producto, termino_busqueda) VALUES
(1, 1,    'manzana'),
(1, NULL, 'frutas'),
(1, 2,    'platano'),
(2, 4,    'papa'),
(2, NULL, 'verduras frescas'),
(3, 7,    'carne de res'),
(4, 10,   'polera'),
(4, NULL, 'ropa barata'),
(1, NULL, 'fruta fresca'),
(6, 16,   'arroz'),
(7, 17,   'gaseosa'),
(1, 3,    'naranja'),
(9, 19,   'audifonos'),
(1, NULL, 'donde comprar fruta'),
(5, 13,   'zapatillas deportivas');

-- =====================================================================
-- FIN - La base queda con: 5 usuarios/comerciantes/puestos (reales),
-- 10 categorías, 20 productos, 15 relaciones producto_puesto,
-- 5 imagen_puesto, 20 imagen_producto, 15 búsquedas.
-- =====================================================================