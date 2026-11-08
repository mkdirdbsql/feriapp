package com.feriapp.app.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.feriapp.app.R
import com.feriapp.app.databinding.FragmentoSeleccionUsuarioBinding

class FragmentoSeleccionUsuario : Fragment() {

    private var _binding: FragmentoSeleccionUsuarioBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentoSeleccionUsuarioBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnIrAConsumidor.setOnClickListener {
            findNavController().navigate(R.id.accionSeleccionAInicioConsumidor)
        }

        binding.btnIrARegistroComerciante.setOnClickListener {
            findNavController().navigate(R.id.accionSeleccionARegistro)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
