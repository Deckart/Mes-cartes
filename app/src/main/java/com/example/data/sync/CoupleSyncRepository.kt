package com.example.data.sync

import android.content.Context
import com.example.R
import com.example.model.LoyaltyCard
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

class CoupleSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("L'utilisateur doit être connecté avec Google pour synchroniser.")
    }

    fun observeUserProfile(userId: String): Flow<UserSyncProfile?> {
        val docRef = db.collection("users").document(userId)
        return docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        UserSyncProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else {
                    null
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(firestoreError ?: error, OperationType.GET, docRef.path)
                }
                throw error
            }
    }

    fun observeHousehold(householdId: String): Flow<Household?> {
        val docRef = db.collection("households").document(householdId)
        return docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        Household::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else {
                    null
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser != null) {
                    handleFirestoreError(firestoreError ?: error, OperationType.GET, docRef.path)
                }
                throw error
            }
    }

    fun observeSharedCards(householdId: String, userId: String): Flow<List<SharedLoyaltyCard>> {
        val collectionRef = db.collection("households").document(householdId).collection("cards")
        return collectionRef
            .whereArrayContains("memberUids", userId)
            .snapshots()
            .map { querySnapshot ->
                querySnapshot.toObjects(
                    SharedLoyaltyCard::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, collectionRef.path)
                }
                throw error
            }
    }

    suspend fun createHousehold(name: String = "Portefeuille du foyer"): Result<String> = runCatching {
        val uid = requireUserId()
        val code = generatePairingCode()
        val householdRef = db.collection("households").document(code)
        val payload = mapOf(
            "ownerUid" to uid,
            "joinCode" to code,
            "name" to name.trim().ifBlank { "Portefeuille du foyer" }.take(60),
            "memberUids" to listOf(uid),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            householdRef.set(payload).await()
            upsertUserHousehold(uid, code)
            code
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, householdRef.path)
            throw e
        }
    }

    suspend fun joinHousehold(rawCode: String): Result<String> = runCatching {
        val uid = requireUserId()
        val code = rawCode.trim().uppercase()
        require(code.matches(Regex("^[A-Z0-9_\\-]{4,32}$"))) {
            "Le code doit contenir 4 à 32 caractères alphanumériques."
        }
        val householdRef = db.collection("households").document(code)
        try {
            householdRef.update(
                mapOf(
                    "memberUids" to FieldValue.arrayUnion(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            upsertUserHousehold(uid, code)
            code
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, householdRef.path)
            throw e
        }
    }

    suspend fun leaveHousehold(householdId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val householdRef = db.collection("households").document(householdId)
        try {
            runCatching {
                householdRef.update(
                    mapOf(
                        "memberUids" to FieldValue.arrayRemove(uid),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            upsertUserHousehold(uid, "")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, householdRef.path)
            throw e
        }
    }

    suspend fun upsertSharedCard(
        householdId: String,
        remoteId: String,
        memberUids: List<String>,
        card: LoyaltyCard
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val normalizedMembers = (memberUids + uid).distinct().take(4)
        val docRef = db.collection("households")
            .document(householdId)
            .collection("cards")
            .document(remoteId)

        try {
            val existingSnap = runCatching { docRef.get().await() }.getOrNull()
            if (existingSnap != null && existingSnap.exists()) {
                docRef.update(
                    mapOf(
                        "memberUids" to normalizedMembers,
                        "storeName" to card.storeName.trim().take(100),
                        "barcodeValue" to card.barcodeValue.trim().take(256),
                        "barcodeFormat" to card.barcodeFormat.name,
                        "colorArgb" to card.colorArgb,
                        "category" to card.category.name,
                        "note" to card.note.trim().take(500),
                        "clientUpdatedAt" to card.updatedAt,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "ownerId" to uid,
                        "memberUids" to normalizedMembers,
                        "storeName" to card.storeName.trim().take(100),
                        "barcodeValue" to card.barcodeValue.trim().take(256),
                        "barcodeFormat" to card.barcodeFormat.name,
                        "colorArgb" to card.colorArgb,
                        "category" to card.category.name,
                        "note" to card.note.trim().take(500),
                        "clientUpdatedAt" to card.updatedAt,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun propagateMemberUidsToCards(
        householdId: String,
        memberUids: List<String>
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val normalizedMembers = (memberUids + uid).distinct().take(4)
        val cardsCol = db.collection("households").document(householdId).collection("cards")
        try {
            val docs = cardsCol.whereArrayContains("memberUids", uid).get().await()
            docs.documents.forEach { doc ->
                val current = (doc.get("memberUids") as? List<*>)?.filterIsInstance<String>().orEmpty()
                if (current.toSet() != normalizedMembers.toSet()) {
                    doc.reference.update(
                        mapOf(
                            "memberUids" to normalizedMembers,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, cardsCol.path)
            throw e
        }
    }

    suspend fun deleteSharedCard(householdId: String, remoteId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("households")
            .document(householdId)
            .collection("cards")
            .document(remoteId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    private suspend fun upsertUserHousehold(uid: String, householdId: String) {
        val userRef = db.collection("users").document(uid)
        val snap = runCatching { userRef.get().await() }.getOrNull()
        if (snap != null && snap.exists()) {
            userRef.update(
                mapOf(
                    "householdId" to householdId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            userRef.set(
                mapOf(
                    "userId" to uid,
                    "householdId" to householdId,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    private fun generatePairingCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        return buildString(6) {
            repeat(6) {
                append(alphabet[random.nextInt(alphabet.length)])
            }
        }
    }
}
