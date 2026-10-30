const pool = require('../config/db');

// Lista todos los productos con su categoría.
async function getProductos(req, res) {
  try {
    const result = await pool.query(`
      SELECT
        pr.id_producto,
        pr.nombre_producto,
        pr.descripcion,
        pr.precio,
        pr.disponible,
        cat.nombre_categoria
      FROM producto pr
      JOIN categoria cat ON cat.id_categoria = pr.id_categoria
      ORDER BY pr.id_producto
    `);
    res.json(result.rows);
  } catch (error) {
    console.error('Error al obtener productos:', error);
    res.status(500).json({ error: 'Error al obtener productos' });
  }
}

// Productos ofrecidos por un puesto específico.
// Uso: GET /api/productos/puesto/:id_puesto
async function getProductosPorPuesto(req, res) {
  const { id_puesto } = req.params;
  try {
    const result = await pool.query(
      `
      SELECT
        pr.id_producto,
        pr.nombre_producto,
        pr.precio,
        pr.disponible,
        cat.nombre_categoria
      FROM producto_puesto pp
      JOIN producto pr ON pr.id_producto = pp.id_producto
      JOIN categoria cat ON cat.id_categoria = pr.id_categoria
      WHERE pp.id_puesto = $1
      `,
      [id_puesto]
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
// puesto se borran solas, por el ON DELETE CASCADE).
async function eliminarProducto(req, res) {
  const { id } = req.params;
  try {
    const result = await pool.query(
      'DELETE FROM producto WHERE id_producto = $1 RETURNING id_producto',
      [id]
    );
    if (result.rows.length === 0) {
      return res.status(404).json({ error: 'Producto no encontrado' });
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
