package com.feriapp.app.ui.consumidor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.feriapp.app.AplicacionFeria
import com.feriapp.app.R
import com.feriapp.app.data.model.Puesto
import com.feriapp.app.databinding.FragmentoInicioConsumidorBinding
import com.feriapp.app.ui.common.FragmentoMapa
import com.feriapp.app.ui.common.fabricaViewModel
import com.feriapp.app.util.construirUrlImagen
import kotlinx.coroutines.launch

class FragmentoInicioConsumidor : Fragment() {

    private var _binding: FragmentoInicioConsumidorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InicioConsumidorViewModel by viewModels {
        fabricaViewModel {
            val contenedor = (requireActivity().application as AplicacionFeria).contenedorApp
            InicioConsumidorViewModel(contenedor.repositorioPuestos)
        }
    }

    private var fragmentoMapa: FragmentoMapa? = null
    private var listaPuestosCargados: List<Puesto> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentoInicioConsumidorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragmentoMapa = childFragmentManager.findFragmentById(R.id.contenedorMapa) as? FragmentoMapa

        configurarNavegacion()
        configurarControlesUbicacion()
        configurarControlesZoom()
        configurarTarjetaPuesto()
        configurarBotonAtras()
        observarEstadoViewModel()
    }

    private fun configurarNavegacion() {
        binding.cardBarraBusqueda.setOnClickListener {
            findNavController().navigate(R.id.accionInicioConsumidorABusqueda)
        }

        binding.btnMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    private fun configurarControlesUbicacion() {
        val tienePermisoPrevio = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        binding.switchUbicacion.isChecked = tienePermisoPrevio
        if (tienePermisoPrevio) {
            fragmentoMapa?.activarUbicacion()
        }

        binding.switchUbicacion.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!esGpsHabilitado()) {
                    binding.switchUbicacion.isChecked = false
                    Toast.makeText(requireContext(), R.string.aviso_gps_desactivado, Toast.LENGTH_SHORT).show()
                    return@setOnCheckedChangeListener
                }

                fragmentoMapa?.activarUbicacion { concedido ->
                    if (!concedido) {
                        binding.switchUbicacion.isChecked = false
                        Toast.makeText(requireContext(), R.string.aviso_permiso_denegado, Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                fragmentoMapa?.desactivarUbicacion()
            }
        }

        binding.btnMiUbicacion.setOnClickListener {
            if (!binding.switchUbicacion.isChecked) {
                Toast.makeText(requireContext(), R.string.activa_ubicacion_primero, Toast.LENGTH_SHORT).show()
            } else {
                val centrado = fragmentoMapa?.centrarEnMiUbicacion() ?: false
                if (!centrado) {
                    Toast.makeText(requireContext(), R.string.buscando_posicion, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun esGpsHabilitado(): Boolean {
        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun configurarControlesZoom() {
        binding.btnAcercar.setOnClickListener {
            fragmentoMapa?.acercar()
        }

        binding.btnAlejar.setOnClickListener {
            fragmentoMapa?.alejar()
        }
    }

    private fun configurarTarjetaPuesto() {
        fragmentoMapa?.alSeleccionarMarcador = { idPuesto ->
            val puesto = listaPuestosCargados.find { it.idPuesto == idPuesto }
            if (puesto != null) {
                mostrarDetallePuesto(puesto)
            }
        }

        binding.btnCerrarTarjeta.setOnClickListener {
            binding.tarjetaPuesto.visibility = View.GONE
        }
    }

    private fun mostrarDetallePuesto(puesto: Puesto) {
        with(binding) {
            tvNombrePuesto.text = puesto.nombrePuesto
            tvNombreComerciante.text = puesto.nombreComercial
            tarjetaPuesto.visibility = View.VISIBLE

            val urlImagen = construirUrlImagen(puesto.imagenUrl)
            Glide.with(this@FragmentoInicioConsumidor)
                .load(urlImagen)
                .placeholder(R.drawable.ic_puesto_placeholder)
                .error(R.drawable.ic_puesto_placeholder)
                .into(imgPuesto)
        }
    }

    private fun configurarBotonAtras() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                        binding.drawerLayout.closeDrawer(GravityCompat.START)
                    } else if (binding.tarjetaPuesto.visibility == View.VISIBLE) {
                        binding.tarjetaPuesto.visibility = View.GONE
                    } else {
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )
    }

    private fun observarEstadoViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.estado.collect { estado ->
                when (estado) {
                    is EstadoInicioConsumidor.Cargando -> {
                        binding.indicadorCargandoPuestos.visibility = View.VISIBLE
                    }
                    is EstadoInicioConsumidor.Exito -> {
                        binding.indicadorCargandoPuestos.visibility = View.GONE
                        listaPuestosCargados = estado.puestos
                        fragmentoMapa?.mostrarMarcadores(estado.puestos)
                    }
                    is EstadoInicioConsumidor.Error -> {
                        binding.indicadorCargandoPuestos.visibility = View.GONE
                        Toast.makeText(requireContext(), estado.mensaje, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
