package com.feriapp.app.ui.comerciante

import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import com.feriapp.app.data.model.Categoria
import com.feriapp.app.data.model.Producto
import com.feriapp.app.databinding.FragmentoAgregarProductoBinding
import com.feriapp.app.ui.common.fabricaViewModel
import com.feriapp.app.util.construirUrlImagen
import com.feriapp.app.util.crearUriFotoCamara
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.util.Locale

class FragmentoAgregarProducto : Fragment() {

    private var _binding: FragmentoAgregarProductoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AgregarProductoViewModel by viewModels {
        fabricaViewModel {
            val contenedor = (requireActivity().application as AplicacionFeria).contenedorApp
            AgregarProductoViewModel(
                contenedor.repositorioCategorias,
                contenedor.repositorioProductos,
                contenedor.repositorioComerciante,
                requireContext().applicationContext
            )
        }
    }

    private var listaCategorias: List<Categoria> = emptyList()
    private var precargadoFormularioHecho = false

    private val lanzadorCamara = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { exito ->
        if (exito) {
            val uri = viewModel.uriFotoCamaraPendiente
            if (uri != null) {
                viewModel.uriImagenState.value = uri
                viewModel.imagenQuitadaManualmente = false
            }
        } else {
            viewModel.uriFotoCamaraPendiente?.let { uri ->
                try {
                    requireContext().contentResolver.delete(uri, null, null)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            viewModel.uriFotoCamaraPendiente = null
        }
    }

    private val lanzadorGaleria = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.uriImagenState.value = uri
            viewModel.imagenQuitadaManualmente = false
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentoAgregarProductoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val idProductoArg = arguments?.getInt("idProducto", -1) ?: -1
        if (idProductoArg != -1) {
            viewModel.precargarProductoParaEdicion(idProductoArg)
            binding.tvTituloAgregarProducto.text = getString(R.string.titulo_editar_producto)
            binding.btnGuardarProducto.text = getString(R.string.btn_guardar_cambios)
            binding.btnEliminarProducto.visibility = View.VISIBLE
        } else {
            binding.tvTituloAgregarProducto.text = getString(R.string.titulo_agregar_tu_producto)
            binding.btnGuardarProducto.text = getString(R.string.btn_guardar)
            binding.btnEliminarProducto.visibility = View.GONE
        }

        configurarOyentes()
        configurarBotonAtras()
        observarEstadoViewModel()
    }

    private fun configurarOyentes() {
        binding.ivImagenProducto.setOnClickListener {
            mostrarOpcionesImagen()
        }

        binding.btnQuitarImagen.setOnClickListener {
            viewModel.uriImagenState.value = null
            viewModel.imagenQuitadaManualmente = true
        }

        binding.btnGuardarProducto.setOnClickListener {
            intentarGuardar()
        }

        binding.btnCancelarProducto.setOnClickListener {
            procesarCancelarOVolver()
        }

        binding.btnEliminarProducto.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.confirmar_eliminar_producto)
                .setPositiveButton(R.string.btn_descartar) { dialog, _ ->
                    dialog.dismiss()
                    viewModel.eliminarProducto()
                }
                .setNegativeButton(R.string.btn_cancelar) { dialog, _ ->
                    dialog.dismiss()
                }
                .show()
        }

