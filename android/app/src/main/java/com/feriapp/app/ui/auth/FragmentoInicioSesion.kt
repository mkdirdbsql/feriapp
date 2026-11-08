package com.feriapp.app.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.feriapp.app.R
import com.feriapp.app.databinding.FragmentoInicioSesionBinding

class FragmentoInicioSesion : Fragment() {

    private var _binding: FragmentoInicioSesionBinding? = null
    private val binding get() = _binding!!

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

        binding.btnIniciarSesionComerciante.setOnClickListener {
            findNavController().navigate(R.id.accionInicioSesionAInicioComerciante)
        }

        binding.btnIniciarSesionAdmin.setOnClickListener {
            findNavController().navigate(R.id.accionInicioSesionAInicioAdministrador)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
