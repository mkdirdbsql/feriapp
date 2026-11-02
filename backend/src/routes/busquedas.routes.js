const express = require('express');
const router = express.Router();
const { registrarBusqueda } = require('../controllers/busquedas.controller');

router.post('/', registrarBusqueda);

module.exports = router;
