package com.example.princeofpersia.game.render

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap

class Animator(
    initialFrames: List<ImageBitmap>,
    private val frameDurationSeconds: Float = 0.08f
) {
    private var frames: List<ImageBitmap> = initialFrames
    private var elapsed = 0f

    var frameIndex by mutableIntStateOf(0)
        private set

    fun setFrames(newFrames: List<ImageBitmap>) {
        if (newFrames !== frames) {
            frames = newFrames
            elapsed = 0f
            frameIndex = 0
        }
    }

    /**
     * @param loop true = la animación se repite en ciclo; false = avanza hasta el ÚLTIMO frame y
     *             se queda ahí (útil para agacharse: se sienta y se mantiene sentado).
     */
    fun update(dt: Float, loop: Boolean = true) {
        if (frames.size <= 1) return
        elapsed += dt
        val raw = (elapsed / frameDurationSeconds).toInt()
        frameIndex = if (loop) raw % frames.size else raw.coerceAtMost(frames.size - 1)
    }

    /** Muestra un frame concreto (0-based). Útil cuando otra lógica (ej. el ataque) decide qué frame toca. */
    fun showFrame(index: Int) {
        if (frames.isEmpty()) return
        frameIndex = index.coerceIn(0, frames.size - 1)
    }

    fun reset() {
        elapsed = 0f
        frameIndex = 0
    }

    val currentFrame: ImageBitmap
        get() = frames.getOrElse(frameIndex) { frames.first() }
}