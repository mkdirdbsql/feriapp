package com.feriapp.app.ui.consumidor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
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
import com.feriapp.app.ui.common.ControladorMenuLateral
import com.feriapp.app.ui.common.FragmentoMapa
import com.feriapp.app.ui.common.MarcadorMapa
import com.feriapp.app.ui.common.fabricaViewModel
import com.feriapp.app.util.LimitesFeria
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
            InicioConsumidorViewModel(
                contenedor.repositorioPuestos,
                contenedor.repositorioProductos,
                contenedor.repositorioRutas
            )
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

    private var datosDetalleActual: DatosDetallePanel? = null

    private var controladorMenuLateral: ControladorMenuLateral? = null
    private lateinit var adaptadorSugerencias: AdaptadorSugerencias

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        childFragmentManager.addFragmentOnAttachListener { _, fragment ->
            if (fragment is FragmentoMapa) {
                fragment.ubicacionDeseada = viewModel.ubicacionActiva
            }
        }
    }

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
        fragmentoMapa?.ubicacionDeseada = viewModel.ubicacionActiva
        fragmentoMapa?.alLlegarADestino = {
            viewModel.limpiarRuta()
        }
        fragmentoMapa?.alDesviarseDeRuta = { miPos, destino ->
            viewModel.trazarRuta(miPos, destino)
        }

        configurarEnfoqueInicial()
        configurarNavegacion()
        configurarControlesUbicacion()
        configurarControlesZoom()
        configurarTarjetaPuesto()
        configurarModoBusqueda()
        configurarDetallePanel()
        configurarObservadorTecladoEInsets()
        configurarBotonAtras()
        configurarControladorMenuLateral()
        observarEstadoViewModel()
    }

    private fun configurarEnfoqueInicial() {
        binding.contenedorPrincipal.requestFocus()
        binding.etBuscarProducto.clearFocus()
    }

    private fun configurarControladorMenuLateral() {
        val contenedor = (requireActivity().application as AplicacionFeria).contenedorApp
        val bindingMenu = binding.includeMenuLateral

        binding.drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
            override fun onDrawerOpened(drawerView: View) {
                viewModel.menuAbierto = true
            }
            override fun onDrawerClosed(drawerView: View) {
                viewModel.menuAbierto = false
            }
            override fun onDrawerStateChanged(newState: Int) {}
        })

        if (viewModel.menuAbierto) {
            binding.drawerLayout.openDrawer(GravityCompat.START, false)
        }

        controladorMenuLateral = ControladorMenuLateral(
            contexto = requireContext(),
            drawerLayout = binding.drawerLayout,
            bindingMenu = bindingMenu,
            gestorSesion = contenedor.gestorSesion,
            gestorPreferencias = contenedor.gestorPreferencias,
            repositorioAutenticacion = contenedor.repositorioAutenticacion,
            coroutineScope = viewLifecycleOwner.lifecycleScope,
            navController = findNavController(),
            alCambiarModoOscuro = { modoOscuro ->
                fragmentoMapa?.aplicarModoOscuroTiles(modoOscuro)
            }
        )
    }

    override fun onResume() {
        super.onResume()

        if (viewModel.camaraAlSalir != null) {
            val camara = viewModel.camaraAlSalir!!
            fragmentoMapa?.ejecutarCuandoEsteListo {
                fragmentoMapa?.fijarCamaraDirecta(camara.latitud, camara.longitud, camara.zoom)
            }
        }

        if (viewModel.modoBusquedaActivo) {
            activarModoBusqueda()
        } else if (viewModel.modoResultadosActivo) {
            modoResultadosActivo = true
            binding.etBuscarProducto.setText(viewModel.textoBusqueda)
            binding.btnLimpiarBusqueda.visibility = View.VISIBLE
            if (viewModel.productoResultadoSeleccionado != null) {
                mostrarDetalleResultado(viewModel.productoResultadoSeleccionado!!)
            }
        } else {
            configurarEnfoqueInicial()
            if (viewModel.puestoNormalSeleccionado != null) {
                mostrarDetallePuesto(viewModel.puestoNormalSeleccionado!!)
            }
        }

        if (!viewModel.modoResultadosActivo) {
            when (val estadoActual = viewModel.estado.value) {
                is EstadoInicioConsumidor.Exito -> {
                    listaPuestosCargados = estadoActual.puestos
                    fragmentoMapa?.mostrarMarcadoresPuestos(estadoActual.puestos)
                    fragmentoMapa?.resaltarMarcador(viewModel.marcadorSeleccionadoId)
                }
                is EstadoInicioConsumidor.Error -> {
                    viewModel.cargarPuestos()
                }
                else -> {}
            }
        } else {
            fragmentoMapa?.resaltarMarcador(viewModel.marcadorSeleccionadoId)
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
        viewModel.modoBusquedaActivo = true

        viewModel.limpiarRuta()
        viewModel.cerrarDetalle()
        binding.tarjetaPuesto.visibility = View.GONE
        viewModel.puestoNormalSeleccionado = null
        viewModel.productoResultadoSeleccionado = null
        viewModel.marcadorSeleccionadoId = null
        fragmentoMapa?.resaltarMarcador(null)

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
        viewModel.modoBusquedaActivo = false

        ocultarTeclado()
        binding.etBuscarProducto.clearFocus()
        if (!viewModel.modoResultadosActivo) {
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
        viewModel.busquedaPorNombre = !nombre.isNullOrBlank()
        viewModel.modoResultadosActivo = true
        viewModel.textoBusqueda = terminoMostrar

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
        viewModel.modoResultadosActivo = false
        viewModel.textoBusqueda = ""
        viewModel.productoResultadoSeleccionado = null
        viewModel.puestoNormalSeleccionado = null
        viewModel.marcadorSeleccionadoId = null
        fragmentoMapa?.resaltarMarcador(null)
        viewModel.limpiarRuta()
        viewModel.cerrarDetalle()
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

    private fun configurarDetallePanel() {
        binding.btnCerrarDetalle.setOnClickListener {
            viewModel.cerrarDetalle()
        }

        binding.btnTrazarRuta.setOnClickListener {
            if (!binding.switchUbicacion.isChecked) {
                Toast.makeText(requireContext(), R.string.activa_ubicacion_primero, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val miUbicacion = fragmentoMapa?.obtenerMiPosicion()
            if (miUbicacion == null) {
                Toast.makeText(requireContext(), R.string.aviso_sin_posicion, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val datosDetalle = (viewModel.estadoDetalle.value as? EstadoDetalle.Listo)?.datos
            if (datosDetalle != null) {
                val destino = GeoPoint(datosDetalle.latitud, datosDetalle.longitud)
                viewModel.cerrarDetalle()
                viewModel.trazarRuta(miUbicacion, destino)
            }
        }
    }

    private fun actualizarContenidoDetallePanel() {
        val datos = datosDetalleActual ?: return
        val productos = datos.productos

        if (productos.isEmpty()) {
            binding.tvNombreProductoDetalle.visibility = View.GONE
            binding.tvPrecioDetalle.visibility = View.GONE
            binding.tvDisponibleDetalle.visibility = View.GONE
            binding.btnFotoAnterior.visibility = View.GONE
            binding.btnFotoSiguiente.visibility = View.GONE
            binding.tvContadorFotos.visibility = View.GONE

            binding.tvDescripcionDetalle.text = getString(R.string.sin_descripcion)

            Glide.with(this).clear(binding.imgFotoDetalle)
            binding.imgFotoDetalle.setImageResource(R.drawable.ic_puesto_placeholder)
            return
        }

        val idx = viewModel.indiceProductoDetalleActual.coerceIn(0, productos.size - 1)
        val prodActual = productos[idx]

        Glide.with(this).clear(binding.imgFotoDetalle)

        if (!prodActual.fotoUrl.isNullOrBlank()) {
            val urlImagen = construirUrlImagen(prodActual.fotoUrl)
            Glide.with(this)
                .load(urlImagen)
                .placeholder(R.drawable.ic_puesto_placeholder)
                .error(R.drawable.ic_puesto_placeholder)
                .into(binding.imgFotoDetalle)
        } else {
            binding.imgFotoDetalle.setImageResource(R.drawable.ic_puesto_placeholder)
        }

        binding.tvNombreProductoDetalle.visibility = View.VISIBLE
        binding.tvNombreProductoDetalle.text = prodActual.nombre

        binding.tvDescripcionDetalle.text = if (prodActual.descripcion.isNullOrBlank()) {
            getString(R.string.sin_descripcion)
        } else {
            prodActual.descripcion
        }

        binding.tvPrecioDetalle.visibility = View.VISIBLE
        binding.tvPrecioDetalle.text = prodActual.precioTexto
        binding.tvDisponibleDetalle.visibility = View.VISIBLE
        binding.tvDisponibleDetalle.text = prodActual.disponibleTexto

        if (productos.size >= 2) {
            binding.btnFotoAnterior.visibility = View.VISIBLE
            binding.btnFotoSiguiente.visibility = View.VISIBLE
            binding.tvContadorFotos.visibility = View.VISIBLE
            binding.tvContadorFotos.text = getString(R.string.formato_contador_fotos, idx + 1, productos.size)

            binding.btnFotoAnterior.setOnClickListener {
                val nuevoIdx = if (idx > 0) idx - 1 else productos.size - 1
                viewModel.indiceProductoDetalleActual = nuevoIdx
                actualizarContenidoDetallePanel()
            }

            binding.btnFotoSiguiente.setOnClickListener {
                val nuevoIdx = if (idx < productos.size - 1) idx + 1 else 0
                viewModel.indiceProductoDetalleActual = nuevoIdx
                actualizarContenidoDetallePanel()
            }
        } else {
            binding.btnFotoAnterior.visibility = View.GONE
            binding.btnFotoSiguiente.visibility = View.GONE
            binding.tvContadorFotos.visibility = View.GONE
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
        Log.d("FeriApp", "Restaurando switchUbicacion con viewModel.ubicacionActiva = ${viewModel.ubicacionActiva}")
        binding.switchUbicacion.isChecked = viewModel.ubicacionActiva

        binding.switchUbicacion.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!esGpsHabilitado()) {
                    viewModel.ubicacionActiva = false
                    Log.d("FeriApp", "GPS no habilitado. Guardando ubicacionActiva = false")
                    binding.switchUbicacion.isChecked = false
                    fragmentoMapa?.ubicacionDeseada = false
                    Toast.makeText(requireContext(), R.string.aviso_gps_desactivado, Toast.LENGTH_SHORT).show()
                    return@setOnCheckedChangeListener
                }

                fragmentoMapa?.activarUbicacion { concedido ->
                    if (concedido) {
                        viewModel.ubicacionActiva = true
                        Log.d("FeriApp", "Permiso concedido. Guardando ubicacionActiva = true")
                        fragmentoMapa?.ubicacionDeseada = true
                    } else {
                        viewModel.ubicacionActiva = false
                        Log.d("FeriApp", "Permiso denegado. Guardando ubicacionActiva = false")
                        binding.switchUbicacion.isChecked = false
                        fragmentoMapa?.ubicacionDeseada = false
                        Toast.makeText(requireContext(), R.string.aviso_permiso_denegado, Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                viewModel.ubicacionActiva = false
                Log.d("FeriApp", "Switch apagado libremente. Guardando ubicacionActiva = false")
                fragmentoMapa?.ubicacionDeseada = false
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
                viewModel.puestoNormalSeleccionado = null
                viewModel.productoResultadoSeleccionado = null
                viewModel.marcadorSeleccionadoId = null
                fragmentoMapa?.resaltarMarcador(null)
            } else if (modoResultadosActivo && datos is ProductoBuscado) {
                viewModel.puestoNormalSeleccionado = null
                mostrarDetalleResultado(datos)
            } else {
                viewModel.productoResultadoSeleccionado = null
                val puesto = listaPuestosCargados.find { it.idPuesto == id }
                if (puesto != null) {
                    mostrarDetallePuesto(puesto)
                }
            }
        }

        binding.btnCerrarTarjeta.setOnClickListener {
            binding.tarjetaPuesto.visibility = View.GONE
            viewModel.puestoNormalSeleccionado = null
            viewModel.productoResultadoSeleccionado = null
            viewModel.marcadorSeleccionadoId = null
            fragmentoMapa?.resaltarMarcador(null)
        }
    }

    private fun mostrarDetallePuesto(puesto: Puesto) {
        viewModel.puestoNormalSeleccionado = puesto
        viewModel.marcadorSeleccionadoId = puesto.idPuesto
        fragmentoMapa?.resaltarMarcador(puesto.idPuesto)

        with(binding) {
            tvNombreProductoTarjeta.text = puesto.nombrePuesto
            tvPrecioProductoTarjeta.visibility = View.GONE

            if (!puesto.descripcion.isNullOrBlank()) {
                tvDescripcionPuestoTarjeta.text = puesto.descripcion
                tvDescripcionPuestoTarjeta.visibility = View.VISIBLE
            } else {
                tvDescripcionPuestoTarjeta.text = ""
                tvDescripcionPuestoTarjeta.visibility = View.GONE
            }

            btnVerMas.visibility = View.VISIBLE
            btnVerMas.setOnClickListener {
                tarjetaPuesto.visibility = View.GONE
                viewModel.cargarDetalle(puesto.idPuesto, -1)
            }

            if (viewModel.estadoDetalle.value is EstadoDetalle.Listo) {
                tarjetaPuesto.visibility = View.GONE
            } else {
                tarjetaPuesto.visibility = View.VISIBLE
            }

            val urlImagen = construirUrlImagen(puesto.imagenUrl)
            Glide.with(this@FragmentoInicioConsumidor)
                .load(urlImagen)
                .placeholder(R.drawable.ic_puesto_placeholder)
                .error(R.drawable.ic_puesto_placeholder)
                .into(imgPuesto)
        }
    }

    private fun mostrarDetalleResultado(producto: ProductoBuscado) {
        viewModel.productoResultadoSeleccionado = producto
        viewModel.marcadorSeleccionadoId = producto.idPuesto
        fragmentoMapa?.resaltarMarcador(producto.idPuesto)

        with(binding) {
            tvNombreProductoTarjeta.text = producto.nombreProducto
            tvDescripcionPuestoTarjeta.visibility = View.GONE

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
                tarjetaPuesto.visibility = View.GONE
                viewModel.cargarDetalle(producto.idPuesto, producto.idProducto)
            }

            if (viewModel.estadoDetalle.value is EstadoDetalle.Listo) {
                tarjetaPuesto.visibility = View.GONE
            } else {
                tarjetaPuesto.visibility = View.VISIBLE
            }

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
                    } else if (viewModel.estadoRuta.value !is EstadoRuta.Inactivo) {
                        viewModel.limpiarRuta()
                    } else if (viewModel.estadoDetalle.value is EstadoDetalle.Listo) {
                        viewModel.cerrarDetalle()
                    } else if (binding.tarjetaPuesto.visibility == View.VISIBLE) {
                        binding.tarjetaPuesto.visibility = View.GONE
                        viewModel.puestoNormalSeleccionado = null
                        viewModel.productoResultadoSeleccionado = null
                        viewModel.marcadorSeleccionadoId = null
                        fragmentoMapa?.resaltarMarcador(null)
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
                                fragmentoMapa?.resaltarMarcador(viewModel.marcadorSeleccionadoId)
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
                viewModel.estadoDetalle.collect { estado ->
                    when (estado) {
                        is EstadoDetalle.Oculto -> {
                            binding.panelDetalle.visibility = View.GONE
                            datosDetalleActual = null

                            if (viewModel.puestoNormalSeleccionado != null) {
                                mostrarDetallePuesto(viewModel.puestoNormalSeleccionado!!)
                            } else if (viewModel.productoResultadoSeleccionado != null) {
                                mostrarDetalleResultado(viewModel.productoResultadoSeleccionado!!)
                            } else {
                                binding.tarjetaPuesto.visibility = View.GONE
                                fragmentoMapa?.resaltarMarcador(null)
                            }
                        }
                        is EstadoDetalle.Cargando -> {
                            binding.indicadorCargandoPuestos.visibility = View.VISIBLE
                        }
                        is EstadoDetalle.Listo -> {
                            binding.indicadorCargandoPuestos.visibility = View.GONE
                            datosDetalleActual = estado.datos
                            binding.tvTituloDetalle.text = estado.datos.titulo

                            actualizarContenidoDetallePanel()
                            binding.panelDetalle.visibility = View.VISIBLE
                            binding.tarjetaPuesto.visibility = View.GONE
                        }
                        is EstadoDetalle.Error -> {
                            binding.indicadorCargandoPuestos.visibility = View.GONE
                            Toast.makeText(requireContext(), estado.mensaje, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estadoRuta.collect { estado ->
                    when (estado) {
                        is EstadoRuta.Inactivo -> {
                            fragmentoMapa?.limpiarRuta()
                        }
                        is EstadoRuta.Calculando -> {
                            binding.indicadorCargandoPuestos.visibility = View.VISIBLE
                        }
                        is EstadoRuta.Exito -> {
                            binding.indicadorCargandoPuestos.visibility = View.GONE
                            val puntos = estado.puntos
                            fragmentoMapa?.dibujarRuta(puntos)
                            fragmentoMapa?.ajustarEncuadrePuntos(puntos)

                            val miUbicacion = fragmentoMapa?.obtenerMiPosicion()
                            if (miUbicacion != null && !LimitesFeria.estaDentro(miUbicacion.latitude, miUbicacion.longitude)) {
                                Toast.makeText(requireContext(), R.string.aviso_fuera_de_feria, Toast.LENGTH_LONG).show()
                            }
                        }
                        is EstadoRuta.FalloLineaRecta -> {
                            binding.indicadorCargandoPuestos.visibility = View.GONE
                            val origen = estado.origen
                            val destino = estado.destino
                            fragmentoMapa?.dibujarRutaRecta(origen, destino)
                            fragmentoMapa?.ajustarEncuadrePuntos(listOf(origen, destino))

                            val mensajeToast = getString(R.string.aviso_error_ruta_recta, estado.motivo)
                            Toast.makeText(requireContext(), mensajeToast, Toast.LENGTH_LONG).show()

                            if (!LimitesFeria.estaDentro(origen.latitude, origen.longitude)) {
                                Toast.makeText(requireContext(), R.string.aviso_fuera_de_feria, Toast.LENGTH_LONG).show()
                            }
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

                                val lista = estado.productosAgrupados
                                if (lista.size == 1) {
                                    mostrarDetalleResultado(lista.first())
                                } else if (lista.size > 1 && viewModel.busquedaPorNombre) {
                                    val miPos = fragmentoMapa?.obtenerMiPosicion()
                                    val productoElegido = if (miPos != null) {
                                        lista.minByOrNull { p ->
                                            val dx = p.latitud - miPos.latitude
                                            val dy = p.longitud - miPos.longitude
                                            dx * dx + dy * dy
                                        } ?: lista.first()
                                    } else {
                                        lista.first()
                                    }
                                    mostrarDetalleResultado(productoElegido)
                                }
                            } else {
                                fragmentoMapa?.resaltarMarcador(viewModel.marcadorSeleccionadoId)
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
