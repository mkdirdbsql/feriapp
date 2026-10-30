const pool = require('../config/db');

// Regla de negocio RN-01: un comerciante solo gestiona sus productos.
// La tabla producto no tiene id_comerciante: la pertenencia se deduce
// con producto_puesto -> puesto -> comerciante.
// Debe ir DESPUÉS de verificarToken y permitirRoles('comerciante').
async function verificarDuenoProducto(req, res, next) {
  const idProducto = Number.parseInt(req.params.id, 10);
  if (!Number.isInteger(idProducto)) {
    return res.status(400).json({ error: 'Identificador de producto no válido' });
  }

  try {
    const existe = await pool.query(
      'SELECT 1 FROM producto WHERE id_producto = $1',
      [idProducto]
    );
    if (existe.rows.length === 0) {
      return res.status(404).json({ error: 'Producto no encontrado' });
    }

    const dueno = await pool.query(
      `SELECT 1
       FROM producto_puesto pp
       JOIN puesto pu ON pu.id_puesto = pp.id_puesto
       WHERE pp.id_producto = $1 AND pu.id_comerciante = $2`,
      [idProducto, req.usuario.id_comerciante]
    );
    if (dueno.rows.length === 0) {
      return res.status(403).json({ error: 'Este producto pertenece a otro comerciante' });
    }

    next();
  } catch (error) {
    console.error('Error al verificar el dueño del producto:', error);
    res.status(500).json({ error: 'Error al verificar el producto' });
  }
}

module.exports = { verificarDuenoProducto };
