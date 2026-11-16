// Uso (desde la carpeta backend):
//   node --env-file=.env scripts/cambiarClave.js IDENTIFICADOR NuevaClave
// IDENTIFICADOR es el teléfono (comerciante) o el nombre_usuario (administrador).
// Guarda el hash bcrypt de la nueva clave. No imprime la clave.
const bcrypt = require('bcryptjs');
const pool = require('../src/config/db');

const LARGO_MINIMO_CLAVE = 6;

async function cambiarClave() {
  const [identificador, clave] = process.argv.slice(2);

  if (!identificador || !clave) {
    console.error('Uso: node --env-file=.env scripts/cambiarClave.js IDENTIFICADOR NuevaClave');
    process.exitCode = 1;
    return;
  }
  if (clave.length < LARGO_MINIMO_CLAVE) {
    console.error(`La clave debe tener al menos ${LARGO_MINIMO_CLAVE} caracteres.`);
    process.exitCode = 1;
    return;
  }

  const buscadas = await pool.query(
    `SELECT id_usuario, nombre, apellido, rol
     FROM usuario
     WHERE telefono = $1 OR nombre_usuario = $1`,
    [identificador]
  );

  if (buscadas.rows.length === 0) {
    console.error(`No existe ninguna cuenta con el teléfono o usuario: ${identificador}`);
    process.exitCode = 1;
    return;
  }
  if (buscadas.rows.length > 1) {
    console.error('Hay más de una cuenta con ese identificador; no se cambió nada.');
    process.exitCode = 1;
    return;
  }

  const usuario = buscadas.rows[0];
  const hash = await bcrypt.hash(clave, 10);
  await pool.query('UPDATE usuario SET contrasena = $1 WHERE id_usuario = $2', [hash, usuario.id_usuario]);

  console.log(`Clave actualizada para ${usuario.nombre} ${usuario.apellido} (${usuario.rol}).`);
}

cambiarClave()
  .catch((error) => {
    console.error('Error al cambiar la clave:', error);
    process.exitCode = 1;
  })
  .finally(() => pool.end());
