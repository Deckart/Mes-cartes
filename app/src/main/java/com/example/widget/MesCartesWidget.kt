package com.example.widget

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.model.LoyaltyCard
import com.example.util.ShortcutHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MesCartesWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val topCards = withContext(Dispatchers.IO) {
            AppDatabase.getInstance(context).loyaltyCardDao().getTopUsedCards(4)
        }

        provideContent {
            GlanceTheme {
                WidgetContent(context = context, cards = topCards)
            }
        }
    }

    @Composable
    private fun WidgetContent(
        context: Context,
        cards: List<LoyaltyCard>
    ) {
        val openHomeIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(20.dp)
                .padding(12.dp)
        ) {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity(openHomeIntent)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mes Cartes",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = "Caisse rapide",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            if (cards.isEmpty()) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .cornerRadius(14.dp)
                        .background(GlanceTheme.colors.secondaryContainer)
                        .clickable(actionStartActivity(openHomeIntent))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Appuyez pour ajouter votre première carte",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSecondaryContainer,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            } else {
                val firstRow = cards.take(2)
                val secondRow = cards.drop(2).take(2)

                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .defaultWeight()
                ) {
                    firstRow.forEachIndexed { index, card ->
                        WidgetCardTile(
                            context = context,
                            card = card,
                            modifier = GlanceModifier.defaultWeight().fillMaxSize()
                        )
                        if (index == 0 && firstRow.size == 2) {
                            Spacer(modifier = GlanceModifier.width(8.dp))
                        }
                    }
                    if (firstRow.size == 1) {
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        Spacer(modifier = GlanceModifier.defaultWeight())
                    }
                }

                if (secondRow.isNotEmpty()) {
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .defaultWeight()
                    ) {
                        secondRow.forEachIndexed { index, card ->
                            WidgetCardTile(
                                context = context,
                                card = card,
                                modifier = GlanceModifier.defaultWeight().fillMaxSize()
                            )
                            if (index == 0 && secondRow.size == 2) {
                                Spacer(modifier = GlanceModifier.width(8.dp))
                            }
                        }
                        if (secondRow.size == 1) {
                            Spacer(modifier = GlanceModifier.width(8.dp))
                            Spacer(modifier = GlanceModifier.defaultWeight())
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun WidgetCardTile(
        context: Context,
        card: LoyaltyCard,
        modifier: GlanceModifier = GlanceModifier
    ) {
        val brandColor = Color(card.colorArgb.toInt())
        val initial = card.storeName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "C"

        Column(
            modifier = modifier
                .cornerRadius(14.dp)
                .background(ColorProvider(brandColor))
                .clickable(actionStartActivity(ShortcutHelper.createOpenCardIntent(context, card.id)))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val logoBitmap = card.logoPath?.let { path ->
                    runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
                }
                if (logoBitmap != null) {
                    Box(
                        modifier = GlanceModifier
                            .size(26.dp)
                            .cornerRadius(7.dp)
                            .background(ColorProvider(Color.White))
                            .padding(3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = ImageProvider(logoBitmap),
                            contentDescription = card.storeName,
                            contentScale = ContentScale.Fit,
                            modifier = GlanceModifier.fillMaxSize()
                        )
                    }
                } else {
                    Box(
                        modifier = GlanceModifier
                            .size(24.dp)
                            .cornerRadius(12.dp)
                            .background(ColorProvider(Color.White.copy(alpha = 0.24f))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initial,
                            style = TextStyle(
                                color = ColorProvider(Color.White),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = card.storeName,
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = card.category.labelFr,
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(Color.White.copy(alpha = 0.85f)),
                    fontSize = 10.sp
                )
            )
        }
    }
}

object MesCartesWidgetUpdater {
    suspend fun updateAll(context: Context) {
        runCatching {
            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(MesCartesWidget::class.java)
            glanceIds.forEach { glanceId ->
                MesCartesWidget().update(context, glanceId)
            }
        }
    }
}
