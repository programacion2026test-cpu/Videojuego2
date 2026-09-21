package com.example.princeofpersia.game.entities

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.princeofpersia.game.level.Platform
import com.example.princeofpersia.game.render.WoundEffects

/**
 * Representa al jugador dentro del juego.
 * width/height a 280x280 (tamaño visual ya ajustado en el proyecto real).
 * Como el sprite es más grande que la celda nativa de 64x64, se escala
 * al dibujarlo — no hace falta ningún offset aparte, el punto (x, y)
 * sigue siendo la esquina superior izquierda tanto para colisión como dibujo.
 */
class Player(
    initialX: Float = 100f,
    initialY: Float = 100f
) {
    companion object {
        /** Corazones máximos. */
        const val MAX_HEALTH = 3
        /** Segundos de invulnerabilidad (parpadeo) después de recibir un golpe. */
        const val INVULNERABLE_DURATION = 1.2f
        /** Duración de la animación de herido (6 frames de 0.08 s). */
        const val HURT_DURATION = 6 * 0.08f
    }

    var x by mutableFloatStateOf(initialX)
    var y by mutableFloatStateOf(initialY)
    var velocityY by mutableFloatStateOf(0f)
    var isOnGround by mutableStateOf(false)

    /** -1 = moviéndose a la izquierda, 0 = quieto, 1 = moviéndose a la derecha. */
    var moveDirection by mutableIntStateOf(0)

    /** Índice del tramo (sala) donde está el jugador. Lo cambia SOLO GameLoop. */
    var roomIndex by mutableIntStateOf(0)

    /**
     * Estado de agachado: al tocar ⬇ (o dejarlo apretado) queda en true y se mantiene aunque se suelte el
     * dedo, hasta que se toca otro botón (caminar o saltar) o el jugador queda en el aire. Solo tiene efecto
     * EN EL SUELO: agachado no se camina y se esquiva la flecha. Lo enciende y apaga la UI; GameLoop además
     * lo apaga cuando el jugador está en el aire.
     */
    var isCrouching by mutableStateOf(false)

    /** La UI lo pone en true al tocar ⚔. GameLoop lo consume: si está en el suelo y libre, empieza el ataque. */
    @Volatile
    var attackRequested: Boolean = false

    /** true si el golpe del ataque en curso ya se aplicó a los enemigos (el golpe cae UNA vez por ataque). */
    var attackHitApplied: Boolean = false

    /** Segundos que le quedan al ataque en curso (0 = no está atacando). Lo maneja SOLO GameLoop. */
    var attackTimer: Float = 0f

    /** true mientras corre: mantener una dirección un momento lo hace correr (más rápido y con su animación). Lo maneja GameLoop. */
    var isRunning: Boolean = false

    /** Segundos que lleva manteniendo la misma dirección en el suelo (cuando llega a RUN_HOLD_SECONDS empieza a correr). */
    var runTimer: Float = 0f

    /** Dirección con la que se está acumulando la carrera (-1, 0, 1); si cambia, se reinicia. */
    var runDirection: Int = 0

    /** true mientras está colgado de un saliente (sin gravedad ni movimiento). Lo maneja GameLoop. */
    var isHanging: Boolean = false

    /** El saliente del que está colgado (null si no está colgado). */
    var hangLedge: Platform? = null

    /** Segundos durante los cuales NO puede volver a agarrarse (evita re-agarrarse apenas se suelta). Lo maneja GameLoop. */
    var grabCooldown: Float = 0f

    /** Segundos que le quedan a la animación de subir al saliente (0 = colgado, sin subir todavía). */
    var pullUpTimer: Float = 0f

    /** true durante un salto en diagonal alto (salto hecho corriendo): sube más y avanza más rápido. Lo maneja GameLoop. */
    var isDiagonalJump: Boolean = false

    /** true si el personaje mira a la derecha (decide qué animación usar). */
    var facingRight by mutableStateOf(true)

    /**
     * El botón de saltar solo "pide" el salto (se activa al PRESIONAR).
     * La física (GameLoop) lo consume en el siguiente frame si está en el suelo.
     */
    @Volatile
    var jumpRequested: Boolean = false

    /** true mientras el dedo está sobre el botón de salto (mantenerlo apretado = saltar en cuanto pise suelo). */
    @Volatile
    var jumpHeld: Boolean = false

    /** Tiempo (s) que queda de "gracia" para saltar justo después de dejar de pisar el suelo. Lo maneja GameLoop. */
    var coyoteTimer: Float = 0f

    /**
     * Tiempo restante (segundos) durante el cual un salto pedido sigue "guardado" esperando pisar el suelo.
     * Lo maneja SOLO GameLoop. Evita perder saltos apretados justo antes de aterrizar o en un frame sin suelo.
     */
    var jumpBufferTimer: Float = 0f

    /**
     * Tamaño del personaje en píxeles (siempre cuadrado: el sprite es de 64x64).
     * GameLoop deriva de acá la gravedad, la velocidad y el salto, y las plataformas
     * miden su altura en "alturas de jugador", así que cambiar SOLO este valor
     * reajusta todo de forma coherente.
     */
    /** Corazones actuales. */
    var health by mutableIntStateOf(MAX_HEALTH)

    /** Segundos de invulnerabilidad que quedan. Es estado de Compose para que el parpadeo se redibuje. */
    var invulnerableTimer by mutableFloatStateOf(0f)

    /** Segundos que quedan de la animación de herido; mientras dura no se puede mover, saltar ni atacar. */
    var hurtTimer: Float = 0f

    /** Manchas de sangre del jugador (siguen al cuerpo). Las enemigos tendrán las suyas igual. */
    val wounds = WoundEffects()

    /**
     * Resta un corazón, salvo que esté invulnerable. Cancela ataque y agachado, arranca la animación de
     * herido y crea la mancha roja en el cuerpo.
     * @param woundRelX / woundRelY dónde queda la herida dentro del dibujo (0..1): pies ≈ (0.5, 0.94), pecho ≈ (0.5, 0.45).
     * @return true si el golpe entró; false si se ignoró por la invulnerabilidad.
     */
    fun takeDamage(woundRelX: Float = 0.5f, woundRelY: Float = 0.5f): Boolean {
        if (invulnerableTimer > 0f) return false
        health = (health - 1).coerceAtLeast(0)
        invulnerableTimer = INVULNERABLE_DURATION
        hurtTimer = HURT_DURATION
        attackTimer = 0f
        isCrouching = false
        wounds.add(woundRelX, woundRelY)
        return true
    }

    // Caja de impacto para proyectiles (flechas). Es más chica que el dibujo, porque el sprite tiene margen,
    // y agachado en el suelo solo cuenta la mitad de abajo: las flechas ALTAS pasan por encima.
    fun hitLeft(): Float = x + width * 0.25f
    fun hitRight(): Float = x + width * 0.75f
    fun hitTop(): Float = if (isCrouching && isOnGround) y + height * 0.55f else y + height * 0.12f
    fun hitBottom(): Float = y + height

    val width: Float = 280f
    val height: Float = 280f
}