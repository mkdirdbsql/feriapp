const express = require('express');
const router = express.Router();
const {
  getPuestos,
  getPuestosCercanos,
  getPuesto,
  crearPuesto,
  actualizarPuesto,
  getUbicacion,
  actualizarUbicacion
} = require('../controllers/puestos.controller');
const { verificarToken, permitirRoles } = require('../middlewares/autenticacion');
const { verificarDuenoPuesto } = require('../middlewares/propiedad');
const { recibirImagenPuesto } = require('../middlewares/subidaImagenes');
const { guardarImagenPuesto, quitarImagenPuesto } = require('../controllers/imagenes.controller');

// OJO: la ruta específica va ANTES que la genérica para que Express
// no confunda "cercanos" con un id.
router.get('/cercanos', getPuestosCercanos);
router.get('/', getPuestos);
router.post('/', verificarToken, permitirRoles('comerciante'), crearPuesto);

router.get('/:id/ubicacion', getUbicacion);
router.put('/:id/ubicacion', verificarToken, permitirRoles('comerciante'), verificarDuenoPuesto, actualizarUbicacion);

// Imagen del puesto (una sola; si ya había, se reemplaza).
router.post('/:id/imagen', verificarToken, permitirRoles('comerciante'), verificarDuenoPuesto, recibirImagenPuesto, guardarImagenPuesto);
router.delete('/:id/imagen', verificarToken, permitirRoles('comerciante'), verificarDuenoPuesto, quitarImagenPuesto);

router.get('/:id', getPuesto);
router.put('/:id', verificarToken, permitirRoles('comerciante'), verificarDuenoPuesto, actualizarPuesto);

module.exports = router;
