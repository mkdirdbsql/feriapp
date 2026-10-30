// Uso (desde la carpeta backend): node --env-file=.env scripts/migrarClaves.js
// Convierte a hash bcrypt las claves de la tabla usuario que todavía están
// en texto plano. Las que ya son hash (empiezan con $2) no se tocan, así
// que se puede ejecutar varias veces sin dañar nada. Las contraseñas con
// las que entra cada persona siguen siendo las mismas.
const bcrypt = require('bcryptjs');
const pool = require('../src/config/db');

async function migrarClaves() {
  const { rows } = await pool.query('SELECT id_usuario, contrasena FROM usuario');
  let convertidas = 0;

  for (const usuario of rows) {
    if (usuario.contrasena.startsWith('$2')) {
      continue;
    }
    const hash = await bcrypt.hash(usuario.contrasena, 10);
    await pool.query(
      'UPDATE usuario SET contrasena = $1 WHERE id_usuario = $2',
      [hash, usuario.id_usuario]
    );
    convertidas++;
  }

  console.log(`Claves convertidas: ${convertidas} de ${rows.length} usuarios`);
}

migrarClaves()
  .catch((error) => {
    console.error('Error al migrar las claves:', error);
    process.exitCode = 1;
  })
  .finally(() => pool.end());
