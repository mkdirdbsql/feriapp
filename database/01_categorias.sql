-- Categorías de la feria (72). Se pueden ejecutar varias veces: no repite
-- las que ya existen (compara el nombre sin importar mayúsculas).
INSERT INTO categoria (nombre_categoria, descripcion)
SELECT n.nombre, n.descripcion
FROM (VALUES
  ('Comida', 'Almuerzos, platos típicos y comida al paso'),
  ('Bebidas y refrescos', 'Jugos, gaseosas, mocochinchi y bebidas'),
  ('Frutas y verduras', 'Productos frescos del campo'),
  ('Carnes y embutidos', 'Carne, pollo, pescado y embutidos'),
  ('Lácteos y quesos', 'Leche, queso, yogur y derivados'),
  ('Panadería y pastelería', 'Pan, tortas y repostería'),
  ('Dulces y golosinas', 'Caramelos, chocolates y snacks'),
  ('Abarrotes', 'Arroz, fideo, azúcar, aceite y productos de despensa'),
  ('Productos naturales', 'Hierbas medicinales, miel y productos orgánicos'),
  ('Ropa de mujer', 'Blusas, vestidos, pantalones y más'),
  ('Ropa de hombre', 'Camisas, pantalones, chamarras y más'),
  ('Ropa infantil', 'Ropa para niños y bebés'),
  ('Ropa deportiva', 'Buzos, shorts, poleras y camisetas deportivas'),
  ('Ropa interior y medias', 'Ropa interior, medias y pijamas'),
  ('Calzados', 'Zapatos, zapatillas, sandalias y botas'),
  ('Carteras y mochilas', 'Carteras, mochilas, maletas y billeteras'),
  ('Accesorios y bisutería', 'Collares, pulseras, aretes y accesorios'),
  ('Joyería y relojes', 'Joyas, relojes y piezas de plata'),
  ('Gorras y sombreros', 'Gorras, sombreros, gorros y bufandas'),
  ('Maquillajes', 'Cosméticos y maquillaje'),
  ('Perfumería', 'Perfumes, colonias y desodorantes'),
  ('Cuidado del cabello', 'Shampoo, tintes y accesorios para el cabello'),
  ('Cuidado de la piel', 'Cremas, jabones y cuidado personal'),
  ('Artículos de limpieza', 'Detergentes, escobas y productos de limpieza'),
  ('Celulares', 'Venta de teléfonos celulares'),
  ('Accesorios para celulares', 'Fundas, cargadores, audífonos y vidrios templados'),
  ('Computadoras y laptops', 'Equipos de cómputo y periféricos'),
  ('Audio y parlantes', 'Parlantes, audífonos y equipos de sonido'),
  ('Televisores y video', 'Televisores, cámaras y proyectores'),
  ('Videojuegos y consolas', 'Consolas, juegos y accesorios'),
  ('Electrodomésticos', 'Licuadoras, cocinas, refrigeradores y más'),
  ('Iluminación y electricidad', 'Focos, cables, tomacorrientes y material eléctrico'),
  ('Herramientas de construcción', 'Taladros, martillos, palas y herramientas de obra'),
  ('Herramientas para auto', 'Llaves, gatos y herramientas para automóviles'),
  ('Herramientas para moto', 'Herramientas y equipo para motocicletas'),
  ('Herramientas para bicicleta', 'Herramientas y equipo para bicicletas'),
  ('Ferretería', 'Clavos, tornillos, candados y artículos de ferretería'),
  ('Materiales de construcción', 'Cemento, ladrillos, fierro y materiales de obra'),
  ('Pinturas y acabados', 'Pinturas, brochas y acabados'),
  ('Plomería y sanitarios', 'Grifería, tubos y artículos sanitarios'),
  ('Venta de autos', 'Automóviles nuevos y usados'),
  ('Venta de motos', 'Motocicletas nuevas y usadas'),
  ('Venta de bicicletas', 'Bicicletas nuevas y usadas'),
  ('Repuestos de auto', 'Repuestos y autopartes'),
  ('Repuestos de moto', 'Repuestos y accesorios para motocicletas'),
  ('Llantas y neumáticos', 'Llantas para autos, motos y bicicletas'),
  ('Aceites y lubricantes', 'Aceites, grasas y aditivos'),
  ('Accesorios para vehículos', 'Fundas, alfombras, radios y accesorios'),
  ('Muebles', 'Mesas, sillas, roperos y camas'),
  ('Colchones y ropa de cama', 'Colchones, frazadas, sábanas y cobertores'),
  ('Decoración del hogar', 'Cuadros, cortinas, alfombras y adornos'),
  ('Utensilios de cocina', 'Ollas, sartenes, cubiertos y utensilios'),
  ('Vajilla y cristalería', 'Platos, vasos, jarras y vajilla'),
  ('Plásticos y envases', 'Baldes, tinas, tapers y envases'),
  ('Juguetes', 'Juguetes y juegos para niños'),
  ('Útiles escolares', 'Cuadernos, mochilas escolares y material escolar'),
  ('Librería y papelería', 'Libros, papel, impresiones y artículos de oficina'),
  ('Instrumentos musicales', 'Guitarras, charangos, zampoñas y más'),
  ('Artículos deportivos', 'Pelotas, guantes y equipo deportivo'),
  ('Camping y pesca', 'Carpas, bolsas de dormir y artículos de pesca'),
  ('Mascotas y accesorios', 'Correas, jaulas, camas y accesorios para mascotas'),
  ('Alimento para animales', 'Alimento para perros, gatos, aves y ganado'),
  ('Plantas y jardinería', 'Plantas, macetas, tierra y herramientas de jardín'),
  ('Artesanías', 'Tejidos, cerámica y artesanía tradicional'),
  ('Souvenirs y regalos', 'Recuerdos, peluches y regalos'),
  ('Artículos religiosos', 'Imágenes, velas e inciensos'),
  ('Fiestas y cotillón', 'Globos, disfraces y artículos para fiestas'),
  ('Antigüedades y usados', 'Objetos usados y de colección'),
  ('Telas y mercería', 'Telas, hilos, botones y artículos de costura'),
  ('Lentes y ópticas', 'Lentes de sol, marcos y accesorios ópticos'),
  ('Farmacia y salud', 'Medicamentos de venta libre y artículos de salud'),
  ('Reparación de celulares', 'Servicio técnico y reparación de celulares')
) AS n(nombre, descripcion)
WHERE NOT EXISTS (
  SELECT 1 FROM categoria c
  WHERE lower(trim(c.nombre_categoria)) = lower(trim(n.nombre))
);

-- Por seguridad, deja el contador de ids al día.
SELECT setval(pg_get_serial_sequence('categoria', 'id_categoria'),
              (SELECT COALESCE(MAX(id_categoria), 1) FROM categoria));

-- Revisa el resultado:
SELECT COUNT(*) AS total_categorias FROM categoria;
SELECT id_categoria, nombre_categoria FROM categoria ORDER BY nombre_categoria;
