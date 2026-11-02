const pool = require('../config/db');
const { estaDentro } = require('../config/limitesFeria');

const MENSAJE_FUERA_DE_AREA = 'La ubicación debe estar dentro del área de la Feria 16 de Julio';

// Lee latitud y longitud del cuerpo de la petición.
// Devuelve { lat, lon } o null si faltan o no son números.
function leerCoordenadas(cuerpo) {
  const { latitud, longitud } = cuerpo;
  if (latitud == null || longitud == null || latitud === '' || longitud === '') {
    return null;
  }
  const lat = Number(latitud);
  const lon = Number(longitud);
  if (!Number.isFinite(lat) || !Number.isFinite(lon)) {
    return null;
  }
  return { lat, lon };
}

// Lista los puestos activos (de comerciantes activos), con el nombre comercial,
// la coordenada separada en longitud/latitud y la ruta de su imagen.
async function getPuestos(req, res) {
  try {
    const result = await pool.query(`
      SELECT
        p.id_puesto,
        p.nombre_puesto,
        p.descripcion,
        p.estado,
        ST_X(p.ubicacion) AS longitud,
        ST_Y(p.ubicacion) AS latitud,
        c.nombre_comercial,
        (SELECT ip.ruta_imagen FROM imagen_puesto ip WHERE ip.id_puesto = p.id_puesto) AS imagen
      FROM puesto p
      JOIN comerciante c ON c.id_comerciante = p.id_comerciante
      WHERE p.estado = 'activo' AND c.estado = 'activo'
      ORDER BY p.id_puesto
    `);
    res.json(result.rows);
  } catch (error) {
    console.error('Error al obtener puestos:', error);
    res.status(500).json({ error: 'Error al obtener puestos' });
  }
}

// Puestos cercanos a una coordenada dada, usando PostGIS (ST_DWithin).
// Uso: GET /api/puestos/cercanos?lat=-16.49&lng=-68.18&radio=500
async function getPuestosCercanos(req, res) {
  const { lat, lng, radio } = req.query;
  if (!lat || !lng) {
    return res.status(400).json({ error: 'Se requieren los parámetros lat y lng' });
  }
  const radioMetros = radio ? parseInt(radio, 10) : 500;

  try {
    const result = await pool.query(
      `
      SELECT
        p.id_puesto,
        p.nombre_puesto,
        c.nombre_comercial,
        ST_X(p.ubicacion) AS longitud,
        ST_Y(p.ubicacion) AS latitud,
        (SELECT ip.ruta_imagen FROM imagen_puesto ip WHERE ip.id_puesto = p.id_puesto) AS imagen,
        ST_Distance(
          p.ubicacion::geography,
          ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography
        ) AS distancia_metros
      FROM puesto p
      JOIN comerciante c ON c.id_comerciante = p.id_comerciante
      WHERE p.estado = 'activo' AND c.estado = 'activo'
        AND ST_DWithin(
          p.ubicacion::geography,
          ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography,
          $3
        )
      ORDER BY distancia_metros
      `,
      [lng, lat, radioMetros]
    );
    res.json(result.rows);
  } catch (error) {
    console.error('Error al buscar puestos cercanos:', error);
    res.status(500).json({ error: 'Error al buscar puestos cercanos' });
  }
}

// GET /api/puestos/:id
async function getPuesto(req, res) {
  const idPuesto = Number.parseInt(req.params.id, 10);
  if (!Number.isInteger(idPuesto)) {
    return res.status(400).json({ error: 'Identificador de puesto no válido' });
  }

  try {
    const result = await pool.query(
      `
      SELECT
        p.id_puesto,
        p.nombre_puesto,
        p.descripcion,
        p.estado,
        ST_X(p.ubicacion) AS longitud,
        ST_Y(p.ubicacion) AS latitud,
        c.nombre_comercial,
        (SELECT ip.ruta_imagen FROM imagen_puesto ip WHERE ip.id_puesto = p.id_puesto) AS imagen
      FROM puesto p
      JOIN comerciante c ON c.id_comerciante = p.id_comerciante
      WHERE p.id_puesto = $1 AND p.estado = 'activo' AND c.estado = 'activo'
      `,
      [idPuesto]
    );
    if (result.rows.length === 0) {
      return res.status(404).json({ error: 'Puesto no encontrado' });
    }
    res.json(result.rows[0]);
  } catch (error) {
    console.error('Error al obtener el puesto:', error);
    res.status(500).json({ error: 'Error al obtener el puesto' });
  }
}

