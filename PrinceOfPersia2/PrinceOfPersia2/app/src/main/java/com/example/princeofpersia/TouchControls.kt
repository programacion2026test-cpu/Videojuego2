package com.example.princeofpersia

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.princeofpersia.game.entities.Player

private enum class Control { LEFT, RIGHT, CROUCH, ATTACK, JUMP }

/**
 * Controles táctiles con UN SOLO detector para todos los dedos.
 *
 * Antes cada botón (Button/clickable) manejaba su propio toque por separado. Ahora un único detector
 * mira TODOS los dedos a la vez y decide, según dónde está cada uno, qué botón está apretado. Eso permite:
 *  - mantener ◀ o ▶ con un dedo y saltar o agacharse con otro, sin que se pisen;
 *  - deslizar el dedo de un botón a otro sin soltar;
 *  - saltar apenas el dedo TOCA el botón de salto (no al soltar), y seguir saltando si se mantiene apretado;
 *  - atacar: un toque en ⚔ da un golpe (no hace falta mantenerlo);
 *  - agacharse: tocar ⬇ (o dejarlo apretado) deja al personaje agachado; se queda así aunque suelte el dedo.
 *    Vuelve a lo normal al tocar otro botón (◀ ▶ ⤴), es decir, al caminar o saltar.
 *
 * El estado se escribe en Player: moveDirection, isCrouching, jumpRequested y jumpHeld.
 */
@Composable
fun TouchControls(player: Player, buttonsDrop: Dp, modifier: Modifier = Modifier) {
    // Rectángulo de cada botón en pantalla (coordenadas de la raíz), lo llena cada botón al medirse
    val bounds = remember { mutableMapOf<Control, Rect>() }
    val origin = remember { mutableStateOf(Offset.Zero) }
    var down by remember { mutableStateOf(emptySet<Control>()) }

    // Zona táctil un poco más grande que el botón dibujado, para que sea más fácil acertar
    val slop = with(LocalDensity.current) { 6.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { origin.value = it.positionInRoot() }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val nowDown = mutableSetOf<Control>()
                        var crouchTapped = false
                        for (change in event.changes) {
                            if (!change.pressed) continue            // dedo levantado: no cuenta
                            val p = change.position + origin.value
                            val control = Control.values().firstOrNull { c ->
                                bounds[c]?.inflate(slop)?.contains(p) == true
                            } ?: continue
                            nowDown += control
                            // Salto: se pide en el instante en que el dedo TOCA el botón
                            if (control == Control.JUMP && change.changedToDown()) {
                                player.jumpRequested = true
                            }
                            // Atacar: un toque = un golpe (no hace falta mantenerlo)
                            if (control == Control.ATTACK && change.changedToDown()) {
                                player.attackRequested = true
                            }
                            // Agacharse: se detecta el TOQUE nuevo (no depende de mantener el dedo)
                            if (control == Control.CROUCH && change.changedToDown()) {
                                crouchTapped = true
                            }
                        }
                        player.jumpHeld = Control.JUMP in nowDown

                        // Otro botón (◀ ▶ ⤴) apretado = el personaje se levanta / vuelve a lo normal.
                        // Si no hay otro botón y se tocó ⬇, queda agachado (hasta que se toque otro botón).
                        val otherButtonPressed =
                            Control.LEFT in nowDown || Control.RIGHT in nowDown ||
                                    Control.JUMP in nowDown || Control.ATTACK in nowDown
                        if (otherButtonPressed || player.jumpRequested) {
                            player.isCrouching = false
                        } else if (crouchTapped) {
                            player.isCrouching = true
                        }
                        player.moveDirection = when {
                            Control.LEFT in nowDown -> -1
                            Control.RIGHT in nowDown -> 1
                            else -> 0
                        }
                        down = nowDown
                    }
                }
            }
    ) {
        // Izquierda: ◀ ▶
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .systemBarsPadding()
                .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 6.dp)
                .offset(y = buttonsDrop),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ControlButton(Control.LEFT, "◀️", 48.dp, 16.sp, DPAD_COLOR, DPAD_COLOR_PRESSED, DPAD_BORDER, Control.LEFT in down, bounds)
            ControlButton(Control.RIGHT, "▶️", 48.dp, 16.sp, DPAD_COLOR, DPAD_COLOR_PRESSED, DPAD_BORDER, Control.RIGHT in down, bounds)
        }

        // Derecha: agacharse (mantener) + saltar
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .systemBarsPadding()
                .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 6.dp)
                .offset(y = buttonsDrop),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlButton(Control.CROUCH, "⬇️", 48.dp, 16.sp, DPAD_COLOR, DPAD_COLOR_PRESSED, DPAD_BORDER, player.isCrouching, bounds)
            ControlButton(Control.ATTACK, "⚔️", 52.dp, 18.sp, ATTACK_COLOR, ATTACK_COLOR_PRESSED, ATTACK_BORDER, Control.ATTACK in down, bounds)
            ControlButton(Control.JUMP, "⤴️", 60.dp, 20.sp, JUMP_COLOR, JUMP_COLOR_PRESSED, JUMP_BORDER, Control.JUMP in down, bounds)
        }
    }
}

private val DPAD_COLOR = Color.White.copy(alpha = 0.16f)
private val DPAD_COLOR_PRESSED = Color.White.copy(alpha = 0.40f)
private val DPAD_BORDER = Color.White.copy(alpha = 0.35f)
private val ATTACK_COLOR = Color(0xFFC9A227).copy(alpha = 0.45f)
private val ATTACK_COLOR_PRESSED = Color(0xFFC9A227).copy(alpha = 0.85f)
private val ATTACK_BORDER = Color.White.copy(alpha = 0.4f)
private val JUMP_COLOR = Color(0xFFB33951).copy(alpha = 0.45f)
private val JUMP_COLOR_PRESSED = Color(0xFFB33951).copy(alpha = 0.85f)
private val JUMP_BORDER = Color.White.copy(alpha = 0.4f)

/** Botón redondo SOLO visual: no maneja toques, solo informa dónde está para el detector de arriba. */
@Composable
private fun ControlButton(
    control: Control,
    text: String,
    size: Dp,
    fontSize: TextUnit,
    color: Color,
    colorPressed: Color,
    borderColor: Color,
    pressed: Boolean,
    bounds: MutableMap<Control, Rect>
) {
    Box(
        modifier = Modifier
            .size(size)
            .onGloballyPositioned { bounds[control] = it.boundsInRoot() }
            .background(if (pressed) colorPressed else color, CircleShape)
            .border(1.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = fontSize, color = Color.White)
    }
}