package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.MainActivity
import com.example.model.LoyaltyCard

object ShortcutHelper {

    const val EXTRA_CARD_ID = "extra_card_id"
    const val ACTION_OPEN_CARD = "com.example.mescartes.OPEN_CARD"

    fun createOpenCardIntent(context: Context, cardId: Long): Intent {
        return Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_CARD
            data = android.net.Uri.parse("mescartes://card/$cardId")
            putExtra(EXTRA_CARD_ID, cardId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    }

    fun isPinSupported(context: Context): Boolean =
        ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    fun pinCardShortcut(context: Context, card: LoyaltyCard): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            return false
        }
        val shortcut = buildShortcutInfo(context, card)
        return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }

    fun updateDynamicShortcuts(context: Context, topCards: List<LoyaltyCard>) {
        runCatching {
            val shortcuts = topCards.take(4).map { card ->
                buildShortcutInfo(context, card)
            }
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        }
    }

    private fun buildShortcutInfo(context: Context, card: LoyaltyCard): ShortcutInfoCompat {
        val iconBitmap = createCardShortcutBitmap(card.storeName, card.colorArgb.toInt(), card.logoPath)
        return ShortcutInfoCompat.Builder(context, "card_${card.id}")
            .setShortLabel(card.storeName.take(18))
            .setLongLabel("Carte ${card.storeName}")
            .setIcon(IconCompat.createWithAdaptiveBitmap(iconBitmap))
            .setIntent(createOpenCardIntent(context, card.id))
            .build()
    }

    private fun createCardShortcutBitmap(
        storeName: String,
        brandColor: Int,
        logoPath: String?
    ): Bitmap {
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Adaptive icon background fill
        canvas.drawColor(brandColor)

        val logoBitmap = logoPath?.let { path ->
            runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
        }
        if (logoBitmap != null) {
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
            }
            val badgeRect = RectF(44f, 44f, size - 44f, size - 44f)
            canvas.drawRoundRect(badgeRect, 24f, 24f, badgePaint)

            val pad = 14f
            val innerWidth = (badgeRect.width() - pad * 2)
            val innerHeight = (badgeRect.height() - pad * 2)
            val scale = minOf(innerWidth / logoBitmap.width, innerHeight / logoBitmap.height)
            val drawW = logoBitmap.width * scale
            val drawH = logoBitmap.height * scale
            val left = (size - drawW) / 2f
            val top = (size - drawH) / 2f
            val destRect = RectF(left, top, left + drawW, top + drawH)
            canvas.drawBitmap(
                logoBitmap,
                Rect(0, 0, logoBitmap.width, logoBitmap.height),
                destRect,
                Paint(Paint.FILTER_BITMAP_FLAG)
            )
            logoBitmap.recycle()
            return bitmap
        }

        // Subtle inner card outline
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(42, 255, 255, 255)
        }
        canvas.drawRoundRect(
            RectF(36f, 52f, size - 36f, size - 52f),
            18f,
            18f,
            cardPaint
        )

        val initial = storeName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "C"
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 72f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val fontMetrics = textPaint.fontMetrics
        val baseline = size / 2f - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText(initial, size / 2f, baseline, textPaint)

        return bitmap
    }
}
