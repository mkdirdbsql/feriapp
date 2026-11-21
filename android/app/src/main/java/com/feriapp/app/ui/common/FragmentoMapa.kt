package com.feriapp.app.ui.common

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.feriapp.app.R
import com.feriapp.app.data.model.Puesto
import com.feriapp.app.databinding.FragmentoMapaBinding
import com.feriapp.app.util.LimitesFeria
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

data class MarcadorMapa(
    val id: Int,
    val latitud: Double,
    val longitud: Double,
    val titulo: String,
    val subtitulo: String? = null,
    val datosOriginales: Any? = null
)

class FragmentoMapa : Fragment() {

    private var _binding: FragmentoMapaBinding? = null
    private val binding get() = _binding!!

    private var capaUbicacion: MyLocationNewOverlay? = null
    private val marcadoresMapa = mutableListOf<Marker>()
    private var capaRuta: Polyline? = null
    private var modoAreaAmpliada: Boolean = false

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
            if (capaUbicacion == null) {
                val proveedorGps = GpsMyLocationProvider(requireContext())
                capaUbicacion = MyLocationNewOverlay(proveedorGps, mapa).apply {
                    enableMyLocation()
                }
                mapa.overlays.add(capaUbicacion)
            } else {
                capaUbicacion?.enableMyLocation()
                if (!mapa.overlays.contains(capaUbicacion)) {
                    mapa.overlays.add(capaUbicacion)
                }
            }
            mapa.invalidate()
        } else {
            Log.d("FeriApp", "FragmentoMapa: aplicando ubicacionDeseada = false")
            capaUbicacion?.let {
                it.disableMyLocation()
                mapa.overlays.remove(it)
            }
            capaUbicacion = null
            mapa.invalidate()
        }
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
                subtitulo = it.nombreComercial,
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

        marcadores.forEach { m ->
            val marcador = Marker(mapa).apply {
                position = GeoPoint(m.latitud, m.longitud)
                title = m.titulo
                snippet = m.subtitulo
                id = m.id.toString()
                setOnMarkerClickListener { _, _ ->
                    alSeleccionarMarcador?.invoke(m.id, m.datosOriginales)
                    true
                }
            }
            marcadoresMapa.add(marcador)
            mapa.overlays.add(marcador)
        }
        mapa.invalidate()
    }

    fun limpiarMarcadores() {
        ultimaListaMarcadores = null
        val mapa = _binding?.mapaFeria ?: return
        marcadoresMapa.forEach { mapa.overlays.remove(it) }
        marcadoresMapa.clear()
        mapa.invalidate()
    }

    override fun onResume() {
        super.onResume()
        _binding?.mapaFeria?.onResume()
        aplicarUbicacionDeseada()
        if (ultimaListaMarcadores != null) {
            dibujarMarcadoresInterno()
        }
    }

    override fun onPause() {
        super.onPause()
        capaUbicacion?.disableMyLocation()
        _binding?.mapaFeria?.let { mapa ->
            ultimoCentro = mapa.mapCenter as? GeoPoint ?: ultimoCentro
            ultimoZoom = mapa.zoomLevelDouble
            mapa.onPause()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        capaUbicacion?.disableMyLocation()
        _binding?.mapaFeria?.onDetach()
        _binding = null
    }
}
