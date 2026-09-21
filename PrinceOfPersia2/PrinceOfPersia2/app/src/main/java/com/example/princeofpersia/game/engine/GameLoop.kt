package com.example.princeofpersia.game.engine

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.princeofpersia.game.entities.ArrowManager
import com.example.princeofpersia.game.entities.Enemy
import com.example.princeofpersia.game.entities.Player
import com.example.princeofpersia.game.level.Room
import com.example.princeofpersia.game.level.SegmentType
import com.example.princeofpersia.game.level.segmentAt
import com.example.princeofpersia.game.render.Animator
import kotlinx.coroutines.isActive
import kotlin.math.abs

// Constantes de física para un personaje de REFERENCE_PLAYER_SIZE px. Se escalan con el tamaño real
// del jugador (player.height), así el salto y la velocidad se sienten igual a cualquier tamaño.
/** Muestra el texto de depuración bajo los corazones ("Tramo x/y | Frames: N"). Ponelo en false para la versión final. */
private const val SHOW_DEBUG_INFO = true

private const val REFERENCE_PLAYER_SIZE = 180f
private const val GRAVITY = 900f
private const val MOVE_SPEED = 220f
private const val JUMP_VELOCITY = -600f
/** Segundos que hay que mantener una dirección en el suelo para empezar a correr. Menos = corre antes. */
private const val RUN_HOLD_SECONDS = 0.35f
/** Velocidad al correr como múltiplo de la velocidad al caminar. */
private const val RUN_SPEED_FACTOR = 1.7f
/** Qué tan rápido se reproduce la animación de correr respecto de la de caminar. */
private const val RUN_ANIMATION_SPEED = 1.4f
/** Salto en diagonal alto (saltar CORRIENDO): multiplica la velocidad vertical inicial del salto. 1.2 ≈ llega 1.6 veces más alto. */
private const val DIAGONAL_JUMP_HEIGHT_FACTOR = 1.2f
/** Salto en diagonal alto: multiplica la velocidad horizontal mientras dura el salto. */
private const val DIAGONAL_JUMP_SPEED_FACTOR = 1.25f
/** Animación de salto: por encima de esta velocidad hacia arriba (px/s para 180 px) se muestra el frame “subiendo”. */
private const val JUMP_ANIM_RISING_SPEED = 300f
/** Animación de salto: por encima de esta velocidad hacia abajo (px/s para 180 px) se muestra el frame “cayendo”. */
private const val JUMP_ANIM_FALLING_SPEED = 150f
/**
 * Colgarse: a qué altura están las manos, como fracción del alto del personaje medida desde su posición (player.y).
 * En el frame de colgarse las manos están al 37% del alto del sprite; con el desplazamiento de dibujo de los pies (6%)
 * quedan al 43%, y se restan 3% para que las manos queden DENTRO de la barra del saliente (no justo en su borde).
 * Más alto = el personaje cuelga más arriba (las manos suben); más bajo = cuelga más abajo.
 */
private const val HANG_HANDS_OFFSET = 0.40f
/** Colgarse: qué tan cerca de la cara superior del saliente deben estar las manos para agarrarse (fracción del alto del personaje). */
private const val GRAB_TOLERANCE = 0.12f
/** Colgarse: solo se agarra si sube o está casi en el punto más alto (velocidad de caída máxima, para un personaje de 180 px). */
private const val GRAB_MAX_VELOCITY = 120f
/** Colgarse: tiempo (s) tras soltarse durante el cual no puede volver a agarrarse. */
private const val GRAB_COOLDOWN_SECONDS = 0.4f
/** Subir al saliente: en qué momento (0 a 1) pasa de la pose colgada a la pose agachada sobre el borde. */
private const val PULL_UP_SIT_PROGRESS = 0.6f
/** Duración de la animación de subir al saliente (s). */
private const val PULL_UP_DURATION = 0.6f
/** En qué momento del ataque (0 a 1) cae el golpe sobre los enemigos. 0.45 ≈ cuando el arma ya extendida. */
private const val ATTACK_HIT_PROGRESS = 0.45f
/** Alcance del golpe del jugador, como múltiplo de su ancho (distancia entre centros, solo hacia donde mira). */
private const val PLAYER_ATTACK_REACH_FACTOR = 0.8f
/** Duración total del ataque en segundos (todos los frames de la animación de golpe). Menos = más rápido. */
private const val ATTACK_DURATION = 0.5f
/** Margen (s) para todavía poder saltar justo después de dejar de pisar el suelo (borde de plataforma, etc.). */
private const val COYOTE_SECONDS = 0.10f
/** Un salto pedido se conserva este tiempo (s) hasta que haya suelo bajo los pies. */
private const val JUMP_BUFFER_SECONDS = 0.12f
private const val MAX_DT = 0.05f // evita "túneles" si un frame tarda demasiado

