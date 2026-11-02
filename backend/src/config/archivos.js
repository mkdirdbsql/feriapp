const fs = require('fs/promises');
const path = require('path');

// Carpeta uploads, en la raíz del backend.
const DIRECTORIO_UPLOADS = path.resolve(__dirname, '..', '..', 'uploads');

// Convierte una ruta guardada en la BD (/uploads/productos/x.jpg) en la
// ruta real del disco. Devuelve null si no es un archivo subido por la app
// (por ejemplo las imágenes de prueba /images/...) o si intenta salirse de
// la carpeta uploads.
function rutaFisica(rutaImagen) {
  if (typeof rutaImagen !== 'string' || !rutaImagen.startsWith('/uploads/')) {
    return null;
  }
  const absoluta = path.resolve(DIRECTORIO_UPLOADS, rutaImagen.slice('/uploads/'.length));
  return absoluta.startsWith(DIRECTORIO_UPLOADS + path.sep) ? absoluta : null;
}

// Borra del disco el archivo de una ruta guardada en la BD. Nunca lanza
// error: si no existe o no es un archivo subido, simplemente no hace nada.
async function borrarArchivoSubido(rutaImagen) {
  const archivo = rutaFisica(rutaImagen);
  if (!archivo) {
    return false;
  }
  try {
    await fs.unlink(archivo);
    return true;
  } catch (error) {
    if (error.code !== 'ENOENT') {
      console.error('No se pudo borrar el archivo', archivo, error.message);
    }
    return false;
  }
}

module.exports = { DIRECTORIO_UPLOADS, rutaFisica, borrarArchivoSubido };
