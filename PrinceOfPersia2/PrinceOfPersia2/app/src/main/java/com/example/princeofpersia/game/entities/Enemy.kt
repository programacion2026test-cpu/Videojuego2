package com.example.princeofpersia.game.entities

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.example.princeofpersia.game.render.Animator
import com.example.princeofpersia.game.render.WoundEffects
import kotlin.math.abs

// =====================================================================================
//  AJUSTES DEL ENEMIGO (todos se aplican a TODOS los enemigos)
// =====================================================================================

/** Corazones (golpes) que aguanta cada enemigo. */
const val ENEMY_MAX_HEALTH = 2

/** Velocidad al caminar/perseguir, pensada para un personaje de 180 px (se escala con su tamaño real). */
const val ENEMY_SPEED = 130f

/** Distancia a la que detecta al jugador y lo persigue, como múltiplo del ancho del enemigo. */
const val ENEMY_DETECTION_RANGE_FACTOR = 1.5f

/** Distancia a la que se detiene y ataca, como múltiplo del ancho del enemigo. */
const val ENEMY_ATTACK_RANGE_FACTOR = 0.7f

/** Duración del espadazo del enemigo (s). Menos = ataca más rápido. */
const val ENEMY_ATTACK_DURATION = 6 * 0.08f

/** En qué momento del espadazo (0 a 1) el golpe llega al jugador. 0.5 = a la mitad de la animación. */
const val ENEMY_HIT_PROGRESS = 0.5f

/** Cuánto queda aturdido al recibir un golpe (s). Hace un gesto de dolor con los primeros frames de la fila "herido". */
const val ENEMY_HURT_DURATION = 6 * 0.08f

// --- Muerte: 1) cae (los 6 frames de la fila "herido", que terminan con el cuerpo en el suelo),
//             2) queda tendido un momento, 3) se DESVANECE de a poco. Total = suma de las tres.
/** Duración de la caída al morir (s). */
const val ENEMY_DEATH_ANIM_DURATION = 0.6f

/** Cuánto tiempo queda tendido, sin desvanecerse todavía (s). */
const val ENEMY_DEATH_HOLD = 0.4f

/** Cuánto tarda en desvanecerse por completo (s). Más alto = se desvanece más despacio. */
const val ENEMY_DEATH_FADE = 1.5f

// --- Arquero (EnemyType.ARCHER): se queda quieto apuntando y dispara flechas altas y bajas, una tras otra ---
/** Distancia máxima a la que dispara, como múltiplo de su ancho (10 = alcanza toda la sala). */
const val ARCHER_RANGE_FACTOR = 10f

/** Duración de la animación de disparo (13 frames de 0.07 s). */
const val ARCHER_SHOOT_DURATION = 13 * 0.07f

/** En qué momento de la animación (0 a 1) suelta la flecha. 0.69 ≈ frame 9 de 13. */
const val ARCHER_RELEASE_PROGRESS = 0.69f

/** Espera entre un disparo y el siguiente (s). */
const val ARCHER_COOLDOWN = 0.7f

enum class EnemyState { PATROL, ATTACK, HURT, DEAD }

/** MELEE: persigue y pelea cuerpo a cuerpo. ARCHER: dispara flechas desde lejos. */
enum class EnemyType { MELEE, ARCHER }

/** Animaciones del enemigo (sprite sheet LPC, mismo formato que el guerrero). */
class EnemyFrames(
    val walkLeft: List<ImageBitmap>,
    val walkRight: List<ImageBitmap>,
    val slashLeft: List<ImageBitmap>,
    val slashRight: List<ImageBitmap>,
    val hurt: List<ImageBitmap>,
    /** Solo para arqueros: animación de disparo (filas 17 izquierda / 19 derecha, 13 frames). */
    val shootLeft: List<ImageBitmap> = emptyList(),
    val shootRight: List<ImageBitmap> = emptyList()
)

/**
 * Enemigo con IA básica. Tipo MELEE: patrulla su tramo, persigue si detecta al jugador en su misma sala y ataca
 * con el arma si está cerca. Tipo ARCHER: patrulla y, si el jugador está a tiro, se queda quieto y dispara
 * flechas altas y bajas. Vive dentro de UNA sala (todavía no cruza de sala ni esquiva trampas).
 *
 * @param patrolMinFraction / patrolMaxFraction límites de su patrulla (borde izquierdo del sprite,
 *        como fracción del ancho de pantalla). Elegilos sobre piso seguro: el enemigo no evita trampas.
 */
