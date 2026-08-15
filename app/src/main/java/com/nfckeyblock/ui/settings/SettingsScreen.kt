package com.nfckeyblock.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nfckeyblock.util.PermissionStatus

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    permissions: PermissionStatus,
    onOpenAccessibility: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onOpenNfcSettings: () -> Unit,
    onOpenBattery: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onEmergencyDelay: (Int) -> Unit,
    onResumeReboot: (Boolean) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onNotification: (Boolean) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    contentPadding: PaddingValues
) {
    LazyColumn(
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 24.dp
        )
    ) {
        item { SectionTitle("Permisos") }
        item {
            PermissionRow(
                title = "Servicio de accesibilidad",
                subtitle = "Imprescindible. Sin él no hay bloqueo.",
                granted = permissions.accessibilityEnabled,
                onClick = onOpenAccessibility
            )
        }
        item {
            PermissionRow(
                title = "Acceso a datos de uso",
                subtitle = "Opcional. Mejora la detección en algunos fabricantes.",
                granted = permissions.usageStatsGranted,
                onClick = onOpenUsageAccess
            )
        }
        item {
            PermissionRow(
                title = "NFC",
                subtitle = if (permissions.nfcAvailable) "Necesario para las tarjetas." else "No disponible en este dispositivo.",
                granted = permissions.nfcEnabled,
                onClick = onOpenNfcSettings
            )
        }
        item {
            PermissionRow(
                title = "Sin restricciones de batería",
                subtitle = "Evita que el sistema mate el servicio en segundo plano.",
                granted = permissions.batteryUnrestricted,
                onClick = onOpenBattery
            )
        }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionTitle("Comportamiento") }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "Retardo del desbloqueo de emergencia: ${state.settings.emergencyDelayMinutes} min",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = state.settings.emergencyDelayMinutes.toFloat(),
                    onValueChange = { onEmergencyDelay(it.toInt()) },
                    valueRange = 0f..120f,
                    steps = 11,
                    enabled = !state.sessionActive
                )
                if (state.sessionActive) {
                    Text(
                        "No se puede cambiar durante una sesión activa.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        item {
            SwitchRow("Reanudar tras reiniciar", "Mantiene la sesión activa después de apagar el teléfono.",
                state.settings.resumeAfterReboot, onResumeReboot)
        }
        item {
            SwitchRow("Notificación de sesión", "Android exige mostrarla mientras el servicio está activo.",
                state.settings.showBlockNotification, onNotification)
        }
        item { SwitchRow("Vibración", "Confirma el toque de la tarjeta.", state.settings.hapticFeedback, onHaptics) }
        item { SwitchRow("Color dinámico", "Usa la paleta del sistema.", state.settings.useDynamicColor, onDynamicColor) }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionTitle("Privacidad") }
        item {
            ListItem(
                headlineContent = { Text("Qué datos se guardan") },
                supportingContent = { Text("Todo permanece en el dispositivo. Sin cuentas, sin servidores, sin analítica.") },
                modifier = Modifier.clickable(onClick = onOpenPrivacy)
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun PermissionRow(title: String, subtitle: String, granted: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Icon(
                if (granted) Icons.Filled.CheckCircle else Icons.Filled.Error,
                contentDescription = if (granted) "Concedido" else "Pendiente",
                tint = if (granted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
            )
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) }
    )
}
