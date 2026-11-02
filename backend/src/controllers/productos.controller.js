const pool = require('../config/db');
const { borrarArchivoSubido } = require('../config/archivos');

// Escapa los comodines de LIKE para que buscar "50%" no coincida con todo.
function escaparComodines(texto) {
  return texto.replace(/[\\%_]/g, '\\$&');
}

// GET /api/productos   (público, para la búsqueda del consumidor)
// Filtros opcionales:
//   ?nombre=man          coincidencia parcial, sin distinguir mayúsculas
//   ?id_categoria=1      productos de una categoría
// Solo devuelve productos que están en un puesto activo de un comerciante
// activo (un producto sin puesto no se puede ubicar en el mapa). Cada fila
// trae lo que necesita la pantalla de resultados: nombre del puesto,
// ubicación e imágenes (rutas relativas; la app antepone la URL base).
// Ordenado por precio para facilitar la comparación.
async function getProductos(req, res) {
  const { nombre, id_categoria } = req.query;
  const condiciones = ["pu.estado = 'activo'", "c.estado = 'activo'"];
  const valores = [];

  if (nombre && String(nombre).trim()) {
    valores.push(`%${escaparComodines(String(nombre).trim())}%`);
    condiciones.push(`pr.nombre_producto ILIKE $${valores.length}`);
  }

  if (id_categoria !== undefined && id_categoria !== '') {
    const idCategoria = Number.parseInt(id_categoria, 10);
    if (!Number.isInteger(idCategoria)) {
      return res.status(400).json({ error: 'id_categoria no válido' });
    }
    valores.push(idCategoria);
    condiciones.push(`pr.id_categoria = $${valores.length}`);
  }

  try {
    const result = await pool.query(
      `
      SELECT
        pr.id_producto,
        pr.nombre_producto,
        pr.descripcion,
        pr.precio,
        pr.disponible,
        cat.id_categoria,
        cat.nombre_categoria,
        pu.id_puesto,
        pu.nombre_puesto,
        c.nombre_comercial,
        ST_X(pu.ubicacion) AS longitud,
        ST_Y(pu.ubicacion) AS latitud,
        (SELECT im.ruta_imagen FROM imagen_producto im
          WHERE im.id_producto = pr.id_producto
          ORDER BY im.id_imagen LIMIT 1) AS imagen_producto,
        (SELECT ip.ruta_imagen FROM imagen_puesto ip
          WHERE ip.id_puesto = pu.id_puesto) AS imagen_puesto
      FROM producto pr
      JOIN categoria cat ON cat.id_categoria = pr.id_categoria
      JOIN producto_puesto pp ON pp.id_producto = pr.id_producto
      JOIN puesto pu ON pu.id_puesto = pp.id_puesto
      JOIN comerciante c ON c.id_comerciante = pu.id_comerciante
      WHERE ${condiciones.join(' AND ')}
      ORDER BY pr.precio, pr.nombre_producto
      `,
      valores
    );
    res.json(result.rows);
  } catch (error) {
    console.error('Error al obtener productos:', error);
    res.status(500).json({ error: 'Error al obtener productos' });
  }
}

// Productos ofrecidos por un puesto específico (público).
// Uso: GET /api/productos/puesto/:id_puesto
async function getProductosPorPuesto(req, res) {
  const idPuesto = Number.parseInt(req.params.id_puesto, 10);
  if (!Number.isInteger(idPuesto)) {
    return res.status(400).json({ error: 'Identificador de puesto no válido' });
  }

  try {
    const result = await pool.query(
      `
      SELECT
        pr.id_producto,
        pr.nombre_producto,
        pr.descripcion,
        pr.precio,
        pr.disponible,
        cat.id_categoria,
        cat.nombre_categoria,
        COALESCE(
          (SELECT json_agg(
             json_build_object('id_imagen', im.id_imagen, 'ruta_imagen', im.ruta_imagen)
             ORDER BY im.id_imagen)
           FROM imagen_producto im
           WHERE im.id_producto = pr.id_producto),
          '[]'::json
        ) AS imagenes
      FROM producto_puesto pp
      JOIN producto pr ON pr.id_producto = pp.id_producto
      JOIN categoria cat ON cat.id_categoria = pr.id_categoria
      JOIN puesto pu ON pu.id_puesto = pp.id_puesto
      JOIN comerciante c ON c.id_comerciante = pu.id_comerciante
      WHERE pp.id_puesto = $1 AND pu.estado = 'activo' AND c.estado = 'activo'
      ORDER BY pr.id_producto
      `,
      [idPuesto]
    );
    res.json(result.rows);
  } catch (error) {
    console.error('Error al obtener productos del puesto:', error);
    res.status(500).json({ error: 'Error al obtener productos del puesto' });
  }
}

