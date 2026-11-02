const fs = require('fs/promises');
const path = require('path');
const bcrypt = require('bcryptjs');
const pool = require('../config/db');

const LARGO_MINIMO_CLAVE = 6;
const FORMATO_TELEFONO = /^[0-9]{7,15}$/;
const ESTADOS_VALIDOS = ['activo', 'inactivo'];
const DIRECTORIO_UPLOADS = path.resolve(__dirname, '..', '..', 'uploads');

// Todos los :id de este archivo son id_comerciante (no id_usuario).
function leerId(valor) {
  const id = Number.parseInt(valor, 10);
  return Number.isInteger(id) ? id : null;
}

function textoOpcional(valor) {
  if (valor === undefined || valor === null) {
    return null;
  }
  return String(valor).trim();
}

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

// GET /api/admin/comerciantes
async function listarComerciantes(req, res) {
  try {
    const result = await pool.query(`
      SELECT
        c.id_comerciante,
        u.id_usuario,
        u.nombre,
        u.apellido,
        u.telefono,
        c.nombre_comercial,
        c.estado,
        u.fecha_registro,
        pu.id_puesto,
        pu.nombre_puesto
      FROM comerciante c
      JOIN usuario u ON u.id_usuario = c.id_usuario
      LEFT JOIN puesto pu ON pu.id_comerciante = c.id_comerciante
      ORDER BY c.id_comerciante
    `);
    res.json(result.rows);
  } catch (error) {
    console.error('Error al listar comerciantes:', error);
    res.status(500).json({ error: 'Error al obtener los comerciantes' });
  }
}

// GET /api/admin/comerciantes/:id
// Perfil completo de un comerciante, con su puesto (o null) y sus productos.
async function obtenerComerciante(req, res) {
  const idComerciante = leerId(req.params.id);
  if (idComerciante === null) {
    return res.status(400).json({ error: 'Identificador de comerciante no válido' });
  }

  try {
    const perfilResult = await pool.query(
      `SELECT
         c.id_comerciante,
         u.id_usuario,
         u.nombre,
         u.apellido,
         u.telefono,
         c.nombre_comercial,
         c.descripcion,
         c.estado,
         u.fecha_registro
       FROM comerciante c
       JOIN usuario u ON u.id_usuario = c.id_usuario
       WHERE c.id_comerciante = $1`,
      [idComerciante]
    );
    if (perfilResult.rows.length === 0) {
      return res.status(404).json({ error: 'Comerciante no encontrado' });
    }

    const puestoResult = await pool.query(
      `SELECT
         p.id_puesto,
         p.nombre_puesto,
         p.descripcion,
         p.estado,
         ST_X(p.ubicacion) AS longitud,
         ST_Y(p.ubicacion) AS latitud,
         (SELECT ip.ruta_imagen FROM imagen_puesto ip WHERE ip.id_puesto = p.id_puesto) AS imagen
       FROM puesto p
       WHERE p.id_comerciante = $1`,
      [idComerciante]
    );
    const puesto = puestoResult.rows[0] || null;

    let productos = [];
    if (puesto) {
      const productosResult = await pool.query(
        `SELECT
           pr.id_producto,
           pr.nombre_producto,
           pr.descripcion,
           pr.precio,
           pr.disponible,
           cat.nombre_categoria
         FROM producto_puesto pp
         JOIN producto pr ON pr.id_producto = pp.id_producto
         JOIN categoria cat ON cat.id_categoria = pr.id_categoria
         WHERE pp.id_puesto = $1
         ORDER BY pr.id_producto`,
        [puesto.id_puesto]
      );
      productos = productosResult.rows;
    }

    res.json({ ...perfilResult.rows[0], puesto, productos });
  } catch (error) {
    console.error('Error al obtener el comerciante:', error);
    res.status(500).json({ error: 'Error al obtener el comerciante' });
  }
}

