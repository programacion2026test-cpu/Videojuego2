package com.example.princeofpersia.game.render

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Cuánto dura la mancha de sangre en pantalla (segundos), incluyendo el tiempo en que se desvanece. */
const val WOUND_DURATION = 1.6f

/** Tamaño de la mancha principal, como fracción del tamaño del personaje. Más alto = mancha más grande. */
const val WOUND_SIZE_FRACTION = 0.045f

private val BLOOD_DARK = Color(0xFF8E0E14)
private val BLOOD = Color(0xFFC1121C)
private val BLOOD_LIGHT = Color(0xFFE3242B)

/**
 * Una herida: mancha roja con salpicaduras. Se guarda RELATIVA al cuerpo (relX, relY de 0 a 1 dentro del
 * dibujo del personaje) para que siga al personaje mientras se mueve. Sirve igual para el jugador
 * y, más adelante, para los enemigos.
 */
class Wound(val relX: Float, val relY: Float, val seed: Int) {
    var age by mutableFloatStateOf(0f)
}

/** Las heridas activas de una entidad (jugador o enemigo). La entidad llama a add() al recibir daño. */
class WoundEffects {
    val active = mutableStateListOf<Wound>()
    private var counter = 0

    /** relX/relY: dónde está la herida en el cuerpo. Pies ≈ (0.5, 0.94), pecho ≈ (0.5, 0.45). */
    fun add(relX: Float, relY: Float) {
        active += Wound(relX, relY, counter++)
        if (active.size > 6) active.removeAt(0)
    }

    fun update(dt: Float) {
        if (active.isEmpty()) return
        for (w in active) w.age += dt
        active.removeAll { it.age >= WOUND_DURATION }
    }

    fun clear() = active.clear()
}

/**
 * Dibuja las heridas de una entidad cuyo dibujo ocupa el cuadrado (left, top, size, size).
 * Todo es dibujo por código: no necesita imágenes.
 */
fun DrawScope.drawWounds(effects: WoundEffects, left: Float, top: Float, size: Float) {
    for (w in effects.active) {
        val p = (w.age / WOUND_DURATION).coerceIn(0f, 1f)
        val center = Offset(left + w.relX * size, top + w.relY * size)
        // Se ve entera la primera mitad y después se desvanece
        val alpha = if (p < 0.55f) 1f else (1f - (p - 0.55f) / 0.45f).coerceIn(0f, 1f)
        val grow = min(w.age / 0.12f, 1f)              // la mancha "aparece" en un instante
        val r = size * WOUND_SIZE_FRACTION

        // Mancha principal (borde oscuro, cuerpo rojo, brillo)
        drawCircle(BLOOD_DARK.copy(alpha = alpha), radius = r * 1.25f * grow, center = center)
        drawCircle(BLOOD.copy(alpha = alpha), radius = r * grow, center = center)
        drawCircle(
            BLOOD_LIGHT.copy(alpha = alpha * 0.9f),
            radius = r * 0.45f * grow,
            center = center + Offset(-r * 0.2f, -r * 0.2f)
        )

        // Salpicaduras: salen hacia afuera rápido y después caen un poco
        val fly = min(w.age / 0.25f, 1f)
        val eased = 1f - (1f - fly) * (1f - fly)
        for (i in 0 until 8) {
            val angle = (((w.seed * 47 + i * 45 + i * i * 13) % 360) * PI / 180.0).toFloat()
            val dist = size * (0.06f + 0.02f * ((i * 7 + w.seed) % 5)) * eased
            val fall = size * 0.15f * w.age * w.age
            val dropR = size * (0.008f + 0.004f * ((i + w.seed) % 3))
            drawCircle(
                BLOOD.copy(alpha = alpha),
                radius = dropR,
                center = Offset(center.x + cos(angle) * dist, center.y + sin(angle) * dist * 0.7f + fall)
            )
        }
    }
}