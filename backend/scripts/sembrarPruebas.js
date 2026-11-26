// Crea comerciantes de PRUEBA con su puesto, imagen y (algunos) productos.
//
// Uso (desde la carpeta backend):
//   node --env-file=.env scripts/sembrarPruebas.js            crea 50 usuarios
//   node --env-file=.env scripts/sembrarPruebas.js --limpiar  borra los de prueba
//
// Antes de crearlos, copia UNA imagen de prueba en  backend/public/images/
// con el nombre  imagen_prueba.jpg  (o .png / .webp). Todos los puestos y
// productos de prueba usan esa misma imagen (ruta /images/...). Como no está
// en /uploads, la app nunca la borra del disco al eliminar un usuario.
//
// Los usuarios entran con teléfono 7100001 ... 7100050 (o nombre de usuario
// prueba01 ... prueba50) y la contraseña  Prueba123

const fs = require('fs');
const path = require('path');
const bcrypt = require('bcryptjs');
const { estaDentro, LATITUD_MINIMA, LATITUD_MAXIMA, LONGITUD_MINIMA, LONGITUD_MAXIMA } =
  require('../src/config/limitesFeria');

const CANTIDAD = 50;
const CLAVE = 'Prueba123';
const PREFIJO_USUARIO = 'prueba';
const PREFIJO_TELEFONO = 7100000;
const CARPETA_IMAGENES = path.resolve(__dirname, '..', 'public', 'images');

const NOMBRES = ['Juan', 'María', 'Carlos', 'Ana', 'Luis', 'Rosa', 'Pedro', 'Lucía', 'Jorge', 'Elena',
  'Miguel', 'Carmen', 'Diego', 'Sofía', 'Raúl', 'Paola', 'Víctor', 'Gabriela', 'Hugo', 'Daniela',
  'Marco', 'Patricia', 'Óscar', 'Verónica', 'Rodrigo'];
const APELLIDOS = ['Mamani', 'Quispe', 'Condori', 'Flores', 'Choque', 'Huanca', 'Ramos', 'Vargas',
  'Torrez', 'Apaza', 'Gutiérrez', 'Rojas', 'Mendoza', 'Copa', 'Limachi', 'Callisaya', 'Villca',
  'Poma', 'Colque', 'Nina'];

// Productos propios de los primeros 15 usuarios (el resto usa CATALOGO).
// Formato: [categoría, producto, precio, descripción]
const PRODUCTOS = [
  [['Comida', 'Salchipapa grande', 15, 'Papa frita con salchicha y salsas'], ['Bebidas y refrescos', 'Mocochinchi vaso', 5, 'Refresco tradicional bien frío']],
  [['Comida', 'Plato paceño', 25, 'Chuño, papa, habas y queso'], ['Comida', 'Sopa de maní', 18, 'Sopa casera con papas fritas']],
  [['Comida', 'Hamburguesa doble', 20, 'Con papas y ensalada'], ['Bebidas y refrescos', 'Gaseosa 2 litros', 12, 'Varios sabores']],
  [['Comida', 'Pollo broaster', 22, 'Presa con arroz y papas']],
  [['Calzados', 'Zapatillas deportivas', 180, 'Varias tallas'], ['Calzados', 'Sandalias de cuero', 90, 'Hechas a mano']],
  [['Calzados', 'Botines de vestir', 220, 'Cuero, tallas 38 al 44']],
  [['Calzados', 'Zapatos escolares', 130, 'Resistentes, tallas 28 al 38'], ['Ropa infantil', 'Uniforme escolar', 95, 'Pantalón y camisa']],
  [['Celulares', 'Celular 64 GB', 850, 'Pantalla 6.5 pulgadas'], ['Accesorios para celulares', 'Cargador rápido', 45, 'Con cable USB-C']],
  [['Celulares', 'Celular 128 GB', 1200, 'Cámara triple, garantía 3 meses']],
  [['Celulares', 'Celular básico', 250, 'Doble chip, batería larga'], ['Reparación de celulares', 'Cambio de pantalla', 150, 'Incluye mano de obra']],
  [['Maquillajes', 'Set de sombras', 60, '18 colores'], ['Maquillajes', 'Labial mate', 25, 'Varios tonos']],
  [['Maquillajes', 'Base líquida', 55, 'Para todo tipo de piel'], ['Perfumería', 'Perfume importado', 120, '100 ml']],
  [['Herramientas para auto', 'Juego de llaves', 140, '24 piezas'], ['Repuestos de auto', 'Filtro de aceite', 35, 'Varias marcas']],
  [['Herramientas para moto', 'Kit de herramientas', 90, 'Llaves y destornilladores'], ['Repuestos de moto', 'Cadena de transmisión', 80, 'Reforzada']],
  [['Herramientas de construcción', 'Taladro eléctrico', 320, '750 W con maletín'], ['Ferretería', 'Candado grande', 40, 'Acero reforzado']],
];