// PUT /api/admin/comerciantes/:id
// Body: cualquier combinación de { nombre, apellido, telefono, contrasena,
// nombre_comercial, descripcion }
async function actualizarComerciante(req, res) {
  const idComerciante = leerId(req.params.id);
  if (idComerciante === null) {
    return res.status(400).json({ error: 'Identificador de comerciante no válido' });
  }

  const nombre = textoOpcional(req.body.nombre);
  const apellido = textoOpcional(req.body.apellido);
  const telefono = textoOpcional(req.body.telefono);
  const nombreComercial = textoOpcional(req.body.nombre_comercial);
  const descripcion = textoOpcional(req.body.descripcion);
  const contrasena = req.body.contrasena == null ? null : String(req.body.contrasena);

  if (nombre === '' || apellido === '' || nombreComercial === '') {
    return res.status(400).json({ error: 'Nombre, apellido y nombre comercial no pueden estar vacíos' });
  }
  if (telefono !== null && !FORMATO_TELEFONO.test(telefono)) {
    return res.status(400).json({ error: 'El teléfono debe tener solo números (entre 7 y 15 dígitos)' });
  }
  if (contrasena !== null && contrasena.length < LARGO_MINIMO_CLAVE) {
    return res.status(400).json({ error: `La contraseña debe tener al menos ${LARGO_MINIMO_CLAVE} caracteres` });
  }

  let client;
  try {
    const hash = contrasena === null ? null : await bcrypt.hash(contrasena, 10);

    client = await pool.connect();
    await client.query('BEGIN');

    const existe = await client.query(
      'SELECT id_usuario FROM comerciante WHERE id_comerciante = $1',
      [idComerciante]
    );
    if (existe.rows.length === 0) {
      await client.query('ROLLBACK');
      return res.status(404).json({ error: 'Comerciante no encontrado' });
    }
    const idUsuario = existe.rows[0].id_usuario;

    await client.query(
      `UPDATE usuario SET
         nombre     = COALESCE($1, nombre),
         apellido   = COALESCE($2, apellido),
         telefono   = COALESCE($3, telefono),
         contrasena = COALESCE($4, contrasena)
       WHERE id_usuario = $5`,
      [nombre, apellido, telefono, hash, idUsuario]
    );

    await client.query(
      `UPDATE comerciante SET
         nombre_comercial = COALESCE($1, nombre_comercial),
         descripcion      = COALESCE($2, descripcion)
       WHERE id_comerciante = $3`,
      [nombreComercial, descripcion, idComerciante]
    );

    await client.query('COMMIT');
    res.json({ mensaje: 'Comerciante actualizado correctamente' });
  } catch (error) {
    if (client) {
      await client.query('ROLLBACK');
    }
    console.error('Error al actualizar el comerciante:', error);
    if (error.code === '23505') {
      return res.status(409).json({ error: 'Ese teléfono ya está registrado' });
    }
    res.status(500).json({ error: 'Error al actualizar el comerciante' });
  } finally {
    if (client) {
      client.release();
    }
  }
}

// PUT /api/admin/comerciantes/:id/estado
// Body: { estado: 'activo' | 'inactivo' }
// Desactiva o reactiva la cuenta sin borrar nada. Un comerciante inactivo no
// puede iniciar sesión y su puesto y productos dejan de aparecer en la app.
async function cambiarEstado(req, res) {
  const idComerciante = leerId(req.params.id);
  if (idComerciante === null) {
    return res.status(400).json({ error: 'Identificador de comerciante no válido' });
  }

  const { estado } = req.body;
  if (!ESTADOS_VALIDOS.includes(estado)) {
    return res.status(400).json({ error: "El estado debe ser 'activo' o 'inactivo'" });
  }

  try {
    const result = await pool.query(
      'UPDATE comerciante SET estado = $1 WHERE id_comerciante = $2 RETURNING id_comerciante',
      [estado, idComerciante]
    );
    if (result.rows.length === 0) {
      return res.status(404).json({ error: 'Comerciante no encontrado' });
    }
    res.json({ mensaje: `Comerciante ${estado === 'activo' ? 'reactivado' : 'desactivado'} correctamente`, estado });
  } catch (error) {
    console.error('Error al cambiar el estado del comerciante:', error);
    res.status(500).json({ error: 'Error al cambiar el estado' });
  }
}

