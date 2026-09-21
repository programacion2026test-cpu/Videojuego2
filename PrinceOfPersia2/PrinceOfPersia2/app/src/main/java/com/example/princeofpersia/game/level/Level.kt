package com.example.princeofpersia.game.level

enum class SegmentType { FLOOR, PIT, SPIKES }

data class LevelSegment(
    val type: SegmentType,
    val widthFraction: Float
)

// =====================================================================================
//  AJUSTES GLOBALES DE PLATAFORMAS (afectan a TODAS las plataformas de TODAS las salas)
//  Sirven para acomodarlas "a gusto" sin tocar una por una. Se aplican tanto al dibujo
//  como a la colisión, así que lo que ves es exactamente donde se pisa.
// =====================================================================================

/** Multiplica el ancho de todas las plataformas. 1.0 = original, 1.3 = 30% más anchas, 0.8 = 20% más angostas.
 *  Crece y se achica desde el CENTRO de cada plataforma. */
const val PLATFORM_WIDTH_SCALE = 2.0f

/** Mueve todas las plataformas a los costados, como fracción del ancho de pantalla.
 *  Positivo = derecha, negativo = izquierda. Ej: 0.05f las corre un 5% a la derecha. */
const val PLATFORM_OFFSET_X = -0.02f

/** Mueve todas las plataformas arriba/abajo, en ALTURAS DE JUGADOR.
 *  Positivo = sube, negativo = baja. Ej: 0.10f sube un 10% de la altura del personaje.
 *  Ojo: el salto llega a ≈1.1, así que no dejes ninguna por encima de ≈1.0 de altura total. */
const val PLATFORM_OFFSET_HEIGHT = 0.3f

// --- SEPARACIÓN ENTRE PLATAFORMAS ---
// Cada plataforma tiene un "paso de separación" (separationStep): la 1ª tiene 0 y la 2ª tiene 1.
// La 2ª se desplaza respecto de la 1ª tantas veces estos valores como indique su paso.
// (Con 3 plataformas, la 3ª con paso 2 se separaría el doble, etc.)

/** Separación HORIZONTAL entre plataformas, como fracción del ancho de pantalla.
 *  Positivo = la 2ª queda más a la derecha de la 1ª; negativo = más a la izquierda; 0 = una sobre otra. */
const val PLATFORM_GAP_X = 0.34f

/** Separación VERTICAL entre plataformas, en ALTURAS DE JUGADOR.
 *  Positivo = la 2ª queda más alta que la 1ª; negativo = más baja; 0 = a la misma altura. */
const val PLATFORM_GAP_HEIGHT = 0.25f

// =====================================================================================
//  PINCHOS Y POZO DEL SUELO (ajustes globales: afectan a TODOS los pinchos y pozos)
//  Se aplican al dibujo y a la colisión: lo que ves es lo que daña.
// =====================================================================================

/** Cuántas secuencias de pinchos EXTRA se agregan a continuación de cada tramo de pinchos.
 *  0 = solo el original, 1 = duplicado (otra secuencia al lado), 2 = triplicado… */
const val SPIKES_EXTRA_COPIES = 0

/** Espacio de piso SEGURO entre una secuencia de pinchos y la siguiente, como fracción del ancho de
 *  pantalla. 0 = pegadas. Solo se nota si SPIKES_EXTRA_COPIES > 0. */
const val SPIKES_COPY_GAP_X = 0.02f

/** Mueve TODOS los pinchos a los costados (fracción del ancho de pantalla). + derecha, - izquierda.
 *  El piso de los costados se acomoda solo. */
const val SPIKES_OFFSET_X = 0.0f

/** Cuántas partes de "centro" EXTRA se agregan a cada pozo para hacerlo más ancho (crece hacia la derecha).
 *  0 = pozo original. */
const val PIT_EXTRA_CENTER_PARTS = 0

/** Ancho de cada parte de centro extra, como fracción del ancho de pantalla. */
const val PIT_CENTER_PART_FRACTION = 0.06f

