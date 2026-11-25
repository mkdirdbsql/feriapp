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
const { descargarReporte } = require('../controllers/reportes.controller');
const { verificarToken, permitirRoles } = require('../middlewares/autenticacion');

// Todo lo de este archivo es solo para el administrador (RN-07).
router.use(verificarToken, permitirRoles('administrador'));

router.get('/comerciantes', listarComerciantes);
router.get('/comerciantes/:id', obtenerComerciante);
router.put('/comerciantes/:id', actualizarComerciante);
router.put('/comerciantes/:id/estado', cambiarEstado);
router.delete('/comerciantes/:id', eliminarComerciante);

router.get('/reportes', generarReporte);
// Descarga del reporte en archivo: ?formato=pdf  o  ?formato=word
router.get('/reportes/archivo', descargarReporte);

module.exports = router;
