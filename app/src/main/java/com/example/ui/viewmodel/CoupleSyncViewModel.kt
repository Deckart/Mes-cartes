package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.AppDatabase
import com.example.data.LoyaltyCardRepository
import com.example.data.sync.CoupleSyncRepository
import com.example.data.sync.Household
import com.example.data.sync.SharedLoyaltyCard
import com.example.data.sync.UserSyncProfile
import com.example.model.LoyaltyCard
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SyncConnectionState {
    data object Loading : SyncConnectionState
    data object ReadyNoHousehold : SyncConnectionState
    data class Paired(
        val household: Household,
        val sharedCardCount: Int
    ) : SyncConnectionState
    data class Error(val message: String) : SyncConnectionState
}

class CoupleSyncViewModel(
    private val localRepo: LoyaltyCardRepository,
    private val syncRepo: CoupleSyncRepository,
    private val currentUserId: String,
    private val onLocalDataUpdated: () -> Unit
) : ViewModel() {

    private val syncMutex = Mutex()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private val profileFlow: StateFlow<UserSyncProfile?> = syncRepo.observeUserProfile(currentUserId)
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val householdFlow: StateFlow<Household?> = profileFlow
        .flatMapLatest { profile ->
            val householdId = profile?.householdId.orEmpty()
            if (householdId.isBlank()) {
                flowOf(null)
            } else {
                syncRepo.observeHousehold(householdId).catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    emit(null)
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val remoteCardsFlow: StateFlow<List<SharedLoyaltyCard>> = householdFlow
        .flatMapLatest { household ->
            if (household == null) {
                flowOf(emptyList())
            } else {
                syncRepo.observeSharedCards(household.id, currentUserId).catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    emit(emptyList())
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val syncState: StateFlow<SyncConnectionState> = combine(
        profileFlow,
        householdFlow,
        remoteCardsFlow
    ) { profile, household, remoteCards ->
        when {
            household != null -> SyncConnectionState.Paired(
                household = household,
                sharedCardCount = remoteCards.size
            )
            profile == null || profile.householdId.isBlank() -> SyncConnectionState.ReadyNoHousehold
            else -> SyncConnectionState.Loading
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = SyncConnectionState.Loading
    )

    private val syncJobs = mutableListOf<kotlinx.coroutines.Job>()

    fun startBackgroundSync() {
        if (syncJobs.isNotEmpty()) return

        // Propagation automatique des membres lorsqu'un(e) conjoint(e) rejoint l'espace
        syncJobs += viewModelScope.launch {
            householdFlow.collect { household ->
                if (household != null && household.memberUids.size > 1) {
                    syncRepo.propagateMemberUidsToCards(household.id, household.memberUids)
                }
            }
        }

        // Synchronisation bidirectionnelle temps réel entre Room et Firestore
        syncJobs += viewModelScope.launch {
            combine(
                householdFlow,
                remoteCardsFlow,
                localRepo.allCards
            ) { household, remoteCards, localCards ->
                Triple(household, remoteCards, localCards)
            }.collect { (household, remoteCards, localCards) ->
                if (household != null) {
                    runSyncCycle(household, remoteCards, localCards)
                }
            }
        }
    }

    fun stopBackgroundSync() {
        syncJobs.forEach { it.cancel() }
        syncJobs.clear()
    }

    private suspend fun runSyncCycle(
        household: Household,
        remoteCards: List<SharedLoyaltyCard>,
        localCards: List<LoyaltyCard>
    ) = syncMutex.withLock {
        val householdId = household.id
        val pendingDeletions = PendingSyncStore.consumePendingDeletions(localRepo, householdId)

        // 1. Supprimer dans Firestore les cartes supprimées ou passées en privées localement
        for (remoteId in pendingDeletions) {
            syncRepo.deleteSharedCard(householdId, remoteId)
        }

        val knownIds = localRepo.getKnownRemoteIds(householdId)
        val (toUpload, latestRemoteIds) = localRepo.mergeRemoteSnapshot(
            remoteCards = remoteCards,
            previouslyKnownRemoteIds = knownIds
        )

        // 2. Pousser les cartes locales synchronisées absentes ou plus récentes
        val extraLocalToPush = localCards.filter { card ->
            card.isSynced && (
                card.remoteId == null ||
                    remoteCards.none { it.id == card.remoteId }
                )
        }
        val combinedToUpload = (toUpload + extraLocalToPush)
            .distinctBy { it.id to it.remoteId }
            .filter { it.remoteId == null || it.remoteId !in pendingDeletions }

        for (card in combinedToUpload) {
            val rid = card.remoteId ?: localRepo.generateRemoteId().also { newId ->
                localRepo.upsertCard(card.copy(remoteId = newId))
            }
            syncRepo.upsertSharedCard(
                householdId = householdId,
                remoteId = rid,
                memberUids = household.memberUids,
                card = card.copy(remoteId = rid)
            )
        }

        val updatedKnownIds = (latestRemoteIds + combinedToUpload.mapNotNull { it.remoteId }) - pendingDeletions
        localRepo.saveKnownRemoteIds(householdId, updatedKnownIds)
        onLocalDataUpdated()
    }

    fun createHousehold(name: String) {
        viewModelScope.launch {
            _isBusy.value = true
            syncRepo.createHousehold(name)
                .onSuccess { code ->
                    _feedbackMessage.value = "Espace foyer créé ! Code d'invitation : $code"
                }
                .onFailure { err ->
                    _feedbackMessage.value = err.localizedMessage ?: "Impossible de créer l'espace partagé."
                }
            _isBusy.value = false
        }
    }

    fun joinHousehold(code: String) {
        viewModelScope.launch {
            _isBusy.value = true
            syncRepo.joinHousehold(code)
                .onSuccess { joinedCode ->
                    _feedbackMessage.value = "Connecté à l'espace partagé $joinedCode !"
                }
                .onFailure { err ->
                    val message = when {
                        err.message?.contains("NOT_FOUND", ignoreCase = true) == true ->
                            "Code introuvable. Vérifiez le code affiché sur l'autre téléphone."
                        err.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true ->
                            "Impossible de rejoindre cet espace (limite atteinte ou accès refusé)."
                        else -> err.localizedMessage ?: "Code invalide ou connexion impossible."
                    }
                    _feedbackMessage.value = message
                }
            _isBusy.value = false
        }
    }

    fun leaveHousehold() {
        val householdId = (syncState.value as? SyncConnectionState.Paired)?.household?.id ?: return
        viewModelScope.launch {
            _isBusy.value = true
            syncRepo.leaveHousehold(householdId)
                .onSuccess {
                    localRepo.clearKnownRemoteIds(householdId)
                    _feedbackMessage.value = "Vous avez quitté l'espace partagé. Vos cartes restent sur cet appareil."
                }
                .onFailure {
                    _feedbackMessage.value = "Impossible de quitter l'espace partagé."
                }
            _isBusy.value = false
        }
    }

    fun clearFeedbackMessage() {
        _feedbackMessage.value = null
    }

    companion object {
        fun factory(
            context: Context,
            currentUserId: String,
            onLocalDataUpdated: () -> Unit
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val appContext = context.applicationContext
                val localRepo = LoyaltyCardRepository(
                    context = appContext,
                    dao = AppDatabase.getInstance(appContext).loyaltyCardDao()
                )
                val databaseId = appContext.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val syncRepo = CoupleSyncRepository(db = db)
                return CoupleSyncViewModel(
                    localRepo = localRepo,
                    syncRepo = syncRepo,
                    currentUserId = currentUserId,
                    onLocalDataUpdated = onLocalDataUpdated
                ) as T
            }
        }
    }
}

object PendingSyncStore {
    private val pendingRemovals = mutableSetOf<String>()

    @Synchronized
    fun markRemovedOrPrivatized(remoteId: String?) {
        if (!remoteId.isNullOrBlank()) {
            pendingRemovals.add(remoteId)
        }
    }

    @Synchronized
    fun unmarkRemoved(remoteId: String?) {
        if (!remoteId.isNullOrBlank()) {
            pendingRemovals.remove(remoteId)
        }
    }

    @Synchronized
    fun consumePendingDeletions(localRepo: LoyaltyCardRepository, householdId: String): Set<String> {
        val copy = pendingRemovals.toSet()
        pendingRemovals.clear()
        return copy
    }
}
