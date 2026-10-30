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

// Consultas públicas: el consumidor no tiene cuenta.
router.get('/puesto/:id_puesto', getProductosPorPuesto);
router.get('/', getProductos);

// Gestión: solo comerciantes, y solo sobre sus propios productos (RN-01).
router.post('/', verificarToken, permitirRoles('comerciante'), crearProducto);
router.put('/:id', verificarToken, permitirRoles('comerciante'), verificarDuenoProducto, actualizarProducto);
router.delete('/:id', verificarToken, permitirRoles('comerciante'), verificarDuenoProducto, eliminarProducto);

module.exports = router;
