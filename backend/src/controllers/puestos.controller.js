const pool = require('../config/db');

// Lista todos los puestos activos, con el nombre comercial del
// comerciante y la coordenada separada en longitud/latitud (más fácil
// de consumir desde Android que un WKT de PostGIS).
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
        c.nombre_comercial
      FROM puesto p
      JOIN comerciante c ON c.id_comerciante = p.id_comerciante
      WHERE p.estado = 'activo'
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
        ST_Distance(
          p.ubicacion::geography,
          ST_SetSRID(ST_MakePoint($1, $2), 4326)::geography
        ) AS distancia_metros
      FROM puesto p
      JOIN comerciante c ON c.id_comerciante = p.id_comerciante
      WHERE ST_DWithin(
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

module.exports = { getPuestos, getPuestosCercanos };