class Enemy(
    val roomIndex: Int,
    val patrolMinFraction: Float,
    val patrolMaxFraction: Float,
    val frames: EnemyFrames,
    val size: Float,
    val maxHealth: Int = ENEMY_MAX_HEALTH,
    val type: EnemyType = EnemyType.MELEE
) {
    var x by mutableFloatStateOf(0f)
    var facingRight by mutableStateOf(true)
    var health by mutableIntStateOf(maxHealth)
    var state by mutableStateOf(EnemyState.PATROL)
    /** Segundos desde que murió (0 mientras vive). Maneja la caída, la pausa y el desvanecimiento. */
    var deathAge by mutableFloatStateOf(0f)

    private var stateTimer = 0f
    private var hasDealtDamageThisAttack = false
    private var patrolDirection = 1
    private var placed = false
    private var cooldown = 0f
    private var shotCount = 0

    /** true mientras el arquero está quieto apuntando (en rango, esperando el próximo disparo). */
    private var aiming = false

    /** Duración de su ataque: el disparo del arquero o el espadazo del cuerpo a cuerpo. */
    private val attackDuration: Float
        get() = if (type == EnemyType.ARCHER) ARCHER_SHOOT_DURATION else ENEMY_ATTACK_DURATION

    /** En qué momento del ataque (0 a 1) cae el golpe o sale la flecha. */
    private val releaseProgress: Float
        get() = if (type == EnemyType.ARCHER) ARCHER_RELEASE_PROGRESS else ENEMY_HIT_PROGRESS

    val width: Float = size
    val height: Float = size
    val animator = Animator(frames.walkLeft)

    /** Manchas de sangre del enemigo (mismo sistema que el jugador). */
    val wounds = WoundEffects()

    val isAlive: Boolean get() = state != EnemyState.DEAD

    /** Se dibuja mientras vive o mientras cae y se desvanece al morir. */
    val isVisible: Boolean
        get() = state != EnemyState.DEAD || deathAge < ENEMY_DEATH_ANIM_DURATION + ENEMY_DEATH_HOLD + ENEMY_DEATH_FADE

    /** Transparencia al dibujar: 1 vivo o recién caído; después baja poco a poco hasta 0. */
    val drawAlpha: Float
        get() = if (state == EnemyState.DEAD) {
            val fadeElapsed = deathAge - ENEMY_DEATH_ANIM_DURATION - ENEMY_DEATH_HOLD
            (1f - fadeElapsed / ENEMY_DEATH_FADE).coerceIn(0f, 1f)
        } else 1f

    /** Lo deja como nuevo (cuando el jugador se queda sin vidas y se reinicia el nivel). */
    fun reset() {
        health = maxHealth
        state = EnemyState.PATROL
        stateTimer = 0f
        deathAge = 0f
        hasDealtDamageThisAttack = false
        patrolDirection = 1
        placed = false
        cooldown = 0f
        shotCount = 0
        wounds.clear()
    }

    /** Resta un golpe. Lo interrumpe (aturdido) o lo mata. Devuelve true si el golpe entró. */
    fun takeDamage(): Boolean {
        if (state == EnemyState.DEAD) return false
        health -= 1
        wounds.add(0.5f, 0.45f)             // herida en el pecho
        hasDealtDamageThisAttack = false
        if (health <= 0) {
            state = EnemyState.DEAD
            deathAge = 0f
            animator.setFrames(frames.hurt)
        } else {
            state = EnemyState.HURT
            stateTimer = ENEMY_HURT_DURATION
        }
        return true
    }

    /** Dispara una flecha hacia donde mira, alternando altura ALTA (se esquiva agachado) y BAJA (se esquiva saltando). */
    private fun shoot(arrows: ArrowManager, floorTopY: Float) {
        val high = shotCount % 2 == 0
        shotCount++
        val arrowWidth = size * ARCHER_ARROW_WIDTH_FRACTION
        val arrowHeight = arrowWidth * ARCHER_ARROW_ASPECT
        val heightFraction = if (high) ARCHER_HIGH_SHOT_HEIGHT else ARCHER_LOW_SHOT_HEIGHT
        val centerY = floorTopY - heightFraction * size
        val startX = if (facingRight) x + width * 0.75f else x + width * 0.25f - arrowWidth
        arrows.shoot(Arrow(roomIndex, startX, centerY, facingRight, arrowWidth, arrowHeight, if (high) 0.45f else 0.85f))
    }

    /** IA y movimiento. Se llama una vez por frame, después de la física del jugador. */
    fun update(player: Player, dt: Float, screenWidth: Float, floorTopY: Float, arrows: ArrowManager) {
        if (!placed && screenWidth > 0f) {
            x = (patrolMinFraction + patrolMaxFraction) / 2f * screenWidth   // arranca en medio de su patrulla
            placed = true
        }
        wounds.update(dt)

        if (state == EnemyState.DEAD) {
            deathAge += dt
            return
        }
        if (stateTimer > 0f) stateTimer = (stateTimer - dt).coerceAtLeast(0f)
        if (cooldown > 0f) cooldown = (cooldown - dt).coerceAtLeast(0f)

        val minX = patrolMinFraction * screenWidth
        val maxX = patrolMaxFraction * screenWidth
        val speed = ENEMY_SPEED * size / 180f
        val playerInSameRoom = player.roomIndex == roomIndex
        val playerCenter = player.x + player.width / 2f
        val myCenter = x + width / 2f
        val distance = abs(playerCenter - myCenter)
        aiming = false

        when (state) {
            EnemyState.HURT -> {
                if (stateTimer <= 0f) state = EnemyState.PATROL
            }
            EnemyState.ATTACK -> {
                // El golpe (o la flecha) sale una sola vez por ataque, en releaseProgress. El golpe cuerpo a cuerpo
                // solo entra si el jugador sigue al alcance y de frente.
                val progress = 1f - stateTimer / attackDuration
                if (!hasDealtDamageThisAttack && progress >= releaseProgress) {
                    hasDealtDamageThisAttack = true
                    if (type == EnemyType.ARCHER) {
                        shoot(arrows, floorTopY)
                    } else {
                        val playerIsAhead = if (facingRight) playerCenter > myCenter else playerCenter < myCenter
                        if (playerInSameRoom && playerIsAhead && distance < width * ENEMY_ATTACK_RANGE_FACTOR * 1.15f) {
                            player.takeDamage(woundRelX = 0.5f, woundRelY = 0.45f)
                        }
                    }
                }
                if (stateTimer <= 0f) {
                    state = EnemyState.PATROL
                    hasDealtDamageThisAttack = false
                    if (type == EnemyType.ARCHER) cooldown = ARCHER_COOLDOWN
                }
            }
            EnemyState.PATROL -> when {
                // Arquero: si el jugador está a tiro, se queda quieto apuntando y dispara cuando termina la espera
                type == EnemyType.ARCHER && playerInSameRoom && distance < width * ARCHER_RANGE_FACTOR -> {
                    facingRight = playerCenter > myCenter
                    aiming = true
                    if (cooldown <= 0f) {
                        state = EnemyState.ATTACK
                        stateTimer = attackDuration
                        hasDealtDamageThisAttack = false
                    }
                }
                type == EnemyType.MELEE && playerInSameRoom && distance < width * ENEMY_ATTACK_RANGE_FACTOR -> {
                    facingRight = playerCenter > myCenter
                    state = EnemyState.ATTACK
                    stateTimer = attackDuration
                    hasDealtDamageThisAttack = false
                }
                type == EnemyType.MELEE && playerInSameRoom && distance < width * ENEMY_DETECTION_RANGE_FACTOR -> {
                    facingRight = playerCenter > myCenter
                    val dir = if (facingRight) 1 else -1
                    x = (x + dir * speed * dt).coerceIn(minX, maxX)
                }
                else -> {
                    x += patrolDirection * speed * dt
                    if (x <= minX) {
                        x = minX; patrolDirection = 1; facingRight = true
                    } else if (x >= maxX) {
                        x = maxX; patrolDirection = -1; facingRight = false
                    }
                }
            }
            EnemyState.DEAD -> Unit
        }
    }

    /** Elige y avanza la animación según el estado. Se llama una vez por frame. */
    fun updateAnimation(dt: Float) {
        when (state) {
            EnemyState.PATROL -> {
                animator.setFrames(if (facingRight) frames.walkRight else frames.walkLeft)
                if (aiming) animator.reset() else animator.update(dt)   // apuntando: pose quieta, sin "caminar en el lugar"
            }
            EnemyState.ATTACK -> {
                // Arquero: animación de disparo; el resto: espadazo
                val f = if (type == EnemyType.ARCHER) {
                    if (facingRight) frames.shootRight else frames.shootLeft
                } else {
                    if (facingRight) frames.slashRight else frames.slashLeft
                }
                animator.setFrames(f)
                animator.showFrame(((1f - stateTimer / attackDuration) * f.size).toInt())
            }
            EnemyState.HURT -> {
                // Gesto de dolor: usa solo los 3 primeros frames de "herido" (se agacha un poco y vuelve).
                animator.setFrames(frames.hurt)
                val p = (1f - stateTimer / ENEMY_HURT_DURATION).coerceIn(0f, 1f)
                val t = if (p < 0.5f) p * 2f else (1f - p) * 2f          // sube y baja: 0 → 1 → 0
                animator.showFrame((t * 2.99f).toInt())                  // frames 0, 1, 2
            }
            EnemyState.DEAD -> {
                // Caída: los 6 frames en ENEMY_DEATH_ANIM_DURATION; después queda en el último (tendido)
                animator.setFrames(frames.hurt)
                animator.showFrame(((deathAge / ENEMY_DEATH_ANIM_DURATION) * frames.hurt.size).toInt())
            }
        }
    }
}