// POST /api/productos   (rol comerciante, requiere token)
// Body: { id_categoria, nombre_producto, descripcion, precio, disponible }
// El puesto NO se envía: se toma del comerciante autenticado, así nadie
// puede agregar productos al puesto de otra persona.
// Crea el producto Y lo asocia a su puesto (producto_puesto).
// Si el puesto ya tiene 3 productos, el trigger de la BD lo bloquea.
async function crearProducto(req, res) {
  const { id_categoria, nombre_producto, descripcion, precio, disponible } = req.body;

  if (!id_categoria || !nombre_producto || precio == null) {
    return res.status(400).json({ error: 'Faltan datos obligatorios (id_categoria, nombre_producto, precio)' });
  }
  if (!(Number(precio) > 0)) {
    return res.status(400).json({ error: 'El precio debe ser mayor a 0' });
  }

  let client;
  try {
    const puestoResult = await pool.query(
      'SELECT id_puesto FROM puesto WHERE id_comerciante = $1',
      [req.usuario.id_comerciante]
    );
    if (puestoResult.rows.length === 0) {
      return res.status(409).json({ error: 'Primero debes registrar tu puesto' });
    }
    const id_puesto = puestoResult.rows[0].id_puesto;

    client = await pool.connect();
    await client.query('BEGIN');

    const productoResult = await client.query(
      `INSERT INTO producto (id_categoria, nombre_producto, descripcion, precio, disponible)
       VALUES ($1, $2, $3, $4, COALESCE($5, true))
       RETURNING id_producto`,
      [id_categoria, nombre_producto, descripcion || null, precio, disponible]
    );
    const id_producto = productoResult.rows[0].id_producto;

    await client.query(
      `INSERT INTO producto_puesto (id_producto, id_puesto) VALUES ($1, $2)`,
      [id_producto, id_puesto]
    );

    await client.query('COMMIT');
    res.status(201).json({ mensaje: 'Producto creado correctamente', id_producto, id_puesto });
  } catch (error) {
    if (client) {
      await client.query('ROLLBACK');
    }
    console.error('Error al crear producto:', error);
    if (error.message && error.message.includes('máximo de 3 productos')) {
      return res.status(409).json({ error: 'Tu puesto ya tiene el máximo de 3 productos. Elimina uno antes de agregar otro.' });
    }
    if (error.code === '23503') {
      return res.status(400).json({ error: 'La categoría indicada no existe' });
    }
    res.status(500).json({ error: 'Error al crear producto' });
  } finally {
    if (client) {
      client.release();
    }
  }
}

// PUT /api/productos/:id   (rol comerciante, solo el dueño)
// Body: cualquier combinación de { nombre_producto, descripcion, precio, disponible }
// Así el comerciante actualiza también la disponibilidad.
async function actualizarProducto(req, res) {
  const { id } = req.params;
  const { nombre_producto, descripcion, precio, disponible } = req.body;

  if (precio != null && !(Number(precio) > 0)) {
    return res.status(400).json({ error: 'El precio debe ser mayor a 0' });
  }

  try {
    const result = await pool.query(
      `UPDATE producto SET
        nombre_producto = COALESCE($1, nombre_producto),
        descripcion     = COALESCE($2, descripcion),
        precio          = COALESCE($3, precio),
        disponible      = COALESCE($4, disponible)
       WHERE id_producto = $5
       RETURNING id_producto`,
      [nombre_producto, descripcion, precio, disponible, id]
    );

    if (result.rows.length === 0) {
      return res.status(404).json({ error: 'Producto no encontrado' });
    }
    res.json({ mensaje: 'Producto actualizado correctamente' });
  } catch (error) {
    console.error('Error al actualizar producto:', error);
    res.status(500).json({ error: 'Error al actualizar producto' });
  }
}

// DELETE /api/productos/:id   (rol comerciante, solo el dueño)
// Borra el producto por completo (sus imágenes y su relación con el
// puesto se borran solas, por el ON DELETE CASCADE; los archivos de imagen
// se borran del disco aquí).
async function eliminarProducto(req, res) {
  const { id } = req.params;
  try {
    // Se leen las rutas antes de borrar: después las filas ya no existen.
    const imagenes = await pool.query(
      'SELECT ruta_imagen FROM imagen_producto WHERE id_producto = $1',
      [id]
    );
    const result = await pool.query(
      'DELETE FROM producto WHERE id_producto = $1 RETURNING id_producto',
      [id]
    );
    if (result.rows.length === 0) {
      return res.status(404).json({ error: 'Producto no encontrado' });
    }
    for (const fila of imagenes.rows) {
      await borrarArchivoSubido(fila.ruta_imagen);
    }
    res.json({ mensaje: 'Producto eliminado correctamente' });
  } catch (error) {
    console.error('Error al eliminar producto:', error);
    res.status(500).json({ error: 'Error al eliminar producto' });
  }
}

module.exports = {
  getProductos,
  getProductosPorPuesto,
  crearProducto,
  actualizarProducto,
  eliminarProducto
};
