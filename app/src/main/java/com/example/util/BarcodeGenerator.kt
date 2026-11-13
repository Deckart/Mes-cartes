package com.example.util

import android.graphics.Bitmap
import android.graphics.Color
import com.example.model.BarcodeFormatType
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

data class BarcodeRenderResult(
    val bitmap: Bitmap?,
    val usedFallbackCode128: Boolean = false
)

object BarcodeGenerator {

    fun generateBarcodeBitmap(
        content: String,
        formatType: BarcodeFormatType,
        widthPx: Int,
        heightPx: Int
    ): BarcodeRenderResult {
        val trimmed = content.trim()
        if (trimmed.isEmpty() || widthPx <= 0 || heightPx <= 0) {
            return BarcodeRenderResult(null)
        }

        val primaryAttempt = runCatching {
            encodeMatrix(trimmed, formatType.zxingFormat, widthPx, heightPx)
        }.getOrNull()

        if (primaryAttempt != null) {
            return BarcodeRenderResult(
                bitmap = matrixToBitmap(primaryAttempt),
                usedFallbackCode128 = false
            )
        }

        // Si le format choisi est strict (ex. EAN-13 avec chiffre de contrôle invalide),
        // bascule automatiquement vers Code 128 pour garantir un code-barres scannable à la caisse.
        val fallbackAttempt = runCatching {
            encodeMatrix(trimmed, BarcodeFormat.CODE_128, widthPx, heightPx)
        }.getOrNull()

        return BarcodeRenderResult(
            bitmap = fallbackAttempt?.let { matrixToBitmap(it) },
            usedFallbackCode128 = fallbackAttempt != null
        )
    }

    private fun encodeMatrix(
        content: String,
        format: BarcodeFormat,
        width: Int,
        height: Int
    ): BitMatrix {
        val hints = mapOf(
            EncodeHintType.MARGIN to 2,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        return MultiFormatWriter().encode(content, format, width, height, hints)
    }

    private fun matrixToBitmap(matrix: BitMatrix): Bitmap {
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    fun formatReadableNumber(raw: String, formatType: BarcodeFormatType): String {
        val value = raw.trim()
        return when {
            formatType == BarcodeFormatType.EAN_13 && value.length == 13 && value.all { it.isDigit() } ->
                "${value.substring(0, 1)}  ${value.substring(1, 7)}  ${value.substring(7, 13)}"
            formatType == BarcodeFormatType.EAN_8 && value.length == 8 && value.all { it.isDigit() } ->
                "${value.substring(0, 4)}  ${value.substring(4, 8)}"
            value.all { it.isDigit() } && value.length >= 10 ->
                value.chunked(4).joinToString("  ")
            else -> value
        }
    }
}
