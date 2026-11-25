const pool = require('../config/db');

// Reporte del administrador en PDF o Word.
// GET /api/admin/reportes/archivo?formato=pdf|word
// Las librerías se cargan solo cuando se piden, así el servidor arranca
// aunque todavía no estén instaladas (npm install pdfkit docx).

const ZONA_HORARIA = 'America/Caracas';

function fechaHoraActual() {
  const ahora = new Date();
  const fecha = ahora.toLocaleDateString('es-VE', {
    timeZone: ZONA_HORARIA, day: '2-digit', month: '2-digit', year: 'numeric'
  });
  const hora = ahora.toLocaleTimeString('es-VE', {
    timeZone: ZONA_HORARIA, hour: '2-digit', minute: '2-digit', hour12: true
  });
  const sello = ahora.toLocaleString('sv-SE', { timeZone: ZONA_HORARIA })
    .replace(/[: ]/g, '-').slice(0, 16);
  return { fecha, hora, sello };
}

async function reunirDatos() {
  const porRol = await pool.query(
    `SELECT rol, COUNT(*)::int AS cantidad FROM usuario GROUP BY rol ORDER BY rol`
  );
  const estados = await pool.query(
    `SELECT estado, COUNT(*)::int AS cantidad FROM comerciante GROUP BY estado`
  );
  const totales = await pool.query(
    `SELECT
       (SELECT COUNT(*)::int FROM puesto) AS puestos,
       (SELECT COUNT(*)::int FROM producto) AS productos,
       (SELECT COUNT(*)::int FROM busquedas) AS busquedas,
       (SELECT COUNT(*)::int FROM usuario WHERE fecha_registro::date = CURRENT_DATE) AS registrados_hoy`
  );
  const categorias = await pool.query(
    `SELECT cat.nombre_categoria AS categoria, COUNT(*)::int AS cantidad
     FROM busquedas b
     JOIN categoria cat ON cat.id_categoria = b.id_categoria
     GROUP BY cat.id_categoria, cat.nombre_categoria
     ORDER BY cantidad DESC, cat.nombre_categoria
     LIMIT 5`
  );
  const comerciantes = await pool.query(
    `SELECT
       c.id_comerciante,
       u.nombre || ' ' || u.apellido AS usuario,
       COALESCE(pu.nombre_puesto, 'Sin puesto') AS puesto,
       c.estado,
       (SELECT COUNT(*)::int FROM producto_puesto pp WHERE pp.id_puesto = pu.id_puesto) AS productos
     FROM comerciante c
     JOIN usuario u ON u.id_usuario = c.id_usuario
     LEFT JOIN puesto pu ON pu.id_comerciante = c.id_comerciante
     ORDER BY c.id_comerciante`
  );

  const mapaEstados = { activo: 0, inactivo: 0 };
  for (const fila of estados.rows) {
    mapaEstados[fila.estado] = fila.cantidad;
  }

  return {
    ...fechaHoraActual(),
    porRol: porRol.rows,
    activos: mapaEstados.activo,
    inactivos: mapaEstados.inactivo,
    totales: totales.rows[0],
    categorias: categorias.rows,
    comerciantes: comerciantes.rows
  };
}

const ETIQUETA_ROL = { administrador: 'Administradores', comerciante: 'Comerciantes' };

function etiquetaRol(rol) {
  return ETIQUETA_ROL[rol] || rol;
}

function textoEstado(estado) {
  return estado === 'activo' ? 'Activo' : 'Inactivo';
}

// ---------------------------------------------------------------- PDF
function construirPdf(datos, res) {
  const PDFDocument = require('pdfkit');
  const doc = new PDFDocument({ size: 'A4', margin: 50 });
  doc.pipe(res);

  const ancho = doc.page.width - 100;

  doc.fontSize(20).font('Helvetica-Bold').text('FeriApp - Reporte del administrador');
  doc.moveDown(0.3);
  doc.fontSize(11).font('Helvetica')
    .text(`Feria 16 de Julio  |  Fecha: ${datos.fecha}  |  Hora: ${datos.hora}`);
  doc.moveDown(1);

  function titulo(texto) {
    doc.moveDown(0.6);
    doc.fontSize(14).font('Helvetica-Bold').text(texto);
    doc.moveDown(0.3);
    doc.fontSize(11).font('Helvetica');
  }

  titulo('Resumen general');
  const resumen = [
    ['Usuarios registrados hoy', datos.totales.registrados_hoy],
    ['Comerciantes activos', datos.activos],
    ['Comerciantes inactivos', datos.inactivos],
    ['Puestos registrados', datos.totales.puestos],
    ['Productos registrados', datos.totales.productos],
    ['Búsquedas registradas', datos.totales.busquedas]
  ];
  for (const [etiqueta, valor] of resumen) {
    doc.text(`${etiqueta}: ${valor}`);
  }

  titulo('Usuarios por rol');
  if (datos.porRol.length === 0) {
    doc.text('Sin datos.');
  }
  for (const fila of datos.porRol) {
    doc.text(`${etiquetaRol(fila.rol)}: ${fila.cantidad}`);
  }

  titulo('Categorías más buscadas');
  if (datos.categorias.length === 0) {
    doc.text('Aún no hay búsquedas registradas.');
  }
  datos.categorias.forEach((fila, indice) => {
    doc.text(`${indice + 1}. ${fila.categoria} (${fila.cantidad})`);
  });

  titulo('Comerciantes y puestos');
  const columnas = [
    { texto: 'ID', x: 50, ancho: 35 },
    { texto: 'Usuario', x: 85, ancho: 150 },
    { texto: 'Puesto', x: 235, ancho: 140 },
    { texto: 'Estado', x: 375, ancho: 70 },
    { texto: 'Productos', x: 445, ancho: 100 }
  ];

  function filaTabla(valores, negrita) {
    if (doc.y > doc.page.height - 80) {
      doc.addPage();
    }
    const y = doc.y;
    doc.font(negrita ? 'Helvetica-Bold' : 'Helvetica').fontSize(10);
    columnas.forEach((columna, i) => {
      doc.text(String(valores[i]), columna.x, y, {
        width: columna.ancho - 5, height: 14, ellipsis: true, lineBreak: false
      });
    });
    doc.y = y + 16;
    doc.x = 50;
  }

  filaTabla(columnas.map((c) => c.texto), true);
  doc.moveTo(50, doc.y - 2).lineTo(50 + ancho, doc.y - 2).stroke();
  if (datos.comerciantes.length === 0) {
    doc.font('Helvetica').text('No hay comerciantes registrados.');
  }
  for (const c of datos.comerciantes) {
    filaTabla([c.id_comerciante, c.usuario, c.puesto, textoEstado(c.estado), c.productos], false);
  }

  doc.end();
}

