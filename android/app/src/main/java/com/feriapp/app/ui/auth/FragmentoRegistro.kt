package com.feriapp.app.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.feriapp.app.AplicacionFeria
import com.feriapp.app.R
import com.feriapp.app.databinding.FragmentoRegistroBinding
import com.feriapp.app.ui.common.fabricaViewModel
import kotlinx.coroutines.launch

class FragmentoRegistro : Fragment() {

    private var _binding: FragmentoRegistroBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RegistroViewModel by viewModels {
        fabricaViewModel {
            val contenedor = (requireActivity().application as AplicacionFeria).contenedorApp
            RegistroViewModel(contenedor.repositorioAutenticacion)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentoRegistroBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        configurarOyentes()
        observarEstado()
    }

    private fun configurarOyentes() {
        binding.btnRegistrarse.setOnClickListener {
            if (validarCampos()) {
                val nombre = binding.etNombre.text?.toString()?.trim().orEmpty()
                val apellido = binding.etApellido.text?.toString()?.trim().orEmpty()
                val telefono = binding.etTelefono.text?.toString()?.trim().orEmpty()
                val contrasena = binding.etContrasena.text?.toString()?.trim().orEmpty()

                viewModel.registrarComerciante(
                    nombre = nombre,
                    apellido = apellido,
                    telefono = telefono,
                    contrasena = contrasena,
                )
            }
        }
    }

    private fun validarCampos(): Boolean {
        limpiarErrores()
        var esValido = true

        val nombre = binding.etNombre.text?.toString()?.trim().orEmpty()
        val apellido = binding.etApellido.text?.toString()?.trim().orEmpty()
        val telefono = binding.etTelefono.text?.toString()?.trim().orEmpty()
        val contrasena = binding.etContrasena.text?.toString()?.trim().orEmpty()
        val repetirContrasena = binding.etRepetirContrasena.text?.toString()?.trim().orEmpty()

        if (nombre.isEmpty()) {
            binding.layoutNombre.error = getString(R.string.error_campo_requerido)
            esValido = false
        }

        if (apellido.isEmpty()) {
            binding.layoutApellido.error = getString(R.string.error_campo_requerido)
            esValido = false
        }

        if (telefono.isEmpty() || !telefono.matches(Regex("^\\d{7,15}$"))) {
            binding.layoutTelefono.error = getString(R.string.error_telefono_invalido)
            esValido = false
        }

        if (contrasena.length < 6) {
            binding.layoutContrasena.error = getString(R.string.error_contrasena_corta)
            esValido = false
        }

        if (repetirContrasena != contrasena) {
            binding.layoutRepetirContrasena.error = getString(R.string.error_contrasenas_no_coinciden)
            esValido = false
        }

        return esValido
    }

    private fun limpiarErrores() {
        binding.layoutNombre.error = null
        binding.layoutApellido.error = null
        binding.layoutTelefono.error = null
        binding.layoutContrasena.error = null
        binding.layoutRepetirContrasena.error = null
    }

    private fun observarEstado() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.estado.collect { estado ->
                when (estado) {
                    is EstadoRegistro.Inicial -> {
                        mostrarCargando(false)
                    }
                    is EstadoRegistro.Cargando -> {
                        mostrarCargando(true)
                    }
                    is EstadoRegistro.Exito -> {
                        mostrarCargando(false)
                        viewModel.restablecerEstado()
                        Toast.makeText(requireContext(), R.string.registro_exitoso_mensaje, Toast.LENGTH_LONG).show()
                        findNavController().navigate(R.id.accionRegistroAInicioSesion)
                    }
                    is EstadoRegistro.Error -> {
                        mostrarCargando(false)
                        Toast.makeText(requireContext(), estado.mensaje, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun mostrarCargando(cargando: Boolean) {
        binding.btnRegistrarse.isEnabled = !cargando
        binding.indicadorCargando.visibility = if (cargando) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
