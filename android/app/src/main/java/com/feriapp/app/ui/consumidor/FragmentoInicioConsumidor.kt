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
import com.feriapp.app.ui.common.MarcadorMapa
import com.feriapp.app.ui.common.fabricaViewModel
import com.feriapp.app.util.construirUrlImagen
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.util.Locale

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
    private var modoResultadosActivo = false
    private var navegandoADetalle = false

    private var puestoNormalSeleccionado: Puesto? = null
    private var productoResultadoSeleccionado: ProductoBuscado? = null

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
        if (modoBusquedaActivo && !navegandoADetalle) {
            desactivarModoBusqueda()
        }

        if (viewModel.camaraAlSalir != null) {
            val camara = viewModel.camaraAlSalir!!
            fragmentoMapa?.ejecutarCuandoEsteListo {
                fragmentoMapa?.fijarCamaraDirecta(camara.latitud, camara.longitud, camara.zoom)
            }
        }

        if (!modoResultadosActivo) {
            configurarEnfoqueInicial()
            if (puestoNormalSeleccionado != null) {
                mostrarDetallePuesto(puestoNormalSeleccionado!!)
            }
        } else if (productoResultadoSeleccionado != null) {
            mostrarDetalleResultado(productoResultadoSeleccionado!!)
        }
        navegandoADetalle = false

        if (!modoResultadosActivo) {
            when (val estadoActual = viewModel.estado.value) {
                is EstadoInicioConsumidor.Exito -> {
                    listaPuestosCargados = estadoActual.puestos
                    fragmentoMapa?.mostrarMarcadoresPuestos(estadoActual.puestos)
                }
                is EstadoInicioConsumidor.Error -> {
                    viewModel.cargarPuestos()
                }
                else -> {}
            }
        }
    }

    override fun onPause() {
        super.onPause()
        val centro = fragmentoMapa?.obtenerCentroActual()
        val zoom = fragmentoMapa?.obtenerZoomActual()
        if (centro != null && zoom != null) {
            viewModel.camaraAlSalir = CamaraMapa(centro.latitude, centro.longitude, zoom)
        }
    }

    private fun configurarNavegacion() {
        binding.btnMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.btnLimpiarBusqueda.setOnClickListener {
            desactivarModoResultados()
        }
    }

    private fun configurarModoBusqueda() {
        adaptadorSugerencias = AdaptadorSugerencias { producto ->
            ejecutarBusquedaResultados(
                nombre = producto.nombreProducto,
                idCategoria = producto.idCategoria,
                terminoMostrar = producto.nombreProducto
            )
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
                    ejecutarBusquedaResultados(
                        nombre = texto,
                        idCategoria = -1,
                        terminoMostrar = texto
                    )
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

        binding.tarjetaPuesto.visibility = View.GONE
        puestoNormalSeleccionado = null
        productoResultadoSeleccionado = null

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
        if (!modoResultadosActivo) {
            binding.etBuscarProducto.setText("")
        }
        binding.contenedorPrincipal.requestFocus()

        binding.btnVolverBusqueda.visibility = View.GONE
        binding.capaOscuraBusqueda.visibility = View.GONE
        binding.panelBusqueda.visibility = View.GONE
        binding.contenedorSugerencias.visibility = View.GONE
        binding.tvSinCoincidencias.visibility = View.GONE
        binding.contenedorCategorias.visibility = View.VISIBLE
        binding.barraInferior.visibility = View.VISIBLE
    }

    private fun ejecutarBusquedaResultados(nombre: String?, idCategoria: Int?, terminoMostrar: String) {
        if (!modoResultadosActivo) {
            val centro = fragmentoMapa?.obtenerCentroActual()
            val zoom = fragmentoMapa?.obtenerZoomActual()
            if (centro != null && zoom != null) {
                viewModel.camaraAntesDeBuscar = CamaraMapa(centro.latitude, centro.longitude, zoom)
            }
        }
        viewModel.busquedaNuevaRealizada = true

        busquedaViewModel.registrarBusqueda(terminoMostrar, idCategoria)
        desactivarModoBusqueda()

        binding.etBuscarProducto.setText(terminoMostrar)
        binding.etBuscarProducto.clearFocus()
        binding.btnLimpiarBusqueda.visibility = View.VISIBLE

        modoResultadosActivo = true
        busquedaViewModel.buscarResultados(nombre, idCategoria, terminoMostrar)
    }

    private fun desactivarModoResultados() {
        modoResultadosActivo = false
        productoResultadoSeleccionado = null
        puestoNormalSeleccionado = null
        busquedaViewModel.limpiarResultados()

        binding.btnLimpiarBusqueda.visibility = View.GONE
        binding.etBuscarProducto.setText("")
        binding.tarjetaPuesto.visibility = View.GONE

        fragmentoMapa?.mostrarMarcadoresPuestos(listaPuestosCargados)

        val camara = viewModel.camaraAntesDeBuscar
        if (camara != null) {
            fragmentoMapa?.restaurarCentroYZoom(
                GeoPoint(camara.latitud, camara.longitud),
                camara.zoom
            )
            viewModel.camaraAntesDeBuscar = null
        }
        viewModel.camaraAlSalir = null
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
                    ejecutarBusquedaResultados(
                        nombre = "",
                        idCategoria = categoria.idCategoria,
                        terminoMostrar = categoria.nombreCategoria
                    )
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
        fragmentoMapa?.alSeleccionarMarcador = { id, datos ->
            if (id == -1) {
                binding.tarjetaPuesto.visibility = View.GONE
                puestoNormalSeleccionado = null
                productoResultadoSeleccionado = null
            } else if (modoResultadosActivo && datos is ProductoBuscado) {
                puestoNormalSeleccionado = null
                mostrarDetalleResultado(datos)
            } else {
                productoResultadoSeleccionado = null
                val puesto = listaPuestosCargados.find { it.idPuesto == id }
                if (puesto != null) {
                    mostrarDetallePuesto(puesto)
                }
            }
        }

        binding.btnCerrarTarjeta.setOnClickListener {
            binding.tarjetaPuesto.visibility = View.GONE
            puestoNormalSeleccionado = null
            productoResultadoSeleccionado = null
        }
    }

    private fun mostrarDetallePuesto(puesto: Puesto) {
        puestoNormalSeleccionado = puesto
        with(binding) {
            tvNombreProductoTarjeta.text = puesto.nombrePuesto
            tvPrecioProductoTarjeta.visibility = View.GONE

            btnVerMas.visibility = View.VISIBLE
            btnVerMas.setOnClickListener {
                navegandoADetalle = true
                val centro = fragmentoMapa?.obtenerCentroActual()
                val zoom = fragmentoMapa?.obtenerZoomActual()
                if (centro != null && zoom != null) {
                    viewModel.camaraAlSalir = CamaraMapa(centro.latitude, centro.longitude, zoom)
                }
                val bundle = bundleOf(
                    "idProducto" to -1,
                    "idPuesto" to puesto.idPuesto
                )
                findNavController().navigate(R.id.accionInicioConsumidorADetalleProducto, bundle)
            }

            tarjetaPuesto.visibility = View.VISIBLE

            val urlImagen = construirUrlImagen(puesto.imagenUrl)
            Glide.with(this@FragmentoInicioConsumidor)
                .load(urlImagen)
                .placeholder(R.drawable.ic_puesto_placeholder)
                .error(R.drawable.ic_puesto_placeholder)
                .into(imgPuesto)
        }
    }

    private fun mostrarDetalleResultado(producto: ProductoBuscado) {
        productoResultadoSeleccionado = producto
        with(binding) {
            tvNombreProductoTarjeta.text = producto.nombreProducto

            tvPrecioProductoTarjeta.visibility = View.VISIBLE
            if (producto.disponible) {
                val precioTexto = if (producto.precio % 1.0 == 0.0) {
                    producto.precio.toInt().toString()
                } else {
                    String.format(Locale.getDefault(), "%.2f", producto.precio)
                }
                tvPrecioProductoTarjeta.text = getString(R.string.precio_formato, precioTexto)
                tvPrecioProductoTarjeta.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_texto))
            } else {
                tvPrecioProductoTarjeta.text = getString(R.string.no_disponible)
                tvPrecioProductoTarjeta.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_error))
            }

            btnVerMas.visibility = View.VISIBLE
            btnVerMas.setOnClickListener {
                navegandoADetalle = true
                val centro = fragmentoMapa?.obtenerCentroActual()
                val zoom = fragmentoMapa?.obtenerZoomActual()
                if (centro != null && zoom != null) {
                    viewModel.camaraAlSalir = CamaraMapa(centro.latitude, centro.longitude, zoom)
                }
                val bundle = bundleOf(
                    "idProducto" to producto.idProducto,
                    "idPuesto" to producto.idPuesto
                )
                findNavController().navigate(R.id.accionInicioConsumidorADetalleProducto, bundle)
            }

            tarjetaPuesto.visibility = View.VISIBLE

            val urlImagen = construirUrlImagen(producto.imagenPuestoUrl)
            Glide.with(this@FragmentoInicioConsumidor)
                .load(urlImagen)
                .placeholder(R.drawable.ic_puesto_placeholder)
                .error(R.drawable.ic_puesto_placeholder)
                .into(imgPuesto)
        }
    }

    private fun configurarObservadorTecladoEInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val tecladoVisible = insets.isVisible(WindowInsetsCompat.Type.ime())

            binding.barraInferior.visibility = if (tecladoVisible) View.GONE else View.VISIBLE

            if (!tecladoVisible && modoBusquedaActivo) {
                desactivarModoBusqueda()
            }
            insets
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
                        puestoNormalSeleccionado = null
                        productoResultadoSeleccionado = null
                    } else if (modoResultadosActivo) {
                        desactivarModoResultados()
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
                            if (!modoResultadosActivo) {
                                fragmentoMapa?.mostrarMarcadoresPuestos(estado.puestos)
                            }
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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                busquedaViewModel.estadoResultados.collect { estado ->
                    when (estado) {
                        is EstadoResultadosBusqueda.Inicial -> {}
                        is EstadoResultadosBusqueda.Cargando -> {
                            binding.indicadorCargandoPuestos.visibility = View.VISIBLE
                        }
                        is EstadoResultadosBusqueda.Exito -> {
                            binding.indicadorCargandoPuestos.visibility = View.GONE
                            val marcadores = estado.productosAgrupados.map { producto ->
                                MarcadorMapa(
                                    id = producto.idPuesto,
                                    latitud = producto.latitud,
                                    longitud = producto.longitud,
                                    titulo = producto.nombrePuesto,
                                    subtitulo = producto.nombreComercial,
                                    datosOriginales = producto
                                )
                            }
                            fragmentoMapa?.mostrarMarcadores(marcadores)
                            if (viewModel.busquedaNuevaRealizada) {
                                viewModel.busquedaNuevaRealizada = false
                                fragmentoMapa?.centrarEnMarcadores(marcadores)
                            }
                        }
                        is EstadoResultadosBusqueda.SinResultados -> {
                            binding.indicadorCargandoPuestos.visibility = View.GONE
                            Toast.makeText(requireContext(), R.string.no_hay_puestos_producto, Toast.LENGTH_SHORT).show()
                            desactivarModoResultados()
                        }
                        is EstadoResultadosBusqueda.Error -> {
                            binding.indicadorCargandoPuestos.visibility = View.GONE
                            Toast.makeText(requireContext(), estado.mensaje, Toast.LENGTH_LONG).show()
                            desactivarModoResultados()
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
