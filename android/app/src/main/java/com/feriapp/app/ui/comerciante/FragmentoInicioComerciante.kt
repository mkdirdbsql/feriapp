package com.feriapp.app.ui.comerciante

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
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
import com.feriapp.app.data.model.PuestoPerfil
import com.feriapp.app.databinding.DialogoAgregarPuestoBinding
import com.feriapp.app.databinding.FragmentoInicioComercianteBinding
import com.feriapp.app.ui.common.ControladorMenuLateral
import com.feriapp.app.ui.common.FragmentoMapa
import com.feriapp.app.ui.common.MarcadorMapa
import com.feriapp.app.ui.common.fabricaViewModel
import com.feriapp.app.ui.consumidor.CamaraMapa
import com.feriapp.app.util.LimitesFeria
import com.feriapp.app.util.construirUrlImagen
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

class FragmentoInicioComerciante : Fragment() {

    private var _binding: FragmentoInicioComercianteBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InicioComercianteViewModel by viewModels {
        fabricaViewModel {
            val contenedor = (requireActivity().application as AplicacionFeria).contenedorApp
            InicioComercianteViewModel(
                contenedor.repositorioComerciante,
                contenedor.repositorioPuestos,
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
            viewModel.uriImagenBorrador = uri
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
        observarEstadoViewModel()
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
                            if (viewModel.fase == FaseAgregarPuesto.Inactiva) {
                                dialogoDatosPuesto?.dismiss()
                                dialogoDatosPuesto = null
                                bindingDialogoPuesto = null
                            }
                        }
                        is EstadoGuardadoPuesto.Guardando -> {
                            bindingDialogoPuesto?.pbGuardando?.visibility = View.VISIBLE
                            setBotonesDialogoHabilitados(false)
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
    }

    private fun setBotonesDialogoHabilitados(habilitados: Boolean) {
        dialogoDatosPuesto?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = habilitados
        dialogoDatosPuesto?.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = habilitados
        bindingDialogoPuesto?.ivImagenPuesto?.isEnabled = habilitados
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
        binding.tvEstadoUbicacion.visibility = View.GONE
        binding.tarjetaMiPuesto.visibility = View.GONE
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
        binding.tarjetaMiPuesto.visibility = View.GONE
        binding.globoAgregarProducto.visibility = View.GONE

        binding.tvEtiquetaAgregar.text = getString(R.string.etiqueta_agregar_puesto)
        binding.tvContadorProductos.visibility = View.GONE

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
                            iniciarBusquedaDeUbicacion()
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

        dialogoDatosPuesto?.dismiss()
        dialogoDatosPuesto = null
        bindingDialogoPuesto = null

        binding.layoutBarraSuperior.visibility = View.VISIBLE
        binding.tvEtiquetaAgregar.text = getString(R.string.etiqueta_agregar_producto)
        binding.tvContadorProductos.visibility = View.VISIBLE
        binding.tvContadorProductos.text = getString(R.string.formato_contador_productos, cantidadProductos)

        val marcadores = listOf(
            MarcadorMapa(
                id = puesto.idPuesto,
                latitud = puesto.latitud,
                longitud = puesto.longitud,
                titulo = puesto.nombrePuesto,
                icono = R.drawable.ic_marcador_puesto
            )
        )
        fragmentoMapa?.mostrarMarcadores(marcadores)

        if (!viewModel.centradoInicialHecho) {
            viewModel.centradoInicialHecho = true
            fragmentoMapa?.ejecutarCuandoEsteListo {
                fragmentoMapa?.fijarCamaraDirecta(puesto.latitud, puesto.longitud, 18.0)
            }
        }

        // Configurar tarjeta fija "Mi puesto"
        binding.tvNombrePuestoMio.text = puesto.nombrePuesto
        val urlImagen = construirUrlImagen(puesto.imagenUrl)
        Glide.with(this)
            .load(urlImagen)
            .placeholder(R.drawable.ic_puesto_placeholder)
            .error(R.drawable.ic_puesto_placeholder)
            .into(binding.imgPuestoMio)

        binding.tarjetaMiPuesto.visibility = View.VISIBLE
        binding.btnEditarPuesto.setOnClickListener {
            // TODO C6: Editar puesto se implementa en un paso siguiente
            Toast.makeText(requireContext(), R.string.toast_editar_puesto_proximamente, Toast.LENGTH_SHORT).show()
        }
        binding.btnEditarProductos.setOnClickListener {
            // TODO C7: Editar productos se implementa en un paso siguiente
            Toast.makeText(requireContext(), R.string.toast_editar_productos_proximamente, Toast.LENGTH_SHORT).show()
        }

        // Configurar globo de ayuda para agregar producto
        if (cantidadProductos == 0 && !viewModel.globoDescartado) {
            binding.globoAgregarProducto.visibility = View.VISIBLE
            binding.globoAgregarProducto.setOnClickListener {
                viewModel.globoDescartado = true
                binding.globoAgregarProducto.visibility = View.GONE
            }
        } else {
            binding.globoAgregarProducto.visibility = View.GONE
        }

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
                // TODO C4: Agregar producto se implementa en un paso siguiente
                Toast.makeText(requireContext(), R.string.toast_agregar_producto_proximamente, Toast.LENGTH_SHORT).show()
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

        binding.tarjetaMiPuesto.visibility = View.GONE
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
