package com.feriapp.app.ui.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.feriapp.app.R
import com.feriapp.app.data.model.Puesto
import com.feriapp.app.databinding.FragmentoMapaBinding
import com.feriapp.app.util.LimitesFeria
import com.feriapp.app.util.RutaUtil
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

data class MarcadorMapa(
    val id: Int,
    val latitud: Double,
    val longitud: Double,
    val titulo: String,
    val subtitulo: String? = null,
    val datosOriginales: Any? = null,
    val icono: Int? = null
)

class CapaMiUbicacion(
    proveedorGps: GpsMyLocationProvider,
    mapView: MapView
) : MyLocationNewOverlay(proveedorGps, mapView) {

    var alCambiarUbicacion: ((GeoPoint) -> Unit)? = null

    fun actualizarAzimut(azimut: Float) {
        val fix = lastFix
        if (fix != null) {
            val loc = Location(fix)
            loc.bearing = azimut
            setLocation(loc)
        }
    }

    override fun onLocationChanged(location: Location?, source: IMyLocationProvider?) {
        super.onLocationChanged(location, source)
        if (location != null) {
            val punto = GeoPoint(location.latitude, location.longitude)
            alCambiarUbicacion?.invoke(punto)
        }
    }
}

class FragmentoMapa : Fragment() {

    private var _binding: FragmentoMapaBinding? = null
    private val binding get() = _binding!!

    private var capaUbicacion: CapaMiUbicacion? = null
    private val marcadoresMapa = mutableListOf<Marker>()
    private var capaRuta: Polyline? = null
    private var modoAreaAmpliada: Boolean = false

    private val puntosRutaOriginal = mutableListOf<GeoPoint>()
    private var destinoRuta: GeoPoint? = null
    private var esRutaRecta: Boolean = false

    var alLlegarADestino: (() -> Unit)? = null
    var alDesviarseDeRuta: ((GeoPoint, GeoPoint) -> Unit)? = null

    private var ultimoTiempoActualizacionRuta: Long = 0L
    private var contadorDesviaciones: Int = 0
    private var ultimoTiempoRecalculo: Long = 0L

    private var sensorManager: SensorManager? = null
    private var sensorBrula: Sensor? = null
    private var azimutSuavizado: Float = 0f
    private var tieneAzimutIniciado: Boolean = false

    var marcadorResaltadoId: Int? = null

