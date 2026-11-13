package com.example.data

import com.example.model.BarcodeFormatType
import com.example.model.CardCategory
import com.example.model.LoyaltyCard
import com.example.ui.theme.CardBrandPalette
import kotlin.math.abs

object DemoCards {
    fun createSampleCards(now: Long = System.currentTimeMillis()): List<LoyaltyCard> = listOf(
        LoyaltyCard(
            storeName = "Bio",
            barcodeValue = "3268840001046",
            barcodeFormat = BarcodeFormatType.EAN_13,
            colorArgb = 0xFF1E6B52L,
            category = CardCategory.ALIMENTATION,
            note = "10 € de bon d'achat dès 200 points",
            usageCount = 14,
            lastUsedAt = now - 3_600_000L,
            createdAt = now - 86_400_000L * 20,
            updatedAt = now - 86_400_000L * 20,
            isSynced = true,
            remoteId = "demo_bio"
        ),
        LoyaltyCard(
            storeName = "Sport",
            barcodeValue = "HS-9940218-FR",
            barcodeFormat = BarcodeFormatType.QR_CODE,
            colorArgb = 0xFF1D4ED8L,
            category = CardCategory.SPORT,
            note = "Échange et garantie 2 ans sans ticket",
            usageCount = 6,
            lastUsedAt = now - 86_400_000L,
            createdAt = now - 86_400_000L * 10,
            updatedAt = now - 86_400_000L * 10,
            isSynced = false,
            remoteId = null
        )
    )
}

object BrandSuggestionHelper {
    data class BrandSuggestion(
        val colorArgb: Long,
        val category: CardCategory
    )

    fun suggestFromStoreName(name: String): BrandSuggestion {
        val normalized = name.trim().lowercase()
        if (normalized.isEmpty()) {
            return BrandSuggestion(CardCategory.AUTRE.defaultColorArgb, CardCategory.AUTRE)
        }

        val category = when {
            listOf(
                "marché", "marche", "bio", "super", "hyper", "épicerie", "epicerie",
                "frais", "halles", "primeur", "boulanger", "saveur", "alim", "coop"
            ).any { normalized.contains(it) } -> CardCategory.ALIMENTATION

            listOf(
                "brico", "atelier", "jardin", "bois", "outil", "quincail", "maison", "déco", "deco"
            ).any { normalized.contains(it) } -> CardCategory.BRICOLAGE

            listOf(
                "mode", "vet", "vêt", "couture", "textile", "style", "chauss", "élan", "elan", "prêt"
            ).any { normalized.contains(it) } -> CardCategory.MODE

            listOf(
                "beauté", "beaute", "parfum", "botan", "soin", "éclat", "eclat", "cosm", "pharma", "para"
            ).any { normalized.contains(it) } -> CardCategory.BEAUTE

            listOf(
                "sport", "fit", "gym", "randon", "vélo", "velo", "run", "montagne", "horizon", "outdoor"
            ).any { normalized.contains(it) } -> CardCategory.SPORT

            listOf(
                "livre", "librair", "page", "cultur", "ciné", "cine", "musique", "papet", "jeu", "presse"
            ).any { normalized.contains(it) } -> CardCategory.CULTURE

            else -> CardCategory.AUTRE
        }

        val colorArgb = if (category != CardCategory.AUTRE) {
            category.defaultColorArgb
        } else {
            val paletteIndex = abs(normalized.hashCode()) % CardBrandPalette.size
            CardBrandPalette[paletteIndex].second
        }

        return BrandSuggestion(colorArgb = colorArgb, category = category)
    }
}
