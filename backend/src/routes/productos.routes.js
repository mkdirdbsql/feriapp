const express = require('express');
const router = express.Router();
const {
  getProductos,
  getProductosPorPuesto,
  crearProducto,
  actualizarProducto,
  eliminarProducto
} = require('../controllers/productos.controller');
const { verificarToken, permitirRoles } = require('../middlewares/autenticacion');
const { verificarDuenoProducto } = require('../middlewares/propiedad');
const { recibirImagenesProducto } = require('../middlewares/subidaImagenes');
const { agregarImagenesProducto, quitarImagenProducto } = require('../controllers/imagenes.controller');

// Consultas públicas: el consumidor no tiene cuenta.
router.get('/puesto/:id_puesto', getProductosPorPuesto);
router.get('/', getProductos);

// Gestión: solo comerciantes, y solo sobre sus propios productos (RN-01).
router.post('/', verificarToken, permitirRoles('comerciante'), crearProducto);
router.put('/:id', verificarToken, permitirRoles('comerciante'), verificarDuenoProducto, actualizarProducto);
router.delete('/:id', verificarToken, permitirRoles('comerciante'), verificarDuenoProducto, eliminarProducto);

// Imágenes del producto. Primero se verifica el dueño y recién después se
// recibe el archivo, para no guardar en disco subidas que se van a rechazar.
router.post('/:id/imagenes', verificarToken, permitirRoles('comerciante'), verificarDuenoProducto, recibirImagenesProducto, agregarImagenesProducto);
router.delete('/:id/imagenes/:idImagen', verificarToken, permitirRoles('comerciante'), verificarDuenoProducto, quitarImagenProducto);

module.exports = router;
