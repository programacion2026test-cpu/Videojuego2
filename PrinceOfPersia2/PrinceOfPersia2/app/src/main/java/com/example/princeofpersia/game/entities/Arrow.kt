package com.example.princeofpersia.game.entities

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue

// =====================================================================================
//  FLECHAS DEL ARQUERO (proyectiles)
// =====================================================================================

/** Velocidad de la flecha en px/s para un personaje de 180 px (se escala con el tamaño real). */
const val ARCHER_ARROW_SPEED = 650f

/** Largo de la flecha dibujada, como fracción del tamaño del arquero. */
const val ARCHER_ARROW_WIDTH_FRACTION = 0.55f

/** Proporción alto/ancho de las imágenes flecha_derecha.png y flecha_izquierda.png (88×32 px). */
const val ARCHER_ARROW_ASPECT = 32f / 88f

/** Altura de la flecha ALTA sobre el suelo, en alturas del arquero. Agachado se esquiva (pasa por encima). */
const val ARCHER_HIGH_SHOT_HEIGHT = 0.62f

/** Altura de la flecha BAJA sobre el suelo, en alturas del arquero. Se esquiva saltando. */
const val ARCHER_LOW_SHOT_HEIGHT = 0.12f

/**
 * Una flecha en vuelo. Vuela en línea recta hacia donde miraba el arquero, a una altura fija.
 * @param startX borde izquierdo inicial (px). @param centerY altura del centro sobre la pantalla (px).
 * @param woundRelY dónde queda la herida en el cuerpo si pega (pecho ≈ 0.45, pies ≈ 0.85).
 */
class Arrow(
    val roomIndex: Int,
    startX: Float,
    val centerY: Float,
    val facingRight: Boolean,
    val width: Float,
    val height: Float,
    val woundRelY: Float
) {
    /** Borde izquierdo (px). Es estado de Compose para que el dibujo se actualice al volar. */
    var x by mutableFloatStateOf(startX)
}

/** Las flechas activas de todo el nivel: se disparan, vuelan, pegan o desaparecen. */
class ArrowManager {
    val arrows = mutableStateListOf<Arrow>()

    fun shoot(arrow: Arrow) {
        arrows += arrow
    }

    fun clear() = arrows.clear()

    /** Mueve las flechas, aplica el daño al jugador y quita las que salieron de pantalla, pegaron o quedaron en otra sala. */
    fun update(dt: Float, player: Player, screenWidth: Float) {
        if (arrows.isEmpty()) return
        val iterator = arrows.listIterator()
        while (iterator.hasNext()) {
            val arrow = iterator.next()
            if (arrow.roomIndex != player.roomIndex) {
                iterator.remove(); continue
            }
            val speed = ARCHER_ARROW_SPEED * player.height / 180f
            arrow.x += (if (arrow.facingRight) 1 else -1) * speed * dt

            if (arrow.x + arrow.width < 0f || arrow.x > screenWidth) {
                iterator.remove(); continue
            }

            // Choque con el cuerpo del jugador (la caja depende de si está agachado)
            val overlapsX = arrow.x < player.hitRight() && arrow.x + arrow.width > player.hitLeft()
            val overlapsY = arrow.centerY - arrow.height / 2f < player.hitBottom() &&
                arrow.centerY + arrow.height / 2f > player.hitTop()
            if (overlapsX && overlapsY) {
                // Si el jugador es invulnerable la flecha lo atraviesa y sigue volando
                if (player.takeDamage(woundRelX = 0.5f, woundRelY = arrow.woundRelY)) {
                    iterator.remove()
                }
            }
        }
    }
}