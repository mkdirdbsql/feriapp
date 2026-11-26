package com.feriapp.app.ui.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.feriapp.app.AplicacionFeria
import com.feriapp.app.R
import com.feriapp.app.data.model.DetalleComerciante
import com.feriapp.app.data.model.ProductoResumen
import com.feriapp.app.databinding.FragmentoEdicionUsuarioBinding
import com.feriapp.app.ui.common.fabricaViewModel
import com.feriapp.app.util.construirUrlImagen
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class FragmentoEdicionUsuario : Fragment() {

    private var _binding: FragmentoEdicionUsuarioBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EdicionUsuarioViewModel by viewModels {
        fabricaViewModel {
            val contenedor = (requireActivity().application as AplicacionFeria).contenedorApp
            EdicionUsuarioViewModel(
                contenedor.repositorioAdministracion,
                contenedor.gestorSesion,
                requireContext().applicationContext
            )
        }
    }

    private var idComercianteArg: Int = -1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentoEdicionUsuarioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        idComercianteArg = arguments?.getInt("idComerciante", -1) ?: -1
        if (idComercianteArg != -1) {
            viewModel.cargarDetalle(idComercianteArg)
        }

        configurarOyentes()
        configurarBotonAtras()
        iniciarReloj()
        observarEstadoViewModel()
    }

    private fun configurarOyentes() {
        binding.toggleGroupEstadoAdmin.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnToggleHabilitado -> viewModel.opcionEstadoSeleccionada = OpcionEstadoAdmin.Habilitado
                    R.id.btnToggleDeshabilitado -> viewModel.opcionEstadoSeleccionada = OpcionEstadoAdmin.Deshabilitado
                    R.id.btnToggleEliminar -> viewModel.opcionEstadoSeleccionada = OpcionEstadoAdmin.Eliminar
                }
            }
        }

        binding.btnGuardarEdicion.setOnClickListener {
            intentarGuardar()
        }

        binding.btnSalirEdicion.setOnClickListener {
            procesarSalir()
        }

        binding.btnReintentarEdicion.setOnClickListener {
            viewModel.reintentar()
        }
    }

    private fun intentarGuardar() {
        val estadoActual = viewModel.estado.value
        val nombreUser = (estadoActual as? EstadoEdicionUsuario.Listo)?.detalle?.nombre ?: "este usuario"

        if (viewModel.opcionEstadoSeleccionada == viewModel.estadoInicialServidor) {
            Toast.makeText(requireContext(), R.string.msg_no_hay_cambios, Toast.LENGTH_SHORT).show()
            return
        }

        when (viewModel.opcionEstadoSeleccionada) {
            OpcionEstadoAdmin.Habilitado -> {
                viewModel.guardarEstado("activo")
            }
            OpcionEstadoAdmin.Deshabilitado -> {
                MaterialAlertDialogBuilder(requireContext())
                    .setMessage(getString(R.string.confirmar_deshabilitar_usuario, nombreUser))
                    .setPositiveButton(R.string.btn_deshabilitar) { dialog, _ ->
                        dialog.dismiss()
                        viewModel.guardarEstado("inactivo")
                    }
                    .setNegativeButton(R.string.btn_cancelar) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }
            OpcionEstadoAdmin.Eliminar -> {
                MaterialAlertDialogBuilder(requireContext())
                    .setMessage(getString(R.string.confirmar_eliminar_usuario, nombreUser))
                    .setPositiveButton(R.string.btn_descartar) { dialog, _ ->
                        dialog.dismiss()
                        viewModel.eliminarComerciante()
                    }
                    .setNegativeButton(R.string.btn_cancelar) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }
        }
    }

    private fun procesarSalir() {
        if (viewModel.estadoGuardado.value is EstadoGuardadoAdmin.Guardando) {
            return
        }

        if (viewModel.opcionEstadoSeleccionada != viewModel.estadoInicialServidor) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.titulo_dialogo_cambios_sin_guardar)
                .setMessage(R.string.mensaje_dialogo_cambios_sin_guardar)
                .setPositiveButton(R.string.btn_salir_sin_guardar) { dialog, _ ->
                    dialog.dismiss()
                    findNavController().popBackStack()
                }
                .setNegativeButton(R.string.btn_seguir_editando) { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        } else {
            findNavController().popBackStack()
        }
    }

    private fun configurarBotonAtras() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    procesarSalir()
                }
            }
        )
    }

    private fun iniciarReloj() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val ahora = Calendar.getInstance().time
                    val formatoFecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    val formatoHora = SimpleDateFormat("hh:mm a", Locale.getDefault())

                    binding.tvFechaEdicionAdmin.text = getString(R.string.formato_fecha_admin, formatoFecha.format(ahora))
                    binding.tvHoraEdicionAdmin.text = getString(R.string.formato_hora_admin, formatoHora.format(ahora))

                    delay(10000)
                }
            }
        }
    }

    private fun observarEstadoViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estado.collect { estado ->
                    when (estado) {
                        is EstadoEdicionUsuario.Cargando -> {
                            mostrarCargando()
                        }
                        is EstadoEdicionUsuario.Listo -> {
                            mostrarListo(estado.detalle)
                        }
                        is EstadoEdicionUsuario.Error -> {
                            if (estado.esSesionExpirada) {
                                Toast.makeText(requireContext(), R.string.msg_sesion_expirada, Toast.LENGTH_LONG).show()
                                viewLifecycleOwner.lifecycleScope.launch {
                                    viewModel.cerrarSesion()
                                    findNavController().navigate(R.id.accionGlobalASeleccionUsuarioLimpiandoPila)
                                }
                            } else if (estado.esNoEncontrado) {
                                Toast.makeText(requireContext(), R.string.msg_usuario_no_existe, Toast.LENGTH_LONG).show()
                                findNavController().previousBackStackEntry?.savedStateHandle?.set("usuario_modificado", true)
                                findNavController().popBackStack()
                            } else {
                                mostrarError(estado.mensaje)
                            }
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estadoGuardado.collect { estado ->
                    when (estado) {
                        is EstadoGuardadoAdmin.Inactivo -> {
                            mostrarGuardando(false)
                        }
                        is EstadoGuardadoAdmin.Guardando -> {
                            mostrarGuardando(true)
                        }
                        is EstadoGuardadoAdmin.ExitoEstado -> {
                            mostrarGuardando(false)
                            viewModel.resetearEstadoGuardado()
                            Toast.makeText(requireContext(), R.string.msg_usuario_estado_actualizado, Toast.LENGTH_SHORT).show()
                            findNavController().previousBackStackEntry?.savedStateHandle?.set("usuario_modificado", true)
                        }
                        is EstadoGuardadoAdmin.ExitoEliminado -> {
                            mostrarGuardando(false)
                            viewModel.resetearEstadoGuardado()
                            Toast.makeText(requireContext(), R.string.msg_usuario_eliminado_admin, Toast.LENGTH_SHORT).show()
                            findNavController().previousBackStackEntry?.savedStateHandle?.set("usuario_modificado", true)
                            findNavController().popBackStack()
                        }
                        is EstadoGuardadoAdmin.Error -> {
                            mostrarGuardando(false)
                            Toast.makeText(requireContext(), estado.mensaje, Toast.LENGTH_LONG).show()
                            viewModel.resetearEstadoGuardado()
                        }
                    }
                }
            }
        }
    }

    private fun mostrarCargando() {
        binding.indicadorCargandoEdicion.visibility = View.VISIBLE
        binding.tarjetaErrorEdicion.visibility = View.GONE
        binding.tarjetaDetalleUsuario.visibility = View.GONE
        binding.tarjetaSeccionProductos.visibility = View.GONE
        binding.toggleGroupEstadoAdmin.visibility = View.GONE
    }

    private fun mostrarListo(detalle: DetalleComerciante) {
        binding.indicadorCargandoEdicion.visibility = View.GONE
        binding.tarjetaErrorEdicion.visibility = View.GONE
        binding.tarjetaDetalleUsuario.visibility = View.VISIBLE
        binding.tarjetaSeccionProductos.visibility = View.VISIBLE
        binding.toggleGroupEstadoAdmin.visibility = View.VISIBLE

        binding.tvNombreUsuarioEdicion.text = getString(R.string.formato_usuario_nombre, detalle.nombre, detalle.apellido)
        val estadoTxt = if (detalle.estado.equals("activo", true)) getString(R.string.estado_activo) else getString(R.string.estado_inactivo)
        binding.tvComercioEstadoEdicion.text = getString(R.string.formato_telefono_estado, detalle.telefono, estadoTxt)

        // Foto Puesto
        val urlFotoPuesto = construirUrlImagen(detalle.puesto?.imagenUrl)
        Glide.with(this)
            .load(urlFotoPuesto)
            .placeholder(R.drawable.ic_puesto_placeholder)
            .error(R.drawable.ic_puesto_placeholder)
            .into(binding.imgPuestoEdicion)

        // Ubicación
        if (detalle.puesto != null) {
            val lat = detalle.puesto.latitud
            val lon = detalle.puesto.longitud
            binding.tvUbicacionEdicion.text = String.format(Locale.getDefault(), "Ubicación: %.5f, %.5f", lat, lon)
            binding.tvUbicacionEdicion.setOnClickListener {
                copiarCoordenadas(lat, lon)
            }
        } else {
            binding.tvUbicacionEdicion.text = getString(R.string.ubicacion_sin_puesto)
            binding.tvUbicacionEdicion.setOnClickListener(null)
        }

        // Configurar toggle group estado
        when (viewModel.opcionEstadoSeleccionada) {
            OpcionEstadoAdmin.Habilitado -> binding.toggleGroupEstadoAdmin.check(R.id.btnToggleHabilitado)
            OpcionEstadoAdmin.Deshabilitado -> binding.toggleGroupEstadoAdmin.check(R.id.btnToggleDeshabilitado)
            OpcionEstadoAdmin.Eliminar -> binding.toggleGroupEstadoAdmin.check(R.id.btnToggleEliminar)
        }

        // Productos
        val productos = detalle.productos
        if (productos.isEmpty()) {
            binding.layoutSelectorProducto.isEnabled = false
            binding.actvSelectorProductoEdicion.isEnabled = false
            binding.actvSelectorProductoEdicion.setText("", false)
            binding.tvSinProductosEdicion.visibility = View.VISIBLE
            binding.contenedorDetalleProductoEdicion.visibility = View.GONE
        } else {
            binding.layoutSelectorProducto.isEnabled = true
            binding.actvSelectorProductoEdicion.isEnabled = true
            binding.tvSinProductosEdicion.visibility = View.GONE
            binding.contenedorDetalleProductoEdicion.visibility = View.VISIBLE

            val opcionesNum = (1..productos.size).map { it.toString() }
            val adaptador = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, opcionesNum)
            binding.actvSelectorProductoEdicion.setAdapter(adaptador)

            if (viewModel.indiceProductoSeleccionado >= productos.size) {
                viewModel.indiceProductoSeleccionado = 0
            }

            val numActual = (viewModel.indiceProductoSeleccionado + 1).toString()
            binding.actvSelectorProductoEdicion.setText(numActual, false)

            binding.actvSelectorProductoEdicion.setOnItemClickListener { _, _, pos, _ ->
                viewModel.indiceProductoSeleccionado = pos
                viewModel.indiceFotoProductoActual = 0
                actualizarDetalleProducto(productos)
            }

            actualizarDetalleProducto(productos)
        }
    }

    private fun actualizarDetalleProducto(productos: List<ProductoResumen>) {
        if (productos.isEmpty()) return
        val idx = viewModel.indiceProductoSeleccionado.coerceIn(0, productos.size - 1)
        val prod = productos[idx]

        binding.tvNombreProductoEdicion.text = getString(
            R.string.formato_detalle_producto_num,
            prod.nombreProducto,
            idx + 1,
            productos.size
        )
        binding.tvCategoriaProductoEdicion.text = getString(R.string.formato_categoria_producto, prod.nombreCategoria)

        val precioTxt = if (prod.precio % 1.0 == 0.0) prod.precio.toInt().toString() else String.format(Locale.getDefault(), "%.2f", prod.precio)
        binding.tvPrecioProductoEdicion.text = getString(R.string.precio_formato, precioTxt)

        val dispTxt = if (prod.disponible) getString(R.string.texto_si) else getString(R.string.texto_no)
        binding.tvDisponibleProductoEdicion.text = getString(R.string.formato_disponible_si_no, dispTxt)
        if (!prod.disponible) {
            binding.tvDisponibleProductoEdicion.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_error))
        } else {
            binding.tvDisponibleProductoEdicion.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_texto))
        }

        // Fotos
        val imagenes = prod.imagenes
        if (imagenes.isEmpty()) {
            binding.btnAnteriorFotoEdicion.visibility = View.GONE
            binding.btnSiguienteFotoEdicion.visibility = View.GONE
            binding.tvContadorFotosEdicion.visibility = View.GONE
            binding.imgProductoEdicion.setImageResource(R.drawable.ic_puesto_placeholder)
        } else {
            val imgIdx = viewModel.indiceFotoProductoActual.coerceIn(0, imagenes.size - 1)
            val imgActual = imagenes[imgIdx]
            val urlImg = construirUrlImagen(imgActual.url)

            Glide.with(this)
                .load(urlImg)
                .placeholder(R.drawable.ic_puesto_placeholder)
                .error(R.drawable.ic_puesto_placeholder)
                .into(binding.imgProductoEdicion)

            if (imagenes.size > 1) {
                binding.btnAnteriorFotoEdicion.visibility = View.VISIBLE
                binding.btnSiguienteFotoEdicion.visibility = View.VISIBLE
                binding.tvContadorFotosEdicion.visibility = View.VISIBLE
                binding.tvContadorFotosEdicion.text = "${imgIdx + 1}/${imagenes.size}"

                binding.btnAnteriorFotoEdicion.setOnClickListener {
                    viewModel.indiceFotoProductoActual = if (imgIdx > 0) imgIdx - 1 else imagenes.size - 1
                    actualizarDetalleProducto(productos)
                }

                binding.btnSiguienteFotoEdicion.setOnClickListener {
                    viewModel.indiceFotoProductoActual = if (imgIdx < imagenes.size - 1) imgIdx + 1 else 0
                    actualizarDetalleProducto(productos)
                }
            } else {
                binding.btnAnteriorFotoEdicion.visibility = View.GONE
                binding.btnSiguienteFotoEdicion.visibility = View.GONE
                binding.tvContadorFotosEdicion.visibility = View.GONE
            }
        }
    }

    private fun copiarCoordenadas(lat: Double, lon: Double) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Coordenadas FeriApp", String.format(Locale.US, "%.5f, %.5f", lat, lon))
        clipboard.setPrimaryClip(clip)
        Toast.makeText(requireContext(), R.string.msg_coordenadas_copiadas, Toast.LENGTH_SHORT).show()
    }

    private fun mostrarError(mensaje: String) {
        binding.indicadorCargandoEdicion.visibility = View.GONE
        binding.tarjetaErrorEdicion.visibility = View.VISIBLE
        binding.tvMensajeErrorEdicion.text = mensaje
    }

    private fun mostrarGuardando(guardando: Boolean) {
        binding.pbGuardandoEdicion.visibility = if (guardando) View.VISIBLE else View.GONE
        binding.btnGuardarEdicion.isEnabled = !guardando
        binding.btnSalirEdicion.isEnabled = !guardando
        binding.btnToggleHabilitado.isEnabled = !guardando
        binding.btnToggleDeshabilitado.isEnabled = !guardando
        binding.btnToggleEliminar.isEnabled = !guardando
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
