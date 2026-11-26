package com.feriapp.app.ui.splash

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.feriapp.app.R
import com.feriapp.app.data.local.GestorSesion
import com.feriapp.app.data.local.Sesion
import com.feriapp.app.databinding.FragmentoCargaBinding
import kotlinx.coroutines.launch

class FragmentoCarga : Fragment() {

    private var _binding: FragmentoCargaBinding? = null
    private val binding get() = _binding!!

    private var animador: ValueAnimator? = null
    private var animacionCompletada = false
    private var lecturaSesionCompletada = false
    private var sesionResultado: Sesion? = null
    private var yaNavegado = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentoCargaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        iniciarAnimacionCarga()
        iniciarLecturaSesion()
    }

    private fun iniciarAnimacionCarga() {
        binding.tvPorcentajeCarga.text = getString(R.string.formato_porcentaje_carga, 0)
        animador = ValueAnimator.ofInt(0, 100).apply {
            duration = 1500L
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val valor = anim.animatedValue as Int
                _binding?.let { bd ->
                    bd.indicadorProgreso.progress = valor
                    bd.tvPorcentajeCarga.text = getString(R.string.formato_porcentaje_carga, valor)
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    animacionCompletada = true
                    intentarNavegar()
                }
            })
            start()
        }
    }

    private fun iniciarLecturaSesion() {
        val gestorSesion = GestorSesion(requireContext())
        viewLifecycleOwner.lifecycleScope.launch {
            sesionResultado = gestorSesion.obtenerSesion()
            lecturaSesionCompletada = true
            intentarNavegar()
        }
    }

    private fun intentarNavegar() {
        if (yaNavegado) return
        if (!animacionCompletada || !lecturaSesionCompletada) return
        if (_binding == null || !isAdded) return

        yaNavegado = true

        val sesion = sesionResultado
        if (sesion == null) {
            findNavController().navigate(R.id.accionCargaASeleccionUsuario)
        } else {
            when (sesion.rol.lowercase()) {
                "comerciante" -> findNavController().navigate(R.id.accionCargaAInicioComerciante)
                "administrador", "admin" -> findNavController().navigate(R.id.accionCargaAInicioAdministrador)
                else -> findNavController().navigate(R.id.accionCargaASeleccionUsuario)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        animador?.cancel()
        animador = null
        _binding = null
    }
}