// Catálogos de relleno: cada puesto sin productos propios recibe uno (2 o 3 productos,
// de 1 o 2 categorías distintas). Se reparten de forma rotativa entre los usuarios.
const CATALOGO = [
  [['Frutas y verduras', 'Manzanas por kilo', 12, 'Manzanas frescas del valle'], ['Frutas y verduras', 'Tomate por kilo', 8, 'Tomate maduro'], ['Abarrotes', 'Arroz 1 kg', 9, 'Arroz de grano largo']],
  [['Ropa de mujer', 'Blusa floral', 70, 'Tallas S a XL'], ['Ropa de mujer', 'Pantalón jean', 120, 'Corte alto'], ['Accesorios y bisutería', 'Pulsera de plata', 45, 'Diseño artesanal']],
  [['Ropa de hombre', 'Camisa casual', 85, 'Algodón, varias tallas'], ['Ropa de hombre', 'Chamarra de cuero', 260, 'Forrada por dentro']],
  [['Juguetes', 'Auto de control remoto', 95, 'Con batería recargable'], ['Juguetes', 'Muñeca articulada', 60, 'Con accesorios'], ['Útiles escolares', 'Mochila escolar', 110, 'Reforzada']],
  [['Panadería y pastelería', 'Torta de chocolate', 80, 'Para 10 porciones'], ['Dulces y golosinas', 'Bolsa de caramelos', 15, 'Surtidos']],
  [['Electrodomésticos', 'Licuadora 1.5 L', 210, 'Tres velocidades'], ['Utensilios de cocina', 'Juego de ollas', 250, 'Acero inoxidable'], ['Utensilios de cocina', 'Sartén antiadherente', 70, '28 cm']],
  [['Audio y parlantes', 'Parlante bluetooth', 150, 'Resistente al agua'], ['Accesorios para celulares', 'Audífonos inalámbricos', 90, 'Con estuche de carga']],
  [['Cuidado del cabello', 'Shampoo de sábila', 30, '500 ml'], ['Cuidado de la piel', 'Crema hidratante', 45, 'Para todo tipo de piel']],
  [['Artículos deportivos', 'Pelota de fútbol', 80, 'Tamaño oficial'], ['Ropa deportiva', 'Buzo deportivo', 140, 'Dos piezas']],
  [['Muebles', 'Silla de madera', 160, 'Resistente'], ['Decoración del hogar', 'Cuadro decorativo', 55, '40 x 60 cm'], ['Colchones y ropa de cama', 'Frazada de lana', 180, 'Doble plaza']],
  [['Plantas y jardinería', 'Maceta con planta', 35, 'Planta de interior'], ['Plantas y jardinería', 'Tierra preparada', 20, 'Bolsa de 5 kg']],
  [['Artesanías', 'Aguayo tejido', 120, 'Tejido a mano'], ['Souvenirs y regalos', 'Llavero de recuerdo', 10, 'Diseño paceño'], ['Souvenirs y regalos', 'Taza decorada', 25, 'Cerámica']],
  [['Librería y papelería', 'Cuaderno universitario', 12, '100 hojas'], ['Útiles escolares', 'Caja de colores', 18, '24 colores']],
  [['Lácteos y quesos', 'Queso criollo', 30, 'Por kilo'], ['Carnes y embutidos', 'Chorizo parrillero', 28, 'Paquete de 6']],
  [['Lentes y ópticas', 'Lentes de sol', 65, 'Protección UV'], ['Gorras y sombreros', 'Gorra deportiva', 40, 'Ajustable']],
  [['Llantas y neumáticos', 'Llanta para auto', 380, 'Rin 15'], ['Aceites y lubricantes', 'Aceite 20W50', 60, 'Botella de 1 litro']],
];

