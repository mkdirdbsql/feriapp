const { Pool } = require('pg');

const pool = new Pool({
  host: process.env.BD_HOST,
  port: process.env.BD_PUERTO,
  user: process.env.BD_USUARIO,
  password: process.env.BD_CLAVE,
  database: process.env.BD_NOMBRE,
});

pool.on('error', (err) => {
  console.error('Error inesperado en el pool de PostgreSQL', err);
});

module.exports = pool;
