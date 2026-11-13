package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.sync.authStateFlow
import com.example.ui.screens.AddEditCardScreen
import com.example.ui.screens.CheckoutCardScreen
import com.example.ui.screens.CoupleSyncScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.MesCartesTheme
import com.example.ui.viewmodel.CoupleSyncViewModel
import com.example.ui.viewmodel.SyncConnectionState
import com.example.ui.viewmodel.WalletViewModel
import com.example.util.ShortcutHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class AddEditCardRoute(val cardId: Long? = null)

@Serializable
data class CheckoutRoute(val cardId: Long)

@Serializable
data object CoupleSyncRoute

class MainActivity : ComponentActivity() {

    private var deepLinkCardId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        extractCardIdFromIntent(intent)?.let { deepLinkCardId = it }

        setContent {
            MesCartesTheme {
                MesCartesApp(
                    deepLinkCardId = deepLinkCardId,
                    onConsumeDeepLink = { deepLinkCardId = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractCardIdFromIntent(intent)?.let { deepLinkCardId = it }
    }

    private fun extractCardIdFromIntent(intent: Intent?): Long? {
        if (intent == null) return null
        val fromExtra = intent.getLongExtra(ShortcutHelper.EXTRA_CARD_ID, -1L)
        if (fromExtra > 0L) return fromExtra
        val dataId = intent.data?.lastPathSegment?.toLongOrNull()
        return dataId?.takeIf { it > 0L }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MesCartesApp(
    deepLinkCardId: Long?,
    onConsumeDeepLink: () -> Unit,
    viewModel: WalletViewModel = viewModel()
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val homeUiState by viewModel.homeUiState.collectAsStateWithLifecycle()
    val currentUser by remember { Firebase.auth.authStateFlow() }
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    // CoupleSyncViewModel et les listeners Firestore ne sont créés que si l'utilisateur est authentifié
    val coupleSyncViewModel: CoupleSyncViewModel? = currentUser?.let { user ->
        key(user.uid) {
            val syncVm: CoupleSyncViewModel = viewModel(
                key = "couple_sync_${user.uid}",
                factory = CoupleSyncViewModel.factory(
                    context = context,
                    currentUserId = user.uid,
                    onLocalDataUpdated = { viewModel.refreshExternalSurfaces() }
                )
            )
            DisposableEffect(syncVm) {
                syncVm.startBackgroundSync()
                onDispose { syncVm.stopBackgroundSync() }
            }
            syncVm
        }
    }

    val syncState: SyncConnectionState? = if (coupleSyncViewModel != null) {
        coupleSyncViewModel.syncState.collectAsStateWithLifecycle().value
    } else {
        null
    }
    val isSyncBusy: Boolean = if (coupleSyncViewModel != null) {
        coupleSyncViewModel.isBusy.collectAsStateWithLifecycle().value
    } else {
        false
    }
    val syncFeedback: String? = if (coupleSyncViewModel != null) {
        coupleSyncViewModel.feedbackMessage.collectAsStateWithLifecycle().value
    } else {
        null
    }

    val pairedState = syncState as? SyncConnectionState.Paired
    val isCoupleSynced = pairedState != null
    val pairedMemberCount = pairedState?.household?.memberUids?.size ?: 0

    LaunchedEffect(deepLinkCardId) {
        if (deepLinkCardId != null) {
            navController.navigate(CheckoutRoute(cardId = deepLinkCardId)) {
                launchSingleTop = true
            }
            onConsumeDeepLink()
        }
    }

    SharedTransitionLayout {
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            enterTransition = { fadeIn() + scaleIn(initialScale = 0.94f) },
            exitTransition = { fadeOut() },
            popEnterTransition = { fadeIn() },
            popExitTransition = { fadeOut() + scaleOut(targetScale = 0.94f) }
        ) {
            composable<HomeRoute> {
                HomeScreen(
                    viewModel = viewModel,
                    isCoupleSynced = isCoupleSynced,
                    pairedMemberCount = pairedMemberCount,
                    onOpenCoupleSync = {
                        navController.navigate(CoupleSyncRoute) {
                            launchSingleTop = true
                        }
                    },
                    onCardClick = { cardId ->
                        navController.navigate(CheckoutRoute(cardId = cardId))
                    },
                    onAddCardClick = {
                        navController.navigate(AddEditCardRoute(cardId = null))
                    },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this
                )
            }

            composable<CoupleSyncRoute> {
                CoupleSyncScreen(
                    currentUser = currentUser,
                    syncState = syncState,
                    isBusy = isSyncBusy,
                    feedbackMessage = syncFeedback,
                    sharedCardCount = homeUiState.sharedCardCount,
                    privateCardCount = homeUiState.privateCardCount,
                    onCreateHousehold = { name ->
                        coupleSyncViewModel?.createHousehold(name)
                    },
                    onJoinHousehold = { code ->
                        coupleSyncViewModel?.joinHousehold(code)
                    },
                    onLeaveHousehold = {
                        coupleSyncViewModel?.leaveHousehold()
                    },
                    onClearFeedback = {
                        coupleSyncViewModel?.clearFeedbackMessage()
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable<AddEditCardRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<AddEditCardRoute>()
                AddEditCardScreen(
                    viewModel = viewModel,
                    editingCardId = route.cardId,
                    onBack = { navController.popBackStack() },
                    onSaved = { savedCardId ->
                        if (route.cardId == null) {
                            navController.navigate(CheckoutRoute(cardId = savedCardId)) {
                                popUpTo(HomeRoute)
                            }
                        } else {
                            navController.popBackStack()
                        }
                    }
                )
            }

            composable<CheckoutRoute> { backStackEntry ->
                val route = backStackEntry.toRoute<CheckoutRoute>()
                CheckoutCardScreen(
                    cardId = route.cardId,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onEditCard = { cardId ->
                        navController.navigate(AddEditCardRoute(cardId = cardId))
                    },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this
                )
            }
        }
    }
}
