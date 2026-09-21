package com.example.princeofpersia

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.princeofpersia.game.engine.GameLoop
import com.example.princeofpersia.game.entities.ArrowManager
import com.example.princeofpersia.game.entities.Enemy
import com.example.princeofpersia.game.entities.EnemyFrames
import com.example.princeofpersia.game.entities.EnemyType
import com.example.princeofpersia.game.entities.Player
import com.example.princeofpersia.game.level.PIT_EXTRA_CENTER_PARTS
import com.example.princeofpersia.game.level.PlatformStyle
import com.example.princeofpersia.game.level.SegmentType
import com.example.princeofpersia.game.level.testRooms
import com.example.princeofpersia.game.render.Animator
import com.example.princeofpersia.game.render.loadBitmapFromAssets
import com.example.princeofpersia.game.render.assetExists
import com.example.princeofpersia.game.render.drawWounds
import com.example.princeofpersia.game.render.loadBackgroundSlices
import com.example.princeofpersia.game.render.loadBitmapFromAssetsAny
import com.example.princeofpersia.game.render.loadSpriteSheetRow
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GameRoot() }
    }
}

/**
 * Alto BASE del suelo como fracción del alto de la pantalla (antes de bajar el mundo).
 * El grosor que se ve y con el que se dibujan suelo, pozo y pinchos es:
 *   FLOOR_HEIGHT_FRACTION - WORLD_LOWER_FRACTION
 * Todo (baldosa, pozo, pinchos) se dibuja con ESE grosor, sin partes escondidas fuera de pantalla:
 * si el grosor baja, el suelo, el pozo y los pinchos se ven más finos y proporcionados.
 */
private const val FLOOR_HEIGHT_FRACTION = 0.20f

/**
 * >>> CONSTANTE PARA ACOMODAR TODO <<<
 * Cuánto se BAJA el mundo, como fracción del alto de la pantalla: la línea del suelo, el pozo,
 * los pinchos, la decoración, el personaje (la física usa la misma línea) y los botones.
 * Al bajar la línea, el suelo queda MÁS FINO (grosor = FLOOR_HEIGHT_FRACTION - WORLD_LOWER_FRACTION).
 *   0.00f = grosor 20% de la pantalla
 *   0.05f = grosor 15%
 *   0.03f = grosor 17%
 *   0.01f = grosor 19% (valor actual)
 *   0.08f = grosor 12%
 * Debe ser menor que FLOOR_HEIGHT_FRACTION. Recomendado: entre 0.00f y 0.10f.
 */
private const val WORLD_LOWER_FRACTION = 0.01f

/** Ajuste fino EXTRA solo para los botones (además de lo que bajan con WORLD_LOWER_FRACTION). */
private val BUTTONS_EXTRA_DROP = 0.dp
private val SPIKES_DANGEROUS_FRAMES = setOf(3, 4, 5)

/** Frames (de la secuencia de 10 de arrowAnimator) en los que la trampa de flechas es peligrosa. */
private val ARROW_DANGEROUS_FRAMES = setOf(4, 5, 6)

/** Grosor visual de las plataformas, como fracción del alto del personaje. */
private const val PLATFORM_THICKNESS_FRACTION = 0.22f
/**
 * Fracción del alto del fondo que es "pared" (el resto, abajo, es el piso pintado
 * en la imagen, que NO se usa porque el juego dibuja su propio piso con floor_stone_tile).
 * En fondo_castillo_largo.png la pared termina en y=498 de 666 -> 0.75.
 */
private const val BACKGROUND_WALL_FRACTION = 0.75f
/**
 * Cuánto se baja el DIBUJO del personaje (fracción de su alto) para que los pies toquen el suelo.
 * El sprite tiene margen transparente bajo los pies. Solo afecta el dibujo, no la colisión.
 * Si flota: subir (0.08f). Si se hunde: bajar (0.04f).
 */
private const val PLAYER_FOOT_OFFSET_FRACTION = 0.06f

