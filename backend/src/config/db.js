// Conexión a PostgreSQL usando el paquete "pg".
// Lee las credenciales desde el archivo .env (nunca las pongas aquí
// directamente ni las subas a GitHub).
const { Pool } = require('pg');
require('dotenv').config();

const pool = new Pool({
  host: process.env.DB_HOST,
  port: process.env.DB_PORT,
  user: process.env.DB_USER,
  password: process.env.DB_PASSWORD,
  database: process.env.DB_NAME,
});

pool.on('error', (err) => {
  console.error('Error inesperado en el pool de PostgreSQL', err);
});

module.exports = pool;
