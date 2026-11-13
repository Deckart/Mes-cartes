package com.example.data.sync

import com.example.model.BarcodeFormatType
import com.example.model.CardCategory
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserSyncProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val householdId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Household(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val joinCode: String = "",
    val name: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SharedLoyaltyCard(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val memberUids: List<String> = emptyList(),
    val storeName: String = "",
    val barcodeValue: String = "",
    val barcodeFormat: String = BarcodeFormatType.EAN_13.name,
    val colorArgb: Long = CardCategory.AUTRE.defaultColorArgb,
    val category: String = CardCategory.AUTRE.name,
    val note: String = "",
    val clientUpdatedAt: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
