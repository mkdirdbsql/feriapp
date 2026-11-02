const pool = require('../config/db');

const LARGO_MAXIMO_TERMINO = 120;

// POST /api/busquedas   (público: el consumidor no tiene cuenta)
// Body: { termino, id_categoria (opcional), id_producto (opcional) }
// Registra la búsqueda para los reportes del administrador
// (por ejemplo, la categoría más buscada).
async function registrarBusqueda(req, res) {
  const { termino, id_categoria, id_producto } = req.body;

  const terminoLimpio = termino == null ? '' : String(termino).trim().slice(0, LARGO_MAXIMO_TERMINO);
  if (!terminoLimpio) {
    return res.status(400).json({ error: 'Falta el término de búsqueda' });
  }

  const idCategoria = id_categoria == null || id_categoria === '' ? null : Number.parseInt(id_categoria, 10);
  const idProducto = id_producto == null || id_producto === '' ? null : Number.parseInt(id_producto, 10);

  if ((idCategoria !== null && !Number.isInteger(idCategoria)) ||
      (idProducto !== null && !Number.isInteger(idProducto))) {
    return res.status(400).json({ error: 'id_categoria o id_producto no válido' });
  }

  try {
    await pool.query(
      `INSERT INTO busquedas (id_categoria, id_producto, termino_busqueda)
       VALUES ($1, $2, $3)`,
      [idCategoria, idProducto, terminoLimpio]
    );
    res.status(201).json({ mensaje: 'Búsqueda registrada' });
  } catch (error) {
    console.error('Error al registrar la búsqueda:', error);
    if (error.code === '23503') {
      return res.status(400).json({ error: 'La categoría o el producto indicado no existe' });
    }
    res.status(500).json({ error: 'Error al registrar la búsqueda' });
  }
}

module.exports = { registrarBusqueda };
