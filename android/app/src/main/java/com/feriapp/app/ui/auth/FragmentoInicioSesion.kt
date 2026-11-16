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
import com.feriapp.app.databinding.FragmentoInicioSesionBinding
import com.feriapp.app.ui.common.fabricaViewModel
import kotlinx.coroutines.launch

class FragmentoInicioSesion : Fragment() {

    private var _binding: FragmentoInicioSesionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InicioSesionViewModel by viewModels {
        fabricaViewModel {
            val contenedor = (requireActivity().application as AplicacionFeria).contenedorApp
            InicioSesionViewModel(contenedor.repositorioAutenticacion)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentoInicioSesionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        configurarOyentes()
        observarEstado()
    }

    private fun configurarOyentes() {
        binding.btnIngresar.setOnClickListener {
            val usuario = binding.etUsuario.text?.toString()?.trim().orEmpty()
            val contrasena = binding.etContrasena.text?.toString()?.trim().orEmpty()

            if (usuario.isEmpty() || contrasena.isEmpty()) {
                Toast.makeText(requireContext(), R.string.error_campos_vacios, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.iniciarSesion(usuario, contrasena)
        }

        binding.btnIrARegistro.setOnClickListener {
            findNavController().navigate(R.id.accionInicioSesionARegistro)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun observarEstado() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.estado.collect { estado ->
                when (estado) {
                    is EstadoInicioSesion.Inicial -> {
                        mostrarCargando(false)
                    }
                    is EstadoInicioSesion.Cargando -> {
                        mostrarCargando(true)
                    }
                    is EstadoInicioSesion.Exito -> {
                        mostrarCargando(false)
                        viewModel.restablecerEstado()
                        navegarSegunRol(estado.rol)
                    }
                    is EstadoInicioSesion.Error -> {
                        mostrarCargando(false)
                        Toast.makeText(requireContext(), estado.mensaje, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun mostrarCargando(cargando: Boolean) {
        binding.btnIngresar.isEnabled = !cargando
        binding.btnIrARegistro.isEnabled = !cargando
        binding.indicadorCargando.visibility = if (cargando) View.VISIBLE else View.GONE
    }

    private fun navegarSegunRol(rol: String) {
        when (rol.lowercase()) {
            "comerciante" -> {
                findNavController().navigate(R.id.accionInicioSesionAInicioComerciante)
            }
            "administrador", "admin" -> {
                findNavController().navigate(R.id.accionInicioSesionAInicioAdministrador)
            }
            else -> {
                findNavController().navigate(R.id.accionInicioSesionAInicioComerciante)
            }
        }
    }
}