// POST /api/puestos   (rol comerciante)
// Body: { nombre_puesto, descripcion, latitud, longitud }
// Crea el puesto del comerciante autenticado. Cada comerciante tiene un
// solo puesto (puesto.id_comerciante es UNIQUE).
async function crearPuesto(req, res) {
  const { nombre_puesto, descripcion } = req.body;

  if (!nombre_puesto || !String(nombre_puesto).trim()) {
    return res.status(400).json({ error: 'Falta el nombre del puesto' });
  }
  const coordenadas = leerCoordenadas(req.body);
  if (!coordenadas) {
    return res.status(400).json({ error: 'Faltan la latitud y la longitud del puesto' });
  }
  if (!estaDentro(coordenadas.lat, coordenadas.lon)) {
    return res.status(400).json({ error: MENSAJE_FUERA_DE_AREA });
  }

  try {
    const result = await pool.query(
      `INSERT INTO puesto (id_comerciante, nombre_puesto, descripcion, ubicacion)
       VALUES ($1, $2, $3, ST_SetSRID(ST_MakePoint($4, $5), 4326))
       RETURNING id_puesto`,
      [
        req.usuario.id_comerciante,
        String(nombre_puesto).trim(),
        descripcion || null,
        coordenadas.lon,
        coordenadas.lat
      ]
    );
    res.status(201).json({
      mensaje: 'Puesto registrado correctamente',
      id_puesto: result.rows[0].id_puesto,
      latitud: coordenadas.lat,
      longitud: coordenadas.lon
    });
  } catch (error) {
    console.error('Error al crear puesto:', error);
    if (error.code === '23505') {
      return res.status(409).json({ error: 'Ya tienes un puesto registrado' });
    }
    res.status(500).json({ error: 'Error al registrar el puesto' });
  }
}

// PUT /api/puestos/:id   (rol comerciante, solo el dueño)
// Body: cualquier combinación de { nombre_puesto, descripcion }
async function actualizarPuesto(req, res) {
  const { nombre_puesto, descripcion } = req.body;

  if (nombre_puesto !== undefined && !String(nombre_puesto).trim()) {
    return res.status(400).json({ error: 'El nombre del puesto no puede estar vacío' });
  }

  try {
    await pool.query(
      `UPDATE puesto SET
         nombre_puesto = COALESCE($1, nombre_puesto),
         descripcion   = COALESCE($2, descripcion)
       WHERE id_puesto = $3`,
      [
        nombre_puesto === undefined ? null : String(nombre_puesto).trim(),
        descripcion === undefined ? null : descripcion,
        req.params.id
      ]
    );
    res.json({ mensaje: 'Puesto actualizado correctamente' });
  } catch (error) {
    console.error('Error al actualizar puesto:', error);
    res.status(500).json({ error: 'Error al actualizar el puesto' });
  }
}

// GET /api/puestos/:id/ubicacion
async function getUbicacion(req, res) {
  const idPuesto = Number.parseInt(req.params.id, 10);
  if (!Number.isInteger(idPuesto)) {
    return res.status(400).json({ error: 'Identificador de puesto no válido' });
  }

  try {
    const result = await pool.query(
      `SELECT ST_Y(p.ubicacion) AS latitud, ST_X(p.ubicacion) AS longitud
       FROM puesto p
       JOIN comerciante c ON c.id_comerciante = p.id_comerciante
       WHERE p.id_puesto = $1 AND p.estado = 'activo' AND c.estado = 'activo'`,
      [idPuesto]
    );
    if (result.rows.length === 0) {
      return res.status(404).json({ error: 'Puesto no encontrado' });
    }
    res.json(result.rows[0]);
  } catch (error) {
    console.error('Error al obtener la ubicación:', error);
    res.status(500).json({ error: 'Error al obtener la ubicación' });
  }
}

// PUT /api/puestos/:id/ubicacion   (rol comerciante, solo el dueño)
// Body: { latitud, longitud }
async function actualizarUbicacion(req, res) {
  const coordenadas = leerCoordenadas(req.body);
  if (!coordenadas) {
    return res.status(400).json({ error: 'Faltan la latitud y la longitud del puesto' });
  }
  if (!estaDentro(coordenadas.lat, coordenadas.lon)) {
    return res.status(400).json({ error: MENSAJE_FUERA_DE_AREA });
  }

  try {
    await pool.query(
      `UPDATE puesto
       SET ubicacion = ST_SetSRID(ST_MakePoint($1, $2), 4326)
       WHERE id_puesto = $3`,
      [coordenadas.lon, coordenadas.lat, req.params.id]
    );
    res.json({
      mensaje: 'Ubicación actualizada correctamente',
      latitud: coordenadas.lat,
      longitud: coordenadas.lon
    });
  } catch (error) {
    console.error('Error al actualizar la ubicación:', error);
    res.status(500).json({ error: 'Error al actualizar la ubicación' });
  }
}

module.exports = {
  getPuestos,
  getPuestosCercanos,
  getPuesto,
  crearPuesto,
  actualizarPuesto,
  getUbicacion,
  actualizarUbicacion
};
