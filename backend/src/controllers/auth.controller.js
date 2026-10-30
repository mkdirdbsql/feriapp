const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const pool = require('../config/db');

// POST /api/auth/login
// Body: { "identificador": "70011001", "contrasena": "clave123" }
//  - Comerciante: identificador = su teléfono.
//  - Administrador: identificador = su nombre_usuario.
// Respuesta: { token, rol, id_usuario, id_comerciante, nombre }
// El token del comerciante dura 365 días (se registra una sola vez);
// el del administrador dura 7 días.
async function login(req, res) {
  const { identificador, contrasena } = req.body;

  if (!identificador || !contrasena) {
    return res.status(400).json({ error: 'Faltan identificador o contrasena' });
  }

  try {
    const buscado = String(identificador).trim();

    let resultado = await pool.query(
      `SELECT id_usuario, nombre, rol, contrasena
       FROM usuario
       WHERE telefono = $1 AND rol = 'comerciante'`,
      [buscado]
    );

    if (resultado.rows.length === 0) {
      resultado = await pool.query(
        `SELECT id_usuario, nombre, rol, contrasena
         FROM usuario
         WHERE nombre_usuario = $1 AND rol = 'administrador'`,
        [buscado]
      );
    }

    const usuario = resultado.rows[0];
    const claveCorrecta = usuario
      ? await bcrypt.compare(String(contrasena), usuario.contrasena)
      : false;

    // Mismo mensaje en ambos casos para no revelar si la cuenta existe.
    if (!claveCorrecta) {
      return res.status(401).json({ error: 'Usuario o contraseña incorrectos' });
    }

    let id_comerciante = null;
    if (usuario.rol === 'comerciante') {
      const comercianteResult = await pool.query(
        'SELECT id_comerciante, estado FROM comerciante WHERE id_usuario = $1',
        [usuario.id_usuario]
      );
      const comerciante = comercianteResult.rows[0];
      if (!comerciante) {
        return res.status(401).json({ error: 'Usuario o contraseña incorrectos' });
      }
      if (comerciante.estado === 'inactivo') {
        return res.status(403).json({ error: 'Cuenta desactivada' });
      }
      id_comerciante = comerciante.id_comerciante;
    }

    const duracion = usuario.rol === 'comerciante' ? '365d' : '7d';
    const token = jwt.sign(
      { id_usuario: usuario.id_usuario, rol: usuario.rol },
      process.env.JWT_SECRETO,
      { expiresIn: duracion }
    );

    res.json({
      token,
      rol: usuario.rol,
      id_usuario: usuario.id_usuario,
      id_comerciante,
      nombre: usuario.nombre
    });
  } catch (error) {
    console.error('Error en login:', error);
    res.status(500).json({ error: 'Error al iniciar sesión' });
  }
}

module.exports = { login };