@Composable
fun GameRoot() {
    val context = LocalContext.current
    val player = remember { Player() }

    // Tamaño real del Canvas: se conoce recién en el primer layout, por eso arranca en 0.
    // GameLoop no corre física hasta tener un tamaño válido (ver chequeo adentro de GameLoop).
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Alto del suelo en píxeles reales, proporcional a la pantalla. Lo usa la física (GameLoop);
    // el Canvas calcula el mismo valor con su propio size.height para dibujar.
    // (alto del suelo - lo que se baja) = parte visible del suelo; define la línea donde se para el jugador.
    val floorHeightPx = canvasSize.height * (FLOOR_HEIGHT_FRACTION - WORLD_LOWER_FRACTION)

    // Cuánto bajan los botones: lo mismo que el mundo + el ajuste fino.
    val density = LocalDensity.current
    val buttonsDrop: Dp = with(density) { (canvasSize.height * WORLD_LOWER_FRACTION).toDp() } + BUTTONS_EXTRA_DROP

    val floorTile = remember { loadBitmapFromAssets(context, "tiles/floor_stone_tile.png") }
    // El fondo (8000x666) se corta en un tramo por sala: 6 salas = 6 tramos.
    val backgroundSlices = remember {
        loadBackgroundSlices(context, "fondos/fondo_castillo_largo.png", sliceCount = testRooms.size)
    }

    val walkLeftFrames = remember { loadSpriteSheetRow(context, "sprites/guerrero_lpc.png", row = 9, frameCount = 9) }
    val walkRightFrames = remember { loadSpriteSheetRow(context, "sprites/guerrero_lpc.png", row = 11, frameCount = 9) }
    // Saltar: filas 27 (izquierda) / 29 (derecha), 5 frames: 0-1 agacharse para despegar, 2 subiendo (pierna arriba),
    // 3 en el aire (pierna recogida), 4 cayendo (brazos abiertos). En el aire se elige el frame según la velocidad vertical.
    val jumpLeftFrames = remember { loadSpriteSheetRow(context, "sprites/guerrero_lpc.png", row = 27, frameCount = 5) }
    val jumpRightFrames = remember { loadSpriteSheetRow(context, "sprites/guerrero_lpc.png", row = 29, frameCount = 5) }
    // Agacharse: filas 31 (izquierda) / 33 (derecha) del sprite sheet, 3 frames c/u
    val crouchLeftFrames = remember { loadSpriteSheetRow(context, "sprites/guerrero_lpc.png", row = 31, frameCount = 3) }
    val crouchRightFrames = remember { loadSpriteSheetRow(context, "sprites/guerrero_lpc.png", row = 33, frameCount = 3) }
    // Correr: filas 39 (izquierda) / 41 (derecha), 8 frames c/u
    val runLeftFrames = remember { loadSpriteSheetRow(context, "sprites/guerrero_lpc.png", row = 39, frameCount = 8) }
    val runRightFrames = remember { loadSpriteSheetRow(context, "sprites/guerrero_lpc.png", row = 41, frameCount = 8) }
    // Atacar con el arma: filas 5 (izquierda) / 7 (derecha), 8 frames c/u
    val attackLeftFrames = remember { loadSpriteSheetRow(context, "sprites/guerrero_lpc.png", row = 5, frameCount = 8) }
    val attackRightFrames = remember { loadSpriteSheetRow(context, "sprites/guerrero_lpc.png", row = 7, frameCount = 8) }
    // Colgarse: pose de brazos arriba = frame 2 de las filas 35 (izquierda) / 37 (derecha)
    val hangLeftFrames = remember { loadSpriteSheetRow(context, "sprites/guerrero_lpc.png", row = 35, frameCount = 3) }
    val hangRightFrames = remember { loadSpriteSheetRow(context, "sprites/guerrero_lpc.png", row = 37, frameCount = 3) }
    // Herido: fila 20, 6 frames (sin distinción de dirección)
    val hurtFrames = remember { loadSpriteSheetRow(context, "sprites/guerrero_lpc.png", row = 20, frameCount = 6) }
    val animator = remember { Animator(walkLeftFrames) }

    // Enemigos del nivel (cada tipo con su sprite, mismo formato LPC que el guerrero).
    // patrolMin/Max = borde izquierdo del sprite como fracción del ancho de pantalla; siempre sobre piso seguro.
    val enemies = remember {
        listOf(
            // Sala 2: espadachín de camisa azul, sobre el piso del inicio (lejos de los pinchos)
            Enemy(
                roomIndex = 1, patrolMinFraction = 0.03f, patrolMaxFraction = 0.22f,
                frames = loadEnemyFrames(context, "sprites/enemigo1.png"), size = player.height
            ),
            // Sala 3: zorro con hacha, antes del pozo
            Enemy(
                roomIndex = 2, patrolMinFraction = 0.04f, patrolMaxFraction = 0.34f,
                frames = loadEnemyFrames(context, "sprites/enemigo2.png"), size = player.height
            ),
            // Sala 4: minotauro arquero, en el piso de la derecha: dispara por encima de los pinchos
            Enemy(
                roomIndex = 3, patrolMinFraction = 0.76f, patrolMaxFraction = 0.88f,
                frames = loadEnemyFrames(context, "sprites/enemigo3.png", archer = true),
                size = player.height, type = EnemyType.ARCHER
            ),
        )
    }

    // Flechas del arquero: sprites obstaculos/flecha_derecha.png y flecha_izquierda.png (88×32 px)
    val enemyArrows = remember { ArrowManager() }
    val flechaDerecha = remember {
        loadBitmapFromAssetsAny(context, "obstaculos/flecha_derecha.png", "sprites/flecha_derecha.png")
    }
    val flechaIzquierda = remember {
        loadBitmapFromAssetsAny(context, "obstaculos/flecha_izquierda.png", "sprites/flecha_izquierda.png")
    }

    val pozoBorderLeft = remember { loadBitmapFromAssets(context, "obstaculos/pozo_borde_izquierdo.png") }
    val pozoCenter = remember { loadBitmapFromAssets(context, "obstaculos/pozo_centro.png") }
    val pozoBorderRight = remember { loadBitmapFromAssets(context, "obstaculos/pozo_borde_derecho.png") }
    val pinchosFrames = remember {
        listOf(
            loadBitmapFromAssets(context, "obstaculos/pincho01.png"),
            loadBitmapFromAssets(context, "obstaculos/pincho02.png"),
            loadBitmapFromAssets(context, "obstaculos/pincho03.png"),
            loadBitmapFromAssets(context, "obstaculos/pincho04.png"),
        )
    }
    val spikesAnimator = remember {
        Animator(
            listOf(
                pinchosFrames[0], pinchosFrames[1], pinchosFrames[2], pinchosFrames[3],
                pinchosFrames[3], pinchosFrames[3], pinchosFrames[2], pinchosFrames[1]
            ),
            frameDurationSeconds = 0.15f
        )
    }
    // Trampa de flechas: 4 frames, en ciclo con pausa (los frames 4-6 de la secuencia son los peligrosos)
    val flechaFrames = remember {
        listOf(
            loadBitmapFromAssetsAny(context, "obstaculos/flecha01.png", "fondos/flecha01.png"),
            loadBitmapFromAssetsAny(context, "obstaculos/flecha02.png", "fondos/flecha02.png"),
            loadBitmapFromAssetsAny(context, "obstaculos/flecha03.png", "fondos/flecha03.png"),
            loadBitmapFromAssetsAny(context, "obstaculos/flecha04.png", "fondos/flecha04.png"),
        )
    }
    val arrowAnimator = remember {
        Animator(
            listOf(
                flechaFrames[0], flechaFrames[0], flechaFrames[1], flechaFrames[2],
                flechaFrames[3], flechaFrames[3], flechaFrames[3], flechaFrames[2],
                flechaFrames[1], flechaFrames[0]
            ),
            frameDurationSeconds = 0.15f
        )
    }
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (isActive) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1_000_000_000f
            last = now
            spikesAnimator.update(dt)
            arrowAnimator.update(dt)
        }
    }

    // Plataformas flotantes (Sesión 14). Busca en assets/plataformas/ y, si no está, en assets/fondos/.
    val plataformaBordeIzq = remember {
        loadBitmapFromAssetsAny(context, "plataformas/plataforma_borde_izquierdo.png", "fondos/plataforma_borde_izquierdo.png")
    }
    val plataformaCentro = remember {
        loadBitmapFromAssetsAny(context, "plataformas/plataforma_centro.png", "fondos/plataforma_centro.png")
    }
    val plataformaBordeDer = remember {
        loadBitmapFromAssetsAny(context, "plataformas/plataforma_borde_derecho.png", "fondos/plataforma_borde_derecho.png")
    }
    val escalonMedio = remember {
        loadBitmapFromAssetsAny(context, "plataformas/escalon_medio.png", "fondos/escalon_medio.png")
    }

    // Saliente para colgarse (Sesión 14i): assets/plataformas/saliente_para_colgarse.png. Si no está, se dibuja con
    // el centro de plataforma para que la app no se cierre.
    val salienteColgarse = remember {
        listOf("plataformas/saliente_para_colgarse.png", "fondos/saliente_para_colgarse.png")
            .firstOrNull { assetExists(context, it) }
            ?.let { loadBitmapFromAssets(context, it) }
    }

    val antorchaEncendida = remember { loadBitmapFromAssets(context, "decoracion/antorcha_pared_encendida.png") }
    val candelabroApagado = remember { loadBitmapFromAssets(context, "decoracion/candelabro_pie_apagado.png") }

    // Los controles (◀ ▶ ⬇ ⤴) se manejan en TouchControls.kt con un único detector multitáctil.

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Box(modifier = Modifier.fillMaxSize()) {

            // ---- Juego: el Canvas SOLO dibuja. No mueve ni resuelve colisión del jugador. ----
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = it }
            ) {
                // Grosor real del suelo (lo que se ve). Suelo, pozo y pinchos se dibujan con este alto.
                val floorHeight = size.height * (FLOOR_HEIGHT_FRACTION - WORLD_LOWER_FRACTION)
                val floorTopY = size.height - floorHeight   // línea del suelo (ya bajada)
                val roomIndex = player.roomIndex.coerceIn(0, testRooms.lastIndex)

                // Fondo: el tramo de esta sala, SIN deformar. Se escala para cubrir todo el
                // ancho y la pared apoya su borde inferior justo sobre el suelo; lo que sobre
                // por arriba/costados se recorta (srcOffset/srcSize).
                val slice = backgroundSlices[roomIndex.coerceAtMost(backgroundSlices.lastIndex)]
                val wallHeight = slice.height * BACKGROUND_WALL_FRACTION
                val scale = maxOf(size.width / slice.width, floorTopY / wallHeight)
                val visibleW = (size.width / scale).toInt().coerceAtMost(slice.width)
                val visibleH = (floorTopY / scale).toInt().coerceAtMost(wallHeight.toInt())
                drawImage(
                    image = slice,
                    srcOffset = IntOffset((slice.width - visibleW) / 2, (wallHeight.toInt() - visibleH).coerceAtLeast(0)),
                    srcSize = IntSize(visibleW, visibleH),
                    dstOffset = IntOffset(0, 0),
                    dstSize = IntSize(size.width.toInt(), floorTopY.toInt())
                )

                var cursorX = 0f
                for (segment in testRooms[roomIndex].layout) {
                    val segWidth = segment.widthFraction * size.width
                    when (segment.type) {
                        SegmentType.FLOOR -> {
                            // Baldosa con su proporción original (sin estirarla) y recortada al tramo.
                            val tileW = (floorTile.width * floorHeight / floorTile.height).toInt().coerceAtLeast(1)
                            clipRect(left = cursorX, top = floorTopY, right = cursorX + segWidth, bottom = size.height) {
                                var fx = cursorX
                                while (fx < cursorX + segWidth) {
                                    drawImage(
                                        image = floorTile,
                                        dstOffset = IntOffset(fx.toInt(), floorTopY.toInt()),
                                        dstSize = IntSize(tileW, floorHeight.toInt())
                                    )
                                    fx += tileW
                                }
                            }
                        }
                        SegmentType.PIT -> {
                            // Bordes con la proporción de su imagen, según el alto del suelo.
                            val leftW = minOf(floorHeight * pozoBorderLeft.width / pozoBorderLeft.height, segWidth * 0.25f)
                            val rightW = minOf(floorHeight * pozoBorderRight.width / pozoBorderRight.height, segWidth * 0.25f)
                            drawImage(
                                image = pozoBorderLeft,
                                dstOffset = IntOffset(cursorX.toInt(), floorTopY.toInt()),
                                dstSize = IntSize(leftW.toInt(), floorHeight.toInt())
                            )
                            // Centro en (1 + PIT_EXTRA_CENTER_PARTS) partes iguales
                            val centerW = (segWidth - leftW - rightW).coerceAtLeast(0f)
                            val centerParts = 1 + PIT_EXTRA_CENTER_PARTS
                            val partW = centerW / centerParts
                            for (n in 0 until centerParts) {
                                drawImage(
                                    image = pozoCenter,
                                    dstOffset = IntOffset((cursorX + leftW + n * partW).toInt(), floorTopY.toInt()),
                                    dstSize = IntSize(partW.toInt() + 1, floorHeight.toInt())
                                )
                            }
                            drawImage(
                                image = pozoBorderRight,
                                dstOffset = IntOffset((cursorX + segWidth - rightW).toInt(), floorTopY.toInt()),
                                dstSize = IntSize(rightW.toInt(), floorHeight.toInt())
                            )
                        }
                        SegmentType.SPIKES -> {
                            // Pinchos con la proporción de su imagen: se escala por el ALTO del suelo y se
                            // repite a lo ancho del tramo (en vez de estirar una sola imagen a todo el ancho).
                            val frame = spikesAnimator.currentFrame
                            val naturalW = frame.width * floorHeight / frame.height
                            val count = (segWidth / naturalW).roundToInt().coerceAtLeast(1)
                            val tileW = segWidth / count
                            for (n in 0 until count) {
                                drawImage(
                                    image = frame,
                                    dstOffset = IntOffset((cursorX + n * tileW).toInt(), floorTopY.toInt()),
                                    dstSize = IntSize(tileW.toInt() + 1, floorHeight.toInt())
                                )
                            }
                        }
                    }
                    cursorX += segWidth
                }

                // Decoración solo en la primera sala (por ahora). Tamaños pensados para una
                // pantalla de 1080 px de alto y escalados según el alto real.
                if (roomIndex == 0) {
                    val k = size.height / 1080f
                    val torchSize = IntSize((70 * k).toInt(), (130 * k).toInt())
                    drawImage(
                        image = antorchaEncendida,
                        dstOffset = IntOffset((0.42f * size.width).toInt(), (floorTopY - 260 * k).toInt()),
                        dstSize = torchSize
                    )
                    drawImage(
                        image = antorchaEncendida,
                        dstOffset = IntOffset((0.83f * size.width).toInt(), (floorTopY - 260 * k).toInt()),
                        dstSize = torchSize
                    )
                    drawImage(
                        image = candelabroApagado,
                        dstOffset = IntOffset((20 * k).toInt(), (floorTopY - 150 * k).toInt()),
                        dstSize = IntSize((80 * k).toInt(), (150 * k).toInt())
                    )
                }

                // Trampas de flechas: ícono cuadrado (sin estirar) a la altura del pecho
                for (trap in testRooms[roomIndex].arrowTraps) {
                    for (box in trap.boxes(size.width, player.height, floorTopY)) {
                        drawImage(
                            image = arrowAnimator.currentFrame,
                            dstOffset = IntOffset(box.left.toInt(), box.top.toInt()),
                            dstSize = IntSize(box.size.toInt(), box.size.toInt())
                        )
                    }
                }

                // Plataformas flotantes: la cara superior es la superficie donde se para el jugador.
                val platformThickness = player.height * PLATFORM_THICKNESS_FRACTION
                for (platform in testRooms[roomIndex].platforms) {
                    val pLeft = platform.effectiveLeft * size.width
                    val pWidth = platform.effectiveWidth * size.width
                    val pTopY = floorTopY - platform.effectiveHeight * player.height
                    when (platform.style) {
                        PlatformStyle.CAPPED -> {
                            val leftW = minOf(platformThickness * plataformaBordeIzq.width / plataformaBordeIzq.height, pWidth * 0.25f)
                            val rightW = minOf(platformThickness * plataformaBordeDer.width / plataformaBordeDer.height, pWidth * 0.25f)
                            drawImage(
                                image = plataformaBordeIzq,
                                dstOffset = IntOffset(pLeft.toInt(), pTopY.toInt()),
                                dstSize = IntSize(leftW.toInt(), platformThickness.toInt())
                            )
                            drawTiledRow(
                                image = plataformaCentro,
                                left = pLeft + leftW, top = pTopY,
                                width = pWidth - leftW - rightW, height = platformThickness
                            )
                            drawImage(
                                image = plataformaBordeDer,
                                dstOffset = IntOffset((pLeft + pWidth - rightW).toInt(), pTopY.toInt()),
                                dstSize = IntSize(rightW.toInt(), platformThickness.toInt())
                            )
                        }
                        PlatformStyle.LEDGE -> drawTiledRow(
                            image = salienteColgarse ?: plataformaCentro,
                            left = pLeft, top = pTopY,
                            width = pWidth, height = platformThickness
                        )
                        PlatformStyle.BLOCK -> drawTiledRow(
                            image = escalonMedio,
                            left = pLeft, top = pTopY,
                            width = pWidth, height = platformThickness
                        )
                    }
                }

                // El Canvas solo DIBUJA: player.x / player.y / player.isOnGround ya vienen
                // resueltos por GameLoop antes de que se ejecute este draw.
                // Enemigos de esta sala (parados sobre la misma línea de suelo que el jugador)
                for (enemy in enemies) {
                    if (enemy.roomIndex != roomIndex || !enemy.isVisible) continue
                    val enemyTop = floorTopY - enemy.height + enemy.height * PLAYER_FOOT_OFFSET_FRACTION
                    drawImage(
                        image = enemy.animator.currentFrame,
                        dstOffset = IntOffset(enemy.x.toInt(), enemyTop.toInt()),
                        dstSize = IntSize(enemy.width.toInt(), enemy.height.toInt()),
                        alpha = enemy.drawAlpha
                    )
                    drawWounds(enemy.wounds, left = enemy.x, top = enemyTop, size = enemy.height)
                }

                // Flechas del arquero en vuelo (solo las de esta sala)
                for (arrow in enemyArrows.arrows) {
                    if (arrow.roomIndex != roomIndex) continue
                    drawImage(
                        image = if (arrow.facingRight) flechaDerecha else flechaIzquierda,
                        dstOffset = IntOffset(arrow.x.toInt(), (arrow.centerY - arrow.height / 2f).toInt()),
                        dstSize = IntSize(arrow.width.toInt(), arrow.height.toInt())
                    )
                }

                // Parpadea mientras es invulnerable (después de un golpe)
                val blink = player.invulnerableTimer > 0f && ((player.invulnerableTimer * 12f).toInt() % 2 == 0)
                val playerDrawTop = player.y + player.height * PLAYER_FOOT_OFFSET_FRACTION
                drawImage(
                    image = animator.currentFrame,
                    dstOffset = IntOffset(player.x.toInt(), playerDrawTop.toInt()),
                    dstSize = IntSize(player.width.toInt(), player.height.toInt()),
                    alpha = if (blink) 0.35f else 1f
                )
                // Manchas de sangre encima del cuerpo (siguen al personaje)
                drawWounds(player.wounds, left = player.x, top = playerDrawTop, size = player.height)
            }

            GameLoop(
                player = player, animator = animator,
                walkLeftFrames = walkLeftFrames, walkRightFrames = walkRightFrames,
                jumpLeftFrames = jumpLeftFrames, jumpRightFrames = jumpRightFrames,
                crouchLeftFrames = crouchLeftFrames, crouchRightFrames = crouchRightFrames,
                runLeftFrames = runLeftFrames, runRightFrames = runRightFrames,
                attackLeftFrames = attackLeftFrames, attackRightFrames = attackRightFrames,
                hurtFrames = hurtFrames,
                hangLeftFrames = hangLeftFrames, hangRightFrames = hangRightFrames,
                enemies = enemies,
                arrows = enemyArrows,
                rooms = testRooms,
                screenWidth = canvasSize.width.toFloat(),
                screenHeight = canvasSize.height.toFloat(),
                floorHeight = floorHeightPx,
                spikesAnimator = spikesAnimator,
                spikesDangerousFrames = SPIKES_DANGEROUS_FRAMES,
                arrowAnimator = arrowAnimator,
                arrowDangerousFrames = ARROW_DANGEROUS_FRAMES,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding()
            )

            // ---- Controles táctiles (multitáctil): flotan en la franja bajo el suelo ----
            TouchControls(player = player, buttonsDrop = buttonsDrop)
        }
    }
}

