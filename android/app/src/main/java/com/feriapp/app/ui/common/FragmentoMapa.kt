package com.feriapp.app.ui.common

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.feriapp.app.data.model.Puesto
import com.feriapp.app.databinding.FragmentoMapaBinding
import com.feriapp.app.util.LimitesFeria
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class FragmentoMapa : Fragment() {

    private var _binding: FragmentoMapaBinding? = null
    private val binding get() = _binding!!

    private var capaUbicacion: MyLocationNewOverlay? = null
    private val marcadoresPuestos = mutableListOf<Marker>()

    var alSeleccionarMarcador: ((idPuesto: Int) -> Unit)? = null
    private var callbackUbicacion: ((Boolean) -> Unit)? = null

    private val lanzadorPermisoUbicacion = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permisos ->
        val concedido = (permisos[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
                (permisos[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
        if (concedido) {
            activarUbicacionActual()
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
    }

    private fun configurarMapa() {
        with(binding.mapaFeria) {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setBuiltInZoomControls(false)
            setScrollableAreaLimitDouble(LimitesFeria.cajaLimites)
            minZoomLevel = 13.0
            maxZoomLevel = 19.0
            controller.setZoom(16.0)
            val puntoInicial = GeoPoint(-16.494905, -68.173642)
            controller.setCenter(puntoInicial)
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
            activarUbicacionActual()
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

    private fun activarUbicacionActual() {
        val mapa = _binding?.mapaFeria ?: return
        if (capaUbicacion == null) {
            val proveedorGps = GpsMyLocationProvider(requireContext())
            capaUbicacion = MyLocationNewOverlay(proveedorGps, mapa).apply {
                enableMyLocation()
            }
            mapa.overlays.add(capaUbicacion)
        } else {
            capaUbicacion?.enableMyLocation()
        }
        mapa.invalidate()
    }

    fun desactivarUbicacion() {
        val mapa = _binding?.mapaFeria ?: return
        capaUbicacion?.let {
            it.disableMyLocation()
            mapa.overlays.remove(it)
        }
        capaUbicacion = null
        mapa.invalidate()
    }

    fun centrarEnMiUbicacion(): Boolean {
        val mapa = _binding?.mapaFeria ?: return false
        val miUbicacion = capaUbicacion?.myLocation ?: return false
        mapa.controller.animateTo(miUbicacion)
        return true
    }

    fun acercar() {
        _binding?.mapaFeria?.controller?.zoomIn()
    }

    fun alejar() {
        _binding?.mapaFeria?.controller?.zoomOut()
    }

    fun centrarEn(latitud: Double, longitud: Double, zoom: Double? = null) {
        val mapa = _binding?.mapaFeria ?: return
        val punto = GeoPoint(latitud, longitud)
        if (zoom != null) {
            mapa.controller.setZoom(zoom)
        }
        mapa.controller.animateTo(punto)
    }

    fun mostrarMarcadores(puestos: List<Puesto>) {
        val mapa = _binding?.mapaFeria ?: return
        limpiarMarcadores()

        puestos.forEach { puesto ->
            val marcador = Marker(mapa).apply {
                position = GeoPoint(puesto.latitud, puesto.longitud)
                title = puesto.nombrePuesto
                snippet = puesto.nombreComercial
                id = puesto.idPuesto.toString()
                setOnMarkerClickListener { _, _ ->
                    alSeleccionarMarcador?.invoke(puesto.idPuesto)
                    true
                }
            }
            marcadoresPuestos.add(marcador)
            mapa.overlays.add(marcador)
        }
        mapa.invalidate()
    }

    fun limpiarMarcadores() {
        val mapa = _binding?.mapaFeria ?: return
        marcadoresPuestos.forEach { mapa.overlays.remove(it) }
        marcadoresPuestos.clear()
        mapa.invalidate()
    }

    override fun onResume() {
        super.onResume()
        _binding?.mapaFeria?.onResume()
    }

    override fun onPause() {
        super.onPause()
        _binding?.mapaFeria?.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        capaUbicacion?.disableMyLocation()
        _binding?.mapaFeria?.onDetach()
        _binding = null
    }
}
