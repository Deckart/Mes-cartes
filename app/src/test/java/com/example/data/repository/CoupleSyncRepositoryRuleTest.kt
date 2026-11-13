package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.sync.CoupleSyncRepository
import com.example.model.BarcodeFormatType
import com.example.model.CardCategory
import com.example.model.LoyaltyCard
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class CoupleSyncRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createAndJoinHousehold_allowsSharedCardsBetweenCouple_andBlocksNonMember() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = CoupleSyncRepository(firestore, auth)

        val joinCode = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createHousehold("Notre portefeuille").getOrThrow()
        }
        assertTrue(joinCode.isNotBlank())

        val bobUid = signInTestUser(BOB_EMAIL)
        val bobRepo = CoupleSyncRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.joinHousehold(joinCode).getOrThrow()
        }

        // Alice publishes a shared card
        signInTestUser(ALICE_EMAIL)
        val cardRemoteId = "card_${UUID.randomUUID().toString().replace("-", "")}"
        val card = LoyaltyCard(
            storeName = "Marché Frais",
            barcodeValue = "3268840001046",
            barcodeFormat = BarcodeFormatType.EAN_13,
            colorArgb = 0xFF1E6B52L,
            category = CardCategory.ALIMENTATION,
            note = "10 € dès 200 pts",
            updatedAt = 1_730_000_000_000L,
            isSynced = true,
            remoteId = cardRemoteId
        )

        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.upsertSharedCard(
                householdId = joinCode,
                remoteId = cardRemoteId,
                memberUids = listOf(aliceUid, bobUid),
                card = card
            ).getOrThrow()
        }

        // Bob observes the shared card in real time
        signInTestUser(BOB_EMAIL)
        val bobCards = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeSharedCards(joinCode, bobUid).first { list ->
                list.any { it.id == cardRemoteId }
            }
        }
        assertEquals("Marché Frais", bobCards.first { it.id == cardRemoteId }.storeName)

        // Eve (not in couple) gets no cards via scoped query and is denied direct/household access
        val eveUid = signInTestUser(EVE_EMAIL)
        val eveRepo = CoupleSyncRepository(firestore, auth)
        val eveScopedCards = withTimeout(FLOW_TIMEOUT_MS) {
            eveRepo.observeSharedCards(joinCode, eveUid).first()
        }
        assertTrue(eveScopedCards.isEmpty())

        val eveHouseholdException = runCatching {
            withTimeout(FLOW_TIMEOUT_MS) {
                eveRepo.observeHousehold(joinCode).first()
            }
        }.exceptionOrNull()
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            eveHouseholdException?.unwrapFirestoreException()?.code
        )

        val eveWriteException = eveRepo.upsertSharedCard(
            householdId = joinCode,
            remoteId = cardRemoteId,
            memberUids = listOf(eveUid),
            card = card
        ).exceptionOrNull()
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            eveWriteException?.unwrapFirestoreException()?.code
        )
    }

    @Test
    fun observeSharedCards_unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = CoupleSyncRepository(firestore, auth)

        val exception = runCatching {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeSharedCards("ANYCODE", "any_uid").first()
            }
        }.exceptionOrNull()

        val firestoreException = checkNotNull(exception?.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException, got $exception"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice.couple@test.com"
        const val BOB_EMAIL = "bob.couple@test.com"
        const val EVE_EMAIL = "eve.stranger@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
