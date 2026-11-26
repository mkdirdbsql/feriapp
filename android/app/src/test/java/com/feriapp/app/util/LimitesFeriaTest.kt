package com.feriapp.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LimitesFeriaTest {

    @Test
    fun centroDeLaFeria_estaDentro() {
        assertTrue(LimitesFeria.estaDentro(-16.4920, -68.1780))
    }

    @Test
    fun puntoLejosAlSur_estaFuera() {
        assertFalse(LimitesFeria.estaDentro(-16.5200, -68.1780))
    }

    @Test
    fun extremoEste_estaDentro() {
        assertTrue(LimitesFeria.estaDentro(-16.4975, -68.1645))
    }

    @Test
    fun puntoA50MetrosFueraDelBordeNorte_estaFuera() {
        assertFalse(LimitesFeria.estaDentro(-16.486755 + 0.00045, -68.180093))
    }

    @Test
    fun puntoA8MetrosFueraDelBordeNorte_estaDentro() {
        assertTrue(LimitesFeria.estaDentro(-16.486755 + 0.00007, -68.180093))
    }
}