/** Suelta el saliente (el personaje vuelve a estar sujeto a la gravedad). */
private fun releaseLedge(player: Player) {
    player.isHanging = false
    player.hangLedge = null
    player.pullUpTimer = 0f
    player.grabCooldown = GRAB_COOLDOWN_SECONDS
}

/** Reaparece en el punto de inicio de la sala actual (después de caer fuera de pantalla). */
private fun respawn(player: Player) {
    releaseLedge(player)
    player.x = 100f
    player.y = 100f
    player.velocityY = 0f
    player.isOnGround = false
}

/** Sin corazones: reinicia desde la Sala 1 con la vida llena. */
private fun resetAfterDeath(player: Player, enemies: List<Enemy>, arrows: ArrowManager) {
    enemies.forEach { it.reset() }
    arrows.clear()
    player.health = Player.MAX_HEALTH
    player.invulnerableTimer = 0f
    player.hurtTimer = 0f
    player.attackTimer = 0f
    player.isCrouching = false
    player.isRunning = false
    player.runTimer = 0f
    player.runDirection = 0
    player.isDiagonalJump = false
    player.wounds.clear()
    player.roomIndex = 0
    respawn(player)
}

/**
 * TODA la física y colisión del jugador vive acá, en un único lugar que corre
 * una vez por frame y en un orden garantizado: gravedad -> movimiento -> colisión.
 *
 * Esta función es la ÚNICA que debe escribir en player.x, player.y,
 * player.velocityY y player.isOnGround. El Canvas solo lee para dibujar.
 */
