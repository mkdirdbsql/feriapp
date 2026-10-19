const express = require('express');
const router = express.Router();
const {
  getProductos,
  getProductosPorPuesto,
  crearProducto,
  actualizarProducto,
  eliminarProducto
} = require('../controllers/productos.controller');

router.get('/puesto/:id_puesto', getProductosPorPuesto);
router.get('/', getProductos);
router.post('/', crearProducto);
router.put('/:id', actualizarProducto);
router.delete('/:id', eliminarProducto);

module.exports = router;
