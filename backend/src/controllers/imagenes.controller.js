const fs = require('fs/promises');
const pool = require('../config/db');
const { borrarArchivoSubido } = require('../config/archivos');
const { MAXIMO_IMAGENES_PRODUCTO } = require('../middlewares/subidaImagenes');

// Las rutas se guardan y se devuelven RELATIVAS (/uploads/productos/x.jpg).
// La app les antepone la URL base del servidor.

function rutaRelativa(carpeta, archivo) {
  return `/uploads/${carpeta}/${archivo.filename}`;
}

// Borra del disco los archivos que multer ya guardó (cuando algo falla después).
async function descartarArchivos(archivos) {
  for (const archivo of archivos) {
    try {
      await fs.unlink(archivo.path);
    } catch (error) {
      if (error.code !== 'ENOENT') {
        console.error('No se pudo descartar el archivo', archivo.path, error.message);
      }
    }
  }
}

// El tipo (mimetype) lo declara el cliente, así que además se comprueban los
// primeros bytes del archivo: JPEG, PNG o WEBP reales.
async function tieneFormatoImagen(rutaArchivo) {
  const manejador = await fs.open(rutaArchivo, 'r');
  try {
    const buffer = Buffer.alloc(12);
    await manejador.read(buffer, 0, 12, 0);
    const esJpeg = buffer[0] === 0xff && buffer[1] === 0xd8 && buffer[2] === 0xff;
    const esPng = buffer
      .subarray(0, 8)
      .equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]));
    const esWebp =
      buffer.subarray(0, 4).toString('ascii') === 'RIFF' &&
      buffer.subarray(8, 12).toString('ascii') === 'WEBP';
    return esJpeg || esPng || esWebp;
  } finally {
    await manejador.close();
  }
}

async function todasSonImagenes(archivos) {
  for (const archivo of archivos) {
    if (!(await tieneFormatoImagen(archivo.path))) {
      return false;
    }
  }
  return true;
}

// POST /api/productos/:id/imagenes   (rol comerciante, solo el dueño)
// Multipart: campo "imagenes" con 1 a 3 archivos. Máximo 3 por producto.
async function agregarImagenesProducto(req, res) {
  const archivos = req.files || [];
  if (archivos.length === 0) {
    return res.status(400).json({ error: 'Debes enviar al menos una imagen (campo imagenes)' });
  }

  const idProducto = Number.parseInt(req.params.id, 10);
  let client;
  try {
    if (!(await todasSonImagenes(archivos))) {
      await descartarArchivos(archivos);
      return res.status(400).json({ error: 'Alguno de los archivos no es una imagen jpg, png o webp válida' });
    }

    const actuales = await pool.query(
      'SELECT COUNT(*)::int AS cantidad FROM imagen_producto WHERE id_producto = $1',
      [idProducto]
    );
    const cantidadActual = actuales.rows[0].cantidad;
    if (cantidadActual + archivos.length > MAXIMO_IMAGENES_PRODUCTO) {
      await descartarArchivos(archivos);
      return res.status(400).json({
        error: `El producto ya tiene ${cantidadActual} imagen(es) y el máximo es ${MAXIMO_IMAGENES_PRODUCTO}`
      });
    }

    client = await pool.connect();
    await client.query('BEGIN');

    const guardadas = [];
    for (const archivo of archivos) {
      const resultado = await client.query(
        `INSERT INTO imagen_producto (id_producto, ruta_imagen)
         VALUES ($1, $2)
         RETURNING id_imagen, ruta_imagen`,
        [idProducto, rutaRelativa('productos', archivo)]
      );
      guardadas.push(resultado.rows[0]);
    }

    await client.query('COMMIT');
    res.status(201).json({ mensaje: 'Imágenes guardadas correctamente', imagenes: guardadas });
  } catch (error) {
    if (client) {
      await client.query('ROLLBACK');
    }
    await descartarArchivos(archivos);
    console.error('Error al guardar las imágenes del producto:', error);
    if (error.message && error.message.includes('máximo de 3 imágenes')) {
      return res.status(400).json({ error: 'El producto ya tiene el máximo de 3 imágenes' });
    }
    res.status(500).json({ error: 'Error al guardar las imágenes' });
  } finally {
    if (client) {
      client.release();
    }
  }
}

