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
import com.feriapp.app.databinding.FragmentoMapaBinding
import com.feriapp.app.util.LimitesFeria
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class FragmentoMapa : Fragment() {

    private var _binding: FragmentoMapaBinding? = null
    private val binding get() = _binding!!

    private var capaUbicacion: MyLocationNewOverlay? = null

    private val lanzadorPermisoUbicacion = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permisos ->
        val concedido = (permisos[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
                (permisos[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
        if (concedido) {
            activarUbicacionActual()
        }
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
        solicitarPermisoUbicacion()
    }

    private fun configurarMapa() {
        with(binding.mapaFeria) {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setScrollableAreaLimitDouble(LimitesFeria.cajaLimites)
            controller.setZoom(16.0)
            val puntoInicial = GeoPoint(-16.494905, -68.173642)
            controller.setCenter(puntoInicial)
        }
    }

    private fun solicitarPermisoUbicacion() {
        val contexto = requireContext()
        val tieneUbicacionPrecisa = ContextCompat.checkSelfPermission(
            contexto, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val tieneUbicacionAproximada = ContextCompat.checkSelfPermission(
            contexto, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (tieneUbicacionPrecisa || tieneUbicacionAproximada) {
            activarUbicacionActual()
        } else {
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
        val proveedorGps = GpsMyLocationProvider(requireContext())
        capaUbicacion = MyLocationNewOverlay(proveedorGps, mapa).apply {
            enableMyLocation()
        }
        mapa.overlays.add(capaUbicacion)
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
