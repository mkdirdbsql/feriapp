// Perímetro de la Feria 16 de Julio: polígono de vértices [latitud, longitud]
// en orden alrededor del borde. Un puesto solo puede registrarse dentro.
const PERIMETRO = [
  [-16.503136, -68.162684],
  [-16.494270, -68.194010],
  [-16.486726, -68.191200],
  [-16.486755, -68.180093],
  [-16.488210, -68.171081],
  [-16.494377, -68.166632],
  [-16.496850, -68.163779],
];

// El GPS del teléfono tiene un error de unos metros: un comerciante parado
// justo en el borde no debe ser rechazado por eso.
const TOLERANCIA_METROS = 15;

// Caja que envuelve al polígono, con un margen (aprox. 300 m) para que el
// mapa pueda moverse un poco más allá del perímetro.
const MARGEN_GRADOS = 0.003;
const LATITUD_MINIMA = Math.min(...PERIMETRO.map((p) => p[0])) - MARGEN_GRADOS;
const LATITUD_MAXIMA = Math.max(...PERIMETRO.map((p) => p[0])) + MARGEN_GRADOS;
const LONGITUD_MINIMA = Math.min(...PERIMETRO.map((p) => p[1])) - MARGEN_GRADOS;
const LONGITUD_MAXIMA = Math.max(...PERIMETRO.map((p) => p[1])) + MARGEN_GRADOS;

const METROS_POR_GRADO_LAT = 110574;

function dentroDelPoligono(lat, lon) {
  let dentro = false;
  for (let i = 0, j = PERIMETRO.length - 1; i < PERIMETRO.length; j = i++) {
    const [latI, lonI] = PERIMETRO[i];
    const [latJ, lonJ] = PERIMETRO[j];
    const cruza = (lonI > lon) !== (lonJ > lon) &&
      lat < ((latJ - latI) * (lon - lonI)) / (lonJ - lonI) + latI;
    if (cruza) {
      dentro = !dentro;
    }
  }
  return dentro;
}

// Distancia en metros del punto al borde del polígono (aprox. plana, válida
// para áreas pequeñas como esta).
function distanciaAlBordeMetros(lat, lon) {
  const metrosPorGradoLon = 111320 * Math.cos((lat * Math.PI) / 180);
  const aMetros = ([la, lo]) => [(lo - lon) * metrosPorGradoLon, (la - lat) * METROS_POR_GRADO_LAT];
  let minima = Infinity;
  for (let i = 0; i < PERIMETRO.length; i++) {
    const [ax, ay] = aMetros(PERIMETRO[i]);
    const [bx, by] = aMetros(PERIMETRO[(i + 1) % PERIMETRO.length]);
    const dx = bx - ax;
    const dy = by - ay;
    const largo2 = dx * dx + dy * dy;
    const t = largo2 === 0 ? 0 : Math.max(0, Math.min(1, (-ax * dx - ay * dy) / largo2));
    const distancia = Math.hypot(ax + t * dx, ay + t * dy);
    if (distancia < minima) {
      minima = distancia;
    }
  }
  return minima;
}

function estaDentro(latitud, longitud) {
  const lat = Number(latitud);
  const lon = Number(longitud);
  if (Number.isNaN(lat) || Number.isNaN(lon)) {
    return false;
  }
  if (dentroDelPoligono(lat, lon)) {
    return true;
  }
  return distanciaAlBordeMetros(lat, lon) <= TOLERANCIA_METROS;
}

module.exports = {
  PERIMETRO,
  LATITUD_MINIMA,
  LATITUD_MAXIMA,
  LONGITUD_MINIMA,
  LONGITUD_MAXIMA,
  estaDentro,
};
