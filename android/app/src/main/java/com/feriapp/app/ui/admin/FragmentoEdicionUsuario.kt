package com.feriapp.app.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.feriapp.app.databinding.FragmentoEdicionUsuarioBinding

class FragmentoEdicionUsuario : Fragment() {

    private var _binding: FragmentoEdicionUsuarioBinding? = null
    private val binding get() = _binding!!

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

        val idComerciante = arguments?.getInt("idComerciante", 0) ?: 0
        binding.tvIdComerciante.text = "ID Comerciante: $idComerciante"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
