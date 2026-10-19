require('dotenv').config();
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

app.use('/images', express.static(path.join(__dirname, 'public/images')));

app.use('/api/categorias', categoriasRoutes);
app.use('/api/puestos', puestosRoutes);
app.use('/api/productos', productosRoutes);
app.use('/api/auth', authRoutes);
app.use('/api/comerciantes', comerciantesRoutes);

app.get('/', (req, res) => {
  res.send('API de FeriApp funcionando correctamente');
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`Servidor escuchando en http://localhost:${PORT}`);
});
