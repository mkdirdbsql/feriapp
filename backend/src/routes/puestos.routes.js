const express = require('express');
const router = express.Router();
const { getPuestos, getPuestosCercanos } = require('../controllers/puestos.controller');

// OJO: la ruta específica va ANTES que la genérica para que Express
// no confunda "cercanos" con un id.
router.get('/cercanos', getPuestosCercanos);
router.get('/', getPuestos);

module.exports = router;
