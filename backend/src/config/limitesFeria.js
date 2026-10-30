const LATITUD_MINIMA = -16.505;
const LATITUD_MAXIMA = -16.482;
const LONGITUD_MINIMA = -68.195;
const LONGITUD_MAXIMA = -68.165;

function estaDentro(latitud, longitud) {
  const lat = Number(latitud);
  const lon = Number(longitud);
  if (Number.isNaN(lat) || Number.isNaN(lon)) {
    return false;
  }
  return (
    lat >= LATITUD_MINIMA &&
    lat <= LATITUD_MAXIMA &&
    lon >= LONGITUD_MINIMA &&
    lon <= LONGITUD_MAXIMA
  );
}

module.exports = {
  LATITUD_MINIMA,
  LATITUD_MAXIMA,
  LONGITUD_MINIMA,
  LONGITUD_MAXIMA,
  estaDentro,
};
