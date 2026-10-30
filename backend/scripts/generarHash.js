// Uso: node scripts/generarHash.js MiClave
// Imprime el hash bcrypt de la clave, para pegarlo en un INSERT o UPDATE
// de la tabla usuario (por ejemplo, para crear al administrador).
const bcrypt = require('bcryptjs');

const clave = process.argv[2];

if (!clave) {
  console.error('Uso: node scripts/generarHash.js MiClave');
  process.exit(1);
}

bcrypt.hash(clave, 10).then((hash) => {
  console.log(hash);
});
