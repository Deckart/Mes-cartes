package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import com.example.data.sync.SharedLoyaltyCard
import com.example.model.BarcodeFormatType
import com.example.model.CardCategory
import com.example.model.LoyaltyCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@Serializable
data class BackupPayload(
    val version: Int = 2,
    val exportedAt: Long = System.currentTimeMillis(),
    val cards: List<BackupCardDto> = emptyList()
)

@Serializable
data class BackupCardDto(
    val storeName: String,
    val barcodeValue: String,
    val barcodeFormat: BarcodeFormatType,
    val colorArgb: Long,
    val category: CardCategory,
    val note: String = "",
    val usageCount: Int = 0,
    val lastUsedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val isSynced: Boolean = true,
    val remoteId: String? = null,
    val logoBase64: String? = null,
    val frontPhotoBase64: String? = null,
    val backPhotoBase64: String? = null
)

class LoyaltyCardRepository(
    private val context: Context,
    private val dao: LoyaltyCardDao
) {
    private val prefs = context.getSharedPreferences("mes_cartes_prefs", Context.MODE_PRIVATE)
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val allCards: Flow<List<LoyaltyCard>> = dao.getAllCards()

    fun getCardByIdFlow(id: Long): Flow<LoyaltyCard?> = dao.getCardByIdFlow(id)

    suspend fun getCardById(id: Long): LoyaltyCard? = dao.getCardById(id)

    suspend fun getAllCardsOnce(): List<LoyaltyCard> = dao.getAllCardsOnce()

    suspend fun getTopUsedCards(limit: Int = 4): List<LoyaltyCard> = dao.getTopUsedCards(limit)

    fun generateRemoteId(): String =
        "card_${UUID.randomUUID().toString().replace("-", "")}"

    suspend fun seedDemoIfFirstLaunch() {
        val alreadySeeded = prefs.getBoolean("demo_seeded_v1", false)
        if (!alreadySeeded) {
            if (dao.countCards() == 0) {
                dao.insertCards(DemoCards.createSampleCards())
            }
            prefs.edit().putBoolean("demo_seeded_v1", true).apply()
        }
    }

    suspend fun resetDemoCards() {
        clearAllPhotos()
        clearAllLogos()
        dao.deleteAllCards()
        dao.insertCards(DemoCards.createSampleCards())
        prefs.edit().putBoolean("demo_seeded_v1", true).apply()
    }

    suspend fun clearAllCards() {
        clearAllPhotos()
        clearAllLogos()
        dao.deleteAllCards()
        prefs.edit().putBoolean("demo_seeded_v1", true).apply()
    }

    suspend fun upsertCard(card: LoyaltyCard): Long {
        val normalized = if (card.isSynced && card.remoteId.isNullOrBlank()) {
            card.copy(remoteId = generateRemoteId())
        } else {
            card
        }
        return if (normalized.id == 0L) {
            dao.insertCard(normalized)
        } else {
            dao.updateCard(normalized)
            normalized.id
        }
    }

    suspend fun deleteCard(card: LoyaltyCard) {
        dao.deleteCard(card)
    }

    suspend fun restoreDeletedCard(card: LoyaltyCard): Long {
        return dao.insertCard(card)
    }

    suspend fun deletePhotoFile(path: String?) = withContext(Dispatchers.IO) {
        if (path.isNullOrBlank()) return@withContext
        runCatching {
            val file = File(path)
            if (file.exists() && file.absolutePath.startsWith(photosDir().absolutePath)) {
                file.delete()
            }
        }
    }

    suspend fun deleteLogoFile(path: String?) = withContext(Dispatchers.IO) {
        if (path.isNullOrBlank()) return@withContext
        runCatching {
            val file = File(path)
            if (file.exists() && file.absolutePath.startsWith(logosDir().absolutePath)) {
                file.delete()
            }
        }
    }

    suspend fun incrementUsage(cardId: Long) {
        dao.incrementUsage(cardId, System.currentTimeMillis())
    }

    suspend fun mergeRemoteSnapshot(
        remoteCards: List<SharedLoyaltyCard>,
        previouslyKnownRemoteIds: Set<String>
    ): Pair<List<LoyaltyCard>, Set<String>> {
        val localCards = dao.getAllCardsOnce()
        val remoteById = remoteCards.associateBy { it.id }
        val currentRemoteIds = remoteById.keys
        val localCardsToUpload = mutableListOf<LoyaltyCard>()

        val remainingLocal = localCards.toMutableList()

        // 1. Associer ou mettre à jour depuis les cartes distantes
        for (remote in remoteCards) {
            val matchByRemoteId = remainingLocal.firstOrNull { it.remoteId == remote.id }
            val matchByContent = if (matchByRemoteId == null) {
                remainingLocal.firstOrNull {
                    it.isSynced &&
                        it.storeName.equals(remote.storeName, ignoreCase = true) &&
                        it.barcodeValue == remote.barcodeValue
                }
            } else null

            val local = matchByRemoteId ?: matchByContent
            if (local == null) {
                val format = runCatching { BarcodeFormatType.valueOf(remote.barcodeFormat) }
                    .getOrDefault(BarcodeFormatType.CODE_128)
                val category = runCatching { CardCategory.valueOf(remote.category) }
                    .getOrDefault(CardCategory.AUTRE)
                val inserted = LoyaltyCard(
                    storeName = remote.storeName,
                    barcodeValue = remote.barcodeValue,
                    barcodeFormat = format,
                    colorArgb = remote.colorArgb,
                    category = category,
                    note = remote.note,
                    createdAt = remote.clientUpdatedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    updatedAt = remote.clientUpdatedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                    isSynced = true,
                    remoteId = remote.id
                )
                dao.insertCard(inserted)
            } else {
                remainingLocal.remove(local)
                if (!local.isSynced) {
                    // L'utilisateur a explicitement rendu cette carte privée sur cet appareil
                    continue
                }
                if (local.remoteId != remote.id) {
                    dao.updateCard(local.copy(remoteId = remote.id))
                }
                if (remote.clientUpdatedAt > local.updatedAt) {
                    val format = runCatching { BarcodeFormatType.valueOf(remote.barcodeFormat) }
                        .getOrDefault(local.barcodeFormat)
                    val category = runCatching { CardCategory.valueOf(remote.category) }
                        .getOrDefault(local.category)
                    dao.updateCard(
                        local.copy(
                            storeName = remote.storeName,
                            barcodeValue = remote.barcodeValue,
                            barcodeFormat = format,
                            colorArgb = remote.colorArgb,
                            category = category,
                            note = remote.note,
                            updatedAt = remote.clientUpdatedAt,
                            remoteId = remote.id
                        )
                    )
                } else if (local.updatedAt > remote.clientUpdatedAt) {
                    localCardsToUpload.add(local.copy(remoteId = remote.id))
                }
            }
        }

        // 2. Traiter les cartes locales synchronisées qui ne sont pas encore dans Firestore
        for (local in remainingLocal) {
            if (!local.isSynced) continue
            val rid = local.remoteId
            if (rid != null && rid in previouslyKnownRemoteIds && rid !in currentRemoteIds) {
                // Supprimée ou passée en privée sur l'autre téléphone
                dao.deleteCard(local)
                deleteLogoFile(local.logoPath)
                deletePhotoFile(local.frontPhotoPath)
                deletePhotoFile(local.backPhotoPath)
            } else {
                val ensured = if (rid.isNullOrBlank()) {
                    val newRid = generateRemoteId()
                    local.copy(remoteId = newRid).also { dao.updateCard(it) }
                } else {
                    local
                }
                localCardsToUpload.add(ensured)
            }
        }

        return localCardsToUpload to currentRemoteIds
    }

    fun getKnownRemoteIds(householdId: String): Set<String> {
        return prefs.getStringSet("known_remote_$householdId", emptySet()) ?: emptySet()
    }

    fun saveKnownRemoteIds(householdId: String, ids: Set<String>) {
        prefs.edit().putStringSet("known_remote_$householdId", ids).apply()
    }

    fun clearKnownRemoteIds(householdId: String) {
        prefs.edit().remove("known_remote_$householdId").apply()
    }

    fun createTempCameraUri(): Pair<Uri, File> {
        val dir = File(context.cacheDir, "camera_cache").apply { mkdirs() }
        val file = File(dir, "capture_${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return uri to file
    }

    suspend fun savePhotoFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val destFile = File(photosDir(), "card_${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                val rawBytes = input.readBytes()
                val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                if (bitmap != null) {
                    val scaled = scaleBitmapIfNeeded(bitmap, 1440)
                    FileOutputStream(destFile).use { out ->
                        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    }
                    if (scaled !== bitmap) scaled.recycle()
                    bitmap.recycle()
                } else {
                    FileOutputStream(destFile).use { out -> out.write(rawBytes) }
                }
            }
            if (destFile.exists() && destFile.length() > 0L) destFile.absolutePath else null
        }.getOrNull()
    }

    suspend fun savePhotoFromFile(sourceFile: File): String? = withContext(Dispatchers.IO) {
        runCatching {
            if (!sourceFile.exists()) return@runCatching null
            val destFile = File(photosDir(), "card_${UUID.randomUUID()}.jpg")
            val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath)
            if (bitmap != null) {
                val scaled = scaleBitmapIfNeeded(bitmap, 1440)
                FileOutputStream(destFile).use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                if (scaled !== bitmap) scaled.recycle()
                bitmap.recycle()
            } else {
                sourceFile.copyTo(destFile, overwrite = true)
            }
            sourceFile.delete()
            if (destFile.exists()) destFile.absolutePath else null
        }.getOrNull()
    }

    suspend fun fetchStoreLogo(storeName: String, customDomain: String? = null): String? =
        withContext(Dispatchers.IO) {
            val pngBytes = StoreLogoFetcher.fetchLogoPngBytes(storeName, customDomain) ?: return@withContext null
            val destFile = File(logosDir(), "logo_${UUID.randomUUID()}.png")
            FileOutputStream(destFile).use { it.write(pngBytes) }
            if (destFile.exists() && destFile.length() > 0L) destFile.absolutePath else null
        }

    suspend fun saveLogoFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val destFile = File(logosDir(), "logo_${UUID.randomUUID()}.png")
            context.contentResolver.openInputStream(uri)?.use { input ->
                val rawBytes = input.readBytes()
                val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return@runCatching null
                val pngBytes = StoreLogoFetcher.encodeLogoPng(bitmap, maxDimension = 320)
                bitmap.recycle()
                if (pngBytes != null) {
                    FileOutputStream(destFile).use { out -> out.write(pngBytes) }
                }
            }
            if (destFile.exists() && destFile.length() > 0L) destFile.absolutePath else null
        }.getOrNull()
    }

    suspend fun exportBackupToUri(uri: Uri): Int = withContext(Dispatchers.IO) {
        val cards = dao.getAllCardsOnce()
        val dtos = cards.map { card ->
            BackupCardDto(
                storeName = card.storeName,
                barcodeValue = card.barcodeValue,
                barcodeFormat = card.barcodeFormat,
                colorArgb = card.colorArgb,
                category = card.category,
                note = card.note,
                usageCount = card.usageCount,
                lastUsedAt = card.lastUsedAt,
                createdAt = card.createdAt,
                updatedAt = card.updatedAt,
                isSynced = card.isSynced,
                remoteId = card.remoteId,
                logoBase64 = encodeLogoBase64(card.logoPath),
                frontPhotoBase64 = encodePhotoBase64(card.frontPhotoPath),
                backPhotoBase64 = encodePhotoBase64(card.backPhotoPath)
            )
        }
        val payload = BackupPayload(cards = dtos)
        val encoded = json.encodeToString(payload)
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(encoded.toByteArray(Charsets.UTF_8))
        } ?: error("Impossible d'ouvrir le fichier de destination.")
        cards.size
    }

    suspend fun parseBackupFromUri(uri: Uri): BackupPayload = withContext(Dispatchers.IO) {
        val content = context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader(Charsets.UTF_8).readText()
        } ?: error("Impossible de lire le fichier sélectionné.")
        json.decodeFromString<BackupPayload>(content)
    }

    suspend fun importBackup(payload: BackupPayload, replaceExisting: Boolean): Int =
        withContext(Dispatchers.IO) {
            if (replaceExisting) {
                clearAllPhotos()
                clearAllLogos()
                dao.deleteAllCards()
            }
            val existing = if (replaceExisting) emptyList() else dao.getAllCardsOnce()
            val cardsToInsert = payload.cards.mapNotNull { dto ->
                val duplicate = !replaceExisting && existing.any {
                    it.storeName.equals(dto.storeName, ignoreCase = true) &&
                        it.barcodeValue == dto.barcodeValue
                }
                if (duplicate) {
                    null
                } else {
                    LoyaltyCard(
                        storeName = dto.storeName.ifBlank { "Enseigne" },
                        barcodeValue = dto.barcodeValue,
                        barcodeFormat = dto.barcodeFormat,
                        colorArgb = dto.colorArgb,
                        category = dto.category,
                        note = dto.note,
                        logoPath = decodeLogoBase64(dto.logoBase64),
                        frontPhotoPath = decodePhotoBase64(dto.frontPhotoBase64),
                        backPhotoPath = decodePhotoBase64(dto.backPhotoBase64),
                        usageCount = dto.usageCount,
                        lastUsedAt = dto.lastUsedAt,
                        createdAt = dto.createdAt,
                        updatedAt = dto.updatedAt,
                        isSynced = dto.isSynced,
                        remoteId = dto.remoteId ?: if (dto.isSynced) generateRemoteId() else null
                    )
                }
            }
            if (cardsToInsert.isNotEmpty()) {
                dao.insertCards(cardsToInsert)
            }
            prefs.edit().putBoolean("demo_seeded_v1", true).apply()
            cardsToInsert.size
        }

    private fun photosDir(): File = File(context.filesDir, "card_photos").apply { mkdirs() }

    private fun logosDir(): File = File(context.filesDir, "card_logos").apply { mkdirs() }

    private fun clearAllPhotos() {
        runCatching {
            photosDir().listFiles()?.forEach { it.delete() }
        }
    }

    private fun clearAllLogos() {
        runCatching {
            logosDir().listFiles()?.forEach { it.delete() }
        }
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDimension && h <= maxDimension) return bitmap
        val ratio = minOf(maxDimension.toFloat() / w, maxDimension.toFloat() / h)
        val newW = (w * ratio).toInt().coerceAtLeast(1)
        val newH = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    private fun encodePhotoBase64(path: String?): String? {
        if (path.isNullOrBlank()) return null
        return runCatching {
            val file = File(path)
            if (!file.exists()) return null
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
            val scaled = scaleBitmapIfNeeded(bitmap, 900)
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 75, baos)
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        }.getOrNull()
    }

    private fun decodePhotoBase64(encoded: String?): String? {
        if (encoded.isNullOrBlank()) return null
        return runCatching {
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            val destFile = File(photosDir(), "card_${UUID.randomUUID()}.jpg")
            FileOutputStream(destFile).use { it.write(bytes) }
            destFile.absolutePath
        }.getOrNull()
    }

    private fun encodeLogoBase64(path: String?): String? {
        if (path.isNullOrBlank()) return null
        return runCatching {
            val file = File(path)
            if (!file.exists()) return null
            Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
        }.getOrNull()
    }

    private fun decodeLogoBase64(encoded: String?): String? {
        if (encoded.isNullOrBlank()) return null
        return runCatching {
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            val destFile = File(logosDir(), "logo_${UUID.randomUUID()}.png")
            FileOutputStream(destFile).use { it.write(bytes) }
            destFile.absolutePath
        }.getOrNull()
    }
}