        binding.btnReintentarCategorias.setOnClickListener {
            viewModel.reintentarCategorias()
        }
    }

    private fun mostrarOpcionesImagen() {
        val tieneImagen = viewModel.uriImagenState.value != null
        val opciones = if (tieneImagen) {
            arrayOf(
                getString(R.string.opcion_tomar_foto),
                getString(R.string.opcion_elegir_galeria),
                getString(R.string.opcion_quitar_imagen)
            )
        } else {
            arrayOf(
                getString(R.string.opcion_tomar_foto),
                getString(R.string.opcion_elegir_galeria)
            )
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.titulo_dialogo_opciones_imagen)
            .setItems(opciones) { dialog, which ->
                dialog.dismiss()
                when (which) {
                    0 -> tomarFoto()
                    1 -> elegirGaleria()
                    2 -> {
                        viewModel.uriImagenState.value = null
                        viewModel.imagenQuitadaManualmente = true
                    }
                }
            }
            .show()
    }

    private fun tomarFoto() {
        try {
            val uri = crearUriFotoCamara(requireContext())
            viewModel.uriFotoCamaraPendiente = uri
            lanzadorCamara.launch(uri)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.aviso_no_app_camara, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), e.localizedMessage ?: "Error al abrir la cámara", Toast.LENGTH_SHORT).show()
        }
    }

    private fun elegirGaleria() {
        lanzadorGaleria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun intentarGuardar() {
        limpiarErrores()
        var esValido = true

        val nombre = binding.etNombreProducto.text?.toString()?.trim().orEmpty()
        val descripcion = binding.etDescripcionProducto.text?.toString()?.trim().orEmpty()
        val precioTexto = binding.etPrecioProducto.text?.toString()?.trim().orEmpty().replace(',', '.')
        val disponible = binding.cbDisponible.isChecked

        if (nombre.isEmpty()) {
            binding.layoutNombreProducto.error = getString(R.string.error_nombre_producto_requerido)
            esValido = false
        }

        if (viewModel.idCategoriaSeleccionada == null) {
            binding.layoutCategoriaProducto.error = getString(R.string.error_categoria_requerida)
            esValido = false
        }

        val precioNum = precioTexto.toDoubleOrNull()
        if (precioNum == null || precioNum <= 0.0) {
            binding.layoutPrecioProducto.error = getString(R.string.error_precio_invalido)
            esValido = false
        }

        if (esValido && precioNum != null) {
            viewModel.guardarProducto(
                nombre = nombre,
                descripcion = descripcion,
                precio = precioNum,
                disponible = disponible
            )
        }
    }

    private fun limpiarErrores() {
        binding.layoutNombreProducto.error = null
        binding.layoutCategoriaProducto.error = null
        binding.layoutPrecioProducto.error = null
    }

    private fun hayDatosIngresados(): Boolean {
        val nombre = binding.etNombreProducto.text?.toString()?.trim().orEmpty()
        val descripcion = binding.etDescripcionProducto.text?.toString()?.trim().orEmpty()
        val precio = binding.etPrecioProducto.text?.toString()?.trim().orEmpty()
        val idCategoria = viewModel.idCategoriaSeleccionada
        val uriImagen = viewModel.uriImagenState.value

        if (!viewModel.modoEdicion) {
            return nombre.isNotEmpty() || descripcion.isNotEmpty() || precio.isNotEmpty() || idCategoria != null || uriImagen != null
        } else {
            val precioNum = precio.replace(',', '.').toDoubleOrNull() ?: 0.0
            val cambioNombre = nombre != viewModel.nombreInicial
            val cambioDescripcion = descripcion != viewModel.descripcionInicial
            val cambioPrecio = Math.abs(precioNum - viewModel.precioInicial) > 0.001
            val cambioDisponible = binding.cbDisponible.isChecked != viewModel.disponibleInicial
            val cambioCategoria = idCategoria != viewModel.idCategoriaInicial
            val cambioImagen = uriImagen?.toString() != viewModel.imagenOriginalUrl

            return cambioNombre || cambioDescripcion || cambioPrecio || cambioDisponible || cambioCategoria || cambioImagen
        }
    }

    private fun procesarCancelarOVolver() {
        if (viewModel.estadoGuardadoProducto.value is EstadoGuardadoProducto.Guardando) {
            return
        }

        if (hayDatosIngresados()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.titulo_dialogo_descartar_producto)
                .setPositiveButton(R.string.btn_descartar) { dialog, _ ->
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
                    procesarCancelarOVolver()
                }
            }
        )
    }

    private fun observarEstadoViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estadoCategorias.collect { estado ->
                    when (estado) {
                        is EstadoCategorias.Cargando -> {
                            binding.tvErrorCategorias.visibility = View.GONE
                            binding.btnReintentarCategorias.visibility = View.GONE
                        }
                        is EstadoCategorias.Lista -> {
                            binding.tvErrorCategorias.visibility = View.GONE
                            binding.btnReintentarCategorias.visibility = View.GONE
                            listaCategorias = estado.categorias
                            configurarDesplegableCategorias(estado.categorias)
                        }
                        is EstadoCategorias.Error -> {
                            binding.tvErrorCategorias.visibility = View.VISIBLE
                            binding.tvErrorCategorias.text = estado.mensaje
                            binding.btnReintentarCategorias.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estadoPrecarga.collect { estado ->
                    if (estado is EstadoPrecargaProducto.Listo) {
                        rellenarCamposEdicion(estado.producto)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uriImagenState.collect { uri ->
                    if (uri != null) {
                        val urlStr = uri.toString()
                        if (urlStr.startsWith("http://") || urlStr.startsWith("https://") || urlStr.startsWith("/")) {
                            val urlCompleta = construirUrlImagen(urlStr)
                            Glide.with(this@FragmentoAgregarProducto)
                                .load(urlCompleta)
                                .into(binding.ivImagenProducto)
                        } else {
                            Glide.with(this@FragmentoAgregarProducto)
                                .load(uri)
                                .into(binding.ivImagenProducto)
                        }
                        binding.ivImagenProducto.scaleType = ImageView.ScaleType.CENTER_CROP
                        binding.ivImagenProducto.setPadding(0, 0, 0, 0)
                        binding.btnQuitarImagen.visibility = View.VISIBLE
                    } else {
                        binding.ivImagenProducto.setImageResource(R.drawable.ic_agregar_imagen)
                        binding.ivImagenProducto.scaleType = ImageView.ScaleType.CENTER_INSIDE
                        val paddingPx = (20 * resources.displayMetrics.density).toInt()
                        binding.ivImagenProducto.setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
                        binding.btnQuitarImagen.visibility = View.GONE
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estadoGuardadoProducto.collect { estado ->
                    when (estado) {
                        is EstadoGuardadoProducto.Inactivo -> {
                            mostrarGuardando(false)
                        }
                        is EstadoGuardadoProducto.Guardando -> {
                            mostrarGuardando(true)
                        }
                        is EstadoGuardadoProducto.Exito -> {
                            mostrarGuardando(false)
                            viewModel.resetearEstadoGuardado()
                            val mensajeToast = if (estado.imagenFallo) {
                                getString(R.string.producto_guardado_sin_imagen)
                            } else if (viewModel.modoEdicion) {
                                getString(R.string.msg_producto_actualizado)
                            } else {
                                getString(R.string.producto_guardado)
                            }
                            Toast.makeText(requireContext(), mensajeToast, Toast.LENGTH_LONG).show()

                            findNavController().previousBackStackEntry?.savedStateHandle?.set("producto_agregado", true)
                            findNavController().popBackStack()
                        }
                        is EstadoGuardadoProducto.ExitoEliminado -> {
                            mostrarGuardando(false)
                            viewModel.resetearEstadoGuardado()
                            Toast.makeText(requireContext(), R.string.msg_producto_eliminado, Toast.LENGTH_SHORT).show()

                            findNavController().previousBackStackEntry?.savedStateHandle?.set("producto_agregado", true)
                            findNavController().popBackStack()
                        }
                        is EstadoGuardadoProducto.Error -> {
                            mostrarGuardando(false)
                            Toast.makeText(requireContext(), estado.mensaje, Toast.LENGTH_LONG).show()
                            viewModel.resetearEstadoGuardado()
                        }
                    }
                }
            }
        }
    }

    private fun rellenarCamposEdicion(producto: Producto) {
        if (!precargadoFormularioHecho) {
            precargadoFormularioHecho = true
            binding.etNombreProducto.setText(producto.nombreProducto)
            binding.etDescripcionProducto.setText(producto.descripcion.orEmpty())

            val precioTxt = if (producto.precio % 1.0 == 0.0) {
                producto.precio.toInt().toString()
            } else {
                String.format(Locale.getDefault(), "%.2f", producto.precio)
            }
            binding.etPrecioProducto.setText(precioTxt)
            binding.cbDisponible.isChecked = producto.disponible

            if (listaCategorias.isNotEmpty()) {
                configurarDesplegableCategorias(listaCategorias)
            }
        }
    }

    private fun configurarDesplegableCategorias(categorias: List<Categoria>) {
        val nombres = categorias.map { it.nombreCategoria }
        val adaptador = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, nombres)
        binding.actvCategoria.setAdapter(adaptador)

        binding.actvCategoria.setOnItemClickListener { _, _, position, _ ->
            binding.layoutCategoriaProducto.error = null
            val seleccionada = categorias.find { it.nombreCategoria == nombres[position] }
            viewModel.idCategoriaSeleccionada = seleccionada?.idCategoria
        }

        val idSel = viewModel.idCategoriaSeleccionada
        if (idSel != null) {
            val seleccionada = categorias.find { it.idCategoria == idSel }
            if (seleccionada != null) {
                binding.actvCategoria.setText(seleccionada.nombreCategoria, false)
            }
        }
    }

    private fun mostrarGuardando(guardando: Boolean) {
        binding.pbGuardandoProducto.visibility = if (guardando) View.VISIBLE else View.GONE
        binding.btnGuardarProducto.isEnabled = !guardando
        binding.btnCancelarProducto.isEnabled = !guardando
        binding.btnEliminarProducto.isEnabled = !guardando
        binding.etNombreProducto.isEnabled = !guardando
        binding.etDescripcionProducto.isEnabled = !guardando
        binding.actvCategoria.isEnabled = !guardando
        binding.etPrecioProducto.isEnabled = !guardando
        binding.cbDisponible.isEnabled = !guardando
        binding.ivImagenProducto.isEnabled = !guardando
        binding.btnQuitarImagen.isEnabled = !guardando
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
