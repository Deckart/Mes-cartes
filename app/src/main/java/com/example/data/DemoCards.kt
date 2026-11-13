package com.example.data

import com.example.model.BarcodeFormatType
import com.example.model.CardCategory
import com.example.model.LoyaltyCard
import com.example.ui.theme.CardBrandPalette
import kotlin.math.abs

object DemoCards {
    fun createSampleCards(now: Long = System.currentTimeMillis()): List<LoyaltyCard> = listOf(
        LoyaltyCard(
            storeName = "Marché Frais",
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
            remoteId = "demo_marche_frais"
        ),
        LoyaltyCard(
            storeName = "BricoAtelier",
            barcodeValue = "3760123456784",
            barcodeFormat = BarcodeFormatType.EAN_13,
            colorArgb = 0xFFC85A32L,
            category = CardCategory.BRICOLAGE,
            note = "Carte Atelier -5 % sur l'outillage",
            usageCount = 9,
            lastUsedAt = now - 86_400_000L,
            createdAt = now - 86_400_000L * 18,
            updatedAt = now - 86_400_000L * 18,
            isSynced = true,
            remoteId = "demo_bricoatelier"
        ),
        LoyaltyCard(
            storeName = "Maison Élan",
            barcodeValue = "ELAN-2026-88412",
            barcodeFormat = BarcodeFormatType.CODE_128,
            colorArgb = 0xFF5B3758L,
            category = CardCategory.MODE,
            note = "Retouches offertes et ventes privées",
            usageCount = 6,
            lastUsedAt = now - 86_400_000L * 3,
            createdAt = now - 86_400_000L * 15,
            updatedAt = now - 86_400_000L * 15,
            isSynced = true,
            remoteId = "demo_maison_elan"
        ),
        LoyaltyCard(
            storeName = "Éclat Botanique",
            barcodeValue = "3017620422003",
            barcodeFormat = BarcodeFormatType.EAN_13,
            colorArgb = 0xFFA63A50L,
            category = CardCategory.BEAUTE,
            note = "Cadeau anniversaire en magasin",
            usageCount = 4,
            lastUsedAt = now - 86_400_000L * 5,
            createdAt = now - 86_400_000L * 12,
            updatedAt = now - 86_400_000L * 12,
            isSynced = false,
            remoteId = null
        ),
        LoyaltyCard(
            storeName = "Horizon Sport",
            barcodeValue = "HS-9940218-FR",
            barcodeFormat = BarcodeFormatType.QR_CODE,
            colorArgb = 0xFF1D4ED8L,
            category = CardCategory.SPORT,
            note = "Échange et garantie 2 ans sans ticket",
            usageCount = 3,
            lastUsedAt = now - 86_400_000L * 7,
            createdAt = now - 86_400_000L * 10,
            updatedAt = now - 86_400_000L * 10,
            isSynced = true,
            remoteId = "demo_horizon_sport"
        ),
        LoyaltyCard(
            storeName = "Librairie Pages",
            barcodeValue = "9782070360024",
            barcodeFormat = BarcodeFormatType.EAN_13,
            colorArgb = 0xFFB47B2BL,
            category = CardCategory.CULTURE,
            note = "15 € offerts dès 300 points cumulés",
            usageCount = 2,
            lastUsedAt = now - 86_400_000L * 9,
            createdAt = now - 86_400_000L * 8,
            updatedAt = now - 86_400_000L * 8,
            isSynced = true,
            remoteId = "demo_librairie_pages"
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
