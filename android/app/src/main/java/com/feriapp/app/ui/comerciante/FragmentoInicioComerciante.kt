package com.feriapp.app.ui.comerciante

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.GravityCompat
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
import com.feriapp.app.data.model.PerfilComerciante
import com.feriapp.app.data.model.PuestoPerfil
import com.feriapp.app.databinding.DialogoAgregarPuestoBinding
import com.feriapp.app.databinding.DialogoElegirProductoBinding
import com.feriapp.app.databinding.FragmentoInicioComercianteBinding
import com.feriapp.app.ui.common.ControladorMenuLateral
import com.feriapp.app.ui.common.FragmentoMapa
import com.feriapp.app.ui.common.MarcadorMapa
import com.feriapp.app.ui.common.fabricaViewModel
import com.feriapp.app.ui.consumidor.CamaraMapa
import com.feriapp.app.util.LimitesFeria
import com.feriapp.app.util.construirUrlImagen
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.util.Locale

class FragmentoInicioComerciante : Fragment() {

    private var _binding: FragmentoInicioComercianteBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InicioComercianteViewModel by viewModels {
        fabricaViewModel {
            val contenedor = (requireActivity().application as AplicacionFeria).contenedorApp
            InicioComercianteViewModel(
                contenedor.repositorioComerciante,
                contenedor.repositorioPuestos,
                contenedor.repositorioProductos,
                requireContext().applicationContext
            )
        }
    }

    private var fragmentoMapa: FragmentoMapa? = null
    private var controladorMenuLateral: ControladorMenuLateral? = null
    private var dialogoUbicacion: AlertDialog? = null

    private var dialogoDatosPuesto: AlertDialog? = null
    private var bindingDialogoPuesto: DialogoAgregarPuestoBinding? = null

    private var snackbarActual: Snackbar? = null

    private var trabajoBusquedaUbicacion: Job? = null
    private var trabajoCentrarUbicacion: Job? = null