    private val oyenteSensorBrula = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientation)
                var azimutGrados = Math.toDegrees(orientation[0].toDouble()).toFloat()
                if (azimutGrados < 0) azimutGrados += 360f

                if (!tieneAzimutIniciado) {
                    azimutSuavizado = azimutGrados
                    tieneAzimutIniciado = true
                    capaUbicacion?.actualizarAzimut(azimutSuavizado)
                    _binding?.mapaFeria?.invalidate()
                } else {
                    val diferencia = Math.abs(azimutGrados - azimutSuavizado)
                    if (diferencia > 2.0f && diferencia < 358.0f) {
                        azimutSuavizado += 0.2f * (azimutGrados - azimutSuavizado)
                        capaUbicacion?.actualizarAzimut(azimutSuavizado)
                        _binding?.mapaFeria?.invalidate()
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    var ubicacionDeseada: Boolean = false
        set(value) {
            field = value
            if (_binding != null) {
                aplicarUbicacionDeseada()
            }
        }

    private var ultimaListaMarcadores: List<MarcadorMapa>? = null
    private var ultimoCentro: GeoPoint? = null
    private var ultimoZoom: Double? = null

    var alSeleccionarMarcador: ((id: Int, datos: Any?) -> Unit)? = null
    private var callbackUbicacion: ((Boolean) -> Unit)? = null

    private val lanzadorPermisoUbicacion = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permisos ->
        val concedido = (permisos[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
                (permisos[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
        if (concedido) {
            ubicacionDeseada = true
        } else {
            ubicacionDeseada = false
        }
        callbackUbicacion?.invoke(concedido)
        callbackUbicacion = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentoMapaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configurarMapa()
        restaurarEstadoMapa()
    }

    private fun configurarMapa() {
        with(binding.mapaFeria) {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setBuiltInZoomControls(false)
            setScrollableAreaLimitDouble(LimitesFeria.cajaLimites)
            minZoomLevel = 14.0
            maxZoomLevel = 20.0
            controller.setZoom(ultimoZoom ?: 16.0)
            val puntoInicial = ultimoCentro ?: GeoPoint(-16.494905, -68.173642)
            controller.setCenter(puntoInicial)

            val esModoOscuro = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            aplicarModoOscuroTiles(esModoOscuro)

            setOnClickListener {
                alSeleccionarMarcador?.invoke(-1, null)
            }
        }
    }

    fun aplicarModoOscuroTiles(activo: Boolean) {
        val mapa = _binding?.mapaFeria ?: return
        if (activo) {
            mapa.overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
        } else {
            mapa.overlayManager.tilesOverlay.setColorFilter(null)
        }
        mapa.invalidate()
    }

    private fun restaurarEstadoMapa() {
        if (ultimaListaMarcadores != null) {
            dibujarMarcadoresInterno()
        }
    }

    fun obtenerCentroActual(): GeoPoint? = _binding?.mapaFeria?.mapCenter as? GeoPoint

    fun obtenerZoomActual(): Double? = _binding?.mapaFeria?.zoomLevelDouble

    fun obtenerMiPosicion(): GeoPoint? = capaUbicacion?.myLocation

    fun fijarCamaraDirecta(latitud: Double, longitud: Double, zoom: Double) {
        val mapa = _binding?.mapaFeria ?: return
        val punto = GeoPoint(latitud, longitud)
        ultimoCentro = punto
        ultimoZoom = zoom
        mapa.controller.setZoom(zoom)
        mapa.controller.setCenter(punto)
    }

    fun ejecutarCuandoEsteListo(accion: () -> Unit) {
        val mapa = _binding?.mapaFeria
        if (mapa != null && mapa.isLayoutOccurred && mapa.width > 0 && mapa.height > 0) {
            accion()
        } else if (mapa != null) {
            mapa.addOnFirstLayoutListener { _, _, _, _, _ ->
                accion()
            }
        }
    }

    fun restaurarCentroYZoom(centro: GeoPoint?, zoom: Double?) {
        val mapa = _binding?.mapaFeria ?: return
        if (zoom != null) {
            mapa.controller.setZoom(zoom)
        }
        if (centro != null) {
            mapa.controller.animateTo(centro)
        }
    }

    fun activarUbicacion(alCompletar: ((Boolean) -> Unit)? = null) {
        val contexto = context ?: run {
            alCompletar?.invoke(false)
            return
        }
        val tienePrecisa = ContextCompat.checkSelfPermission(
            contexto, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val tieneAproximada = ContextCompat.checkSelfPermission(
            contexto, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (tienePrecisa || tieneAproximada) {
            ubicacionDeseada = true
            alCompletar?.invoke(true)
        } else {
            callbackUbicacion = alCompletar
            lanzadorPermisoUbicacion.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun aplicarUbicacionDeseada() {
        val mapa = _binding?.mapaFeria ?: return
        if (ubicacionDeseada) {
            Log.d("FeriApp", "FragmentoMapa: aplicando ubicacionDeseada = true")
            if (capaUbicacion == null || !mapa.overlays.contains(capaUbicacion)) {
                if (capaUbicacion != null) {
                    try {
                        capaUbicacion?.disableMyLocation()
                    } catch (e: Exception) {
                        Log.d("FeriApp", "Limpiando capa vieja de ubicacion: ${e.message}")
                    }
                    capaUbicacion = null
                }
                val proveedorGps = GpsMyLocationProvider(requireContext())
                capaUbicacion = CapaMiUbicacion(proveedorGps, mapa).apply {
                    enableMyLocation()

                    val drawableFlecha = ContextCompat.getDrawable(requireContext(), R.drawable.ic_flecha_ubicacion)
                    if (drawableFlecha != null) {
                        val bitmapFlecha = drawableToBitmap(drawableFlecha)
                        setDirectionIcon(bitmapFlecha)
                        setPersonIcon(bitmapFlecha)
                        setDirectionAnchor(0.5f, 0.5f)
                        setPersonAnchor(0.5f, 0.5f)
                    }

                    alCambiarUbicacion = { miPos ->
                        procesarCambioUbicacionParaRuta(miPos)
                    }
                }
                mapa.overlays.add(capaUbicacion)
            } else {
                capaUbicacion?.enableMyLocation()
            }
            activarBrulaSensores()
            mapa.invalidate()
        } else {
            Log.d("FeriApp", "FragmentoMapa: aplicando ubicacionDeseada = false")
            desactivarBrulaSensores()
            capaUbicacion?.let {
                try {
                    it.disableMyLocation()
                } catch (e: Exception) {
                    Log.d("FeriApp", "Error al deshabilitar ubicacion: ${e.message}")
                }
                mapa.overlays.remove(it)
            }
            capaUbicacion = null
            mapa.invalidate()
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth.coerceAtLeast(1),
            drawable.intrinsicHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun activarBrulaSensores() {
        if (sensorManager == null) {
            sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            sensorBrula = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        }
        sensorBrula?.let { sensor ->
            sensorManager?.registerListener(oyenteSensorBrula, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    private fun desactivarBrulaSensores() {
        sensorManager?.unregisterListener(oyenteSensorBrula)
    }

    private fun procesarCambioUbicacionParaRuta(miPos: GeoPoint) {
        val dest = destinoRuta ?: return
        if (puntosRutaOriginal.isEmpty()) return

        val ahora = System.currentTimeMillis()
        if (ahora - ultimoTiempoActualizacionRuta < 1000L) return
        ultimoTiempoActualizacionRuta = ahora

        // 1. Llegada al destino (< 15m)
        val distDestino = RutaUtil.distanciaEnMetros(miPos, dest)
        if (distDestino < 15.0) {
            Toast.makeText(requireContext(), R.string.msg_llegaste_a_destino, Toast.LENGTH_SHORT).show()
            val cb = alLlegarADestino
            limpiarRuta()
            cb?.invoke()
            return
        }

        // 2. Desviación de la ruta (> 40m)
        val distRuta = RutaUtil.distanciaMinimaARutaMetros(miPos, puntosRutaOriginal)
        if (distRuta > 40.0) {
            contadorDesviaciones++
            if (contadorDesviaciones >= 3 && (ahora - ultimoTiempoRecalculo >= 20000L)) {
                contadorDesviaciones = 0
                ultimoTiempoRecalculo = ahora
                Toast.makeText(requireContext(), R.string.msg_recalculando_ruta, Toast.LENGTH_SHORT).show()
                alDesviarseDeRuta?.invoke(miPos, dest)
                return
            }
        } else {
            contadorDesviaciones = 0
        }

        // 3. Recortar la polilínea desde la posición actual
        val polilinea = capaRuta ?: return
        if (esRutaRecta) {
            polilinea.setPoints(listOf(miPos, dest))
        } else {
            val recortados = RutaUtil.recortarRuta(puntosRutaOriginal, miPos)
            polilinea.setPoints(recortados)
        }
        _binding?.mapaFeria?.invalidate()
    }

    fun desactivarUbicacion() {
        ubicacionDeseada = false
    }

    fun centrarEnMiUbicacion(): Boolean {
        val mapa = _binding?.mapaFeria ?: return false
        val miUbicacion = capaUbicacion?.myLocation ?: return false
        ultimoCentro = miUbicacion
        mapa.controller.animateTo(miUbicacion)
        return true
    }

    fun acercar() {
        _binding?.mapaFeria?.let { mapa ->
            mapa.controller.zoomIn()
            ultimoZoom = mapa.zoomLevelDouble
        }
    }

    fun alejar() {
        _binding?.mapaFeria?.let { mapa ->
            mapa.controller.zoomOut()
            ultimoZoom = mapa.zoomLevelDouble
        }
    }

    fun centrarEn(latitud: Double, longitud: Double, zoom: Double? = null) {
        val punto = GeoPoint(latitud, longitud)
        ultimoCentro = punto
        if (zoom != null) {
            ultimoZoom = zoom
        }
        val mapa = _binding?.mapaFeria ?: return
        if (zoom != null) {
            mapa.controller.setZoom(zoom)
        }
        mapa.controller.animateTo(punto)
    }

    fun mostrarMarcadoresPuestos(puestos: List<Puesto>) {
        val marcadores = puestos.map {
            MarcadorMapa(
                id = it.idPuesto,
                latitud = it.latitud,
                longitud = it.longitud,
                titulo = it.nombrePuesto,
                subtitulo = null,
                datosOriginales = it
            )
        }
        mostrarMarcadores(marcadores)
    }

    fun mostrarMarcadores(marcadores: List<MarcadorMapa>) {
        ultimaListaMarcadores = marcadores
        if (_binding != null) {
            dibujarMarcadoresInterno()
        }
    }

    fun resaltarMarcador(id: Int?) {
        marcadorResaltadoId = id
        val mapa = _binding?.mapaFeria ?: return
        var marcadorDestacado: Marker? = null

        marcadoresMapa.forEach { marcador ->
            val idMarcadorInt = marcador.id?.toIntOrNull()
            val mData = ultimaListaMarcadores?.find { it.id == idMarcadorInt }
            if (mData?.icono == null) {
                if (idMarcadorInt != null && idMarcadorInt == id) {
                    marcador.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_marcador_comercio_seleccionado)
                    marcador.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    marcadorDestacado = marcador
                } else {
                    marcador.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_marcador_comercio)
                    marcador.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                }
            }
        }

        if (marcadorDestacado != null) {
            mapa.overlays.remove(marcadorDestacado)
            mapa.overlays.add(marcadorDestacado)
        }
        mapa.invalidate()
    }

    fun centrarEnMarcadores(marcadores: List<MarcadorMapa>) {
        val mapa = _binding?.mapaFeria ?: return
        if (marcadores.isEmpty()) return

        var minLat = 90.0
        var maxLat = -90.0
        var minLng = 180.0
        var maxLng = -180.0

        marcadores.forEach { m ->
            if (m.latitud < minLat) minLat = m.latitud
            if (m.latitud > maxLat) maxLat = m.latitud
            if (m.longitud < minLng) minLng = m.longitud
            if (m.longitud > maxLng) maxLng = m.longitud
        }

        val latPadding = (maxLat - minLat).coerceAtLeast(0.002) * 0.2
        val lngPadding = (maxLng - minLng).coerceAtLeast(0.002) * 0.2

        val box = BoundingBox(
            (maxLat + latPadding).coerceAtMost(LimitesFeria.LATITUD_MAXIMA),
            (maxLng + lngPadding).coerceAtMost(LimitesFeria.LONGITUD_MAXIMA),
            (minLat - latPadding).coerceAtLeast(LimitesFeria.LATITUD_MINIMA),
            (minLng - lngPadding).coerceAtLeast(LimitesFeria.LONGITUD_MINIMA)
        )

        mapa.zoomToBoundingBox(box, true)
    }

    fun dibujarRuta(puntos: List<GeoPoint>) {
        val mapa = _binding?.mapaFeria ?: return
        limpiarRuta()

        puntosRutaOriginal.clear()
        puntosRutaOriginal.addAll(puntos)
        destinoRuta = puntos.lastOrNull()
        esRutaRecta = false
        contadorDesviaciones = 0

        val colorRuta = ContextCompat.getColor(requireContext(), R.color.color_secundario)
        val anchoPx = (7 * resources.displayMetrics.density)
        val polilinea = Polyline(mapa).apply {
            outlinePaint.color = colorRuta
            outlinePaint.strokeWidth = anchoPx
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
            setPoints(puntos)
        }
        capaRuta = polilinea
        mapa.overlays.add(polilinea)
        mapa.invalidate()
    }

    fun dibujarRutaRecta(origen: GeoPoint, destino: GeoPoint) {
        val mapa = _binding?.mapaFeria ?: return
        limpiarRuta()

        puntosRutaOriginal.clear()
        puntosRutaOriginal.add(origen)
        puntosRutaOriginal.add(destino)
        destinoRuta = destino
        esRutaRecta = true
        contadorDesviaciones = 0

        val colorRuta = ContextCompat.getColor(requireContext(), R.color.color_secundario)
        val anchoPx = (7 * resources.displayMetrics.density)
        val polilinea = Polyline(mapa).apply {
            outlinePaint.color = colorRuta
            outlinePaint.strokeWidth = anchoPx
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
            outlinePaint.pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
            setPoints(listOf(origen, destino))
        }
        capaRuta = polilinea
        mapa.overlays.add(polilinea)
        mapa.invalidate()
    }

    fun limpiarRuta() {
        val mapa = _binding?.mapaFeria ?: return
        capaRuta?.let {
            mapa.overlays.remove(it)
        }
        capaRuta = null
        puntosRutaOriginal.clear()
        destinoRuta = null
        esRutaRecta = false
        contadorDesviaciones = 0

        if (modoAreaAmpliada) {
            modoAreaAmpliada = false
            mapa.setScrollableAreaLimitDouble(LimitesFeria.cajaLimites)
            mapa.minZoomLevel = 14.0
        }

        mapa.invalidate()
    }

    fun ajustarEncuadrePuntos(puntos: List<GeoPoint>) {
        val mapa = _binding?.mapaFeria ?: return
        if (puntos.isEmpty()) return

        val hayPuntoFuera = puntos.any { !LimitesFeria.estaDentro(it.latitude, it.longitude) }
        if (hayPuntoFuera) {
            modoAreaAmpliada = true
            mapa.setScrollableAreaLimitDouble(null)
            mapa.minZoomLevel = 6.0
        }

        var minLat = 90.0
        var maxLat = -90.0
        var minLng = 180.0
        var maxLng = -180.0

        puntos.forEach { p ->
            if (p.latitude < minLat) minLat = p.latitude
            if (p.latitude > maxLat) maxLat = p.latitude
            if (p.longitude < minLng) minLng = p.longitude
            if (p.longitude > maxLng) maxLng = p.longitude
        }

        val latPadding = (maxLat - minLat).coerceAtLeast(0.001) * 0.25
        val lngPadding = (maxLng - minLng).coerceAtLeast(0.001) * 0.25

        val box = BoundingBox(
            maxLat + latPadding,
            maxLng + lngPadding,
            minLat - latPadding,
            minLng - lngPadding
        )

        mapa.zoomToBoundingBox(box, true)
    }

    private fun dibujarMarcadoresInterno() {
        val mapa = _binding?.mapaFeria ?: return
        val marcadores = ultimaListaMarcadores ?: return

        marcadoresMapa.forEach { mapa.overlays.remove(it) }
        marcadoresMapa.clear()

        var marcadorDestacado: Marker? = null

        marcadores.forEach { m ->
            val esDestacado = (marcadorResaltadoId != null && m.id == marcadorResaltadoId)
            val marcador = Marker(mapa).apply {
                position = GeoPoint(m.latitud, m.longitud)
                title = m.titulo
                snippet = m.subtitulo
                id = m.id.toString()
                if (m.icono != null) {
                    icon = ContextCompat.getDrawable(requireContext(), m.icono)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                } else {
                    val iconoRes = if (esDestacado) {
                        R.drawable.ic_marcador_comercio_seleccionado
                    } else {
                        R.drawable.ic_marcador_comercio
                    }
                    icon = ContextCompat.getDrawable(requireContext(), iconoRes)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                }
                setOnMarkerClickListener { _, _ ->
                    alSeleccionarMarcador?.invoke(m.id, m.datosOriginales)
                    true
                }
            }
            marcadoresMapa.add(marcador)
            if (esDestacado && m.icono == null) {
                marcadorDestacado = marcador
            } else {
                mapa.overlays.add(marcador)
            }
        }

        marcadorDestacado?.let {
            mapa.overlays.add(it)
        }
        mapa.invalidate()
    }

    fun limpiarMarcadores() {
        ultimaListaMarcadores = null
        marcadorResaltadoId = null
        val mapa = _binding?.mapaFeria ?: return
        marcadoresMapa.forEach { mapa.overlays.remove(it) }
        marcadoresMapa.clear()
        mapa.invalidate()
    }

    override fun onResume() {
        super.onResume()
        _binding?.mapaFeria?.onResume()
        aplicarUbicacionDeseada()
        if (ubicacionDeseada) {
            activarBrulaSensores()
        }
        if (ultimaListaMarcadores != null) {
            dibujarMarcadoresInterno()
        }
    }

    override fun onPause() {
        super.onPause()
        desactivarBrulaSensores()
        try {
            capaUbicacion?.disableMyLocation()
        } catch (e: Exception) {
            Log.d("FeriApp", "Error al deshabilitar ubicacion en onPause: ${e.message}")
        }
        _binding?.mapaFeria?.let { mapa ->
            ultimoCentro = mapa.mapCenter as? GeoPoint ?: ultimoCentro
            ultimoZoom = mapa.zoomLevelDouble
            mapa.onPause()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        desactivarBrulaSensores()
        try {
            capaUbicacion?.disableMyLocation()
        } catch (e: Exception) {
            Log.d("FeriApp", "Error al deshabilitar ubicacion en onDestroyView: ${e.message}")
        }
        _binding?.mapaFeria?.onDetach()

        capaUbicacion = null
        capaRuta = null
        marcadoresMapa.clear()

        _binding = null
    }
}