/** Margen de seguridad: los pinchos y pozos nunca se acercan más que esto a los bordes de la sala
 *  (fracción del ancho), para que al cruzar de sala el jugador no aparezca sobre una trampa. */
const val HAZARD_EDGE_MARGIN = 0.04f

// =====================================================================================
//  TRAMPA DE FLECHAS (mecanismo en la pared que dispara a la altura del pecho, en ciclo)
// =====================================================================================

/** Tamaño del ícono de la trampa, como fracción de la altura del personaje (0.5 = la mitad). */
const val ARROW_SIZE_FRACTION = 0.5f

/** Mueve TODAS las trampas de flechas a los costados (fracción del ancho de pantalla). + derecha, - izquierda. */
const val ARROW_OFFSET_X = 0.0f

/** Mueve TODAS las trampas de flechas arriba/abajo, en alturas de jugador. + sube, - baja. */
const val ARROW_OFFSET_HEIGHT = 0.0f

/** Cuántos íconos de flecha EXTRA se agregan al lado de cada trampa (0 = uno solo, 1 = dos, …).
 *  Todos son peligrosos a la vez y se esquivan igual (agachado). */
const val ARROW_EXTRA_COPIES = 0

/** Espacio LIBRE horizontal entre un ícono y el siguiente, como fracción del ancho de pantalla. 0 = pegados. */
const val ARROW_COPY_GAP_X = 0.0f

/** Desplazamiento VERTICAL de cada ícono respecto del anterior, en alturas de jugador.
 *  0 = todos a la misma altura; + = cada uno más alto que el anterior; - = más bajo. */
const val ARROW_COPY_GAP_HEIGHT = 0.0f

/** Rectángulo (en píxeles) de un ícono de flecha ya colocado en pantalla. */
data class ArrowBox(val left: Float, val top: Float, val size: Float)

/**
 * Trampa de flechas. Peligrosa solo en ciertos frames de su animación, y solo si el jugador NO está
 * agachado y su cuerpo cruza la franja donde vuela la flecha (saltando por encima también se esquiva).
 *
 * @param xFraction      borde izquierdo del ícono, como fracción del ancho de pantalla.
 * @param heightFraction altura del BORDE SUPERIOR del ícono sobre el suelo, en alturas de jugador.
 *                       0.75 con un ícono de 0.5 => la franja va de 0.25 a 0.75 (pecho/abdomen).
 */
data class ArrowTrap(
    val xFraction: Float,
    val heightFraction: Float
) {
    val effectiveLeft: Float get() = xFraction + ARROW_OFFSET_X
    val effectiveHeight: Float get() = heightFraction + ARROW_OFFSET_HEIGHT

    /** Todos los íconos de esta trampa (el original + las copias extra), en píxeles. Dibujo y física usan esto. */
    fun boxes(screenWidth: Float, playerHeight: Float, floorTopY: Float): List<ArrowBox> {
        val size = playerHeight * ARROW_SIZE_FRACTION
        return (0..ARROW_EXTRA_COPIES).map { i ->
            ArrowBox(
                left = effectiveLeft * screenWidth + i * (size + ARROW_COPY_GAP_X * screenWidth),
                top = floorTopY - (effectiveHeight + i * ARROW_COPY_GAP_HEIGHT) * playerHeight,
                size = size
            )
        }
    }
}

/** Cómo se dibuja una plataforma. */
enum class PlatformStyle {
    /** Borde izquierdo + centro repetido + borde derecho (plataforma larga). */
    CAPPED,
    /** Bloque de escalón repetido a lo ancho (plataforma tipo escalón). */
    BLOCK,
    /** Saliente para COLGARSE: además de pararse encima, el personaje puede agarrarse de su borde al saltar y subir. */
    LEDGE
}

