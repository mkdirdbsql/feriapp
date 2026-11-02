const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const pool = require('../config/db');

const LARGO_MINIMO_CLAVE = 6;
const FORMATO_TELEFONO = /^[0-9]{7,15}$/;

// POST /api/comerciantes/registro
// Body: { nombre, apellido, telefono, contrasena, nombre_comercial, descripcion_comercio }
// Crea SOLO la cuenta (usuario + comerciante) en una transacción. El puesto
// se registra después con POST /api/puestos.
// Devuelve el token para que el comerciante quede con la sesión iniciada
// (dura 365 días: se registra una sola vez).
async function registrarComerciante(req, res) {
  const { nombre, apellido, telefono, contrasena, nombre_comercial, descripcion_comercio } = req.body;

  if (!nombre || !apellido || !telefono || !contrasena || !nombre_comercial) {
    return res.status(400).json({ error: 'Faltan datos obligatorios (nombre, apellido, telefono, contrasena, nombre_comercial)' });
  }

  const telefonoLimpio = String(telefono).trim();
  if (!FORMATO_TELEFONO.test(telefonoLimpio)) {
    return res.status(400).json({ error: 'El teléfono debe tener solo números (entre 7 y 15 dígitos)' });
  }
  if (String(contrasena).length < LARGO_MINIMO_CLAVE) {
    return res.status(400).json({ error: `La contraseña debe tener al menos ${LARGO_MINIMO_CLAVE} caracteres` });
  }

  let client;
  try {
    const hash = await bcrypt.hash(String(contrasena), 10);

    client = await pool.connect();
    await client.query('BEGIN');

    const usuarioResult = await client.query(
      `INSERT INTO usuario (nombre, apellido, telefono, contrasena, rol)
       VALUES ($1, $2, $3, $4, 'comerciante') RETURNING id_usuario`,
      [String(nombre).trim(), String(apellido).trim(), telefonoLimpio, hash]
    );
    const id_usuario = usuarioResult.rows[0].id_usuario;

    const comercianteResult = await client.query(
      `INSERT INTO comerciante (id_usuario, nombre_comercial, descripcion)
       VALUES ($1, $2, $3) RETURNING id_comerciante`,
      [id_usuario, String(nombre_comercial).trim(), descripcion_comercio || null]
    );
    const id_comerciante = comercianteResult.rows[0].id_comerciante;

    await client.query('COMMIT');

    const token = jwt.sign(
      { id_usuario, rol: 'comerciante' },
      process.env.JWT_SECRETO,
      { expiresIn: '365d' }
    );

    res.status(201).json({
      token,
      rol: 'comerciante',
      id_usuario,
      id_comerciante,
      nombre: String(nombre).trim()
    });
  } catch (error) {
    if (client) {
      await client.query('ROLLBACK');
    }
    console.error('Error al registrar comerciante:', error);
    if (error.code === '23505') {
      return res.status(409).json({ error: 'Ese teléfono ya está registrado' });
    }
    res.status(500).json({ error: 'Error al registrar comerciante' });
  } finally {
    if (client) {
      client.release();
    }
  }
}

