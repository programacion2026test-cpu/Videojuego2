package com.example.princeofpersia.game.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

fun loadBitmapFromAssets(context: Context, assetPath: String): ImageBitmap {
    val original = context.assets.open(assetPath).use { BitmapFactory.decodeStream(it) }
    return original.asImageBitmap()
}

/**
 * Prueba varias rutas posibles y devuelve la primera que exista. Útil cuando un asset pudo quedar
 * en carpetas distintas (ej. "plataformas/" o "fondos/"). Falla con un mensaje claro si no está en ninguna.
 */
fun loadBitmapFromAssetsAny(context: Context, vararg assetPaths: String): ImageBitmap {
    for (path in assetPaths) {
        try {
            return loadBitmapFromAssets(context, path)
        } catch (e: java.io.IOException) {
            // probar la siguiente ruta
        }
    }
    error("No se encontró el asset en ninguna de estas rutas: ${assetPaths.joinToString()}")
}

/** true si el archivo existe en assets/ (sirve para no cerrar la app si falta un sprite opcional). */
fun assetExists(context: Context, assetPath: String): Boolean =
    try {
        context.assets.open(assetPath).close()
        true
    } catch (e: java.io.IOException) {
        false
    }

/**
 * Carga un fondo muy ancho (ej. 8000x666) a resolución COMPLETA y lo corta en
 * [sliceCount] tramos iguales, uno por sala. Cada tramo queda como bitmap chico
 * (evita el límite de textura de la GPU y no pierde calidad como el muestreo).
 */
fun loadBackgroundSlices(context: Context, assetPath: String, sliceCount: Int): List<ImageBitmap> {
    val full = context.assets.open(assetPath).use { BitmapFactory.decodeStream(it) }
        ?: error("No se pudo decodificar $assetPath")
    val sliceWidth = full.width / sliceCount
    val slices = (0 until sliceCount).map { i ->
        Bitmap.createBitmap(full, i * sliceWidth, 0, sliceWidth, full.height).asImageBitmap()
    }
    full.recycle()
    return slices
}

/** Extrae una fila de frames de un sprite sheet en grilla uniforme (formato Universal LPC, 64x64). */
fun loadSpriteSheetRow(
    context: Context, assetPath: String, row: Int, frameCount: Int,
    frameWidth: Int = 64, frameHeight: Int = 64
): List<ImageBitmap> {
    val sheet = context.assets.open(assetPath).use { BitmapFactory.decodeStream(it) }
    return (0 until frameCount).map { col ->
        Bitmap.createBitmap(sheet, col * frameWidth, row * frameHeight, frameWidth, frameHeight)
            .asImageBitmap()
    }
}