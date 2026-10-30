const express = require('express');
const cors = require('cors');
const path = require('path');

const categoriasRoutes = require('./src/routes/categorias.routes');
const puestosRoutes = require('./src/routes/puestos.routes');
const productosRoutes = require('./src/routes/productos.routes');
const authRoutes = require('./src/routes/auth.routes');
const comerciantesRoutes = require('./src/routes/comerciantes.routes');

const app = express();

app.use(cors());
app.use(express.json());

app.use('/uploads', express.static(path.join(__dirname, 'uploads')));
app.use('/images', express.static(path.join(__dirname, 'public/images')));

app.use('/api/categorias', categoriasRoutes);
app.use('/api/puestos', puestosRoutes);
app.use('/api/productos', productosRoutes);
app.use('/api/auth', authRoutes);
app.use('/api/comerciantes', comerciantesRoutes);

app.get('/', (req, res) => {
  res.send('API de FeriApp funcionando correctamente');
});

// Middleware para rutas no encontradas (404)
app.use((req, res) => {
  res.status(404).json({ error: 'Ruta no encontrada' });
});

// Manejador de errores final
app.use((err, req, res, next) => {
  console.error('Error no controlado:', err);
  const estado = err.status || err.statusCode || 500;
  const mensaje = err.message || 'Error interno del servidor';
  res.status(estado).json({ error: mensaje });
});

const PUERTO = process.env.PUERTO || 3000;
app.listen(PUERTO, '0.0.0.0', () => {
  console.log(`Servidor escuchando en http://0.0.0.0:${PUERTO}`);
});
