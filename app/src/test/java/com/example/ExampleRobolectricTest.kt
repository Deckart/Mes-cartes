package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.BackupCardDto
import com.example.data.BackupPayload
import com.example.data.BrandSuggestionHelper
import com.example.data.DemoCards
import com.example.data.LoyaltyCardRepository
import com.example.data.StoreLogoFetcher
import com.example.data.sync.SharedLoyaltyCard
import com.example.model.BarcodeFormatType
import com.example.model.CardCategory
import com.example.model.LoyaltyCard
import com.example.ui.components.isStoreNameConfirmationMatch
import com.example.util.BarcodeGenerator
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name is Mes Cartes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Mes Cartes", appName)
    }

    @Test
    fun `all demo cards generate crisp barcodes without fallback`() {
        val demoCards = DemoCards.createSampleCards()
        assertEquals(6, demoCards.size)
        demoCards.forEach { card ->
            val result = BarcodeGenerator.generateBarcodeBitmap(
                content = card.barcodeValue,
                formatType = card.barcodeFormat,
                widthPx = if (card.barcodeFormat.is2D) 400 else 600,
                heightPx = if (card.barcodeFormat.is2D) 400 else 220
            )
            assertNotNull("Barcode bitmap should not be null for ${card.storeName}", result.bitmap)
            assertFalse("Demo card ${card.storeName} should encode natively", result.usedFallbackCode128)
        }
    }

    @Test
    fun `invalid ean13 falls back cleanly to code128`() {
        val result = BarcodeGenerator.generateBarcodeBitmap(
            content = "1234567890120", // invalid check digit
            formatType = BarcodeFormatType.EAN_13,
            widthPx = 600,
            heightPx = 220
        )
        assertNotNull(result.bitmap)
        assertTrue(result.usedFallbackCode128)
    }

    @Test
    fun `readable barcode formatting groups digits cleanly`() {
        assertEquals(
            "3  268840  001046",
            BarcodeGenerator.formatReadableNumber("3268840001046", BarcodeFormatType.EAN_13)
        )
        assertEquals(
            "9638  5074",
            BarcodeGenerator.formatReadableNumber("96385074", BarcodeFormatType.EAN_8)
        )
    }

    @Test
    fun `brand suggestion detects category and color`() {
        val brico = BrandSuggestionHelper.suggestFromStoreName("Brico Jardin")
        assertEquals(CardCategory.BRICOLAGE, brico.category)
        val sport = BrandSuggestionHelper.suggestFromStoreName("Montagne Sport")
        assertEquals(CardCategory.SPORT, sport.category)
    }

    @Test
    fun `backup payload json round trip preserves cards and sync flags`() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val payload = BackupPayload(
            cards = listOf(
                BackupCardDto(
                    storeName = "Marché Frais",
                    barcodeValue = "3268840001046",
                    barcodeFormat = BarcodeFormatType.EAN_13,
                    colorArgb = 0xFF1E6B52L,
                    category = CardCategory.ALIMENTATION,
                    note = "10 € dès 200 points",
                    usageCount = 5,
                    isSynced = false
                )
            )
        )
        val encoded = json.encodeToString(payload)
        val decoded = json.decodeFromString<BackupPayload>(encoded)
        assertEquals(1, decoded.cards.size)
        assertEquals("Marché Frais", decoded.cards.first().storeName)
        assertEquals(BarcodeFormatType.EAN_13, decoded.cards.first().barcodeFormat)
        assertEquals(5, decoded.cards.first().usageCount)
        assertFalse(decoded.cards.first().isSynced)
    }

    @Test
    fun `mergeRemoteSnapshot excludes private cards and pulls partner cards`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = LoyaltyCardRepository(context, db.loyaltyCardDao())

            // Carte privée locale (ex. jeux vidéo) + carte partagée locale
            repo.upsertCard(
                LoyaltyCard(
                    storeName = "Pixel Games",
                    barcodeValue = "555000111222",
                    barcodeFormat = BarcodeFormatType.CODE_128,
                    colorArgb = 0xFF3949ABL,
                    category = CardCategory.CULTURE,
                    isSynced = false
                )
            )
            repo.upsertCard(
                LoyaltyCard(
                    storeName = "Marché Frais",
                    barcodeValue = "3268840001046",
                    barcodeFormat = BarcodeFormatType.EAN_13,
                    colorArgb = 0xFF1E6B52L,
                    category = CardCategory.ALIMENTATION,
                    isSynced = true,
                    remoteId = "card_local_shared"
                )
            )

            // Carte distante créée par le/la conjoint(e)
            val partnerRemoteCard = SharedLoyaltyCard(
                id = "card_partner_shared",
                ownerId = "partner_uid",
                memberUids = listOf("me_uid", "partner_uid"),
                storeName = "BricoAtelier",
                barcodeValue = "4006381333931",
                barcodeFormat = BarcodeFormatType.EAN_13.name,
                colorArgb = 0xFFD97706L,
                category = CardCategory.BRICOLAGE.name,
                note = "Carte partagée",
                clientUpdatedAt = 1_730_000_000_000L
            )

            val (toUpload, knownIds) = repo.mergeRemoteSnapshot(
                remoteCards = listOf(partnerRemoteCard),
                previouslyKnownRemoteIds = emptySet()
            )

            // La carte privée ne doit JAMAIS être poussée vers Firestore
            assertFalse(toUpload.any { it.storeName == "Pixel Games" })
            // La carte partagée locale absente du cloud doit être poussée
            assertTrue(toUpload.any { it.storeName == "Marché Frais" })
            assertEquals(setOf("card_partner_shared"), knownIds)

            // Les 3 cartes existent en local (la privée est préservée + la carte du conjoint est ajoutée)
            val allLocal = repo.getAllCardsOnce()
            assertEquals(3, allLocal.size)
            assertTrue(allLocal.any { it.storeName == "BricoAtelier" && it.isSynced })
            assertTrue(allLocal.any { it.storeName == "Pixel Games" && !it.isSynced })
        } finally {
            db.close()
        }
    }

    @Test
    fun `store logo fetcher resolves known brands and custom domains`() {
        val leclerc = StoreLogoFetcher.resolveCandidateDomains("E.Leclerc Drive")
        assertTrue(leclerc.contains("e.leclerc"))

        val decathlon = StoreLogoFetcher.resolveCandidateDomains("Décathlon Lyon")
        assertTrue(decathlon.contains("decathlon.fr"))

        val custom = StoreLogoFetcher.resolveCandidateDomains(
            storeName = "Ma Boutique",
            customDomain = "https://www.boutique-locale.fr/fidelite"
        )
        assertEquals("boutique-locale.fr", custom.first())
    }

    @Test
    fun `deleted synced card does not reappear during stale remote snapshot`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = LoyaltyCardRepository(context, db.loyaltyCardDao())
            val insertedId = repo.upsertCard(
                LoyaltyCard(
                    storeName = "Sephora",
                    barcodeValue = "3268840001046",
                    barcodeFormat = BarcodeFormatType.EAN_13,
                    colorArgb = 0xFF111827L,
                    category = CardCategory.BEAUTE,
                    isSynced = true,
                    remoteId = "card_to_delete"
                )
            )
            val savedCard = repo.getCardById(insertedId)!!

            // Suppression locale de la carte synchronisée
            repo.deleteCard(savedCard)
            assertEquals(0, repo.getAllCardsOnce().size)
            assertTrue("card_to_delete" in repo.getPendingRemoteRemovals())

            // Un snapshot Firestore encore non mis à jour contient toujours card_to_delete
            val staleRemote = SharedLoyaltyCard(
                id = "card_to_delete",
                ownerId = "me_uid",
                memberUids = listOf("me_uid"),
                storeName = "Sephora",
                barcodeValue = "3268840001046",
                barcodeFormat = BarcodeFormatType.EAN_13.name,
                colorArgb = 0xFF111827L,
                category = CardCategory.BEAUTE.name,
                clientUpdatedAt = System.currentTimeMillis() + 5_000L
            )

            val (toUpload, knownAfter) = repo.mergeRemoteSnapshot(
                remoteCards = listOf(staleRemote),
                previouslyKnownRemoteIds = setOf("card_to_delete")
            )

            // La carte ne doit NI réapparaître en local NI être ré-uploadée
            assertTrue(repo.getAllCardsOnce().isEmpty())
            assertTrue(toUpload.isEmpty())
            assertFalse("card_to_delete" in knownAfter)

            // Même après confirmation Firestore tant que le snapshot local est en retard
            repo.markRemoteRemovalSynced("card_to_delete", stillPresentInRemote = true)
            repo.mergeRemoteSnapshot(
                remoteCards = listOf(staleRemote),
                previouslyKnownRemoteIds = emptySet()
            )
            assertTrue(repo.getAllCardsOnce().isEmpty())

            // Une fois disparue de Firestore, la tombe est nettoyée
            repo.pruneConfirmedRemovals(emptySet())
            assertTrue(repo.getAllSuppressedRemoteIds().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun `clearAllCards marks synced cards as pending remote deletion`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = LoyaltyCardRepository(context, db.loyaltyCardDao())
            repo.upsertCard(
                LoyaltyCard(
                    storeName = "Carrefour",
                    barcodeValue = "4006381333931",
                    barcodeFormat = BarcodeFormatType.EAN_13,
                    colorArgb = 0xFF1D4ED8L,
                    category = CardCategory.ALIMENTATION,
                    isSynced = true,
                    remoteId = "card_clear_all_1"
                )
            )
            repo.clearAllCards()

            assertTrue(repo.getAllCardsOnce().isEmpty())
            assertTrue("card_clear_all_1" in repo.getPendingRemoteRemovals())
            repo.clearPendingRemoteRemoval("card_clear_all_1")
        } finally {
            db.close()
        }
    }

    @Test
    fun `store name confirmation matching handles case and surrounding spaces safely`() {
        assertTrue(isStoreNameConfirmationMatch("E.Leclerc", "E.Leclerc"))
        assertTrue(isStoreNameConfirmationMatch("E.Leclerc", "  e.leclerc "))
        assertFalse(isStoreNameConfirmationMatch("E.Leclerc", ""))
        assertFalse(isStoreNameConfirmationMatch("E.Leclerc", "Leclerc"))
        assertFalse(isStoreNameConfirmationMatch("Carrefour", "Auchan"))
    }
}
