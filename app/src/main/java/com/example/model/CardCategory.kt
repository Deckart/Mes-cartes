package com.example.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

@Serializable
enum class CardCategory(
    val labelFr: String,
    val defaultColorArgb: Long,
    val icon: ImageVector
) {
    ALIMENTATION(
        labelFr = "Alimentation",
        defaultColorArgb = 0xFF1E6B52L,
        icon = Icons.Rounded.LocalGroceryStore
    ),
    BRICOLAGE(
        labelFr = "Bricolage",
        defaultColorArgb = 0xFFC85A32L,
        icon = Icons.Rounded.Build
    ),
    MODE(
        labelFr = "Mode",
        defaultColorArgb = 0xFF5B3758L,
        icon = Icons.Rounded.Checkroom
    ),
    BEAUTE(
        labelFr = "Beauté",
        defaultColorArgb = 0xFFA63A50L,
        icon = Icons.Rounded.AutoAwesome
    ),
    SPORT(
        labelFr = "Sport",
        defaultColorArgb = 0xFF1D4ED8L,
        icon = Icons.Rounded.FitnessCenter
    ),
    CULTURE(
        labelFr = "Culture",
        defaultColorArgb = 0xFFB47B2BL,
        icon = Icons.Rounded.MenuBook
    ),
    AUTRE(
        labelFr = "Autre",
        defaultColorArgb = 0xFF1B2A4AL,
        icon = Icons.Rounded.CreditCard
    )
}