    private val lanzadorFotoPuesto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            if (viewModel.modoEdicionDialogo) {
                viewModel.uriImagenEdicionBorrador = uri
            } else {
                viewModel.uriImagenBorrador = uri
            }
            bindingDialogoPuesto?.let { bd ->
                bd.ivImagenPuesto.setImageURI(uri)
                bd.ivImagenPuesto.scaleType = ImageView.ScaleType.CENTER_CROP
                bd.ivImagenPuesto.setPadding(0, 0, 0, 0)
            }
        }
    }

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
        _binding = FragmentoInicioComercianteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragmentoMapa = childFragmentManager.findFragmentById(R.id.contenedorMapa) as? FragmentoMapa
        fragmentoMapa?.ubicacionDeseada = viewModel.ubicacionActiva

        configurarNavegacion()
        configurarControlesZoom()
        configurarBotonCentrarUbicacion()
        configurarBotonAtras()
        configurarControladorMenuLateral()
        configurarSeleccionMarcador()
        observarResultadoAgregarProducto()
        observarEstadoViewModel()
    }

    private fun configurarSeleccionMarcador() {
        fragmentoMapa?.alSeleccionarMarcador = { id, datos ->
            if (datos is PuestoVecino) {
                viewModel.idPuestoVecinoSeleccionado = id
                actualizarTarjetasFlotantes()
            } else {
                viewModel.idPuestoVecinoSeleccionado = null
                actualizarTarjetasFlotantes()
            }
        }

        binding.btnCerrarVecino.setOnClickListener {
            viewModel.idPuestoVecinoSeleccionado = null
            actualizarTarjetasFlotantes()
        }
    }

    private fun observarResultadoAgregarProducto() {
        findNavController().currentBackStackEntry?.savedStateHandle
            ?.getLiveData<Boolean>("producto_agregado")
            ?.observe(viewLifecycleOwner) { agregado ->
                if (agregado == true) {
                    viewModel.recargarPerfil()
                    findNavController().currentBackStackEntry?.savedStateHandle?.remove<Boolean>("producto_agregado")
                }
            }
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

        if (viewModel.camaraGuardada != null) {
            val camara = viewModel.camaraGuardada!!
            fragmentoMapa?.ejecutarCuandoEsteListo {
                fragmentoMapa?.fijarCamaraDirecta(camara.latitud, camara.longitud, camara.zoom)
            }
        }

        val estadoActual = viewModel.estado.value
        if (estadoActual is EstadoInicioComerciante.SinPuesto) {
            sincronizarFaseUI()
            if (viewModel.dialogoUbicacionVisible && !ubicacionDisponible()) {
                mostrarDialogoUbicacion()
            }
        } else if (estadoActual is EstadoInicioComerciante.ConPuesto) {
            redibujarMarcadores()
            actualizarTarjetasFlotantes()
            if (viewModel.modoEdicionDialogo) {
                mostrarDialogoEdicionPuesto(estadoActual.puesto)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        val centro = fragmentoMapa?.obtenerCentroActual()
        val zoom = fragmentoMapa?.obtenerZoomActual()
        if (centro != null && zoom != null) {
            viewModel.camaraGuardada = CamaraMapa(centro.latitude, centro.longitude, zoom)
        }
    }

    private fun configurarNavegacion() {
        binding.btnMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    private fun configurarControlesZoom() {
        binding.btnAcercar.setOnClickListener {
            fragmentoMapa?.acercar()
        }
        binding.btnAlejar.setOnClickListener {
            fragmentoMapa?.alejar()
        }
    }

    private fun configurarBotonCentrarUbicacion() {
        binding.btnCentrarUbicacion.setOnClickListener {
            val estadoActual = viewModel.estado.value
            if (ubicacionDisponible()) {
                viewModel.ubicacionActiva = true
                fragmentoMapa?.ubicacionDeseada = true
                val centrado = fragmentoMapa?.centrarEnMiUbicacion() ?: false
                if (!centrado) {
                    Toast.makeText(requireContext(), R.string.buscando_posicion, Toast.LENGTH_SHORT).show()
                    intentarCentrarReintentos()
                }
            } else {
                fragmentoMapa?.activarUbicacion { concedido ->
                    if (concedido) {
                        viewModel.ubicacionActiva = true
                        fragmentoMapa?.ubicacionDeseada = true
                        val centrado = fragmentoMapa?.centrarEnMiUbicacion() ?: false
                        if (!centrado) {
                            intentarCentrarReintentos()
                        }
                    } else {
                        if (estadoActual is EstadoInicioComerciante.ConPuesto) {
                            val puesto = estadoActual.puesto
                            fragmentoMapa?.fijarCamaraDirecta(puesto.latitud, puesto.longitud, 18.0)
                        } else {
                            Toast.makeText(requireContext(), R.string.activa_ubicacion_centrarte, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun intentarCentrarReintentos() {
        trabajoCentrarUbicacion?.cancel()
        trabajoCentrarUbicacion = viewLifecycleOwner.lifecycleScope.launch {
            for (i in 1..20) {
                delay(500)
                val centrado = fragmentoMapa?.centrarEnMiUbicacion() ?: false
                if (centrado) break
            }
        }
    }

    private fun configurarBotonAtras() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                        binding.drawerLayout.closeDrawer(GravityCompat.START)
                    } else if (viewModel.idPuestoVecinoSeleccionado != null) {
                        viewModel.idPuestoVecinoSeleccionado = null
                        actualizarTarjetasFlotantes()
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
                        is EstadoInicioComerciante.Cargando -> {
                            mostrarCargando()
                        }
                        is EstadoInicioComerciante.SinPuesto -> {
                            mostrarSinPuesto()
                        }
                        is EstadoInicioComerciante.ConPuesto -> {
                            mostrarConPuesto(estado.puesto, estado.cantidadProductos)
                        }
                        is EstadoInicioComerciante.Error -> {
                            mostrarError(estado.mensaje)
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estadoGuardado.collect { estadoGuardado ->
                    when (estadoGuardado) {
                        is EstadoGuardadoPuesto.Inactivo -> {
                            bindingDialogoPuesto?.pbGuardando?.visibility = View.GONE
                            setBotonesDialogoHabilitados(true)
                            if (viewModel.fase == FaseAgregarPuesto.Inactiva && !viewModel.modoEdicionDialogo) {
                                dialogoDatosPuesto?.dismiss()
                                dialogoDatosPuesto = null
                                bindingDialogoPuesto = null
                            }
                        }
                        is EstadoGuardadoPuesto.Guardando -> {
                            bindingDialogoPuesto?.pbGuardando?.visibility = View.VISIBLE
                            setBotonesDialogoHabilitados(false)
                        }
                        is EstadoGuardadoPuesto.Exito -> {
                            bindingDialogoPuesto?.pbGuardando?.visibility = View.GONE
                            setBotonesDialogoHabilitados(true)

                            if (viewModel.modoEdicionDialogo) {
                                val tuvoNuevaUbicacion = viewModel.latitudEdicionNueva != null && viewModel.longitudEdicionNueva != null
                                val nuevaLat = viewModel.latitudEdicionNueva
                                val nuevaLon = viewModel.longitudEdicionNueva

                                dialogoDatosPuesto?.dismiss()
                                dialogoDatosPuesto = null
                                bindingDialogoPuesto = null
                                viewModel.limpiarBorradorEdicion()
                                Toast.makeText(requireContext(), R.string.msg_puesto_actualizado, Toast.LENGTH_SHORT).show()

                                if (tuvoNuevaUbicacion && nuevaLat != null && nuevaLon != null) {
                                    fragmentoMapa?.ejecutarCuandoEsteListo {
                                        fragmentoMapa?.fijarCamaraDirecta(nuevaLat, nuevaLon, 18.0)
                                    }
                                }
                            } else if (viewModel.fase == FaseAgregarPuesto.Inactiva) {
                                dialogoDatosPuesto?.dismiss()
                                dialogoDatosPuesto = null
                                bindingDialogoPuesto = null
                            }
                        }
                        is EstadoGuardadoPuesto.Error -> {
                            bindingDialogoPuesto?.pbGuardando?.visibility = View.GONE
                            setBotonesDialogoHabilitados(true)
                            Toast.makeText(requireContext(), estadoGuardado.mensaje, Toast.LENGTH_LONG).show()
                            viewModel.resetearEstadoGuardado()
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.categoriasDelComerciante.collect { categorias ->
                    actualizarChipsCategorias(categorias)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.vecinos.collect {
                    if (viewModel.estado.value is EstadoInicioComerciante.ConPuesto) {
                        redibujarMarcadores()
                        actualizarTarjetasFlotantes()
                    }
                }
            }
        }
    }

    private fun actualizarChipsCategorias(categorias: List<CategoriaFiltroComerciante>) {
        val estadoActual = viewModel.estado.value
        if (estadoActual !is EstadoInicioComerciante.ConPuesto || estadoActual.cantidadProductos == 0 || categorias.isEmpty()) {
            binding.contenedorFiltrosCategorias.visibility = View.GONE
            return
        }

        binding.contenedorFiltrosCategorias.visibility = View.VISIBLE
        binding.chipGroupCategorias.removeAllViews()

        categorias.forEach { cat ->
            val chip = Chip(requireContext()).apply {
                text = cat.nombreCategoria
                isCheckable = true
                isChecked = cat.idCategoria in viewModel.categoriasActivas
                setOnCheckedChangeListener { _, isChecked ->
                    viewModel.alternarCategoria(
                        idCategoria = cat.idCategoria,
                        activa = isChecked,
                        alError = { msg ->
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                        },
                        alSinPuestos = { msg ->
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
            binding.chipGroupCategorias.addView(chip)
        }
    }

    private fun redibujarMarcadores() {
        val estadoActual = viewModel.estado.value
        if (estadoActual !is EstadoInicioComerciante.ConPuesto) return

        val puestoMio = estadoActual.puesto
        val listaMarcadores = mutableListOf<MarcadorMapa>()

        listaMarcadores.add(
            MarcadorMapa(
                id = puestoMio.idPuesto,
                latitud = puestoMio.latitud,
                longitud = puestoMio.longitud,
                titulo = puestoMio.nombrePuesto,
                icono = R.drawable.ic_marcador_puesto,
                datosOriginales = null
            )
        )

        viewModel.vecinos.value.forEach { vecino ->
            listaMarcadores.add(
                MarcadorMapa(
                    id = vecino.idPuesto,
                    latitud = vecino.latitud,
                    longitud = vecino.longitud,
                    titulo = vecino.nombrePuesto,
                    subtitulo = vecino.nombreComercial,
                    icono = R.drawable.ic_marcador_puesto_vecino,
                    datosOriginales = vecino
                )
            )
        }

        fragmentoMapa?.mostrarMarcadores(listaMarcadores)
    }

    private fun actualizarTarjetasFlotantes() {
        val estadoActual = viewModel.estado.value
        if (estadoActual !is EstadoInicioComerciante.ConPuesto) {
            binding.tarjetaMiPuesto.visibility = View.GONE
            binding.tarjetaVecino.visibility = View.GONE
            binding.globoAgregarProducto.visibility = View.GONE
            return
        }

        val idVecinoSel = viewModel.idPuestoVecinoSeleccionado
        val vecinoSeleccionado = viewModel.vecinos.value.find { it.idPuesto == idVecinoSel }

        if (vecinoSeleccionado != null) {
            binding.tarjetaMiPuesto.visibility = View.GONE
            binding.globoAgregarProducto.visibility = View.GONE

            binding.tvNombrePuestoVecino.text = vecinoSeleccionado.nombrePuesto
            binding.tvNombreComercialVecino.text = vecinoSeleccionado.nombreComercial

            val urlImagen = construirUrlImagen(vecinoSeleccionado.imagenPuestoUrl)
            Glide.with(this)
                .load(urlImagen)
                .placeholder(R.drawable.ic_puesto_placeholder)
                .error(R.drawable.ic_puesto_placeholder)
                .into(binding.imgPuestoVecino)

            binding.contenedorProductosVecino.removeAllViews()
            vecinoSeleccionado.productos.forEach { prod ->
                val tvProd = TextView(requireContext()).apply {
                    val precioTxt = if (prod.precio % 1.0 == 0.0) {
                        prod.precio.toInt().toString()
                    } else {
                        String.format(Locale.getDefault(), "%.2f", prod.precio)
                    }
                    text = getString(R.string.formato_producto_vecino, prod.nombreProducto, precioTxt)
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.color_texto))
                    textSize = 12f
                }
                binding.contenedorProductosVecino.addView(tvProd)
            }

            binding.tarjetaVecino.visibility = View.VISIBLE
        } else {
            binding.tarjetaVecino.visibility = View.GONE
            binding.tarjetaMiPuesto.visibility = View.VISIBLE

            if (estadoActual.cantidadProductos == 0 && !viewModel.globoDescartado) {
                binding.globoAgregarProducto.visibility = View.VISIBLE
            } else {
                binding.globoAgregarProducto.visibility = View.GONE
            }
        }
    }

    private fun setBotonesDialogoHabilitados(habilitados: Boolean) {
        dialogoDatosPuesto?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = habilitados
        dialogoDatosPuesto?.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = habilitados
        bindingDialogoPuesto?.ivImagenPuesto?.isEnabled = habilitados
        bindingDialogoPuesto?.btnActualizarUbicacion?.isEnabled = habilitados
    }

    private fun habilitarBarra(habilitada: Boolean) {
        binding.btnMenu.isEnabled = habilitada
        binding.btnMenu.alpha = if (habilitada) 1.0f else 0.4f

        binding.btnAgregar.isEnabled = habilitada
        binding.btnAgregar.alpha = if (habilitada) 1.0f else 0.4f

        binding.btnCentrarUbicacion.isEnabled = habilitada
        binding.btnCentrarUbicacion.alpha = if (habilitada) 1.0f else 0.4f

        binding.btnAcercar.isEnabled = habilitada
        binding.btnAcercar.alpha = if (habilitada) 1.0f else 0.4f

        binding.btnAlejar.isEnabled = habilitada
        binding.btnAlejar.alpha = if (habilitada) 1.0f else 0.4f
    }

    private fun mostrarCargando() {
        binding.indicadorCargandoPerfil.visibility = View.VISIBLE
        binding.tarjetaError.visibility = View.GONE
        binding.layoutBarraSuperior.visibility = View.GONE
        binding.contenedorFiltrosCategorias.visibility = View.GONE
        binding.tvEstadoUbicacion.visibility = View.GONE
        binding.tarjetaMiPuesto.visibility = View.GONE
        binding.tarjetaVecino.visibility = View.GONE
        binding.globoAgregarProducto.visibility = View.GONE
        habilitarBarra(false)
    }

    private fun ubicacionDisponible(): Boolean {
        val contexto = context ?: return false
        val tienePrecisa = ContextCompat.checkSelfPermission(
            contexto, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val tieneAproximada = ContextCompat.checkSelfPermission(
            contexto, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val locationManager = contexto.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val gpsHabilitado = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        return (tienePrecisa || tieneAproximada) && gpsHabilitado
    }

    private fun mostrarSinPuesto() {
        binding.indicadorCargandoPerfil.visibility = View.GONE
        binding.tarjetaError.visibility = View.GONE
        binding.layoutBarraSuperior.visibility = View.GONE
        binding.contenedorFiltrosCategorias.visibility = View.GONE
        binding.tarjetaMiPuesto.visibility = View.GONE
        binding.tarjetaVecino.visibility = View.GONE
        binding.globoAgregarProducto.visibility = View.GONE

        binding.tvEtiquetaAgregar.text = getString(R.string.etiqueta_agregar_puesto)
        binding.tvContadorProductos.visibility = View.GONE

        fragmentoMapa?.limpiarMarcadores()

        if (ubicacionDisponible()) {
            viewModel.ubicacionActiva = true
            fragmentoMapa?.ubicacionDeseada = true
            dialogoUbicacion?.dismiss()
            viewModel.dialogoUbicacionVisible = false

            if (viewModel.fase == FaseAgregarPuesto.Inactiva) {
                iniciarBusquedaDeUbicacion()
            } else {
                sincronizarFaseUI()
            }
        } else {
            mostrarDialogoUbicacion()
        }

        binding.btnAgregar.setOnClickListener {
            if (!ubicacionDisponible()) {
                mostrarDialogoUbicacion()
            } else if (viewModel.fase == FaseAgregarPuesto.Inactiva) {
                iniciarBusquedaDeUbicacion()
            }
        }
    }

    private fun iniciarBusquedaDeUbicacion() {
        viewModel.fase = FaseAgregarPuesto.BuscandoUbicacion
        sincronizarFaseUI()

        trabajoBusquedaUbicacion?.cancel()
        trabajoBusquedaUbicacion = viewLifecycleOwner.lifecycleScope.launch {
            var posicionEncontrada: GeoPoint? = fragmentoMapa?.obtenerMiPosicion()
            if (posicionEncontrada == null) {
                for (i in 1..40) {
                    delay(500)
                    val pos = fragmentoMapa?.obtenerMiPosicion()
                    if (pos != null) {
                        posicionEncontrada = pos
                        break
                    }
                }
            }

            if (posicionEncontrada != null) {
                val lat = posicionEncontrada.latitude
                val lon = posicionEncontrada.longitude
                if (LimitesFeria.estaDentro(lat, lon)) {
                    viewModel.latitudPuesto = lat
                    viewModel.longitudPuesto = lon
                    fragmentoMapa?.centrarEn(lat, lon, 18.0)
                    viewModel.fase = FaseAgregarPuesto.DialogoDatos
                    sincronizarFaseUI()
                } else {
                    Toast.makeText(requireContext(), R.string.aviso_ubicacion_fuera_feria_c2b, Toast.LENGTH_LONG).show()
                    viewModel.fase = FaseAgregarPuesto.Inactiva
                    sincronizarFaseUI()
                }
            } else {
                Toast.makeText(requireContext(), R.string.aviso_no_pudimos_obtener_ubicacion_c2b, Toast.LENGTH_LONG).show()
                viewModel.fase = FaseAgregarPuesto.Inactiva
                sincronizarFaseUI()
            }
        }
    }

    private fun sincronizarFaseUI() {
        when (viewModel.fase) {
            FaseAgregarPuesto.Inactiva -> {
                binding.tvEstadoUbicacion.visibility = View.GONE
                habilitarBarra(true)
            }
            FaseAgregarPuesto.BuscandoUbicacion -> {
                binding.tvEstadoUbicacion.visibility = View.VISIBLE
                habilitarBarra(false)
            }
            FaseAgregarPuesto.DialogoDatos -> {
                binding.tvEstadoUbicacion.visibility = View.GONE
                habilitarBarra(true)
                mostrarDialogoDatosPuesto()
            }
        }
    }

    private fun mostrarDialogoDatosPuesto() {
        if (dialogoDatosPuesto?.isShowing == true) return

        val bd = DialogoAgregarPuestoBinding.inflate(layoutInflater)
        bindingDialogoPuesto = bd

        bd.tvTituloDialogo.text = getString(R.string.titulo_ubicacion_encontrada)
        bd.btnActualizarUbicacion.visibility = View.GONE
        bd.tvEstadoUbicacionEdicion.visibility = View.GONE

        bd.etNombrePuesto.setText(viewModel.nombreBorrador)
        bd.etNombrePuesto.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.nombreBorrador = s?.toString().orEmpty()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        if (viewModel.uriImagenBorrador != null) {
            bd.ivImagenPuesto.setImageURI(viewModel.uriImagenBorrador)
            bd.ivImagenPuesto.scaleType = ImageView.ScaleType.CENTER_CROP
            bd.ivImagenPuesto.setPadding(0, 0, 0, 0)
        } else {
            bd.ivImagenPuesto.setImageResource(R.drawable.ic_agregar_imagen)
            bd.ivImagenPuesto.scaleType = ImageView.ScaleType.CENTER_INSIDE
            bd.ivImagenPuesto.setPadding(16, 16, 16, 16)
        }

        bd.ivImagenPuesto.setOnClickListener {
            lanzadorFotoPuesto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        dialogoDatosPuesto = MaterialAlertDialogBuilder(requireContext())
            .setView(bd.root)
            .setCancelable(false)
            .setPositiveButton(R.string.btn_guardar, null) // Listener sobrescrito abajo
            .setNegativeButton(R.string.btn_cancelar) { dialog, _ ->
                dialog.dismiss()
                dialogoDatosPuesto = null
                bindingDialogoPuesto = null
                viewModel.nombreBorrador = ""
                viewModel.uriImagenBorrador = null
                viewModel.fase = FaseAgregarPuesto.Inactiva
                sincronizarFaseUI()
            }
            .create()

        dialogoDatosPuesto?.show()

        dialogoDatosPuesto?.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            val nombre = bd.etNombrePuesto.text?.toString()?.trim().orEmpty()
            if (nombre.isEmpty()) {
                bd.layoutNombrePuesto.error = getString(R.string.error_nombre_puesto_requerido)
            } else {
                bd.layoutNombrePuesto.error = null
                val lat = viewModel.latitudPuesto
                val lon = viewModel.longitudPuesto
                if (lat == null || lon == null) {
                    Toast.makeText(requireContext(), R.string.aviso_sin_posicion_guardar, Toast.LENGTH_SHORT).show()
                    dialogoDatosPuesto?.dismiss()
                    dialogoDatosPuesto = null
                    bindingDialogoPuesto = null
                    iniciarBusquedaDeUbicacion()
                } else {
                    viewModel.guardarPuesto(
                        nombre = nombre,
                        latitud = lat,
                        longitud = lon,
                        uriImagen = viewModel.uriImagenBorrador,
                        alErrorSubirImagen = {
                            Toast.makeText(requireContext(), R.string.aviso_puesto_guardado_sin_imagen, Toast.LENGTH_LONG).show()
                        }
                    )
                }
            }
        }
    }

    private fun mostrarDialogoEdicionPuesto(puesto: PuestoPerfil) {
        if (dialogoDatosPuesto?.isShowing == true) return

        viewModel.modoEdicionDialogo = true
        val bd = DialogoAgregarPuestoBinding.inflate(layoutInflater)
        bindingDialogoPuesto = bd

        bd.tvTituloDialogo.text = getString(R.string.titulo_edicion_puesto)
        bd.btnActualizarUbicacion.visibility = View.VISIBLE

        val nombreInicial = viewModel.nombreEdicionBorrador ?: puesto.nombrePuesto
        viewModel.nombreEdicionBorrador = nombreInicial
        bd.etNombrePuesto.setText(nombreInicial)

        bd.etNombrePuesto.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.nombreEdicionBorrador = s?.toString().orEmpty()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        if (viewModel.uriImagenEdicionBorrador != null) {
            bd.ivImagenPuesto.setImageURI(viewModel.uriImagenEdicionBorrador)
            bd.ivImagenPuesto.scaleType = ImageView.ScaleType.CENTER_CROP
            bd.ivImagenPuesto.setPadding(0, 0, 0, 0)
        } else if (!puesto.imagenUrl.isNullOrBlank()) {
            val urlImagen = construirUrlImagen(puesto.imagenUrl)
            Glide.with(this)
                .load(urlImagen)
                .placeholder(R.drawable.ic_puesto_placeholder)
                .error(R.drawable.ic_puesto_placeholder)
                .into(bd.ivImagenPuesto)
            bd.ivImagenPuesto.scaleType = ImageView.ScaleType.CENTER_CROP
            bd.ivImagenPuesto.setPadding(0, 0, 0, 0)
        } else {
            bd.ivImagenPuesto.setImageResource(R.drawable.ic_agregar_imagen)
            bd.ivImagenPuesto.scaleType = ImageView.ScaleType.CENTER_INSIDE
            bd.ivImagenPuesto.setPadding(16, 16, 16, 16)
        }

        bd.ivImagenPuesto.setOnClickListener {
            lanzadorFotoPuesto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        if (viewModel.ubicacionEdicionActualizada) {
            bd.tvEstadoUbicacionEdicion.visibility = View.VISIBLE
        } else {
            bd.tvEstadoUbicacionEdicion.visibility = View.GONE
        }

        bd.btnActualizarUbicacion.setOnClickListener {
            if (!ubicacionDisponible()) {
                mostrarDialogoUbicacion()
            } else {
                val posActual = fragmentoMapa?.obtenerMiPosicion()
                if (posActual != null) {
                    val lat = posActual.latitude
                    val lon = posActual.longitude
                    if (LimitesFeria.estaDentro(lat, lon)) {
                        viewModel.latitudEdicionNueva = lat
                        viewModel.longitudEdicionNueva = lon
                        viewModel.ubicacionEdicionActualizada = true
                        bd.tvEstadoUbicacionEdicion.visibility = View.VISIBLE
                        Toast.makeText(requireContext(), R.string.msg_ubicacion_actualizada, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), R.string.aviso_ubicacion_fuera_feria_c2b, Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(requireContext(), R.string.buscando_posicion, Toast.LENGTH_SHORT).show()
                    fragmentoMapa?.activarUbicacion()
                }
            }
        }

        dialogoDatosPuesto = MaterialAlertDialogBuilder(requireContext())
            .setView(bd.root)
            .setCancelable(false)
            .setPositiveButton(R.string.btn_guardar, null)
            .setNegativeButton(R.string.btn_cancelar) { dialog, _ ->
                dialog.dismiss()
                dialogoDatosPuesto = null
                bindingDialogoPuesto = null
                viewModel.limpiarBorradorEdicion()
            }
            .create()

        dialogoDatosPuesto?.show()

        dialogoDatosPuesto?.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            val nombre = bd.etNombrePuesto.text?.toString()?.trim().orEmpty()
            if (nombre.isEmpty()) {
                bd.layoutNombrePuesto.error = getString(R.string.error_nombre_puesto_requerido)
            } else {
                bd.layoutNombrePuesto.error = null
                viewModel.guardarEdicionPuesto(
                    idPuesto = puesto.idPuesto,
                    nombre = nombre,
                    nuevaLat = viewModel.latitudEdicionNueva,
                    nuevaLon = viewModel.longitudEdicionNueva,
                    uriImagenNueva = viewModel.uriImagenEdicionBorrador
                )
            }
        }
    }

    private fun mostrarDialogoElegirProductoParaEditar(perfil: PerfilComerciante) {
        val productos = perfil.productos
        if (productos.isEmpty()) {
            Toast.makeText(requireContext(), R.string.msg_sin_productos_editar, Toast.LENGTH_SHORT).show()
            return
        }

        val bd = DialogoElegirProductoBinding.inflate(layoutInflater)
        var dialogoSeleccion: AlertDialog? = null

        val px40 = (40 * resources.displayMetrics.density).toInt()
        val px12 = (12 * resources.displayMetrics.density).toInt()
        val px16 = (16 * resources.displayMetrics.density).toInt()
        val px20Corner = 20 * resources.displayMetrics.density

        productos.forEachIndexed { i, prod ->
            val filaView = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(px16, px12, px16, px12)
                isClickable = true
                isFocusable = true
                val outValue = android.util.TypedValue()
                requireContext().theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                setBackgroundResource(outValue.resourceId)
            }

            val img = ShapeableImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(px40, px40).apply {
                    marginEnd = px12
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                    .setAllCorners(CornerFamily.ROUNDED, px20Corner)
                    .build()
            }

            val primeraImagen = prod.imagenes.firstOrNull()
            if (primeraImagen != null && !primeraImagen.url.isNullOrBlank()) {
                val urlImagen = construirUrlImagen(primeraImagen.url)
                Glide.with(this)
                    .load(urlImagen)
                    .placeholder(R.drawable.ic_puesto_placeholder)
                    .error(R.drawable.ic_puesto_placeholder)
                    .into(img)
            } else {
                img.setImageResource(R.drawable.ic_puesto_placeholder)
            }

            val tvNombre = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
                text = getString(R.string.formato_producto_vecino, "${i + 1}. ${prod.nombreProducto}", "")
                textSize = 15f
                setTextColor(ContextCompat.getColor(context, R.color.color_texto))
                typeface = Typeface.DEFAULT_BOLD
            }

            filaView.addView(img)
            filaView.addView(tvNombre)

            filaView.setOnClickListener {
                dialogoSeleccion?.dismiss()
                findNavController().navigate(
                    R.id.accionInicioComercianteAAgregarProducto,
                    bundleOf("idProducto" to prod.idProducto)
                )
            }

            bd.contenedorListaProductos.addView(filaView)
        }

        dialogoSeleccion = MaterialAlertDialogBuilder(requireContext())
            .setView(bd.root)
            .setNegativeButton(R.string.btn_cancelar) { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        dialogoSeleccion.show()
    }

    private fun mostrarDialogoUbicacion() {
        if (dialogoUbicacion?.isShowing == true) return

        viewModel.dialogoUbicacionVisible = true
        habilitarBarra(false)

        dialogoUbicacion = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.titulo_dialogo_ubicacion_comerciante)
            .setMessage(R.string.mensaje_dialogo_ubicacion_comerciante)
            .setCancelable(false)
            .setPositiveButton(R.string.btn_activar) { _, _ ->
                val contexto = requireContext()
                val locationManager = contexto.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                val gpsHabilitado = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

                if (!gpsHabilitado) {
                    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    startActivity(intent)
                    Toast.makeText(requireContext(), R.string.aviso_encender_gps_comerciante, Toast.LENGTH_LONG).show()
                    dialogoUbicacion?.dismiss()
                    viewModel.dialogoUbicacionVisible = false
                    habilitarBarra(true)
                } else {
                    fragmentoMapa?.activarUbicacion { concedido ->
                        if (concedido) {
                            viewModel.ubicacionActiva = true
                            fragmentoMapa?.ubicacionDeseada = true
                            dialogoUbicacion?.dismiss()
                            viewModel.dialogoUbicacionVisible = false
                            if (viewModel.fase == FaseAgregarPuesto.Inactiva) {
                                iniciarBusquedaDeUbicacion()
                            }
                        } else {
                            procesarCancelarDialogoUbicacion()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.btn_cancelar) { _, _ ->
                procesarCancelarDialogoUbicacion()
            }
            .create()

        dialogoUbicacion?.show()
    }

    private fun procesarCancelarDialogoUbicacion() {
        dialogoUbicacion?.dismiss()
        viewModel.dialogoUbicacionVisible = false
        habilitarBarra(true)

        snackbarActual?.dismiss()
        val snackbar = Snackbar.make(
            binding.barraInferior,
            R.string.snackbar_ubicacion_comerciante,
            10000
        )
        snackbar.setTextMaxLines(4)
        snackbar.setAction(R.string.btn_informacion) {
            navegarAMasInformacionSegura()
        }
        snackbarActual = snackbar
        snackbar.show()
    }

    private fun navegarAMasInformacionSegura() {
        snackbarActual?.dismiss()
        snackbarActual = null
        if (isAdded && view != null && findNavController().currentDestination?.id == R.id.fragmentoInicioComerciante) {
            findNavController().navigate(R.id.accionGlobalAMasInformacion)
        }
    }

    private fun mostrarConPuesto(puesto: PuestoPerfil, cantidadProductos: Int) {
        trabajoBusquedaUbicacion?.cancel()
        binding.indicadorCargandoPerfil.visibility = View.GONE
        binding.tarjetaError.visibility = View.GONE
        binding.tvEstadoUbicacion.visibility = View.GONE

        dialogoUbicacion?.dismiss()
        viewModel.dialogoUbicacionVisible = false

        if (!viewModel.modoEdicionDialogo) {
            dialogoDatosPuesto?.dismiss()
            dialogoDatosPuesto = null
            bindingDialogoPuesto = null
        }

        binding.layoutBarraSuperior.visibility = View.VISIBLE
        binding.tvEtiquetaAgregar.text = getString(R.string.etiqueta_agregar_producto)
        binding.tvContadorProductos.visibility = View.VISIBLE
        binding.tvContadorProductos.text = getString(R.string.formato_contador_productos, cantidadProductos)

        redibujarMarcadores()

        if (!viewModel.centradoInicialHecho) {
            viewModel.centradoInicialHecho = true
            fragmentoMapa?.ejecutarCuandoEsteListo {
                fragmentoMapa?.fijarCamaraDirecta(puesto.latitud, puesto.longitud, 18.0)
            }
        }

        binding.tvNombrePuestoMio.text = puesto.nombrePuesto
        val urlImagen = construirUrlImagen(puesto.imagenUrl)
        Glide.with(this)
            .load(urlImagen)
            .placeholder(R.drawable.ic_puesto_placeholder)
            .error(R.drawable.ic_puesto_placeholder)
            .into(binding.imgPuestoMio)

        binding.tarjetaMiPuesto.visibility = View.VISIBLE
        binding.btnEditarPuesto.setOnClickListener {
            mostrarDialogoEdicionPuesto(puesto)
        }

        val estadoActual = viewModel.estado.value
        if (estadoActual is EstadoInicioComerciante.ConPuesto) {
            binding.btnEditarProductos.setOnClickListener {
                mostrarDialogoElegirProductoParaEditar(estadoActual.perfil)
            }
        }

        if (cantidadProductos == 0 && !viewModel.globoDescartado) {
            binding.globoAgregarProducto.visibility = View.VISIBLE
            binding.globoAgregarProducto.setOnClickListener {
                viewModel.globoDescartado = true
                binding.globoAgregarProducto.visibility = View.GONE
            }
        } else {
            binding.globoAgregarProducto.visibility = View.GONE
        }

        actualizarChipsCategorias(viewModel.categoriasDelComerciante.value)
        actualizarTarjetasFlotantes()

        habilitarBarra(true)
        if (cantidadProductos >= 3) {
            binding.btnAgregar.isEnabled = true
            binding.btnAgregar.alpha = 0.4f
            binding.btnAgregar.setOnClickListener {
                Toast.makeText(requireContext(), R.string.toast_maximo_productos_alcanzado, Toast.LENGTH_SHORT).show()
            }
        } else {
            binding.btnAgregar.isEnabled = true
            binding.btnAgregar.alpha = 1.0f
            binding.btnAgregar.setOnClickListener {
                viewModel.globoDescartado = true
                binding.globoAgregarProducto.visibility = View.GONE
                findNavController().navigate(R.id.accionInicioComercianteAAgregarProducto)
            }
        }
    }

    private fun mostrarError(mensaje: String) {
        trabajoBusquedaUbicacion?.cancel()
        binding.indicadorCargandoPerfil.visibility = View.GONE
        binding.tarjetaError.visibility = View.VISIBLE
        binding.tvMensajeError.text = mensaje
        binding.btnReintentar.setOnClickListener {
            viewModel.recargarPerfil()
        }

        binding.contenedorFiltrosCategorias.visibility = View.GONE
        binding.tarjetaMiPuesto.visibility = View.GONE
        binding.tarjetaVecino.visibility = View.GONE
        binding.globoAgregarProducto.visibility = View.GONE

        habilitarBarra(false)
        binding.btnMenu.isEnabled = true
        binding.btnMenu.alpha = 1.0f
        binding.btnAcercar.isEnabled = true
        binding.btnAcercar.alpha = 1.0f
        binding.btnAlejar.isEnabled = true
        binding.btnAlejar.alpha = 1.0f
    }

    override fun onDestroyView() {
        super.onDestroyView()
        trabajoBusquedaUbicacion?.cancel()
        trabajoCentrarUbicacion?.cancel()

        snackbarActual?.dismiss()
        snackbarActual = null

        dialogoUbicacion?.dismiss()
        dialogoUbicacion = null

        dialogoDatosPuesto?.dismiss()
        dialogoDatosPuesto = null
        bindingDialogoPuesto = null

        _binding = null
    }
}