/**
 * Plataforma flotante de colisión SOLO POR ARRIBA (se puede atravesar desde abajo y aterrizar encima).
 *
 * @param xFraction        borde izquierdo, como fracción del ancho de pantalla.
 * @param widthFraction    ancho, como fracción del ancho de pantalla.
 * @param heightFraction   altura de la cara superior sobre el suelo, medida en ALTURAS DEL JUGADOR
 *                         (1.0 = tan alta como el personaje). El salto alcanza ≈ 1.1, así que
 *                         usá valores entre 0.4 y 0.95 para que se pueda subir de un salto.
 * @param separationStep   0 = plataforma de referencia; 1, 2… = cuántas veces se le aplica la
 *                         separación PLATFORM_GAP_X / PLATFORM_GAP_HEIGHT.
 */
data class Platform(
    val xFraction: Float,
    val widthFraction: Float,
    val heightFraction: Float,
    val style: PlatformStyle = PlatformStyle.CAPPED,
    val separationStep: Int = 0
) {
    // Valores YA ajustados con las constantes globales de arriba. Dibujo y física usan SOLO estos.
    /** true si el personaje puede agarrarse de esta plataforma (estilo LEDGE). */
    val isGrabbable: Boolean get() = style == PlatformStyle.LEDGE

    val effectiveWidth: Float get() = widthFraction * PLATFORM_WIDTH_SCALE
    val effectiveLeft: Float
        get() = xFraction + PLATFORM_OFFSET_X - (effectiveWidth - widthFraction) / 2f + separationStep * PLATFORM_GAP_X
    val effectiveHeight: Float
        get() = heightFraction + PLATFORM_OFFSET_HEIGHT + separationStep * PLATFORM_GAP_HEIGHT
}

/**
 * Una sala = una pantalla = un tramo del fondo largo.
 * Las fracciones de sus segmentos deben sumar 1f.
 * Regla: el primer y el último segmento deben ser FLOOR, porque el jugador
 * aparece justo en el borde al cruzar de una sala a otra.
 */
data class Room(
    val segments: List<LevelSegment>,
    val platforms: List<Platform> = emptyList(),
    val arrowTraps: List<ArrowTrap> = emptyList()
) {
    /**
     * `segments` es el DISEÑO base. `layout` es el resultado ya aplicando las constantes globales de
     * pinchos y pozo (copias, separación, desplazamiento, pozo más ancho). El dibujo y la física usan
     * SOLO `layout`, así siempre coinciden. Cubre exactamente el ancho de la sala (suma 1f).
     */
    val layout: List<LevelSegment> = buildLayout(segments)
}

private class Span(val type: SegmentType, val start: Float, val end: Float)

/** Convierte el diseño base en el tramo final: las trampas son intervalos y todo lo demás es piso. */
private fun buildLayout(segments: List<LevelSegment>): List<LevelSegment> {
    // 1) Intervalos de trampas según el diseño, ya con copias / separación / desplazamiento / extensión
    val spans = mutableListOf<Span>()
    var cursor = 0f
    for (seg in segments) {
        val start = cursor
        val end = cursor + seg.widthFraction
        when (seg.type) {
            SegmentType.FLOOR -> Unit
            SegmentType.PIT ->
                spans += Span(SegmentType.PIT, start, end + PIT_EXTRA_CENTER_PARTS * PIT_CENTER_PART_FRACTION)
            SegmentType.SPIKES -> for (i in 0..SPIKES_EXTRA_COPIES) {
                val s = start + SPIKES_OFFSET_X + i * (seg.widthFraction + SPIKES_COPY_GAP_X)
                spans += Span(SegmentType.SPIKES, s, s + seg.widthFraction)
            }
        }
        cursor = end
    }

    // 2) Recortar a los márgenes, ordenar y evitar solapes (lo que se solape se recorta)
    val minX = HAZARD_EDGE_MARGIN
    val maxX = 1f - HAZARD_EDGE_MARGIN
    val out = mutableListOf<LevelSegment>()
    var pos = 0f
    for (span in spans.sortedBy { it.start }) {
        val s = maxOf(span.start.coerceIn(minX, maxX), pos)
        val e = span.end.coerceIn(minX, maxX)
        if (e - s <= 0.001f) continue
        if (s > pos) out += LevelSegment(SegmentType.FLOOR, s - pos)
        out += LevelSegment(span.type, e - s)
        pos = e
    }
    if (pos < 1f) out += LevelSegment(SegmentType.FLOOR, 1f - pos)
    return out
}

