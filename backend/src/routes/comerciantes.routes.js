const express = require('express');
const router = express.Router();
const { registrarComerciante } = require('../controllers/comerciantes.controller');

router.post('/registro', registrarComerciante);

module.exports = router;
