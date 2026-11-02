const express = require('express');
const router = express.Router();
const {
  registrarComerciante,
  obtenerPerfil,
  actualizarPerfil
} = require('../controllers/comerciantes.controller');
const { verificarToken, permitirRoles } = require('../middlewares/autenticacion');

router.post('/registro', registrarComerciante);

// Perfil del comerciante autenticado (puesto y productos incluidos).
router.get('/yo', verificarToken, permitirRoles('comerciante'), obtenerPerfil);
router.put('/yo', verificarToken, permitirRoles('comerciante'), actualizarPerfil);

module.exports = router;
