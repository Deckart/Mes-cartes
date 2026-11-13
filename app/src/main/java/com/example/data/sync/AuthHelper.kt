package com.example.data.sync

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

object GoogleAuthHelper {

    fun attemptAutoSignIn(
        context: Context,
        credentialManager: CredentialManager,
        onAuthSuccess: () -> Unit,
        onUnauthenticated: () -> Unit,
        scope: CoroutineScope
    ) {
        if (Firebase.auth.currentUser != null) {
            onAuthSuccess()
            return
        }
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            onUnauthenticated()
            return
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        scope.launch {
            try {
                val activityContext = context.findActivity() ?: context
                val result = credentialManager.getCredential(activityContext, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    Firebase.auth.signInWithCredential(authCredential).await()
                    onAuthSuccess()
                } else {
                    onUnauthenticated()
                }
            } catch (e: Exception) {
                onUnauthenticated()
            }
        }
    }

    fun signInWithGoogle(
        context: Context,
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onAuthSuccess: () -> Unit,
        onAuthError: (String) -> Unit,
        onAuthCancelled: () -> Unit = {},
        onNoCredentials: (() -> Unit)? = null
    ) {
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            onAuthError("Configuration Google Sign-In introuvable.")
            return
        }

        val activityContext = context.findActivity() ?: context

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val primaryRequest = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        scope.launch {
            try {
                val result = try {
                    credentialManager.getCredential(activityContext, primaryRequest)
                } catch (noCredPrimary: NoCredentialException) {
                    Log.w(
                        "Auth",
                        "GetSignInWithGoogleOption returned NoCredentialException; trying GetGoogleIdOption fallback",
                        noCredPrimary
                    )
                    // Certains appareils / émulateurs renvoient NoCredentialException sur GetSignInWithGoogleOption
                    // alors que GetGoogleIdOption(filterByAuthorizedAccounts = false) ouvre le sélecteur.
                    val fallbackOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(clientId)
                        .setAutoSelectEnabled(false)
                        .build()
                    val fallbackRequest = GetCredentialRequest.Builder()
                        .addCredentialOption(fallbackOption)
                        .build()
                    credentialManager.getCredential(activityContext, fallbackRequest)
                }

                completeFirebaseSignIn(result, onAuthSuccess, onAuthError)
            } catch (e: GetCredentialCancellationException) {
                Log.w("Auth", "Connexion Google annulée ou fermée : ${e.message}", e)
                onAuthCancelled()
            } catch (e: NoCredentialException) {
                Log.w("Auth", "Aucun identifiant Google disponible via Credential Manager : ${e.message}", e)
                if (onNoCredentials != null) {
                    onNoCredentials()
                } else {
                    onAuthError("Le gestionnaire d'identifiants Android n'a trouvé aucun compte disponible. Utilisez la connexion via le navigateur.")
                }
            } catch (e: GetCredentialProviderConfigurationException) {
                Log.e("Auth", "Fournisseur Credential Manager indisponible", e)
                onAuthError("Le service de connexion Google n'est pas disponible sur cet appareil (vérifiez les Services Google Play).")
            } catch (e: GetCredentialUnsupportedException) {
                Log.e("Auth", "Credential Manager non pris en charge", e)
                onAuthError("La connexion Google n'est pas prise en charge via Credential Manager sur cet appareil.")
            } catch (e: GetCredentialInterruptedException) {
                Log.w("Auth", "Connexion Google interrompue", e)
                onAuthError("La connexion a été interrompue. Veuillez réessayer.")
            } catch (e: GetCredentialException) {
                Log.e("Auth", "Échec Credential Manager (${e.type})", e)
                onAuthError("Impossible de se connecter avec Google (${e.localizedMessage ?: e.type}).")
            } catch (e: Exception) {
                Log.e("Auth", "Échec de la connexion Google / Firebase", e)
                onAuthError("Connexion impossible. Vérifiez votre accès Internet et réessayez.")
            }
        }
    }

    /**
     * Repli officiel Firebase Auth OAuth ("google.com") via navigateur / Custom Tab
     * lorsque Credential Manager renvoie NoCredentialException malgré un compte Google déjà présent sur l'appareil.
     */
    fun signInWithGoogleWebOAuth(
        context: Context,
        onAuthSuccess: () -> Unit,
        onAuthError: (String) -> Unit,
        onAuthCancelled: () -> Unit = {}
    ) {
        val activity = context.findActivity()
        if (activity == null) {
            onAuthError("Activité Android introuvable pour lancer la connexion Google.")
            return
        }

        val auth = Firebase.auth
        val pendingTask = auth.pendingAuthResult
        if (pendingTask != null) {
            pendingTask
                .addOnSuccessListener { onAuthSuccess() }
                .addOnFailureListener { err ->
                    handleWebOAuthFailure(err, onAuthError, onAuthCancelled)
                }
            return
        }

        val provider = OAuthProvider.newBuilder("google.com")
            .addCustomParameter("prompt", "select_account")
            .build()

        auth.startActivityForSignInWithProvider(activity, provider)
            .addOnSuccessListener {
                onAuthSuccess()
            }
            .addOnFailureListener { err ->
                handleWebOAuthFailure(err, onAuthError, onAuthCancelled)
            }
    }

    fun checkPendingWebOAuthResult(
        onAuthSuccess: () -> Unit,
        onAuthError: (String) -> Unit
    ) {
        val pendingTask = Firebase.auth.pendingAuthResult ?: return
        pendingTask
            .addOnSuccessListener { onAuthSuccess() }
            .addOnFailureListener { err ->
                Log.w("Auth", "Résultat OAuth en attente en échec", err)
                onAuthError(err.localizedMessage ?: "Échec de la connexion Google via navigateur.")
            }
    }

    private fun handleWebOAuthFailure(
        err: Exception,
        onAuthError: (String) -> Unit,
        onAuthCancelled: () -> Unit
    ) {
        val msg = err.message.orEmpty()
        if (msg.contains("canceled", ignoreCase = true) ||
            msg.contains("cancelled", ignoreCase = true) ||
            msg.contains("closed", ignoreCase = true)
        ) {
            Log.w("Auth", "Connexion Google via navigateur annulée : $msg", err)
            onAuthCancelled()
        } else {
            Log.e("Auth", "Échec de la connexion Google via navigateur", err)
            onAuthError(
                "Impossible de finaliser la connexion Google via le navigateur (${err.localizedMessage ?: "erreur réseau ou navigateur"})."
            )
        }
    }

    private suspend fun completeFirebaseSignIn(
        result: GetCredentialResponse,
        onAuthSuccess: () -> Unit,
        onAuthError: (String) -> Unit
    ) {
        val credential = result.credential
        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
            Firebase.auth.signInWithCredential(authCredential).await()
            onAuthSuccess()
        } else {
            onAuthError("Type d'identifiant Google inattendu.")
        }
    }

    fun signOut(
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onSignOutComplete: () -> Unit = {}
    ) {
        scope.launch {
            withContext(NonCancellable) {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w("Auth", "Impossible de vider l'état Credential Manager : ${e.message}")
                } finally {
                    Firebase.auth.signOut()
                    onSignOutComplete()
                }
            }
        }
    }
}