private fun updatePhysics(
    player: Player,
    dt: Float,
    rooms: List<Room>,
    screenWidth: Float,
    screenHeight: Float,
    floorHeight: Float,
    spikesAnimator: Animator?,
    spikesDangerousFrames: Set<Int>,
    arrowAnimator: Animator?,
    arrowDangerousFrames: Set<Int>,
    enemies: List<Enemy>,
    arrows: ArrowManager
) {
    val floorTopY = screenHeight - floorHeight
    val prevBottom = player.y + player.height

    val scale = player.height / REFERENCE_PLAYER_SIZE
    val gravity = GRAVITY * scale
    val moveSpeed = MOVE_SPEED * scale
    val jumpVelocity = JUMP_VELOCITY * scale

    // Temporizadores de daño: herida (mancha), animación de herido e invulnerabilidad
    player.wounds.update(dt)
    if (player.hurtTimer > 0f) player.hurtTimer = (player.hurtTimer - dt).coerceAtLeast(0f)
    if (player.invulnerableTimer > 0f) player.invulnerableTimer = (player.invulnerableTimer - dt).coerceAtLeast(0f)
    val hurting = player.hurtTimer > 0f
    if (player.grabCooldown > 0f) player.grabCooldown = (player.grabCooldown - dt).coerceAtLeast(0f)

    // Colgado de un saliente: sin gravedad ni movimiento. Un toque en ⤴ sube, ⬇ se suelta, y un golpe también lo suelta.
    val ledge = player.hangLedge
    if (player.isHanging && ledge != null) {
        val ledgeTopY = floorTopY - ledge.effectiveHeight * player.height
        val hangY = ledgeTopY - HANG_HANDS_OFFSET * player.height
        val standY = ledgeTopY - player.height
        if (hurting) {
            releaseLedge(player)
        } else {
            player.velocityY = 0f
            player.isOnGround = false
            player.attackRequested = false
            player.jumpBufferTimer = 0f
            if (player.pullUpTimer > 0f) {
                // Subiendo: el cuerpo se desliza hacia arriba hasta quedar sobre el saliente
                player.pullUpTimer -= dt
                if (player.pullUpTimer <= 0f) {
                    player.y = standY
                    player.isOnGround = true
                    releaseLedge(player)
                } else {
                    val progress = 1f - player.pullUpTimer / PULL_UP_DURATION
                    player.y = hangY + (standY - hangY) * progress
                }
                return
            } else if (player.jumpRequested) {
                player.jumpRequested = false
                player.pullUpTimer = PULL_UP_DURATION
            } else if (player.isCrouching) {
                releaseLedge(player)          // se suelta y cae
                player.isCrouching = false
            }
            if (player.isHanging) {
                player.y = hangY
                return
            }
        }
    }

    // 0) Salto pedido por el botón (se resuelve acá para que la física sea la única que escribe)
    // Estar en el aire "levanta" al personaje: se apaga el agachado.
    if (!player.isOnGround) player.isCrouching = false

    // Margen de gracia para saltar apenas se deja de pisar el suelo
    player.coyoteTimer = if (player.isOnGround) COYOTE_SECONDS else (player.coyoteTimer - dt).coerceAtLeast(0f)

    // Ataque: un toque en ⚔ empieza el golpe si está en el suelo y no está atacando ya.
    // Mientras dura no se puede caminar ni saltar; si queda en el aire, se corta.
    if (player.attackRequested) {
        if (player.isOnGround && player.attackTimer <= 0f && !hurting) {
            player.attackTimer = ATTACK_DURATION
            player.attackHitApplied = false
            player.isCrouching = false
        }
        player.attackRequested = false
    }
    if (player.attackTimer > 0f) {
        if (player.isOnGround) player.attackTimer -= dt else player.attackTimer = 0f
    }
    val attacking = player.attackTimer > 0f

    // El golpe cae UNA vez por ataque, cuando la animación llega a ATTACK_HIT_PROGRESS:
    // daña a los enemigos vivos de esta sala que estén de frente y al alcance.
    if (attacking && !player.attackHitApplied && 1f - player.attackTimer / ATTACK_DURATION >= ATTACK_HIT_PROGRESS) {
        player.attackHitApplied = true
        val playerCenter = player.x + player.width / 2f
        val reach = player.width * PLAYER_ATTACK_REACH_FACTOR
        for (enemy in enemies) {
            if (!enemy.isAlive || enemy.roomIndex != player.roomIndex) continue
            val enemyCenter = enemy.x + enemy.width / 2f
            val facingEnemy = if (player.facingRight) enemyCenter > playerCenter else enemyCenter < playerCenter
            if (facingEnemy && abs(enemyCenter - playerCenter) < reach) enemy.takeDamage()
        }
    }

    // Al pisar el suelo termina el salto en diagonal
    if (player.isOnGround) player.isDiagonalJump = false

    // Un toque (jumpRequested) o el botón mantenido (jumpHeld) piden el salto; se guarda un instante
    // (buffer) hasta que haya suelo. Mantenido = vuelve a saltar en cuanto aterriza.
    if (player.jumpRequested || player.jumpHeld) {
        player.jumpBufferTimer = JUMP_BUFFER_SECONDS
        player.jumpRequested = false
    }
    if (player.jumpBufferTimer > 0f) {
        if (!attacking && !hurting && (player.isOnGround || player.coyoteTimer > 0f)) {
            // Saltar CORRIENDO = salto en diagonal alto: más altura y más velocidad horizontal en el aire
            val diagonal = player.isRunning && player.moveDirection != 0
            player.isDiagonalJump = diagonal
            player.velocityY = if (diagonal) jumpVelocity * DIAGONAL_JUMP_HEIGHT_FACTOR else jumpVelocity
            player.isOnGround = false
            player.coyoteTimer = 0f
            player.jumpBufferTimer = 0f
        } else {
            player.jumpBufferTimer -= dt
        }
    }

    // Agachado solo cuenta en el suelo: en el aire el botón ⬇ no hace nada.
    val crouching = player.isCrouching && player.isOnGround

    // 1) Gravedad y movimiento (agachado no se camina)
    player.velocityY += gravity * dt
    player.y += player.velocityY * dt

    if (player.moveDirection != 0 && !crouching && !attacking && !hurting) {
        // Carrera: mantener la misma dirección en el suelo RUN_HOLD_SECONDS activa el correr. Se conserva
        // en el aire (salto con carrera) y se pierde al soltar la dirección o cambiar de sentido.
        if (player.moveDirection != player.runDirection) {
            player.runDirection = player.moveDirection
            player.runTimer = 0f
            player.isRunning = false
        }
        if (player.isOnGround && !player.isRunning) {
            player.runTimer += dt
            if (player.runTimer >= RUN_HOLD_SECONDS) player.isRunning = true
        }
        var speed = if (player.isRunning) moveSpeed * RUN_SPEED_FACTOR else moveSpeed
        if (player.isDiagonalJump) speed *= DIAGONAL_JUMP_SPEED_FACTOR
        player.x += player.moveDirection * speed * dt
        player.facingRight = player.moveDirection > 0
    } else {
        player.isRunning = false
        player.runTimer = 0f
        player.runDirection = 0
    }

    // 1b) Cambio de sala/tramo al cruzar el borde de la pantalla (por el centro del jugador).
    //     Se hace ANTES de la colisión para que ésta use el piso de la sala nueva.
    val centerX = player.x + player.width / 2f
    if (centerX > screenWidth) {
        if (player.roomIndex < rooms.lastIndex) {
            player.roomIndex++
            player.x = -player.width / 2f          // centro justo en el borde izquierdo
        } else {
            player.x = screenWidth - player.width / 2f  // último tramo: tope
        }
    } else if (centerX < 0f) {
        if (player.roomIndex > 0) {
            player.roomIndex--
            player.x = screenWidth - player.width / 2f  // centro justo en el borde derecho
        } else {
            player.x = -player.width / 2f          // primer tramo: tope
        }
    }
    player.roomIndex = player.roomIndex.coerceIn(0, rooms.lastIndex)
    val room = rooms[player.roomIndex]
    val level = room.layout

    // 2) Colisión con plataformas (SOLO desde arriba): aterriza únicamente si el jugador cae
    //    (velocityY >= 0), sus pies están dentro del ancho de la plataforma y en el frame anterior
    //    estaba por encima de su cara superior. Desde abajo o de costado la atraviesa.
    val footX = player.x + player.width / 2f
    val bottom = player.y + player.height
    var landedOnPlatform = false
    if (player.velocityY >= 0f) {
        for (platform in room.platforms) {
            val left = platform.effectiveLeft * screenWidth
            val right = left + platform.effectiveWidth * screenWidth
            val topY = floorTopY - platform.effectiveHeight * player.height
            if (footX in left..right && prevBottom <= topY + 1f && bottom >= topY) {
                player.y = topY - player.height
                player.velocityY = 0f
                player.isOnGround = true
                landedOnPlatform = true
                break
            }
        }
    }

    // Colgarse: en el aire, subiendo (o casi en el punto más alto) y con las manos a la altura de la cara superior
    // de un saliente (Platform LEDGE), el personaje se agarra solo.
    var grabbedLedge = false
    if (!landedOnPlatform && !player.isOnGround && !hurting && !attacking &&
        player.grabCooldown <= 0f && player.velocityY <= GRAB_MAX_VELOCITY * scale
    ) {
        val handsY = player.y + player.height * HANG_HANDS_OFFSET
        for (platform in room.platforms) {
            if (!platform.isGrabbable) continue
            val left = platform.effectiveLeft * screenWidth
            val right = left + platform.effectiveWidth * screenWidth
            val topY = floorTopY - platform.effectiveHeight * player.height
            if (abs(handsY - topY) <= player.height * GRAB_TOLERANCE && footX in left..right) {
                player.isHanging = true
                player.hangLedge = platform
                player.pullUpTimer = 0f
                player.velocityY = 0f
                player.isRunning = false
                player.isDiagonalJump = false
                player.jumpBufferTimer = 0f
                player.y = topY - HANG_HANDS_OFFSET * player.height
                grabbedLedge = true
                break
            }
        }
    }

    // 3) Colisión con el piso/nivel (si ya aterrizó en una plataforma o se agarró de un saliente se omite)
    val segmentUnderPlayer = segmentAt(level, footX, screenWidth)

    if (landedOnPlatform || grabbedLedge) {
        // nada más que resolver este frame
    } else if (player.y + player.height > floorTopY) {
        // Solo "aterriza" si venía desde arriba del piso. Si ya estaba por debajo
        // (cayendo dentro de un pozo), no lo teletransportamos hacia arriba.
        val cameFromAbove = prevBottom <= floorTopY + 1f

        if (cameFromAbove) {
            when (segmentUnderPlayer.type) {
                SegmentType.FLOOR -> {
                    player.y = floorTopY - player.height
                    player.velocityY = 0f
                    player.isOnGround = true
                }
                SegmentType.SPIKES -> {
                    player.y = floorTopY - player.height
                    player.velocityY = 0f
                    player.isOnGround = true
                    val frameIndex = spikesAnimator?.frameIndex
                    if (frameIndex != null && frameIndex in spikesDangerousFrames) {
                        // Pinchos: la herida queda en los pies
                        player.takeDamage(woundRelX = 0.5f, woundRelY = 0.94f)
                    }
                }
                SegmentType.PIT -> player.isOnGround = false
            }
        } else {
            player.isOnGround = false
        }
    } else {
        player.isOnGround = false
    }

    // 4) Trampa de flechas: daña si el ícono está en un frame peligroso, el jugador está bajo su
    //    ancho, su cuerpo cruza la franja de vuelo y NO está agachado.
    val arrowFrame = arrowAnimator?.frameIndex
    if (arrowFrame != null && arrowFrame in arrowDangerousFrames && !crouching) {
        val centerXNow = player.x + player.width / 2f
        var hit = false
        for (trap in room.arrowTraps) {
            for (box in trap.boxes(screenWidth, player.height, floorTopY)) {
                val overlapsX = centerXNow in box.left..(box.left + box.size)
                val overlapsY = player.y < box.top + box.size && player.y + player.height > box.top
                if (overlapsX && overlapsY) { hit = true; break }
            }
            if (hit) break
        }
        // Flecha: la herida queda en el pecho
        if (hit) player.takeDamage(woundRelX = 0.5f, woundRelY = 0.45f)
    }

    // 5) Cayó fuera de pantalla -> pierde un corazón (si no es invulnerable) y reaparece
    if (player.y > screenHeight) {
        player.takeDamage(woundRelX = 0.5f, woundRelY = 0.6f)
        respawn(player)
    }

    // 6) Sin corazones -> reinicia desde la Sala 1 con la vida llena
    if (player.health <= 0) {
        resetAfterDeath(player, enemies, arrows)
    }
}