// DELETE /api/productos/:id/imagenes/:idImagen   (rol comerciante, solo el dueño)
async function quitarImagenProducto(req, res) {
  const idImagen = Number.parseInt(req.params.idImagen, 10);
  if (!Number.isInteger(idImagen)) {
    return res.status(400).json({ error: 'Identificador de imagen no válido' });
  }

  try {
    // Se filtra también por producto: no se puede borrar la imagen de otro.
    const resultado = await pool.query(
      `DELETE FROM imagen_producto
       WHERE id_imagen = $1 AND id_producto = $2
       RETURNING ruta_imagen`,
      [idImagen, req.params.id]
    );
    if (resultado.rows.length === 0) {
      return res.status(404).json({ error: 'Imagen no encontrada en este producto' });
    }

    await borrarArchivoSubido(resultado.rows[0].ruta_imagen);
    res.json({ mensaje: 'Imagen eliminada correctamente' });
  } catch (error) {
    console.error('Error al eliminar la imagen del producto:', error);
    res.status(500).json({ error: 'Error al eliminar la imagen' });
  }
}

// POST /api/puestos/:id/imagen   (rol comerciante, solo el dueño)
// Multipart: campo "imagen" con 1 archivo. Un puesto tiene exactamente una
// imagen: si ya tenía, se reemplaza y se borra el archivo anterior.
async function guardarImagenPuesto(req, res) {
  const archivo = req.file;
  if (!archivo) {
    return res.status(400).json({ error: 'Debes enviar una imagen (campo imagen)' });
  }

  const idPuesto = Number.parseInt(req.params.id, 10);
  let client;
  try {
    if (!(await todasSonImagenes([archivo]))) {
      await descartarArchivos([archivo]);
      return res.status(400).json({ error: 'El archivo no es una imagen jpg, png o webp válida' });
    }

    client = await pool.connect();
    await client.query('BEGIN');

    const anterior = await client.query(
      'SELECT ruta_imagen FROM imagen_puesto WHERE id_puesto = $1',
      [idPuesto]
    );
    const rutaAnterior = anterior.rows[0] ? anterior.rows[0].ruta_imagen : null;

    const resultado = await client.query(
      `INSERT INTO imagen_puesto (id_puesto, ruta_imagen)
       VALUES ($1, $2)
       ON CONFLICT (id_puesto) DO UPDATE SET ruta_imagen = EXCLUDED.ruta_imagen
       RETURNING id_imagen, ruta_imagen`,
      [idPuesto, rutaRelativa('puestos', archivo)]
    );

    await client.query('COMMIT');

    if (rutaAnterior) {
      await borrarArchivoSubido(rutaAnterior);
    }

    res.status(rutaAnterior ? 200 : 201).json({
      mensaje: rutaAnterior ? 'Imagen del puesto reemplazada' : 'Imagen del puesto guardada',
      ...resultado.rows[0]
    });
  } catch (error) {
    if (client) {
      await client.query('ROLLBACK');
    }
    await descartarArchivos([archivo]);
    console.error('Error al guardar la imagen del puesto:', error);
    res.status(500).json({ error: 'Error al guardar la imagen del puesto' });
  } finally {
    if (client) {
      client.release();
    }
  }
}

// DELETE /api/puestos/:id/imagen   (rol comerciante, solo el dueño)
async function quitarImagenPuesto(req, res) {
  try {
    const resultado = await pool.query(
      'DELETE FROM imagen_puesto WHERE id_puesto = $1 RETURNING ruta_imagen',
      [req.params.id]
    );
    if (resultado.rows.length === 0) {
      return res.status(404).json({ error: 'El puesto no tiene imagen' });
    }

    await borrarArchivoSubido(resultado.rows[0].ruta_imagen);
    res.json({ mensaje: 'Imagen del puesto eliminada correctamente' });
  } catch (error) {
    console.error('Error al eliminar la imagen del puesto:', error);
    res.status(500).json({ error: 'Error al eliminar la imagen del puesto' });
  }
}

module.exports = {
  agregarImagenesProducto,
  quitarImagenProducto,
  guardarImagenPuesto,
  quitarImagenPuesto
};
