const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const multer = require('multer');
const { DIRECTORIO_UPLOADS } = require('../config/archivos');

const TAMANO_MAXIMO_BYTES = 3 * 1024 * 1024; // 3 MB por imagen
const MAXIMO_IMAGENES_PRODUCTO = 3;

// Tipos permitidos y la extensión con la que se guardan. La extensión sale de
// aquí y no del nombre que manda el cliente.
const EXTENSIONES = {
  'image/jpeg': '.jpg',
  'image/png': '.png',
  'image/webp': '.webp'
};

const CARPETA_PRODUCTOS = path.join(DIRECTORIO_UPLOADS, 'productos');
const CARPETA_PUESTOS = path.join(DIRECTORIO_UPLOADS, 'puestos');

// El contenido de uploads no se sube a Git: si las carpetas no existen
// (por ejemplo en otra computadora), se crean al iniciar.
fs.mkdirSync(CARPETA_PRODUCTOS, { recursive: true });
fs.mkdirSync(CARPETA_PUESTOS, { recursive: true });

function crearAlmacenamiento(carpeta, prefijo) {
  return multer.diskStorage({
    destination: (req, file, cb) => cb(null, carpeta),
    filename: (req, file, cb) => {
      const unico = `${Date.now()}-${crypto.randomBytes(6).toString('hex')}`;
      cb(null, `${prefijo}-${req.params.id}-${unico}${EXTENSIONES[file.mimetype]}`);
    }
  });
}

function filtrarFormato(req, file, cb) {
  if (EXTENSIONES[file.mimetype]) {
    return cb(null, true);
  }
  const error = new Error('Solo se permiten imágenes jpg, png o webp');
  error.codigo = 'FORMATO_INVALIDO';
  cb(error);
}

// Convierte los errores de multer en respuestas 400 en español.
function conManejoDeErrores(subida, mensajeCantidad) {
  return (req, res, next) => {
    subida(req, res, (error) => {
      if (!error) {
        return next();
      }
      if (error instanceof multer.MulterError) {
        const mensajes = {
          LIMIT_FILE_SIZE: 'Cada imagen debe pesar como máximo 3 MB',
          LIMIT_UNEXPECTED_FILE: mensajeCantidad
        };
        return res.status(400).json({ error: mensajes[error.code] || 'No se pudo procesar la imagen' });
      }
      if (error.codigo === 'FORMATO_INVALIDO') {
        return res.status(400).json({ error: error.message });
      }
      next(error);
    });
  };
}

// Productos: hasta 3 archivos en el campo "imagenes".
const recibirImagenesProducto = conManejoDeErrores(
  multer({
    storage: crearAlmacenamiento(CARPETA_PRODUCTOS, 'producto'),
    limits: { fileSize: TAMANO_MAXIMO_BYTES },
    fileFilter: filtrarFormato
  }).array('imagenes', MAXIMO_IMAGENES_PRODUCTO),
  'Envía hasta 3 imágenes en el campo imagenes'
);

// Puestos: un solo archivo en el campo "imagen".
const recibirImagenPuesto = conManejoDeErrores(
  multer({
    storage: crearAlmacenamiento(CARPETA_PUESTOS, 'puesto'),
    limits: { fileSize: TAMANO_MAXIMO_BYTES },
    fileFilter: filtrarFormato
  }).single('imagen'),
  'Envía una sola imagen en el campo imagen'
);

module.exports = {
  recibirImagenesProducto,
  recibirImagenPuesto,
  MAXIMO_IMAGENES_PRODUCTO
};
