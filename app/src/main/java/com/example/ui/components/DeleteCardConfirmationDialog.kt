package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.model.LoyaltyCard

fun isStoreNameConfirmationMatch(expectedStoreName: String, typedInput: String): Boolean {
    val expected = expectedStoreName.trim()
    val typed = typedInput.trim()
    return expected.isNotEmpty() && typed.equals(expected, ignoreCase = true)
}

@Composable
fun DeleteCardConfirmationDialog(
    card: LoyaltyCard,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    var typedName by remember(card.id) { mutableStateOf("") }
    val isMatch = isStoreNameConfirmationMatch(card.storeName, typedName)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        title = {
            Text(
                text = "Supprimer cette carte ?",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color(card.colorArgb.toInt()))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = card.storeName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (card.isSynced) {
                                    "Sera supprimée de cet appareil et du foyer."
                                } else {
                                    "Sera supprimée définitivement de cet appareil."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Text(
                    text = "Par sécurité, saisissez « ${card.storeName} » ci-dessous pour confirmer :",
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = typedName,
                    onValueChange = { typedName = it },
                    label = { Text("Nom du magasin") },
                    placeholder = { Text(card.storeName) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = if (isMatch) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Nom confirmé",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (isMatch) onConfirmDelete()
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("delete_confirm_store_name_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                enabled = isMatch,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.testTag("confirm_delete_card_button")
            ) {
                Text("Supprimer définitivement")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_delete_card_button")
            ) {
                Text("Annuler")
            }
        }
    )
}

@Composable
fun ClearAllCardsConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirmClearAll: () -> Unit
) {
    var typedKeyword by remember { mutableStateOf("") }
    val isMatch = isStoreNameConfirmationMatch("SUPPRIMER", typedKeyword)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        title = {
            Text(
                text = "Vider tout le portefeuille ?",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Toutes vos cartes (y compris celles partagées avec le foyer) seront supprimées définitivement.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Tapez SUPPRIMER pour confirmer :",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = typedKeyword,
                    onValueChange = { typedKeyword = it },
                    label = { Text("Confirmation") },
                    placeholder = { Text("SUPPRIMER") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (isMatch) onConfirmClearAll()
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clear_all_confirm_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmClearAll,
                enabled = isMatch,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.testTag("confirm_clear_all_button")
            ) {
                Text("Tout supprimer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        }
    )
}
