const pool = require('../config/db');

async function getCategorias(req, res) {
  try {
    const result = await pool.query(
      'SELECT id_categoria, nombre_categoria, descripcion FROM categoria ORDER BY nombre_categoria'
    );
    res.json(result.rows);
  } catch (error) {
    console.error('Error al obtener categorías:', error);
    res.status(500).json({ error: 'Error al obtener categorías' });
  }
}

module.exports = { getCategorias };
