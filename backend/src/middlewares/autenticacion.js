const jwt = require('jsonwebtoken');
const pool = require('../config/db');

// Si falta el secreto, el servidor no debe arrancar con tokens inseguros.
if (!process.env.JWT_SECRETO) {
  throw new Error('Falta la variable JWT_SECRETO en el archivo .env');
}

// Verifica el token del encabezado Authorization: Bearer <token>.
// Consulta la base de datos en cada petición para que un comerciante
// desactivado o eliminado pierda el acceso de inmediato, aunque su
// token (que dura 365 días) todavía no haya vencido.
// Deja en req.usuario: { id_usuario, rol, id_comerciante }
async function verificarToken(req, res, next) {
  const encabezado = req.headers.authorization || '';
  const [tipo, token] = encabezado.split(' ');

  if (tipo !== 'Bearer' || !token) {
    return res.status(401).json({ error: 'Debes iniciar sesión' });
  }

  let datos;
  try {
    datos = jwt.verify(token, process.env.JWT_SECRETO);
  } catch (error) {
    return res.status(401).json({ error: 'Sesión no válida o vencida' });
  }

  try {
    const resultado = await pool.query(
      `SELECT u.id_usuario, u.rol, c.id_comerciante, c.estado AS estado_comerciante
       FROM usuario u
       LEFT JOIN comerciante c ON c.id_usuario = u.id_usuario
       WHERE u.id_usuario = $1`,
      [datos.id_usuario]
    );

    if (resultado.rows.length === 0) {
      return res.status(401).json({ error: 'Sesión no válida' });
    }

    const usuario = resultado.rows[0];

    if (usuario.rol === 'comerciante' && usuario.estado_comerciante === 'inactivo') {
      return res.status(403).json({ error: 'Cuenta desactivada' });
    }

    req.usuario = {
      id_usuario: usuario.id_usuario,
      rol: usuario.rol,
      id_comerciante: usuario.id_comerciante || null
    };
    next();
  } catch (error) {
    console.error('Error al verificar la sesión:', error);
    res.status(500).json({ error: 'Error al verificar la sesión' });
  }
}

// Uso: permitirRoles('comerciante'), permitirRoles('administrador')
// Debe ir DESPUÉS de verificarToken.
function permitirRoles(...roles) {
  return (req, res, next) => {
    if (!req.usuario || !roles.includes(req.usuario.rol)) {
      return res.status(403).json({ error: 'No tienes permiso para realizar esta acción' });
    }
    next();
  };
}

module.exports = { verificarToken, permitirRoles };