/**
 * Dibuja [image] repetida horizontalmente dentro del rectángulo (left, top, width, height),
 * manteniendo la proporción de la imagen y recortando lo que sobre.
 */
private fun DrawScope.drawTiledRow(image: ImageBitmap, left: Float, top: Float, width: Float, height: Float) {
    if (width <= 0f || height <= 0f) return
    val tileW = (image.width * height / image.height).coerceAtLeast(1f)
    clipRect(left = left, top = top, right = left + width, bottom = top + height) {
        var x = left
        while (x < left + width) {
            drawImage(
                image = image,
                dstOffset = IntOffset(x.toInt(), top.toInt()),
                dstSize = IntSize(tileW.toInt() + 1, height.toInt())
            )
            x += tileW
        }
    }
}

/**
 * Carga las animaciones de un enemigo: caminar filas 9/11 (9 frames), espadazo 13/15 (6 frames), herido/muerte
 * fila 20 (6 frames) y, solo si es arquero, disparo filas 17/19 (13 frames).
 * Si el archivo no está en assets/, usa el sprite del guerrero (mismo formato) para que la app no se cierre.
 */
private fun loadEnemyFrames(context: Context, sheet: String, archer: Boolean = false): EnemyFrames {
    val path = if (assetExists(context, sheet)) sheet else "sprites/guerrero_lpc.png"
    return EnemyFrames(
        walkLeft = loadSpriteSheetRow(context, path, row = 9, frameCount = 9),
        walkRight = loadSpriteSheetRow(context, path, row = 11, frameCount = 9),
        slashLeft = loadSpriteSheetRow(context, path, row = 13, frameCount = 6),
        slashRight = loadSpriteSheetRow(context, path, row = 15, frameCount = 6),
        hurt = loadSpriteSheetRow(context, path, row = 20, frameCount = 6),
        shootLeft = if (archer) loadSpriteSheetRow(context, path, row = 17, frameCount = 13) else emptyList(),
        shootRight = if (archer) loadSpriteSheetRow(context, path, row = 19, frameCount = 13) else emptyList(),
    )
}