// DELETE /api/admin/comerciantes/:id
// Elimina definitivamente al comerciante: su cuenta, su puesto, sus productos
// y sus imágenes (también los archivos del disco).
// Borrar el usuario elimina en cascada comerciante, puesto, producto_puesto e
// imagen_puesto, pero NO los productos (quedarían huérfanos), por eso primero
// se borran los productos del puesto. Todo en una sola transacción.
async function eliminarComerciante(req, res) {
  const idComerciante = leerId(req.params.id);
  if (idComerciante === null) {
    return res.status(400).json({ error: 'Identificador de comerciante no válido' });
  }

  let client;
  let rutasImagenes = [];
  try {
    client = await pool.connect();
    await client.query('BEGIN');

    const datos = await client.query(
      `SELECT c.id_usuario, u.rol
       FROM comerciante c
       JOIN usuario u ON u.id_usuario = c.id_usuario
       WHERE c.id_comerciante = $1`,
      [idComerciante]
    );
    if (datos.rows.length === 0) {
      await client.query('ROLLBACK');
      return res.status(404).json({ error: 'Comerciante no encontrado' });
    }
    if (datos.rows[0].rol !== 'comerciante') {
      await client.query('ROLLBACK');
      return res.status(403).json({ error: 'Solo se pueden eliminar cuentas de comerciantes' });
    }
    const idUsuario = datos.rows[0].id_usuario;

    // Rutas de las imágenes, para borrar los archivos después de confirmar.
    const imagenes = await client.query(
      `SELECT ip.ruta_imagen
       FROM imagen_puesto ip
       JOIN puesto pu ON pu.id_puesto = ip.id_puesto
       WHERE pu.id_comerciante = $1
       UNION ALL
       SELECT im.ruta_imagen
       FROM imagen_producto im
       JOIN producto_puesto pp ON pp.id_producto = im.id_producto
       JOIN puesto pu ON pu.id_puesto = pp.id_puesto
       WHERE pu.id_comerciante = $1`,
      [idComerciante]
    );
    rutasImagenes = imagenes.rows.map((fila) => fila.ruta_imagen);

    await client.query(
      `DELETE FROM producto
       WHERE id_producto IN (
         SELECT pp.id_producto
         FROM producto_puesto pp
         JOIN puesto pu ON pu.id_puesto = pp.id_puesto
         WHERE pu.id_comerciante = $1
       )`,
      [idComerciante]
    );

    await client.query('DELETE FROM usuario WHERE id_usuario = $1', [idUsuario]);

    await client.query('COMMIT');
  } catch (error) {
    if (client) {
      await client.query('ROLLBACK');
    }
    console.error('Error al eliminar el comerciante:', error);
    return res.status(500).json({ error: 'Error al eliminar el comerciante' });
  } finally {
    if (client) {
      client.release();
    }
  }

  // Con la BD ya confirmada, se borran los archivos. Si alguno falla o no
  // existe, no importa: los datos ya se eliminaron.
  let archivosBorrados = 0;
  for (const ruta of rutasImagenes) {
    const archivo = rutaFisica(ruta);
    if (!archivo) {
      continue;
    }
    try {
      await fs.unlink(archivo);
      archivosBorrados++;
    } catch (error) {
      if (error.code !== 'ENOENT') {
        console.error('No se pudo borrar el archivo', archivo, error.message);
      }
    }
  }

  res.json({ mensaje: 'Comerciante eliminado correctamente', archivos_borrados: archivosBorrados });
}

// GET /api/admin/reportes   (RF-21)
// {
//   usuarios_por_rol: [{ rol, cantidad }],
//   categoria_mas_buscada: { categoria, cantidad } | null,
//   usuarios_registrados_hoy: n
// }
// Los consumidores no tienen cuenta, así que no aparecen en usuarios_por_rol.
async function generarReporte(req, res) {
  try {
    const porRol = await pool.query(
      `SELECT rol, COUNT(*)::int AS cantidad
       FROM usuario
       GROUP BY rol
       ORDER BY rol`
    );

    const masBuscada = await pool.query(
      `SELECT cat.nombre_categoria AS categoria, COUNT(*)::int AS cantidad
       FROM busquedas b
       JOIN categoria cat ON cat.id_categoria = b.id_categoria
       GROUP BY cat.id_categoria, cat.nombre_categoria
       ORDER BY cantidad DESC, cat.nombre_categoria
       LIMIT 1`
    );

    const hoy = await pool.query(
      `SELECT COUNT(*)::int AS cantidad
       FROM usuario
       WHERE fecha_registro::date = CURRENT_DATE`
    );

    res.json({
      usuarios_por_rol: porRol.rows,
      categoria_mas_buscada: masBuscada.rows[0] || null,
      usuarios_registrados_hoy: hoy.rows[0].cantidad
    });
  } catch (error) {
    console.error('Error al generar el reporte:', error);
    res.status(500).json({ error: 'Error al generar el reporte' });
  }
}

module.exports = {
  listarComerciantes,
  obtenerComerciante,
  actualizarComerciante,
  cambiarEstado,
  eliminarComerciante,
  generarReporte
};
