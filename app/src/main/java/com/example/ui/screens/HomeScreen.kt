package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.R
import com.example.data.AppUpdateInfo
import com.example.data.BackupPayload
import com.example.model.CardCategory
import com.example.model.LoyaltyCard
import com.example.model.SortOption
import com.example.ui.components.ClearAllCardsConfirmationDialog
import com.example.ui.components.DeleteCardConfirmationDialog
import com.example.ui.components.LoyaltyCardVisual
import com.example.ui.viewmodel.WalletUiEvent
import com.example.ui.viewmodel.WalletViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(
    viewModel: WalletViewModel,
    isCoupleSynced: Boolean = false,
    pairedMemberCount: Int = 0,
    onOpenCoupleSync: () -> Unit = {},
    onCardClick: (Long) -> Unit,
    onAddCardClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val context = LocalContext.current
    val uiState by viewModel.homeUiState.collectAsStateWithLifecycle()
    val availableUpdate by viewModel.availableUpdate.collectAsStateWithLifecycle()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showBackupSheet by remember { mutableStateOf(false) }
    var pendingImportPayload by remember { mutableStateOf<BackupPayload?>(null) }
    var pendingDeleteCard by remember { mutableStateOf<LoyaltyCard?>(null) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var showUpdateRepoDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackup(uri)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.inspectBackupFile(uri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is WalletUiEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is WalletUiEvent.PromptBackupImport -> {
                    pendingImportPayload = event.payload
                }
                WalletUiEvent.PromptUpdateRepoSetup -> {
                    showUpdateRepoDialog = true
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            if (uiState.totalCardCount > 0) {
                ExtendedFloatingActionButton(
                    onClick = onAddCardClick,
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.QrCodeScanner,
                            contentDescription = null
                        )
                    },
                    text = { Text("Ajouter une carte") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("fab_add_card")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // En-tête de l'application
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mes Cartes",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val syncSuffix = if (isCoupleSynced) {
                        if (pairedMemberCount > 1) "Synchro foyer ($pairedMemberCount tél.)"
                        else "Synchro foyer active"
                    } else {
                        "Hors connexion"
                    }
                    val countText = when (uiState.totalCardCount) {
                        0 -> "Portefeuille vide • $syncSuffix"
                        1 -> "1 carte enregistrée • $syncSuffix"
                        else -> "${uiState.totalCardCount} cartes • $syncSuffix"
                    }
                    Text(
                        text = countText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isCoupleSynced) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onOpenCoupleSync,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (isCoupleSynced) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                                }
                            )
                            .testTag("couple_sync_button")
                    ) {
                        Icon(
                            imageVector = if (isCoupleSynced) Icons.Rounded.CloudDone else Icons.Rounded.Group,
                            contentDescription = "Synchronisation du foyer",
                            tint = if (isCoupleSynced) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    IconButton(
                        onClick = { showBackupSheet = true },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                            .testTag("backup_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SettingsBackupRestore,
                            contentDescription = "Sauvegarde et options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (uiState.totalCardCount > 0) {
                // Barre de recherche
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::updateSearchQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .testTag("search_cards_input"),
                    placeholder = { Text("Rechercher une enseigne, note…") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        AnimatedVisibility(
                            visible = uiState.searchQuery.isNotEmpty(),
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            IconButton(
                                onClick = { viewModel.updateSearchQuery("") },
                                modifier = Modifier.testTag("clear_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Effacer la recherche"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Barre de tri rapide
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        Icon(
                            imageVector = Icons.Rounded.Sort,
                            contentDescription = "Trier par",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    items(SortOption.entries) { option ->
                        val selected = uiState.sortOption == option
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.selectSortOption(option) },
                            label = { Text(option.labelFr) },
                            leadingIcon = if (selected) {
                                {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            modifier = Modifier.testTag("sort_${option.name.lowercase()}")
                        )
                    }
                }

                // Filtres par catégorie
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = uiState.selectedCategory == null,
                            onClick = { viewModel.selectCategory(null) },
                            label = { Text("Toutes") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("category_all")
                        )
                    }
                    items(CardCategory.entries) { category ->
                        val selected = uiState.selectedCategory == category
                        FilterChip(
                            selected = selected,
                            onClick = {
                                viewModel.selectCategory(if (selected) null else category)
                            },
                            label = { Text(category.labelFr) },
                            leadingIcon = {
                                Icon(
                                    imageVector = category.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.testTag("category_${category.name.lowercase()}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
            }

            // Contenu principal : état vide, aucun résultat, ou grille des cartes
            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize())
                }
                uiState.totalCardCount == 0 -> {
                    EmptyWalletState(
                        onScanFirstCard = onAddCardClick,
                        onRestoreDemo = { viewModel.resetDemoCards() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                uiState.cards.isEmpty() -> {
                    NoSearchResultsState(
                        onResetFilters = {
                            viewModel.updateSearchQuery("")
                            viewModel.selectCategory(null)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 6.dp,
                            bottom = 96.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(
                            items = uiState.cards,
                            key = { _, card -> card.id }
                        ) { index, card ->
                            SwipeableLoyaltyCardItem(
                                card = card,
                                isTopFavorite = uiState.sortOption == SortOption.USAGE && index < 2 && card.usageCount > 0,
                                onClick = { onCardClick(card.id) },
                                onSwipeDelete = { pendingDeleteCard = card },
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                        }
                    }
                }
            }
        }
    }

    if (showBackupSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showBackupSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Sauvegarde & confidentialité",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Vos cartes restent accessibles hors connexion. La synchronisation du foyer est facultative et exclut vos cartes privées.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                ListItem(
                    headlineContent = { Text("Synchronisation du foyer", fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Text(
                            if (isCoupleSynced) "Synchronisation active • Gérer le partage"
                            else "Partager vos cartes au sein du foyer"
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            showBackupSheet = false
                            onOpenCoupleSync()
                        }
                        .testTag("sheet_couple_sync_item")
                )

                Spacer(modifier = Modifier.height(6.dp))

                ListItem(
                    headlineContent = { Text("Exporter une sauvegarde", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Enregistrer toutes vos cartes et photos dans un fichier JSON") },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.Upload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            showBackupSheet = false
                            val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date())
                            exportLauncher.launch("mes-cartes-$stamp.json")
                        }
                        .testTag("export_backup_item")
                )

                Spacer(modifier = Modifier.height(6.dp))

                ListItem(
                    headlineContent = { Text("Importer une sauvegarde", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Restaurer vos cartes depuis un fichier JSON exporté") },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            showBackupSheet = false
                            importLauncher.launch(arrayOf("application/json", "*/*"))
                        }
                        .testTag("import_backup_item")
                )

                Spacer(modifier = Modifier.height(6.dp))

                ListItem(
                    headlineContent = { Text("Vérifier les mises à jour", fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Text("Version installée : v${BuildConfig.VERSION_NAME}")
                    },
                    leadingContent = {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.SystemUpdate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    trailingContent = {
                        TextButton(
                            onClick = {
                                showBackupSheet = false
                                showUpdateRepoDialog = true
                            }
                        ) {
                            Text("Source")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            showBackupSheet = false
                            viewModel.checkForUpdatesManually()
                        }
                        .testTag("check_updates_item")
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            showBackupSheet = false
                            viewModel.resetDemoCards()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("reset_demo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Démo")
                    }
                    OutlinedButton(
                        onClick = {
                            showBackupSheet = false
                            showClearAllConfirm = true
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("clear_all_cards_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tout vider")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    pendingImportPayload?.let { payload ->
        AlertDialog(
            onDismissRequest = { pendingImportPayload = null },
            title = { Text("Importer la sauvegarde") },
            text = {
                Text(
                    "Ce fichier contient ${payload.cards.size} carte(s). Voulez-vous les ajouter à votre portefeuille actuel ou remplacer toutes vos cartes ?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.confirmBackupImport(payload, replaceExisting = false)
                        pendingImportPayload = null
                    }
                ) {
                    Text("Fusionner")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { pendingImportPayload = null }) {
                        Text("Annuler")
                    }
                    TextButton(
                        onClick = {
                            viewModel.confirmBackupImport(payload, replaceExisting = true)
                            pendingImportPayload = null
                        }
                    ) {
                        Text("Tout remplacer", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        )
    }

    pendingDeleteCard?.let { cardToDelete ->
        DeleteCardConfirmationDialog(
            card = cardToDelete,
            onDismiss = { pendingDeleteCard = null },
            onConfirmDelete = {
                pendingDeleteCard = null
                viewModel.deleteCardPermanently(cardToDelete)
            }
        )
    }

    if (showClearAllConfirm) {
        ClearAllCardsConfirmationDialog(
            onDismiss = { showClearAllConfirm = false },
            onConfirmClearAll = {
                showClearAllConfirm = false
                viewModel.clearAllCards()
            }
        )
    }

    availableUpdate?.let { update ->
        AppUpdateDialog(
            update = update,
            onDownloadAndInstall = {
                viewModel.dismissAvailableUpdate(rememberDismissed = false)
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(update.apkDownloadUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }.onFailure {
                    viewModel.emitMessage("Impossible d'ouvrir le lien de téléchargement.")
                }
            },
            onDismissLater = {
                viewModel.dismissAvailableUpdate(rememberDismissed = true)
            }
        )
    }

    if (showUpdateRepoDialog) {
        UpdateRepoConfigDialog(
            initialValue = viewModel.getConfiguredUpdateRepo(),
            onDismiss = { showUpdateRepoDialog = false },
            onSave = { rawInput ->
                showUpdateRepoDialog = false
                viewModel.saveUpdateRepoAndCheck(rawInput)
            }
        )
    }
}

@Composable
private fun AppUpdateDialog(
    update: AppUpdateInfo,
    onDownloadAndInstall: () -> Unit,
    onDismissLater: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissLater,
        icon = {
            Icon(
                imageVector = Icons.Rounded.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Mise à jour disponible (v${update.versionName})",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Une nouvelle version de Mes Cartes est prête.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (update.releaseNotes.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = update.releaseNotes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                Text(
                    text = if (update.hasDirectApkAsset) {
                        "Appuyez sur le bouton ci-dessous pour télécharger le fichier, puis touchez « Ouvrir » et « Mettre à jour »."
                    } else {
                        "Appuyez ci-dessous pour ouvrir la nouvelle version et la télécharger."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDownloadAndInstall,
                modifier = Modifier.testTag("download_update_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Télécharger et installer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissLater) {
                Text("Plus tard")
            }
        }
    )
}

@Composable
private fun UpdateRepoConfigDialog(
    initialValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var repoText by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Source des mises à jour") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Indiquez votre dépôt GitHub public (rempli automatiquement quand l'APK est compilé via GitHub Actions) :",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = repoText,
                    onValueChange = { repoText = it },
                    label = { Text("Dépôt GitHub") },
                    placeholder = { Text("ex. pseudo/mes-cartes") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("update_repo_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(repoText) },
                enabled = repoText.isNotBlank()
            ) {
                Text("Vérifier")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SwipeableLoyaltyCardItem(
    card: LoyaltyCard,
    isTopFavorite: Boolean,
    onClick: () -> Unit,
    onSwipeDelete: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                onSwipeDelete()
            }
            // Toujours revenir en place jusqu'à confirmation en saisissant le nom du magasin
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val isSwiping = dismissState.targetValue != SwipeToDismissBoxValue.Settled
            val bgColor by animateColorAsState(
                targetValue = if (isSwiping) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                },
                label = "swipe_bg"
            )
            val iconTint = if (isSwiping) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.586f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(bgColor)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Supprimer",
                        tint = iconTint,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Supprimer",
                        style = MaterialTheme.typography.labelSmall,
                        color = iconTint,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    ) {
        with(sharedTransitionScope) {
            LoyaltyCardVisual(
                card = card,
                isTopFavorite = isTopFavorite,
                onClick = onClick,
                modifier = Modifier
                    .sharedBounds(
                        sharedContentState = rememberSharedContentState(key = "card_${card.id}"),
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                    .testTag("loyalty_card_${card.id}")
            )
        }
    }
}

@Composable
private fun EmptyWalletState(
    onScanFirstCard: () -> Unit,
    onRestoreDemo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_wallet),
                contentDescription = "Portefeuille vide",
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(24.dp)),
                contentScale = ContentScale.Crop
            )
            Text(
                text = "Scannez votre première carte et videz votre portefeuille",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Toutes vos cartes de fidélité sont stockées uniquement sur votre appareil et restent disponibles même sans réseau.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onScanFirstCard,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("empty_scan_first_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(imageVector = Icons.Rounded.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Scanner ma première carte",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            TextButton(onClick = onRestoreDemo) {
                Text("Charger les cartes de démonstration")
            }
        }
    }
}

@Composable
private fun NoSearchResultsState(
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "Aucune carte trouvée",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Essayez un autre nom d'enseigne ou réinitialisez les filtres.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            OutlinedButton(onClick = onResetFilters) {
                Text("Effacer les filtres")
            }
        }
    }
}
