package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "loyalty_cards")
data class LoyaltyCard(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val storeName: String,
    val barcodeValue: String,
    val barcodeFormat: BarcodeFormatType = BarcodeFormatType.EAN_13,
    val colorArgb: Long = CardCategory.AUTRE.defaultColorArgb,
    val category: CardCategory = CardCategory.AUTRE,
    val note: String = "",
    val logoPath: String? = null,
    val frontPhotoPath: String? = null,
    val backPhotoPath: String? = null,
    val usageCount: Int = 0,
    val lastUsedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val isSynced: Boolean = true,
    val remoteId: String? = null
)
