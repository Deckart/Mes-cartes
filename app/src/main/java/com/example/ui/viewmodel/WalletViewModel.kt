package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.BackupPayload
import com.example.data.LoyaltyCardRepository
import com.example.model.CardCategory
import com.example.model.LoyaltyCard
import com.example.model.SortOption
import com.example.util.ShortcutHelper
import com.example.widget.MesCartesWidgetUpdater
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class HomeUiState(
    val cards: List<LoyaltyCard> = emptyList(),
    val totalCardCount: Int = 0,
    val sharedCardCount: Int = 0,
    val privateCardCount: Int = 0,
    val searchQuery: String = "",
    val selectedCategory: CardCategory? = null,
    val sortOption: SortOption = SortOption.USAGE,
    val isLoading: Boolean = true
)

sealed interface WalletUiEvent {
    data class ShowMessage(val message: String) : WalletUiEvent
    data class ShowUndoDelete(val card: LoyaltyCard) : WalletUiEvent
    data class PromptBackupImport(val payload: BackupPayload) : WalletUiEvent
}

class WalletViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val repository: LoyaltyCardRepository = LoyaltyCardRepository(
        context = appContext,
        dao = AppDatabase.getInstance(appContext).loyaltyCardDao()
    )

    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow<CardCategory?>(null)
    private val _sortOption = MutableStateFlow(SortOption.USAGE)
    private val _isInitialized = MutableStateFlow(false)

    private val _events = MutableSharedFlow<WalletUiEvent>()
    val events: SharedFlow<WalletUiEvent> = _events.asSharedFlow()

    private val _pendingUndoCard = MutableStateFlow<LoyaltyCard?>(null)
    val pendingUndoCard: StateFlow<LoyaltyCard?> = _pendingUndoCard.asStateFlow()

    val homeUiState: StateFlow<HomeUiState> = combine(
        repository.allCards,
        _searchQuery,
        _selectedCategory,
        _sortOption,
        _isInitialized
    ) { allCards, query, category, sort, initialized ->
        val trimmedQuery = query.trim().lowercase()
        val filtered = allCards.filter { card ->
            val matchesCategory = category == null || card.category == category
            val matchesQuery = trimmedQuery.isEmpty() ||
                card.storeName.lowercase().contains(trimmedQuery) ||
                card.category.labelFr.lowercase().contains(trimmedQuery) ||
                card.note.lowercase().contains(trimmedQuery) ||
                card.barcodeValue.lowercase().contains(trimmedQuery)
            matchesCategory && matchesQuery
        }

        val sorted = when (sort) {
            SortOption.USAGE -> filtered.sortedWith(
                compareByDescending<LoyaltyCard> { it.usageCount }
                    .thenByDescending { it.lastUsedAt }
                    .thenBy { it.storeName.lowercase() }
            )
            SortOption.NAME -> filtered.sortedBy { it.storeName.lowercase() }
            SortOption.CATEGORY -> filtered.sortedWith(
                compareBy<LoyaltyCard> { it.category.labelFr }
                    .thenByDescending { it.usageCount }
                    .thenBy { it.storeName.lowercase() }
            )
        }

        HomeUiState(
            cards = sorted,
            totalCardCount = allCards.size,
            sharedCardCount = allCards.count { it.isSynced },
            privateCardCount = allCards.count { !it.isSynced },
            searchQuery = query,
            selectedCategory = category,
            sortOption = sort,
            isLoading = !initialized
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    init {
        viewModelScope.launch {
            repository.seedDemoIfFirstLaunch()
            _isInitialized.value = true
            syncExternalSurfaces()
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: CardCategory?) {
        _selectedCategory.value = category
    }

    fun selectSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun getCardFlow(cardId: Long): Flow<LoyaltyCard?> = repository.getCardByIdFlow(cardId)

    suspend fun getCardOnce(cardId: Long): LoyaltyCard? = repository.getCardById(cardId)

    fun saveCard(card: LoyaltyCard, onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val previous = if (card.id != 0L) repository.getCardById(card.id) else null
            val updated = card.copy(updatedAt = System.currentTimeMillis())
            if (previous != null && previous.isSynced && !updated.isSynced) {
                PendingSyncStore.markRemovedOrPrivatized(previous.remoteId)
            } else if (updated.isSynced && !updated.remoteId.isNullOrBlank()) {
                PendingSyncStore.unmarkRemoved(updated.remoteId)
            }
            val savedId = repository.upsertCard(updated)
            syncExternalSurfaces()
            onSaved(savedId)
        }
    }

    fun toggleCardSync(card: LoyaltyCard) {
        viewModelScope.launch {
            val nextSynced = !card.isSynced
            if (card.isSynced && !nextSynced) {
                PendingSyncStore.markRemovedOrPrivatized(card.remoteId)
            } else if (nextSynced && !card.remoteId.isNullOrBlank()) {
                PendingSyncStore.unmarkRemoved(card.remoteId)
            }
            repository.upsertCard(
                card.copy(
                    isSynced = nextSynced,
                    updatedAt = System.currentTimeMillis()
                )
            )
            syncExternalSurfaces()
            _events.emit(
                WalletUiEvent.ShowMessage(
                    if (nextSynced) {
                        "« ${card.storeName} » est maintenant partagée avec le foyer."
                    } else {
                        "« ${card.storeName} » est maintenant privée (non synchronisée)."
                    }
                )
            )
        }
    }

    fun recordCardUsed(cardId: Long) {
        viewModelScope.launch {
            repository.incrementUsage(cardId)
            syncExternalSurfaces()
        }
    }

    fun deleteCardWithUndo(card: LoyaltyCard) {
        viewModelScope.launch {
            _pendingUndoCard.value = card
            if (card.isSynced) {
                PendingSyncStore.markRemovedOrPrivatized(card.remoteId)
            }
            repository.deleteCard(card)
            syncExternalSurfaces()
            _events.emit(WalletUiEvent.ShowUndoDelete(card))
        }
    }

    fun undoLastDelete() {
        val card = _pendingUndoCard.value ?: return
        _pendingUndoCard.value = null
        viewModelScope.launch {
            if (card.isSynced) {
                PendingSyncStore.unmarkRemoved(card.remoteId)
            }
            repository.restoreDeletedCard(card)
            syncExternalSurfaces()
        }
    }

    fun refreshExternalSurfaces() {
        viewModelScope.launch {
            syncExternalSurfaces()
        }
    }

    fun commitPendingDeleteIfAny() {
        val card = _pendingUndoCard.value ?: return
        _pendingUndoCard.value = null
        viewModelScope.launch {
            repository.deleteLogoFile(card.logoPath)
            repository.deletePhotoFile(card.frontPhotoPath)
            repository.deletePhotoFile(card.backPhotoPath)
        }
    }

    fun pinCardToHomeScreen(card: LoyaltyCard) {
        viewModelScope.launch {
            if (!ShortcutHelper.isPinSupported(appContext)) {
                _events.emit(WalletUiEvent.ShowMessage("Votre lanceur d'applications ne prend pas en charge l'épinglage de raccourcis."))
                return@launch
            }
            val requested = ShortcutHelper.pinCardShortcut(appContext, card)
            if (requested) {
                _events.emit(WalletUiEvent.ShowMessage("Raccourci « ${card.storeName} » prêt pour l'écran d'accueil."))
            } else {
                _events.emit(WalletUiEvent.ShowMessage("Impossible d'ajouter le raccourci sur l'écran d'accueil."))
            }
        }
    }

    fun createTempCameraUri(): Pair<Uri, File> = repository.createTempCameraUri()

    suspend fun savePhotoFromUri(uri: Uri): String? = repository.savePhotoFromUri(uri)

    suspend fun savePhotoFromFile(file: File): String? = repository.savePhotoFromFile(file)

    suspend fun fetchStoreLogo(storeName: String, customDomain: String? = null): String? =
        repository.fetchStoreLogo(storeName, customDomain)

    suspend fun saveLogoFromUri(uri: Uri): String? = repository.saveLogoFromUri(uri)

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                repository.exportBackupToUri(uri)
            }.onSuccess { count ->
                _events.emit(
                    WalletUiEvent.ShowMessage(
                        if (count <= 1) "Sauvegarde exportée ($count carte)."
                        else "Sauvegarde exportée ($count cartes)."
                    )
                )
            }.onFailure {
                _events.emit(WalletUiEvent.ShowMessage("Échec de l'export de la sauvegarde."))
            }
        }
    }

    fun inspectBackupFile(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                repository.parseBackupFromUri(uri)
            }.onSuccess { payload ->
                if (payload.cards.isEmpty()) {
                    _events.emit(WalletUiEvent.ShowMessage("Ce fichier de sauvegarde ne contient aucune carte."))
                } else {
                    _events.emit(WalletUiEvent.PromptBackupImport(payload))
                }
            }.onFailure {
                _events.emit(WalletUiEvent.ShowMessage("Fichier de sauvegarde invalide ou illisible."))
            }
        }
    }

    fun confirmBackupImport(payload: BackupPayload, replaceExisting: Boolean) {
        viewModelScope.launch {
            runCatching {
                repository.importBackup(payload, replaceExisting)
            }.onSuccess { importedCount ->
                syncExternalSurfaces()
                val msg = when {
                    importedCount == 0 -> "Toutes les cartes de cette sauvegarde sont déjà présentes."
                    importedCount == 1 -> "1 carte importée avec succès."
                    else -> "$importedCount cartes importées avec succès."
                }
                _events.emit(WalletUiEvent.ShowMessage(msg))
            }.onFailure {
                _events.emit(WalletUiEvent.ShowMessage("Impossible d'importer la sauvegarde."))
            }
        }
    }

    fun resetDemoCards() {
        viewModelScope.launch {
            repository.resetDemoCards()
            syncExternalSurfaces()
            _events.emit(WalletUiEvent.ShowMessage("Cartes de démonstration restaurées."))
        }
    }

    fun clearAllCards() {
        viewModelScope.launch {
            repository.clearAllCards()
            syncExternalSurfaces()
            _events.emit(WalletUiEvent.ShowMessage("Toutes les cartes ont été effacées."))
        }
    }

    fun emitMessage(message: String) {
        viewModelScope.launch {
            _events.emit(WalletUiEvent.ShowMessage(message))
        }
    }

    private suspend fun syncExternalSurfaces() {
        val topCards = repository.getTopUsedCards(4)
        ShortcutHelper.updateDynamicShortcuts(appContext, topCards)
        MesCartesWidgetUpdater.updateAll(appContext)
    }
}
