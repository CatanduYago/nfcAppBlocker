package com.nfckeyblock.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector

enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Inicio", Icons.Filled.Home),
    Apps("apps", "Apps", Icons.Filled.Apps),
    Cards("cards", "Tarjetas", Icons.Filled.Contactless),
    Profiles("profiles", "Perfiles", Icons.Filled.Tune),
    Stats("stats", "Datos", Icons.Filled.BarChart);

    companion object {
        const val SETTINGS_ROUTE = "settings"
        const val ONBOARDING_ROUTE = "onboarding"
    }
}