// Plan final de productos de cada usuario de prueba: mínimo 2, máximo 3 (límite del puesto).
function planDe(i) {
  const propio = PRODUCTOS[i - 1] || [];
  if (propio.length >= 2) {
    return propio;
  }
  const plan = [...propio];
  const relleno = CATALOGO[(i - 1) % CATALOGO.length];
  for (const item of relleno) {
    if (plan.length >= 3) {
      break;
    }
    const categoriasActuales = new Set(plan.map((p) => p[0]));
    const excedeCategorias = !categoriasActuales.has(item[0]) && categoriasActuales.size >= 2;
    if (!plan.some((p) => p[1] === item[1]) && !excedeCategorias) {
      plan.push(item);
    }
  }
  return plan;
}

// Generador pseudoaleatorio con semilla fija: siempre salen los mismos puntos.
function crearAleatorio(semilla) {
  let estado = semilla >>> 0;
  return () => {
    estado = (estado + 0x6d2b79f5) >>> 0;
    let t = estado;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function puntoDentroDeLaFeria(aleatorio) {
  for (let intento = 0; intento < 10000; intento++) {
    const lat = LATITUD_MINIMA + aleatorio() * (LATITUD_MAXIMA - LATITUD_MINIMA);
    const lon = LONGITUD_MINIMA + aleatorio() * (LONGITUD_MAXIMA - LONGITUD_MINIMA);
    // Solo se aceptan puntos que la propia validación del backend da por buenos.
    if (estaDentro(lat, lon)) {
      return { lat: Number(lat.toFixed(6)), lon: Number(lon.toFixed(6)) };
    }
  }
  throw new Error('No se pudo generar un punto dentro de la feria');
}

function buscarImagenDePrueba() {
  for (const extension of ['jpg', 'jpeg', 'png', 'webp']) {
    const nombre = `imagen_prueba.${extension}`;
    if (fs.existsSync(path.join(CARPETA_IMAGENES, nombre))) {
      return `/images/${nombre}`;
    }
  }
  return null;
}

async function limpiar(pool) {
  const cliente = await pool.connect();
  try {
    await cliente.query('BEGIN');
    const usuarios = await cliente.query(
      `SELECT id_usuario FROM usuario WHERE nombre_usuario LIKE $1`, [`${PREFIJO_USUARIO}%`]);
    const ids = usuarios.rows.map((f) => f.id_usuario);
    if (ids.length === 0) {
      await cliente.query('ROLLBACK');
      console.log('No hay usuarios de prueba que borrar.');
      return;
    }
    const productos = await cliente.query(
      `SELECT DISTINCT pp.id_producto FROM producto_puesto pp
         JOIN puesto p ON p.id_puesto = pp.id_puesto
         JOIN comerciante c ON c.id_comerciante = p.id_comerciante
        WHERE c.id_usuario = ANY($1)`, [ids]);
    const idsProductos = productos.rows.map((f) => f.id_producto);
    await cliente.query('DELETE FROM imagen_producto WHERE id_producto = ANY($1)', [idsProductos]);
    await cliente.query('DELETE FROM producto_puesto WHERE id_producto = ANY($1)', [idsProductos]);
    await cliente.query('DELETE FROM producto WHERE id_producto = ANY($1)', [idsProductos]);
    await cliente.query(
      `DELETE FROM imagen_puesto WHERE id_puesto IN (
         SELECT p.id_puesto FROM puesto p JOIN comerciante c ON c.id_comerciante = p.id_comerciante
          WHERE c.id_usuario = ANY($1))`, [ids]);
    await cliente.query(
      `DELETE FROM puesto WHERE id_comerciante IN (SELECT id_comerciante FROM comerciante WHERE id_usuario = ANY($1))`, [ids]);
    await cliente.query('DELETE FROM comerciante WHERE id_usuario = ANY($1)', [ids]);
    await cliente.query('DELETE FROM usuario WHERE id_usuario = ANY($1)', [ids]);
    await cliente.query('COMMIT');
    console.log(`Borrados ${ids.length} usuarios de prueba (con sus puestos y productos).`);
  } catch (error) {
    await cliente.query('ROLLBACK');
    throw error;
  } finally {
    cliente.release();
  }
}

// Agrega los productos planificados a un puesto. Devuelve cuántos agregó.
async function agregarProductos(cliente, idPuesto, plan, idPorNombre, rutaImagen, categoriasFaltantes) {
  let agregados = 0;
  for (const [categoria, nombreProducto, precio, descripcion] of plan) {
    const idCategoria = idPorNombre.get(categoria.toLowerCase());
    if (!idCategoria) {
      categoriasFaltantes.add(categoria);
      continue;
    }
    const producto = await cliente.query(
      `INSERT INTO producto (id_categoria, nombre_producto, descripcion, precio, disponible)
       VALUES ($1, $2, $3, $4, true) RETURNING id_producto`,
      [idCategoria, nombreProducto, descripcion, precio]);
    const idProducto = producto.rows[0].id_producto;
    await cliente.query(
      'INSERT INTO producto_puesto (id_producto, id_puesto) VALUES ($1, $2)', [idProducto, idPuesto]);
    await cliente.query(
      'INSERT INTO imagen_producto (id_producto, ruta_imagen) VALUES ($1, $2)', [idProducto, rutaImagen]);
    agregados++;
  }
  return agregados;
}

async function sembrar(pool) {
  const rutaImagen = buscarImagenDePrueba();
  if (!rutaImagen) {
    throw new Error(
      `Falta la imagen de prueba. Copia una imagen en ${CARPETA_IMAGENES} con el nombre imagen_prueba.jpg (o .png / .webp)`);
  }

  const columnas = await pool.query(
    `SELECT column_name FROM information_schema.columns WHERE table_name = 'comerciante'`);
  const nombres = columnas.rows.map((f) => f.column_name);
  const tieneNombreComercial = nombres.includes('nombre_comercial');

  const categorias = await pool.query('SELECT id_categoria, nombre_categoria FROM categoria');
  const idPorNombre = new Map(
    categorias.rows.map((f) => [f.nombre_categoria.trim().toLowerCase(), f.id_categoria]));

  const hash = await bcrypt.hash(CLAVE, 10);
  const aleatorio = crearAleatorio(16072026);
  const cliente = await pool.connect();
  let creados = 0;
  let conProductos = 0;
  let omitidos = 0;
  let completados = 0;
  const categoriasFaltantes = new Set();

  try {
    await cliente.query('BEGIN');
    for (let i = 1; i <= CANTIDAD; i++) {
      const numero = String(i).padStart(2, '0');
      const nombreUsuario = `${PREFIJO_USUARIO}${numero}`;
      const telefono = String(PREFIJO_TELEFONO + i);

      const existe = await cliente.query(
        'SELECT 1 FROM usuario WHERE nombre_usuario = $1 OR telefono = $2', [nombreUsuario, telefono]);
      const punto = puntoDentroDeLaFeria(aleatorio); // se consume siempre, para que los puntos sean estables
      if (existe.rows.length > 0) {
        omitidos++;
        // Si ya existía, se le completan los productos que le falten del plan.
        const plan = planDe(i);
        const puestoExistente = await cliente.query(
          `SELECT p.id_puesto FROM usuario u
             JOIN comerciante c ON c.id_usuario = u.id_usuario
             JOIN puesto p ON p.id_comerciante = c.id_comerciante
            WHERE u.nombre_usuario = $1`, [nombreUsuario]);
        const fila = puestoExistente.rows[0];
        if (fila) {
          const actuales = await cliente.query(
            `SELECT pr.nombre_producto FROM producto_puesto pp
               JOIN producto pr ON pr.id_producto = pp.id_producto
              WHERE pp.id_puesto = $1`, [fila.id_puesto]);
          const nombresActuales = new Set(actuales.rows.map((f) => f.nombre_producto));
          const faltantes = plan.filter((p) => !nombresActuales.has(p[1]));
          const cupo = Math.max(0, 3 - actuales.rows.length);
          if (actuales.rows.length < plan.length && faltantes.length > 0 && cupo > 0) {
            const agregados = await agregarProductos(
              cliente, fila.id_puesto, faltantes.slice(0, cupo), idPorNombre, rutaImagen, categoriasFaltantes);
            if (agregados > 0) {
              completados++;
            }
          }
        }
        continue;
      }

      const nombre = NOMBRES[(i - 1) % NOMBRES.length];
      const apellido = APELLIDOS[Math.floor((i - 1) / NOMBRES.length) % APELLIDOS.length]
        + (i > NOMBRES.length * APELLIDOS.length ? ` ${i}` : '');

      const usuario = await cliente.query(
        `INSERT INTO usuario (nombre, apellido, telefono, contrasena, rol, nombre_usuario)
         VALUES ($1, $2, $3, $4, 'comerciante', $5) RETURNING id_usuario`,
        [nombre, apellido, telefono, hash, nombreUsuario]);
      const idUsuario = usuario.rows[0].id_usuario;

      const comerciante = tieneNombreComercial
        ? await cliente.query(
          `INSERT INTO comerciante (id_usuario, nombre_comercial) VALUES ($1, $2) RETURNING id_comerciante`,
          [idUsuario, `Comercio de prueba ${numero}`])
        : await cliente.query(
          `INSERT INTO comerciante (id_usuario) VALUES ($1) RETURNING id_comerciante`, [idUsuario]);
      const idComerciante = comerciante.rows[0].id_comerciante;

      const puesto = await cliente.query(
        `INSERT INTO puesto (id_comerciante, nombre_puesto, descripcion, ubicacion)
         VALUES ($1, $2, $3, ST_SetSRID(ST_MakePoint($4, $5), 4326)) RETURNING id_puesto`,
        [idComerciante, `Puesto de prueba ${numero}`,
          `Puesto de prueba número ${numero} de la feria`, punto.lon, punto.lat]);
      const idPuesto = puesto.rows[0].id_puesto;

      await cliente.query(
        'INSERT INTO imagen_puesto (id_puesto, ruta_imagen) VALUES ($1, $2)', [idPuesto, rutaImagen]);

      const productos = planDe(i);
      const agregoAlguno = (await agregarProductos(
        cliente, idPuesto, productos, idPorNombre, rutaImagen, categoriasFaltantes)) > 0;
      if (agregoAlguno) {
        conProductos++;
      }
      creados++;
    }
    await cliente.query('COMMIT');
  } catch (error) {
    await cliente.query('ROLLBACK');
    throw error;
  } finally {
    cliente.release();
  }

  console.log(`Usuarios de prueba creados: ${creados} (con productos: ${conProductos}). Ya existían: ${omitidos} (a ${completados} de ellos se les completaron los productos).`);
  console.log(`Imagen usada: ${rutaImagen}`);
  console.log(`Ingreso: teléfono 7100001 ... 71000${CANTIDAD} (o usuario ${PREFIJO_USUARIO}01 ...), contraseña ${CLAVE}`);
  if (categoriasFaltantes.size > 0) {
    console.log('AVISO: estas categorías no existen y sus productos se omitieron (ejecuta primero 01_categorias.sql):');
    console.log([...categoriasFaltantes].join(', '));
  }
}

async function principal() {
  const pool = require('../src/config/db');
  try {
    if (process.argv.includes('--limpiar')) {
      await limpiar(pool);
    } else {
      await sembrar(pool);
    }
  } finally {
    await pool.end();
  }
}

module.exports = { sembrar, limpiar, puntoDentroDeLaFeria, crearAleatorio };

if (require.main === module) {
  principal().catch((error) => {
    console.error('Error:', error.message);
    process.exit(1);
  });
}
