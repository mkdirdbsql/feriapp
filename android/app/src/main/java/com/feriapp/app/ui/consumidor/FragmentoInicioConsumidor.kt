package com.feriapp.app.ui.consumidor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.feriapp.app.AplicacionFeria
import com.feriapp.app.R
import com.feriapp.app.data.model.Categoria
import com.feriapp.app.data.model.ProductoBuscado
import com.feriapp.app.data.model.Puesto
import com.feriapp.app.databinding.FragmentoInicioConsumidorBinding
import com.feriapp.app.ui.common.FragmentoMapa
import com.feriapp.app.ui.common.fabricaViewModel
import com.feriapp.app.util.construirUrlImagen
import com.google.android.material.chip.Chip
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

    private val busquedaViewModel: BusquedaViewModel by viewModels {
        fabricaViewModel {
            val contenedor = (requireActivity().application as AplicacionFeria).contenedorApp
            BusquedaViewModel(contenedor.repositorioCategorias, contenedor.repositorioProductos)
        }
    }

    private var fragmentoMapa: FragmentoMapa? = null
    private var listaPuestosCargados: List<Puesto> = emptyList()
    private var modoBusquedaActivo = false
    private var navegandoAResultados = false

    private lateinit var adaptadorSugerencias: AdaptadorSugerencias

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

        configurarEnfoqueInicial()
        configurarNavegacion()
        configurarControlesUbicacion()
        configurarControlesZoom()
        configurarTarjetaPuesto()
        configurarModoBusqueda()
        configurarObservadorTecladoEInsets()
        configurarBotonAtras()
        observarEstadoViewModel()
    }

    private fun configurarEnfoqueInicial() {
        binding.contenedorPrincipal.requestFocus()
        binding.etBuscarProducto.clearFocus()
    }

    override fun onResume() {
        super.onResume()
        navegandoAResultados = false
        if (modoBusquedaActivo) {
            desactivarModoBusqueda()
        }
        configurarEnfoqueInicial()

        when (val estadoActual = viewModel.estado.value) {
            is EstadoInicioConsumidor.Exito -> {
                listaPuestosCargados = estadoActual.puestos
                fragmentoMapa?.mostrarMarcadores(estadoActual.puestos)
            }
            is EstadoInicioConsumidor.Error -> {
                viewModel.cargarPuestos()
            }
            else -> {}
        }
    }

    private fun configurarNavegacion() {
        binding.btnMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    private fun configurarModoBusqueda() {
        adaptadorSugerencias = AdaptadorSugerencias { producto ->
            navegandoAResultados = true
            busquedaViewModel.registrarBusqueda(producto.nombreProducto, producto.idCategoria)
            val bundle = bundleOf("nombre" to producto.nombreProducto, "idCategoria" to producto.idCategoria)
            desactivarModoBusqueda()
            findNavController().navigate(R.id.accionInicioConsumidorAResultados, bundle)
        }
        binding.rvSugerencias.adapter = adaptadorSugerencias

        binding.etBuscarProducto.setOnClickListener {
            activarModoBusqueda()
        }

        binding.etBuscarProducto.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                v.performClick()
                activarModoBusqueda()
            }
            false
        }

        binding.btnVolverBusqueda.setOnClickListener {
            desactivarModoBusqueda()
        }

        binding.capaOscuraBusqueda.setOnClickListener {
            desactivarModoBusqueda()
        }

        binding.etBuscarProducto.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString().orEmpty()
                if (modoBusquedaActivo) {
                    busquedaViewModel.buscarSugerencias(query)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etBuscarProducto.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val texto = binding.etBuscarProducto.text?.toString()?.trim().orEmpty()
                if (texto.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.escribe_algo_buscar, Toast.LENGTH_SHORT).show()
                } else {
                    navegandoAResultados = true
                    busquedaViewModel.registrarBusqueda(texto, null)
                    val bundle = bundleOf("nombre" to texto, "idCategoria" to -1)
                    desactivarModoBusqueda()
                    findNavController().navigate(R.id.accionInicioConsumidorAResultados, bundle)
                }
                true
            } else {
                false
            }
        }
    }

    private fun activarModoBusqueda() {
        if (modoBusquedaActivo) return
        modoBusquedaActivo = true

        binding.btnVolverBusqueda.visibility = View.VISIBLE
        binding.capaOscuraBusqueda.visibility = View.VISIBLE
        binding.panelBusqueda.visibility = View.VISIBLE

        binding.etBuscarProducto.requestFocus()
        mostrarTeclado(binding.etBuscarProducto)

        busquedaViewModel.cargarCategorias()
    }

    private fun desactivarModoBusqueda() {
        if (!modoBusquedaActivo) return
        modoBusquedaActivo = false

        ocultarTeclado()
        binding.etBuscarProducto.clearFocus()
        binding.etBuscarProducto.setText("")
        binding.contenedorPrincipal.requestFocus()

        binding.btnVolverBusqueda.visibility = View.GONE
        binding.capaOscuraBusqueda.visibility = View.GONE
        binding.panelBusqueda.visibility = View.GONE
        binding.contenedorSugerencias.visibility = View.GONE
        binding.tvSinCoincidencias.visibility = View.GONE
        binding.contenedorCategorias.visibility = View.VISIBLE
        binding.barraInferior.visibility = View.VISIBLE
    }

    private fun configurarObservadorTecladoEInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val tecladoVisible = insets.isVisible(WindowInsetsCompat.Type.ime())

            // Ocultar barra inferior por completo cuando el teclado esté visible
            binding.barraInferior.visibility = if (tecladoVisible) View.GONE else View.VISIBLE

            // Si el teclado se oculta mientras el modo búsqueda está activo (y no estamos navegando), desactivar el modo búsqueda
            if (!tecladoVisible && modoBusquedaActivo && !navegandoAResultados) {
                desactivarModoBusqueda()
            }
            insets
        }
    }

    private fun mostrarTeclado(view: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun ocultarTeclado() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    private fun mostrarCategorias(categorias: List<Categoria>) {
        binding.contenedorCategorias.visibility = View.VISIBLE
        binding.contenedorSugerencias.visibility = View.GONE
        binding.tvSinCoincidencias.visibility = View.GONE

        binding.chipGroupCategorias.removeAllViews()
        categorias.forEach { categoria ->
            val chip = Chip(requireContext()).apply {
                text = categoria.nombreCategoria
                isCheckable = false
                isClickable = true
                setOnClickListener {
                    navegandoAResultados = true
                    busquedaViewModel.registrarBusqueda(categoria.nombreCategoria, categoria.idCategoria)
                    val bundle = bundleOf("nombre" to "", "idCategoria" to categoria.idCategoria)
                    desactivarModoBusqueda()
                    findNavController().navigate(R.id.accionInicioConsumidorAResultados, bundle)
                }
            }
            binding.chipGroupCategorias.addView(chip)
        }
    }

    private fun mostrarSugerencias(sugerencias: List<ProductoBuscado>, sinCoincidencias: Boolean) {
        if (sinCoincidencias) {
            binding.contenedorCategorias.visibility = View.VISIBLE
            binding.contenedorSugerencias.visibility = View.GONE
            binding.tvSinCoincidencias.visibility = View.VISIBLE
        } else {
            binding.contenedorCategorias.visibility = View.GONE
            binding.tvSinCoincidencias.visibility = View.GONE
            binding.contenedorSugerencias.visibility = View.VISIBLE

            adaptadorSugerencias.actualizarLista(sugerencias)
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
                    } else if (modoBusquedaActivo) {
                        desactivarModoBusqueda()
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
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                busquedaViewModel.estado.collect { estado ->
                    when (estado) {
                        is EstadoBusqueda.CargandoCategorias -> {}
                        is EstadoBusqueda.ListoCategorias -> {
                            if (modoBusquedaActivo) {
                                mostrarCategorias(estado.categorias)
                            }
                        }
                        is EstadoBusqueda.Sugerencias -> {
                            if (modoBusquedaActivo) {
                                mostrarSugerencias(estado.sugerencias, estado.sinCoincidencias)
                            }
                        }
                        is EstadoBusqueda.Error -> {
                            Toast.makeText(requireContext(), estado.mensaje, Toast.LENGTH_LONG).show()
                        }
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