@Composable
fun GameLoop(
    player: Player,
    animator: Animator,
    walkLeftFrames: List<ImageBitmap>,
    walkRightFrames: List<ImageBitmap>,
    jumpLeftFrames: List<ImageBitmap>,
    jumpRightFrames: List<ImageBitmap>,
    crouchLeftFrames: List<ImageBitmap>,
    crouchRightFrames: List<ImageBitmap>,
    runLeftFrames: List<ImageBitmap>,
    runRightFrames: List<ImageBitmap>,
    attackLeftFrames: List<ImageBitmap>,
    attackRightFrames: List<ImageBitmap>,
    hurtFrames: List<ImageBitmap>,
    hangLeftFrames: List<ImageBitmap>,
    hangRightFrames: List<ImageBitmap>,
    enemies: List<Enemy> = emptyList(),
    arrows: ArrowManager = ArrowManager(),
    rooms: List<Room>,
    screenWidth: Float,
    screenHeight: Float,
    floorHeight: Float,
    spikesAnimator: Animator? = null,
    spikesDangerousFrames: Set<Int> = emptySet(),
    arrowAnimator: Animator? = null,
    arrowDangerousFrames: Set<Int> = emptySet(),
    modifier: Modifier = Modifier
) {
    var frameCount by remember { mutableLongStateOf(0L) }

    // LaunchedEffect(Unit) se lanza UNA sola vez, en la primera composición, cuando
    // screenWidth/screenHeight todavía valen 0 (el Canvas aún no midió su tamaño).
    // rememberUpdatedState hace que el loop lea SIEMPRE el valor más reciente de cada
    // parámetro (tamaño, salas, frames, enemigos) en lugar de quedarse con el inicial.
    val currentScreenWidth by rememberUpdatedState(screenWidth)
    val currentScreenHeight by rememberUpdatedState(screenHeight)
    val currentFloorHeight by rememberUpdatedState(floorHeight)
    val currentRooms by rememberUpdatedState(rooms)
    val currentSpikesAnimator by rememberUpdatedState(spikesAnimator)
    val currentDangerousFrames by rememberUpdatedState(spikesDangerousFrames)
    val currentWalkLeft by rememberUpdatedState(walkLeftFrames)
    val currentWalkRight by rememberUpdatedState(walkRightFrames)
    val currentJumpLeft by rememberUpdatedState(jumpLeftFrames)
    val currentJumpRight by rememberUpdatedState(jumpRightFrames)
    val currentRunLeft by rememberUpdatedState(runLeftFrames)
    val currentRunRight by rememberUpdatedState(runRightFrames)
    val currentEnemies by rememberUpdatedState(enemies)
    val currentArrows by rememberUpdatedState(arrows)
    val currentHurtFrames by rememberUpdatedState(hurtFrames)
    val currentHangLeft by rememberUpdatedState(hangLeftFrames)
    val currentHangRight by rememberUpdatedState(hangRightFrames)
    val currentAttackLeft by rememberUpdatedState(attackLeftFrames)
    val currentAttackRight by rememberUpdatedState(attackRightFrames)
    val currentCrouchLeft by rememberUpdatedState(crouchLeftFrames)
    val currentCrouchRight by rememberUpdatedState(crouchRightFrames)
    val currentArrowAnimator by rememberUpdatedState(arrowAnimator)
    val currentArrowDangerousFrames by rememberUpdatedState(arrowDangerousFrames)

    LaunchedEffect(Unit) {
        var lastFrameTimeNanos = withFrameNanos { it }
        while (isActive) {
            val frameTimeNanos = withFrameNanos { it }
            val dt = ((frameTimeNanos - lastFrameTimeNanos) / 1_000_000_000f).coerceAtMost(MAX_DT)
            lastFrameTimeNanos = frameTimeNanos
            frameCount++

            // Solo actualiza física si ya conocemos el tamaño real de pantalla
            if (currentScreenWidth > 0f && currentScreenHeight > 0f) {
                updatePhysics(
                    player = player,
                    dt = dt,
                    rooms = currentRooms,
                    screenWidth = currentScreenWidth,
                    screenHeight = currentScreenHeight,
                    floorHeight = currentFloorHeight,
                    spikesAnimator = currentSpikesAnimator,
                    spikesDangerousFrames = currentDangerousFrames,
                    arrowAnimator = currentArrowAnimator,
                    arrowDangerousFrames = currentArrowDangerousFrames,
                    enemies = currentEnemies,
                    arrows = currentArrows
                )
                // IA de los enemigos y flechas en vuelo (después de la física del jugador)
                val floorTopY = currentScreenHeight - currentFloorHeight
                for (enemy in currentEnemies) enemy.update(player, dt, currentScreenWidth, floorTopY, currentArrows)
                currentArrows.update(dt, player, currentScreenWidth)
            }
            for (enemy in currentEnemies) enemy.updateAnimation(dt)

            when {
                player.hurtTimer > 0f -> {
                    // Herido: 6 frames que avanzan según el tiempo restante (mismo frame para izquierda y derecha)
                    animator.setFrames(currentHurtFrames)
                    val progress = 1f - player.hurtTimer / Player.HURT_DURATION
                    animator.showFrame((progress * currentHurtFrames.size).toInt())
                }
                player.isHanging -> {
                    // Colgado: pose de brazos arriba (frame 2 de las filas 35/37). Al subir se desliza con esa misma pose
                    // y, pasado PULL_UP_SIT_PROGRESS, pasa a la pose agachada (primer frame de agacharse) sobre el borde.
                    val progress = if (player.pullUpTimer > 0f) 1f - player.pullUpTimer / PULL_UP_DURATION else 0f
                    if (progress >= PULL_UP_SIT_PROGRESS) {
                        animator.setFrames(if (player.facingRight) currentCrouchRight else currentCrouchLeft)
                        animator.reset()
                    } else {
                        animator.setFrames(if (player.facingRight) currentHangRight else currentHangLeft)
                        animator.showFrame(2)
                    }
                }
                !player.isOnGround -> {
                    // Salto: el frame depende de la velocidad vertical (subiendo, en el punto más alto o cayendo)
                    animator.setFrames(if (player.facingRight) currentJumpRight else currentJumpLeft)
                    val jumpScale = player.height / REFERENCE_PLAYER_SIZE
                    animator.showFrame(
                        when {
                            player.velocityY < -JUMP_ANIM_RISING_SPEED * jumpScale -> 2    // subiendo fuerte: pierna arriba
                            player.velocityY < JUMP_ANIM_FALLING_SPEED * jumpScale -> 3    // cerca del punto más alto: pierna recogida
                            else -> 4                                                       // cayendo: brazos abiertos
                        }
                    )
                }
                player.attackTimer > 0f -> {
                    // Golpe: el frame sale del avance del ataque (0 → último), sin depender del reloj del Animator
                    val frames = if (player.facingRight) currentAttackRight else currentAttackLeft
                    animator.setFrames(frames)
                    val progress = 1f - player.attackTimer / ATTACK_DURATION
                    animator.showFrame((progress * frames.size).toInt())
                }
                player.isCrouching -> {
                    // Pose de agachado: SIEMPRE el primer frame (el más bajo, sentado en el suelo), sin animar
                    animator.setFrames(if (player.facingRight) currentCrouchRight else currentCrouchLeft)
                    animator.reset()
                }
                player.moveDirection != 0 -> {
                    // Caminar o correr (la carrera usa sus propios frames y se reproduce más rápido)
                    val running = player.isRunning
                    animator.setFrames(
                        if (player.facingRight) (if (running) currentRunRight else currentWalkRight)
                        else (if (running) currentRunLeft else currentWalkLeft)
                    )
                    animator.update(if (running) dt * RUN_ANIMATION_SPEED else dt)
                }
                else -> {
                    // Quieto: pose inicial de caminar (así no queda congelado el primer frame de otra animación)
                    animator.setFrames(if (player.facingRight) currentWalkRight else currentWalkLeft)
                    animator.reset()
                }
            }
        }
    }

    // HUD: corazones (♥ llenos en rojo, ♡ vacíos) + texto de depuración chico
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Color(0xFFE53935))) { append("♥".repeat(player.health)) }
                withStyle(SpanStyle(color = Color.White.copy(alpha = 0.7f))) {
                    append("♡".repeat((Player.MAX_HEALTH - player.health).coerceAtLeast(0)))
                }
            },
            fontSize = 30.sp
        )
        if (SHOW_DEBUG_INFO) {
            Text(
                text = "Tramo ${player.roomIndex + 1}/${rooms.size}  |  Frames: $frameCount",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
        }
    }
}