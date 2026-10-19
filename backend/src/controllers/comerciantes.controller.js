const pool = require('../config/db');

// POST /api/comerciantes/registro
// Crea en una sola operación: usuario (rol comerciante) + comerciante + puesto.
// Si algo falla a mitad de camino, no queda nada a medias (transacción).
async function registrarComerciante(req, res) {
  const {
    nombre, apellido, telefono, contrasena,
    nombre_comercial, descripcion_comercio,
    nombre_puesto, descripcion_puesto,
    latitud, longitud
  } = req.body;

  if (!nombre || !apellido || !telefono || !contrasena ||
      !nombre_comercial || !nombre_puesto || latitud == null || longitud == null) {
    return res.status(400).json({ error: 'Faltan datos obligatorios' });
  }

  const client = await pool.connect();
  try {
    await client.query('BEGIN');

    const usuarioResult = await client.query(
      `INSERT INTO usuario (nombre, apellido, telefono, contrasena, rol)
       VALUES ($1, $2, $3, $4, 'comerciante') RETURNING id_usuario`,
      [nombre, apellido, telefono, contrasena]
    );
    const id_usuario = usuarioResult.rows[0].id_usuario;

    const comercianteResult = await client.query(
      `INSERT INTO comerciante (id_usuario, nombre_comercial, descripcion)
       VALUES ($1, $2, $3) RETURNING id_comerciante`,
      [id_usuario, nombre_comercial, descripcion_comercio || null]
    );
    const id_comerciante = comercianteResult.rows[0].id_comerciante;

    const puestoResult = await client.query(
      `INSERT INTO puesto (id_comerciante, nombre_puesto, descripcion, ubicacion)
       VALUES ($1, $2, $3, ST_SetSRID(ST_MakePoint($4, $5), 4326))
       RETURNING id_puesto`,
      [id_comerciante, nombre_puesto, descripcion_puesto || null, longitud, latitud]
    );

    await client.query('COMMIT');

    res.status(201).json({
      mensaje: 'Comerciante registrado correctamente',
      id_usuario,
      id_comerciante,
      id_puesto: puestoResult.rows[0].id_puesto
    });
  } catch (error) {
    await client.query('ROLLBACK');
    console.error('Error al registrar comerciante:', error);
    if (error.code === '23505') {
      return res.status(409).json({ error: 'Ese teléfono ya está registrado' });
    }
    res.status(500).json({ error: 'Error al registrar comerciante' });
  } finally {
    client.release();
  }
}

module.exports = { registrarComerciante };