// GET /api/comerciantes/yo   (rol comerciante)
// Devuelve el perfil, su puesto (o null si todavía no lo registró) y sus
// productos. La app decide qué pantalla mostrar según puesto sea null o no.
async function obtenerPerfil(req, res) {
  try {
    const perfilResult = await pool.query(
      `SELECT
         c.id_comerciante,
         u.id_usuario,
         u.nombre,
         u.apellido,
         u.telefono,
         c.nombre_comercial,
         c.descripcion,
         c.estado
       FROM comerciante c
       JOIN usuario u ON u.id_usuario = c.id_usuario
       WHERE c.id_comerciante = $1`,
      [req.usuario.id_comerciante]
    );
    if (perfilResult.rows.length === 0) {
      return res.status(404).json({ error: 'Comerciante no encontrado' });
    }

    const puestoResult = await pool.query(
      `SELECT
         p.id_puesto,
         p.nombre_puesto,
         p.descripcion,
         p.estado,
         ST_X(p.ubicacion) AS longitud,
         ST_Y(p.ubicacion) AS latitud,
         (SELECT ip.ruta_imagen FROM imagen_puesto ip WHERE ip.id_puesto = p.id_puesto) AS imagen
       FROM puesto p
       WHERE p.id_comerciante = $1`,
      [req.usuario.id_comerciante]
    );
    const puesto = puestoResult.rows[0] || null;

    let productos = [];
    if (puesto) {
      const productosResult = await pool.query(
        `SELECT
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
         WHERE pp.id_puesto = $1
         ORDER BY pr.id_producto`,
        [puesto.id_puesto]
      );
      productos = productosResult.rows;
    }

    res.json({ ...perfilResult.rows[0], puesto, productos });
  } catch (error) {
    console.error('Error al obtener el perfil del comerciante:', error);
    res.status(500).json({ error: 'Error al obtener el perfil' });
  }
}

// Devuelve null si el campo no vino, o el texto sin espacios sobrantes.
// Si vino pero está vacío, devuelve '' (el llamador lo rechaza).
function textoOpcional(valor) {
  if (valor === undefined || valor === null) {
    return null;
  }
  return String(valor).trim();
}

// PUT /api/comerciantes/yo   (rol comerciante)
// Body: cualquier combinación de { nombre, apellido, telefono, contrasena,
// nombre_comercial, descripcion }
async function actualizarPerfil(req, res) {
  const nombre = textoOpcional(req.body.nombre);
  const apellido = textoOpcional(req.body.apellido);
  const telefono = textoOpcional(req.body.telefono);
  const nombreComercial = textoOpcional(req.body.nombre_comercial);
  const descripcion = textoOpcional(req.body.descripcion);
  const contrasena = req.body.contrasena == null ? null : String(req.body.contrasena);

  if (nombre === '' || apellido === '' || nombreComercial === '') {
    return res.status(400).json({ error: 'Nombre, apellido y nombre comercial no pueden estar vacíos' });
  }
  if (telefono !== null && !FORMATO_TELEFONO.test(telefono)) {
    return res.status(400).json({ error: 'El teléfono debe tener solo números (entre 7 y 15 dígitos)' });
  }
  if (contrasena !== null && contrasena.length < LARGO_MINIMO_CLAVE) {
    return res.status(400).json({ error: `La contraseña debe tener al menos ${LARGO_MINIMO_CLAVE} caracteres` });
  }

  let client;
  try {
    const hash = contrasena === null ? null : await bcrypt.hash(contrasena, 10);

    client = await pool.connect();
    await client.query('BEGIN');

    await client.query(
      `UPDATE usuario SET
         nombre     = COALESCE($1, nombre),
         apellido   = COALESCE($2, apellido),
         telefono   = COALESCE($3, telefono),
         contrasena = COALESCE($4, contrasena)
       WHERE id_usuario = $5`,
      [nombre, apellido, telefono, hash, req.usuario.id_usuario]
    );

    await client.query(
      `UPDATE comerciante SET
         nombre_comercial = COALESCE($1, nombre_comercial),
         descripcion      = COALESCE($2, descripcion)
       WHERE id_comerciante = $3`,
      [nombreComercial, descripcion, req.usuario.id_comerciante]
    );

    await client.query('COMMIT');
    res.json({ mensaje: 'Perfil actualizado correctamente' });
  } catch (error) {
    if (client) {
      await client.query('ROLLBACK');
    }
    console.error('Error al actualizar el perfil:', error);
    if (error.code === '23505') {
      return res.status(409).json({ error: 'Ese teléfono ya está registrado' });
    }
    res.status(500).json({ error: 'Error al actualizar el perfil' });
  } finally {
    if (client) {
      client.release();
    }
  }
}

module.exports = { registrarComerciante, obtenerPerfil, actualizarPerfil };