/** Sala 1: el nivel de prueba original. */
val testLevel = listOf(
    LevelSegment(SegmentType.FLOOR, 0.28f),
    LevelSegment(SegmentType.PIT, 0.12f),
    LevelSegment(SegmentType.FLOOR, 0.20f),
    LevelSegment(SegmentType.SPIKES, 0.12f),
    LevelSegment(SegmentType.FLOOR, 0.28f),
)

/**
 * 6 salas = 6 tramos del fondo. El fondo se corta en tantas partes iguales
 * como salas haya en esta lista: para tener 7 tramos, agregá una sala más acá.
 *
 * Por ahora SOLO la sala 1 tiene plataformas (2). Regla de diseño: no ponerlas sobre un pozo ni
 * sobre pinchos, para que no sirvan para saltearse las trampas.
 */
val testRooms = listOf(
    // Sala 1: pozo 0.28-0.40 | pinchos 0.60-0.72.
    // Las 2 plataformas se definen en la MISMA posición base; la 2ª se separa de la 1ª con
    // PLATFORM_GAP_X (horizontal) y PLATFORM_GAP_HEIGHT (vertical). Mové la base con xFraction / heightFraction.
    Room(
        segments = testLevel,
        platforms = listOf(
            Platform(xFraction = 0.475f, widthFraction = 0.07f, heightFraction = 0.40f, style = PlatformStyle.CAPPED, separationStep = 0),
            Platform(xFraction = 0.475f, widthFraction = 0.07f, heightFraction = 0.40f, style = PlatformStyle.BLOCK, separationStep = 1),
        ),
        // Después de los pinchos (0.60-0.72): hay que agacharse para pasar cuando dispara
        arrowTraps = listOf(
            ArrowTrap(xFraction = 0.80f, heightFraction = 0.75f)
        )
    ),
    Room(
        segments = listOf(
            LevelSegment(SegmentType.FLOOR, 0.35f),
            LevelSegment(SegmentType.SPIKES, 0.10f),
            LevelSegment(SegmentType.FLOOR, 0.20f),
            LevelSegment(SegmentType.PIT, 0.10f),
            LevelSegment(SegmentType.FLOOR, 0.25f),
        )
    ),
    Room(
        segments = listOf(
            LevelSegment(SegmentType.FLOOR, 0.50f),
            LevelSegment(SegmentType.PIT, 0.15f),
            LevelSegment(SegmentType.FLOOR, 0.35f),
        )
    ),
    Room(
        segments = listOf(
            LevelSegment(SegmentType.FLOOR, 0.30f),
            LevelSegment(SegmentType.SPIKES, 0.15f),
            LevelSegment(SegmentType.FLOOR, 0.15f),
            LevelSegment(SegmentType.SPIKES, 0.15f),
            LevelSegment(SegmentType.FLOOR, 0.25f),
        )
    ),
    Room(
        segments = listOf(
            LevelSegment(SegmentType.FLOOR, 0.40f),
            LevelSegment(SegmentType.PIT, 0.20f),
            LevelSegment(SegmentType.FLOOR, 0.40f),
        )
    ),
    // Sala 6: piso seguro con un saliente alto para colgarse: saltá debajo, agarrate y subí con ⤴ (⬇ para soltarte)
    Room(
        segments = listOf(
            LevelSegment(SegmentType.FLOOR, 1.00f),
        ),
        platforms = listOf(
            Platform(xFraction = 0.42f, widthFraction = 0.10f, heightFraction = 1.1f, style = PlatformStyle.LEDGE),
        )
    ),
)

fun segmentAt(level: List<LevelSegment>, x: Float, screenWidth: Float): LevelSegment {
    var start = 0f
    for (segment in level) {
        val width = segment.widthFraction * screenWidth
        if (x < start + width) return segment
        start += width
    }
    return level.last()
}