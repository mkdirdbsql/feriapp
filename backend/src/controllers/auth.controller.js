const pool = require('../config/db');

// POST /api/auth/login
// Body esperado: { "telefono": "70011001", "contrasena": "clave123" }
async function login(req, res) {
  const { telefono, contrasena } = req.body;

  if (!telefono || !contrasena) {
    return res.status(400).json({ error: 'Faltan telefono o contrasena' });
  }

  try {
    const result = await pool.query(
      'SELECT id_usuario, nombre, apellido, rol FROM usuario WHERE telefono = $1 AND contrasena = $2',
      [telefono, contrasena]
    );

    if (result.rows.length === 0) {
      return res.status(401).json({ error: 'Teléfono o contraseña incorrectos' });
    }

    const usuario = result.rows[0];

    // Si es comerciante, le adjuntamos su id_comerciante (útil para
    // que la app sepa de inmediato a qué puesto pertenece)
    if (usuario.rol === 'comerciante') {
      const comercianteResult = await pool.query(
        'SELECT id_comerciante FROM comerciante WHERE id_usuario = $1',
        [usuario.id_usuario]
      );
      usuario.id_comerciante = comercianteResult.rows[0]?.id_comerciante || null;
    }

    res.json(usuario);
  } catch (error) {
    console.error('Error en login:', error);
    res.status(500).json({ error: 'Error al iniciar sesión' });
  }
}

module.exports = { login };
