package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.BrandSuggestionHelper
import com.example.model.BarcodeFormatType
import com.example.model.CardCategory
import com.example.model.LoyaltyCard
import com.example.ui.components.BarcodeScannerView
import com.example.ui.components.DeleteCardConfirmationDialog
import com.example.ui.components.MiniBarcodePreview
import com.example.ui.theme.CardBrandPalette
import com.example.ui.viewmodel.WalletViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private enum class PhotoSide { FRONT, BACK }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditCardScreen(
    viewModel: WalletViewModel,
    editingCardId: Long?,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var existingCard by remember { mutableStateOf<LoyaltyCard?>(null) }
    var isEditingLoaded by remember { mutableStateOf(editingCardId == null) }

    var scannerMode by remember { mutableStateOf(editingCardId == null) }
    var scanDetectedBanner by remember { mutableStateOf(false) }

    var storeName by remember { mutableStateOf("") }
    var barcodeValue by remember { mutableStateOf("") }
    var barcodeFormat by remember { mutableStateOf(BarcodeFormatType.EAN_13) }
    var selectedColorArgb by remember { mutableLongStateOf(CardCategory.ALIMENTATION.defaultColorArgb) }
    var selectedCategory by remember { mutableStateOf(CardCategory.ALIMENTATION) }
    var note by remember { mutableStateOf("") }
    var logoPath by remember { mutableStateOf<String?>(null) }
    var frontPhotoPath by remember { mutableStateOf<String?>(null) }
    var backPhotoPath by remember { mutableStateOf<String?>(null) }
    var isSynced by remember { mutableStateOf(true) }

    var customDomainInput by remember { mutableStateOf("") }
    var showDomainInput by remember { mutableStateOf(false) }
    var isFetchingLogo by remember { mutableStateOf(false) }
    var logoStatusMessage by remember { mutableStateOf<String?>(null) }
    var userCustomizedLogo by remember { mutableStateOf(false) }

    var userCustomizedColor by remember { mutableStateOf(editingCardId != null) }
    var userCustomizedCategory by remember { mutableStateOf(editingCardId != null) }
    var storeNameError by remember { mutableStateOf(false) }
    var barcodeError by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(editingCardId) {
        if (editingCardId != null) {
            val loaded = viewModel.getCardOnce(editingCardId)
            existingCard = loaded
            if (loaded != null) {
                storeName = loaded.storeName
                barcodeValue = loaded.barcodeValue
                barcodeFormat = loaded.barcodeFormat
                selectedColorArgb = loaded.colorArgb
                selectedCategory = loaded.category
                note = loaded.note
                logoPath = loaded.logoPath
                frontPhotoPath = loaded.frontPhotoPath
                backPhotoPath = loaded.backPhotoPath
                isSynced = loaded.isSynced
                userCustomizedColor = true
                userCustomizedCategory = true
                userCustomizedLogo = loaded.logoPath != null
            }
            isEditingLoaded = true
        }
    }

    // Récupération automatique du logo dès que l'utilisateur saisit le nom de l'enseigne
    LaunchedEffect(storeName, isEditingLoaded) {
        val trimmed = storeName.trim()
        if (!isEditingLoaded || userCustomizedLogo || trimmed.length < 3 || logoPath != null) {
            return@LaunchedEffect
        }
        delay(650)
        isFetchingLogo = true
        val fetched = viewModel.fetchStoreLogo(trimmed, customDomainInput.takeIf { it.isNotBlank() })
        isFetchingLogo = false
        if (fetched != null) {
            logoPath = fetched
            logoStatusMessage = "Logo trouvé automatiquement."
        }
    }

    // Gestion capture photo Recto / Verso
    var activePhotoSide by remember { mutableStateOf(PhotoSide.FRONT) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCameraFile
        pendingCameraFile = null
        if (success && file != null) {
            scope.launch {
                val savedPath = viewModel.savePhotoFromFile(file)
                if (savedPath != null) {
                    if (activePhotoSide == PhotoSide.FRONT) {
                        frontPhotoPath = savedPath
                    } else {
                        backPhotoPath = savedPath
                    }
                }
            }
        } else {
            file?.delete()
        }
    }

    val cameraPermissionForPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val (uri, file) = viewModel.createTempCameraUri()
            pendingCameraFile = file
            takePictureLauncher.launch(uri)
        } else {
            viewModel.emitMessage("Autorisez l'appareil photo pour photographier votre carte.")
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val savedPath = viewModel.savePhotoFromUri(uri)
                if (savedPath != null) {
                    if (activePhotoSide == PhotoSide.FRONT) {
                        frontPhotoPath = savedPath
                    } else {
                        backPhotoPath = savedPath
                    }
                }
            }
        }
    }

    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isFetchingLogo = true
                val savedLogo = viewModel.saveLogoFromUri(uri)
                isFetchingLogo = false
                if (savedLogo != null) {
                    logoPath = savedLogo
                    userCustomizedLogo = true
                    logoStatusMessage = "Image importée comme logo."
                } else {
                    logoStatusMessage = "Impossible d'importer cette image."
                }
            }
        }
    }

    fun triggerManualLogoFetch() {
        val cleanName = storeName.trim()
        val cleanDomain = customDomainInput.trim()
        if (cleanName.isEmpty() && cleanDomain.isEmpty()) {
            logoStatusMessage = "Saisissez d'abord le nom du magasin ou son site web."
            return
        }
        scope.launch {
            isFetchingLogo = true
            logoStatusMessage = "Recherche du logo…"
            val fetched = viewModel.fetchStoreLogo(
                storeName = cleanName.ifEmpty { cleanDomain },
                customDomain = cleanDomain.takeIf { it.isNotBlank() }
            )
            isFetchingLogo = false
            if (fetched != null) {
                logoPath = fetched
                userCustomizedLogo = true
                showDomainInput = false
                logoStatusMessage = "Logo récupéré avec succès."
            } else {
                showDomainInput = true
                logoStatusMessage = "Logo introuvable automatiquement. Précisez le site ou importez une image."
            }
        }
    }

    fun launchPhotoCapture(side: PhotoSide) {
        activePhotoSide = side
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            val (uri, file) = viewModel.createTempCameraUri()
            pendingCameraFile = file
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionForPhotoLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun launchGalleryPicker(side: PhotoSide) {
        activePhotoSide = side
        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (editingCardId == null) "Nouvelle carte" else "Modifier la carte",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("add_edit_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Retour"
                        )
                    }
                },
                actions = {
                    if (existingCard != null) {
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.testTag("add_edit_delete_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Supprimer la carte",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 8.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = {
                            val cleanName = storeName.trim()
                            val cleanCode = barcodeValue.trim()
                            storeNameError = cleanName.isEmpty()
                            barcodeError = cleanCode.isEmpty()
                            if (!storeNameError && !barcodeError) {
                                val now = System.currentTimeMillis()
                                val targetCard = LoyaltyCard(
                                    id = existingCard?.id ?: 0L,
                                    storeName = cleanName,
                                    barcodeValue = cleanCode,
                                    barcodeFormat = barcodeFormat,
                                    colorArgb = selectedColorArgb,
                                    category = selectedCategory,
                                    note = note.trim(),
                                    logoPath = logoPath,
                                    frontPhotoPath = frontPhotoPath,
                                    backPhotoPath = backPhotoPath,
                                    usageCount = existingCard?.usageCount ?: 0,
                                    lastUsedAt = existingCard?.lastUsedAt ?: 0L,
                                    createdAt = existingCard?.createdAt ?: now,
                                    updatedAt = now,
                                    isSynced = isSynced,
                                    remoteId = existingCard?.remoteId
                                )
                                viewModel.saveCard(targetCard, onSaved = onSaved)
                            }
                        },
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("save_card_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(imageVector = Icons.Rounded.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (editingCardId == null) "Enregistrer la carte" else "Mettre à jour",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        if (!isEditingLoaded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Bascule Scanner / Saisie manuelle
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = scannerMode,
                        onClick = {
                            scannerMode = true
                            scanDetectedBanner = false
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.QrCodeScanner,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag("mode_scanner_button")
                    ) {
                        Text("Scanner")
                    }
                    SegmentedButton(
                        selected = !scannerMode,
                        onClick = { scannerMode = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Keyboard,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag("mode_manual_button")
                    ) {
                        Text("Saisie manuelle")
                    }
                }

                // Zone scanner caméra
                AnimatedVisibility(visible = scannerMode) {
                    BarcodeScannerView(
                        onBarcodeDetected = { value, detectedFormat ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            barcodeValue = value
                            barcodeFormat = detectedFormat
                            barcodeError = false
                            scanDetectedBanner = true
                            scannerMode = false
                        },
                        onSwitchToManual = { scannerMode = false }
                    )
                }

                AnimatedVisibility(visible = scanDetectedBanner) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "Code-barres détecté (${barcodeFormat.labelFr}) ! Complétez le nom de l'enseigne.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Nom de l'enseigne (suggère automatiquement couleur & catégorie)
                OutlinedTextField(
                    value = storeName,
                    onValueChange = { next ->
                        storeName = next
                        storeNameError = false
                        if (!userCustomizedColor || !userCustomizedCategory) {
                            val suggestion = BrandSuggestionHelper.suggestFromStoreName(next)
                            if (!userCustomizedColor) selectedColorArgb = suggestion.colorArgb
                            if (!userCustomizedCategory) selectedCategory = suggestion.category
                        }
                    },
                    label = { Text("Nom de l'enseigne *") },
                    placeholder = { Text("ex. Marché Frais, BricoAtelier…") },
                    isError = storeNameError,
                    supportingText = if (storeNameError) {
                        { Text("Veuillez saisir le nom du magasin") }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("store_name_input")
                )

                // Logo du magasin (récupération automatique ou import d'une image)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val currentLogoFile = logoPath?.let { File(it) }?.takeIf { it.exists() }
                            Surface(
                                modifier = Modifier.size(54.dp),
                                shape = RoundedCornerShape(14.dp),
                                color = if (currentLogoFile != null) Color.White else Color(selectedColorArgb.toInt()),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (currentLogoFile != null) {
                                        AsyncImage(
                                            model = currentLogoFile,
                                            contentDescription = "Aperçu du logo",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(6.dp)
                                        )
                                    } else {
                                        val initial = storeName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "C"
                                        Text(
                                            text = initial,
                                            color = Color.White,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (isFetchingLogo) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.35f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(22.dp),
                                                strokeWidth = 2.dp,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Logo du magasin",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = logoStatusMessage ?: if (currentLogoFile != null) {
                                        "Logo prêt pour identifier facilement votre carte."
                                    } else {
                                        "Récupération automatique ou import depuis la galerie."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (currentLogoFile != null) {
                                IconButton(
                                    onClick = {
                                        logoPath = null
                                        userCustomizedLogo = true
                                        logoStatusMessage = "Logo retiré."
                                    },
                                    modifier = Modifier.testTag("clear_logo_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Retirer le logo"
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { triggerManualLogoFetch() },
                                enabled = !isFetchingLogo,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("fetch_logo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ImageSearch,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Rechercher", maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = {
                                    logoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                enabled = !isFetchingLogo,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("import_logo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AddPhotoAlternate,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Importer", maxLines = 1)
                            }
                        }

                        AnimatedVisibility(visible = showDomainInput) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = customDomainInput,
                                    onValueChange = { customDomainInput = it },
                                    label = { Text("Site du magasin (facultatif)") },
                                    placeholder = { Text("ex. carrefour.fr, fnac.com") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("logo_domain_input")
                                )
                                TextButton(
                                    onClick = { triggerManualLogoFetch() },
                                    enabled = !isFetchingLogo && customDomainInput.isNotBlank()
                                ) {
                                    Text("OK")
                                }
                            }
                        }
                    }
                }

                // Numéro de carte + choix du format
                OutlinedTextField(
                    value = barcodeValue,
                    onValueChange = {
                        barcodeValue = it
                        barcodeError = false
                    },
                    label = { Text("Numéro / code de la carte *") },
                    placeholder = { Text(barcodeFormat.hintFr) },
                    isError = barcodeError,
                    supportingText = if (barcodeError) {
                        { Text("Scannez ou saisissez le numéro de la carte") }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("barcode_value_input")
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Format du code-barres",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(end = 8.dp)
                    ) {
                        items(BarcodeFormatType.entries) { format ->
                            FilterChip(
                                selected = barcodeFormat == format,
                                onClick = { barcodeFormat = format },
                                label = { Text(format.labelFr) },
                                modifier = Modifier.testTag("format_${format.name.lowercase()}")
                            )
                        }
                    }
                }

                if (barcodeValue.isNotBlank()) {
                    MiniBarcodePreview(
                        barcodeValue = barcodeValue,
                        formatType = barcodeFormat
                    )
                }

                // Sélecteur de couleur de carte (proposée automatiquement, modifiable)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Couleur de la carte",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        items(CardBrandPalette) { (colorName, argb) ->
                            val isSelected = selectedColorArgb == argb
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(argb.toInt()))
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(
                                                width = 3.dp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                shape = CircleShape
                                            )
                                        } else {
                                            Modifier.border(
                                                width = 1.dp,
                                                color = Color.White.copy(alpha = 0.4f),
                                                shape = CircleShape
                                            )
                                        }
                                    )
                                    .clickable {
                                        selectedColorArgb = argb
                                        userCustomizedColor = true
                                    }
                                    .testTag("color_swatch_$colorName"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = colorName,
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Catégorie
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Catégorie",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CardCategory.entries.forEach { cat ->
                            val selected = selectedCategory == cat
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedCategory = cat
                                    userCustomizedCategory = true
                                    if (!userCustomizedColor) {
                                        selectedColorArgb = cat.defaultColorArgb
                                    }
                                },
                                label = { Text(cat.labelFr) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = cat.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.testTag("pick_category_${cat.name.lowercase()}")
                            )
                        }
                    }
                }

                // Note facultative
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note facultative") },
                    placeholder = { Text("ex. « 15 € de bon d'achat dès 300 points »") },
                    maxLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_note_input")
                )

                // Option de synchronisation avec le couple (permet de garder une carte privée)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSynced) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    },
                    border = BorderStroke(
                        1.dp,
                        if (isSynced) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isSynced = !isSynced }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (isSynced) Icons.Rounded.Sync else Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = if (isSynced) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Synchroniser avec le foyer",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isSynced) {
                                    "Partagée avec les appareils du foyer si la synchronisation est activée."
                                } else {
                                    "Carte privée : reste uniquement sur ce téléphone (ex. beauté, jeux vidéo)."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isSynced,
                            onCheckedChange = { isSynced = it },
                            modifier = Modifier.testTag("card_sync_switch")
                        )
                    }
                }

                // Photos Recto & Verso
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Photos de la carte (facultatif)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PhotoSlotCard(
                            label = "Recto",
                            photoPath = frontPhotoPath,
                            onTakePhoto = { launchPhotoCapture(PhotoSide.FRONT) },
                            onPickGallery = { launchGalleryPicker(PhotoSide.FRONT) },
                            onClear = { frontPhotoPath = null },
                            modifier = Modifier.weight(1f)
                        )
                        PhotoSlotCard(
                            label = "Verso",
                            photoPath = backPhotoPath,
                            onTakePhoto = { launchPhotoCapture(PhotoSide.BACK) },
                            onPickGallery = { launchGalleryPicker(PhotoSide.BACK) },
                            onClear = { backPhotoPath = null },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    val cardToDelete = existingCard
    if (showDeleteConfirm && cardToDelete != null) {
        DeleteCardConfirmationDialog(
            card = cardToDelete,
            onDismiss = { showDeleteConfirm = false },
            onConfirmDelete = {
                showDeleteConfirm = false
                viewModel.deleteCardPermanently(cardToDelete)
                onBack()
            }
        )
    }
}

@Composable
private fun PhotoSlotCard(
    label: String,
    photoPath: String?,
    onTakePhoto: () -> Unit,
    onPickGallery: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSourceMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.height(126.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (photoPath != null) {
                AsyncImage(
                    model = File(photoPath),
                    contentDescription = "Photo $label",
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { showSourceMenu = true },
                    contentScale = ContentScale.Crop
                )
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = label,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                IconButton(
                    onClick = onClear,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Supprimer photo $label",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { showSourceMenu = true }
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Photo $label",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Appareil ou galerie",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            DropdownMenu(
                expanded = showSourceMenu,
                onDismissRequest = { showSourceMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Prendre une photo") },
                    leadingIcon = { Icon(Icons.Rounded.CameraAlt, contentDescription = null) },
                    onClick = {
                        showSourceMenu = false
                        onTakePhoto()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Choisir dans la galerie") },
                    leadingIcon = { Icon(Icons.Rounded.PhotoLibrary, contentDescription = null) },
                    onClick = {
                        showSourceMenu = false
                        onPickGallery()
                    }
                )
            }
        }
    }
}
