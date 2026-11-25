package com.feriapp.app.ui.admin

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.FileProvider
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.feriapp.app.AplicacionFeria
import com.feriapp.app.R
import com.feriapp.app.data.model.Comerciante
import com.feriapp.app.data.model.Reporte
import com.feriapp.app.databinding.FragmentoInicioAdministradorBinding
import com.feriapp.app.ui.common.FragmentoMapa
import com.feriapp.app.ui.common.MarcadorMapa
import com.feriapp.app.ui.common.fabricaViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class FragmentoInicioAdministrador : Fragment() {

    private var _binding: FragmentoInicioAdministradorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InicioAdministradorViewModel by viewModels {
        fabricaViewModel {
            val contenedor = (requireActivity().application as AplicacionFeria).contenedorApp
            InicioAdministradorViewModel(
                contenedor.repositorioAdministracion,
                contenedor.gestorSesion,
                requireContext().applicationContext
            )
        }
    }

    private var fragmentoMapa: FragmentoMapa? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentoInicioAdministradorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragmentoMapa = childFragmentManager.findFragmentById(R.id.contenedorMapaAdmin) as? FragmentoMapa

        configurarNavegacion()
        configurarControlesZoom()
        configurarToggleFormato()
        configurarBotonAtras()
        iniciarReloj()
        observarResultadoEdicionUsuario()
        observarEstadoViewModel()
    }

    private fun configurarNavegacion() {
        binding.btnVerUsuarioAdmin.setOnClickListener {
            val id = viewModel.idComercianteSeleccionado ?: return@setOnClickListener
            findNavController().navigate(
                R.id.accionInicioAdministradorAEdicionUsuario,
                bundleOf("idComerciante" to id)
            )
        }

        binding.btnGenerarReporteAdmin.setOnClickListener {
            val formato = viewModel.formatoReporteSeleccionado
            viewModel.generarReporte(formato)
        }

        binding.btnSalirAdmin.setOnClickListener {
            confirmarSalirAdmin()
        }

        binding.btnReintentarAdmin.setOnClickListener {
            viewModel.reintentar()
        }
    }

    private fun configurarControlesZoom() {
        binding.btnAcercarAdmin.setOnClickListener {
            fragmentoMapa?.acercar()
        }
        binding.btnAlejarAdmin.setOnClickListener {
            fragmentoMapa?.alejar()
        }
    }

    private fun configurarToggleFormato() {
        if (viewModel.formatoReporteSeleccionado == "Word") {
            binding.toggleGroupFormatoReporte.check(R.id.btnFormatoWord)
        } else {
            binding.toggleGroupFormatoReporte.check(R.id.btnFormatoPdf)
        }

        binding.toggleGroupFormatoReporte.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnFormatoWord -> viewModel.formatoReporteSeleccionado = "Word"
                    else -> viewModel.formatoReporteSeleccionado = "PDF"
                }
            }
        }
    }

    private fun configurarBotonAtras() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    confirmarSalirAdmin()
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

                    binding.tvFechaAdmin.text = getString(R.string.formato_fecha_admin, formatoFecha.format(ahora))
                    binding.tvHoraAdmin.text = getString(R.string.formato_hora_admin, formatoHora.format(ahora))

                    delay(10000)
                }
            }
        }
    }

    private fun observarResultadoEdicionUsuario() {
        findNavController().currentBackStackEntry?.savedStateHandle
            ?.getLiveData<Boolean>("usuario_modificado")
            ?.observe(viewLifecycleOwner) { modificado ->
                if (modificado == true) {
                    viewModel.cargarDatos()
                    findNavController().currentBackStackEntry?.savedStateHandle?.remove<Boolean>("usuario_modificado")
                }
            }
    }

    private fun observarEstadoViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estado.collect { estado ->
                    when (estado) {
                        is EstadoInicioAdmin.Cargando -> {
                            mostrarCargando()
                        }
                        is EstadoInicioAdmin.Listo -> {
                            mostrarListo(estado.comerciantes, estado.reporte)
                        }
                        is EstadoInicioAdmin.Error -> {
                            if (estado.esSesionExpirada) {
                                Toast.makeText(requireContext(), R.string.msg_sesion_expirada, Toast.LENGTH_LONG).show()
                                viewLifecycleOwner.lifecycleScope.launch {
                                    viewModel.cerrarSesion()
                                    findNavController().navigate(R.id.accionInicioAdministradorASeleccionUsuario)
                                }
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
                viewModel.estadoReporte.collect { estadoReporte ->
                    when (estadoReporte) {
                        is EstadoReporteAdmin.Inactivo -> {
                            mostrarGenerandoReporte(false)
                        }
                        is EstadoReporteAdmin.Generando -> {
                            mostrarGenerandoReporte(true)
                        }
                        is EstadoReporteAdmin.Listo -> {
                            mostrarGenerandoReporte(false)
                            if (!estadoReporte.consumido) {
                                mostrarDialogoReporteListo(estadoReporte.archivo, estadoReporte.mimeType)
                            }
                        }
                        is EstadoReporteAdmin.Error -> {
                            mostrarGenerandoReporte(false)
                            if (estadoReporte.esSesionExpirada) {
                                Toast.makeText(requireContext(), R.string.msg_sesion_expirada, Toast.LENGTH_LONG).show()
                                viewLifecycleOwner.lifecycleScope.launch {
                                    viewModel.cerrarSesion()
                                    findNavController().navigate(R.id.accionInicioAdministradorASeleccionUsuario)
                                }
                            } else {
                                Toast.makeText(requireContext(), estadoReporte.mensaje, Toast.LENGTH_LONG).show()
                                viewModel.consumirReporte()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun mostrarGenerandoReporte(generando: Boolean) {
        binding.indicadorCargandoAdmin.visibility = if (generando) View.VISIBLE else View.GONE
        binding.btnGenerarReporteAdmin.isEnabled = !generando
        binding.btnSalirAdmin.isEnabled = !generando
        binding.btnFormatoPdf.isEnabled = !generando
        binding.btnFormatoWord.isEnabled = !generando
    }

    private fun mostrarDialogoReporteListo(archivo: File, mimeType: String) {
        viewModel.consumirReporte()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.reporte_generado_titulo)
            .setMessage(archivo.name)
            .setPositiveButton(R.string.btn_abrir) { dialog, _ ->
                dialog.dismiss()
                abrirArchivoReporte(archivo, mimeType)
            }
            .setNeutralButton(R.string.btn_compartir) { dialog, _ ->
                dialog.dismiss()
                compartirArchivoReporte(archivo, mimeType)
            }
            .setNegativeButton(R.string.btn_cerrar) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun abrirArchivoReporte(archivo: File, mimeType: String) {
        try {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                archivo
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.error_sin_app_para_abrir, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), e.localizedMessage ?: "Error al abrir reporte", Toast.LENGTH_SHORT).show()
        }
    }

    private fun compartirArchivoReporte(archivo: File, mimeType: String) {
        try {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                archivo
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, uri)
                type = mimeType
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.btn_compartir)))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), e.localizedMessage ?: "Error al compartir reporte", Toast.LENGTH_SHORT).show()
        }
    }

    private fun mostrarCargando() {
        binding.indicadorCargandoAdmin.visibility = View.VISIBLE
        binding.tarjetaErrorAdmin.visibility = View.GONE
        binding.btnVerUsuarioAdmin.isEnabled = false
    }

    private fun mostrarListo(comerciantes: List<Comerciante>, reporte: Reporte) {
        binding.indicadorCargandoAdmin.visibility = View.GONE
        binding.tarjetaErrorAdmin.visibility = View.GONE

        // Configurar mapa y marcadores
        fragmentoMapa?.alSeleccionarMarcador = { id, datos ->
            if (datos is Comerciante) {
                viewModel.idComercianteSeleccionado = id
                val estadoActual = viewModel.estado.value
                if (estadoActual is EstadoInicioAdmin.Listo) {
                    actualizarInterfaceUsuario(estadoActual.comerciantes, estadoActual.reporte)
                }
            } else {
                viewModel.idComercianteSeleccionado = null
                val estadoActual = viewModel.estado.value
                if (estadoActual is EstadoInicioAdmin.Listo) {
                    actualizarInterfaceUsuario(estadoActual.comerciantes, estadoActual.reporte)
                }
            }
        }

        actualizarInterfaceUsuario(comerciantes, reporte)
    }

    private fun actualizarInterfaceUsuario(comerciantes: List<Comerciante>, reporte: Reporte) {
        val marcadores = comerciantes.filter { it.latitud != null && it.longitud != null }.map { c ->
            val esSeleccionado = c.idComerciante == viewModel.idComercianteSeleccionado
            val iconoRes = if (esSeleccionado) {
                R.drawable.ic_usuario_resaltado
            } else if (c.estado.equals("activo", ignoreCase = true)) {
                R.drawable.ic_usuario_activo
            } else {
                R.drawable.ic_usuario_inactivo
            }

            MarcadorMapa(
                id = c.idComerciante,
                latitud = c.latitud!!,
                longitud = c.longitud!!,
                titulo = c.nombrePuesto ?: c.nombreComercial,
                subtitulo = "${c.nombre} ${c.apellido}",
                icono = iconoRes,
                datosOriginales = c
            )
        }
        fragmentoMapa?.mostrarMarcadores(marcadores)

        val cSel = comerciantes.find { it.idComerciante == viewModel.idComercianteSeleccionado }
        if (cSel != null) {
            binding.tvUsuarioAdmin.text = getString(R.string.formato_panel_usuario, cSel.nombre, cSel.apellido, cSel.idComerciante)
            binding.tvPuestoAdmin.text = getString(R.string.formato_panel_puesto, cSel.nombrePuesto ?: getString(R.string.sin_puesto))
            binding.btnVerUsuarioAdmin.isEnabled = true
        } else {
            binding.tvUsuarioAdmin.text = getString(R.string.panel_usuario_no_seleccionado)
            binding.tvPuestoAdmin.text = ""
            binding.btnVerUsuarioAdmin.isEnabled = false
        }

        val catMasBuscada = reporte.categoriaMasBuscada
        if (catMasBuscada != null) {
            binding.tvProductoMasBuscadoAdmin.text = getString(
                R.string.formato_producto_mas_buscado,
                catMasBuscada.categoria,
                catMasBuscada.cantidad
            )
        } else {
            binding.tvProductoMasBuscadoAdmin.text = getString(R.string.producto_mas_buscado_sin_datos)
        }
    }

    private fun mostrarError(mensaje: String) {
        binding.indicadorCargandoAdmin.visibility = View.GONE
        binding.tarjetaErrorAdmin.visibility = View.VISIBLE
        binding.tvMensajeErrorAdmin.text = mensaje
    }

    private fun confirmarSalirAdmin() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.titulo_dialogo_salir_admin)
            .setMessage(R.string.mensaje_dialogo_salir_admin)
            .setPositiveButton(R.string.btn_salir) { dialog, _ ->
                dialog.dismiss()
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.cerrarSesion()
                    findNavController().navigate(R.id.accionInicioAdministradorASeleccionUsuario)
                }
            }
            .setNegativeButton(R.string.btn_cancelar) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
