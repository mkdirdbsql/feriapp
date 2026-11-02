const express = require('express');
const router = express.Router();
const {
  listarComerciantes,
  obtenerComerciante,
  actualizarComerciante,
  cambiarEstado,
  eliminarComerciante,
  generarReporte
} = require('../controllers/admin.controller');
const { verificarToken, permitirRoles } = require('../middlewares/autenticacion');

// Todo lo de este archivo es solo para el administrador (RN-07).
router.use(verificarToken, permitirRoles('administrador'));

router.get('/comerciantes', listarComerciantes);
router.get('/comerciantes/:id', obtenerComerciante);
router.put('/comerciantes/:id', actualizarComerciante);
router.put('/comerciantes/:id/estado', cambiarEstado);
router.delete('/comerciantes/:id', eliminarComerciante);

router.get('/reportes', generarReporte);

module.exports = router;
