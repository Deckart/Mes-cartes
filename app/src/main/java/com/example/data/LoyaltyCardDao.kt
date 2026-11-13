package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.model.LoyaltyCard
import kotlinx.coroutines.flow.Flow

@Dao
interface LoyaltyCardDao {

    @Query("SELECT * FROM loyalty_cards ORDER BY usageCount DESC, lastUsedAt DESC, storeName COLLATE NOCASE ASC")
    fun getAllCards(): Flow<List<LoyaltyCard>>

    @Query("SELECT * FROM loyalty_cards ORDER BY usageCount DESC, lastUsedAt DESC, storeName COLLATE NOCASE ASC")
    suspend fun getAllCardsOnce(): List<LoyaltyCard>

    @Query("SELECT * FROM loyalty_cards ORDER BY usageCount DESC, lastUsedAt DESC, storeName COLLATE NOCASE ASC LIMIT :limit")
    suspend fun getTopUsedCards(limit: Int = 4): List<LoyaltyCard>

    @Query("SELECT * FROM loyalty_cards WHERE id = :id")
    fun getCardByIdFlow(id: Long): Flow<LoyaltyCard?>

    @Query("SELECT * FROM loyalty_cards WHERE id = :id")
    suspend fun getCardById(id: Long): LoyaltyCard?

    @Query("SELECT * FROM loyalty_cards WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getCardByRemoteId(remoteId: String): LoyaltyCard?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: LoyaltyCard): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCards(cards: List<LoyaltyCard>)

    @Update
    suspend fun updateCard(card: LoyaltyCard)

    @Delete
    suspend fun deleteCard(card: LoyaltyCard)

    @Query("DELETE FROM loyalty_cards WHERE id = :id")
    suspend fun deleteCardById(id: Long)

    @Query("DELETE FROM loyalty_cards WHERE remoteId = :remoteId")
    suspend fun deleteCardByRemoteId(remoteId: String)

    @Query("DELETE FROM loyalty_cards")
    suspend fun deleteAllCards()

    @Query("UPDATE loyalty_cards SET usageCount = usageCount + 1, lastUsedAt = :timestamp WHERE id = :id")
    suspend fun incrementUsage(id: Long, timestamp: Long)

    @Query("SELECT COUNT(*) FROM loyalty_cards")
    suspend fun countCards(): Int
}