// --------------------------------------------------------------- Word
async function construirWord(datos) {
  const {
    Document, Packer, Paragraph, TextRun, HeadingLevel,
    Table, TableRow, TableCell, WidthType
  } = require('docx');

  const parrafo = (texto) => new Paragraph({ children: [new TextRun(texto)] });
  const encabezado = (texto) => new Paragraph({ text: texto, heading: HeadingLevel.HEADING_2, spacing: { before: 300, after: 120 } });

  const celda = (texto, negrita) => new TableCell({
    children: [new Paragraph({ children: [new TextRun({ text: String(texto), bold: !!negrita })] })]
  });
  const fila = (valores, negrita) => new TableRow({
    children: valores.map((valor) => celda(valor, negrita))
  });

  const hijos = [
    new Paragraph({ text: 'FeriApp - Reporte del administrador', heading: HeadingLevel.HEADING_1 }),
    parrafo(`Feria 16 de Julio  |  Fecha: ${datos.fecha}  |  Hora: ${datos.hora}`),

    encabezado('Resumen general'),
    parrafo(`Usuarios registrados hoy: ${datos.totales.registrados_hoy}`),
    parrafo(`Comerciantes activos: ${datos.activos}`),
    parrafo(`Comerciantes inactivos: ${datos.inactivos}`),
    parrafo(`Puestos registrados: ${datos.totales.puestos}`),
    parrafo(`Productos registrados: ${datos.totales.productos}`),
    parrafo(`Búsquedas registradas: ${datos.totales.busquedas}`),

    encabezado('Usuarios por rol')
  ];

  if (datos.porRol.length === 0) {
    hijos.push(parrafo('Sin datos.'));
  }
  for (const f of datos.porRol) {
    hijos.push(parrafo(`${etiquetaRol(f.rol)}: ${f.cantidad}`));
  }

  hijos.push(encabezado('Categorías más buscadas'));
  if (datos.categorias.length === 0) {
    hijos.push(parrafo('Aún no hay búsquedas registradas.'));
  }
  datos.categorias.forEach((f, i) => {
    hijos.push(parrafo(`${i + 1}. ${f.categoria} (${f.cantidad})`));
  });

  hijos.push(encabezado('Comerciantes y puestos'));
  if (datos.comerciantes.length === 0) {
    hijos.push(parrafo('No hay comerciantes registrados.'));
  } else {
    hijos.push(new Table({
      width: { size: 100, type: WidthType.PERCENTAGE },
      rows: [
        fila(['ID', 'Usuario', 'Puesto', 'Estado', 'Productos'], true),
        ...datos.comerciantes.map((c) =>
          fila([c.id_comerciante, c.usuario, c.puesto, textoEstado(c.estado), c.productos], false))
      ]
    }));
  }

  const documento = new Document({ sections: [{ children: hijos }] });
  return Packer.toBuffer(documento);
}

// ------------------------------------------------------------ endpoint
async function descargarReporte(req, res) {
  const formato = String(req.query.formato || 'pdf').toLowerCase();
  if (!['pdf', 'word'].includes(formato)) {
    return res.status(400).json({ error: "El formato debe ser 'pdf' o 'word'" });
  }

  let datos;
  try {
    datos = await reunirDatos();
  } catch (error) {
    console.error('Error al reunir los datos del reporte:', error);
    return res.status(500).json({ error: 'Error al generar el reporte' });
  }

  const nombreBase = `reporte-feriapp-${datos.sello}`;

  try {
    if (formato === 'pdf') {
      res.setHeader('Content-Type', 'application/pdf');
      res.setHeader('Content-Disposition', `attachment; filename="${nombreBase}.pdf"`);
      construirPdf(datos, res);
    } else {
      const buffer = await construirWord(datos);
      res.setHeader('Content-Type', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document');
      res.setHeader('Content-Disposition', `attachment; filename="${nombreBase}.docx"`);
      res.send(buffer);
    }
  } catch (error) {
    console.error('Error al construir el reporte:', error);
    if (error.code === 'MODULE_NOT_FOUND') {
      const libreria = formato === 'pdf' ? 'pdfkit' : 'docx';
      return res.status(500).json({ error: `Falta instalar la librería ${libreria} en el servidor` });
    }
    if (!res.headersSent) {
      res.status(500).json({ error: 'Error al construir el reporte' });
    }
  }
}

module.exports = { descargarReporte };
