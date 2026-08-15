package com.nfckeyblock.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nfckeyblock.domain.model.Profile
import com.nfckeyblock.util.Format

@Composable
fun HomeScreen(
    ui: HomeUiState,
    accessibilityReady: Boolean,
    nfcReady: Boolean,
    onFixPermissions: () -> Unit,
    onStart: (Long) -> Unit,
    onStop: () -> Unit,
    onRequestEmergency: () -> Unit,
    onCancelEmergency: () -> Unit,
    onConfirmEmergency: () -> Unit,
    contentPadding: PaddingValues
) {
    val blocking = ui.state.isBlocking
    val accent by animateColorAsState(
        if (blocking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
        label = "accent"
    )

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).background(accent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (blocking) Icons.Filled.Lock else Icons.Filled.LockOpen,
                                contentDescription = null,
                                tint = accent
                            )
                        }
                        Spacer(Modifier.size(14.dp))
                        Column {
                            Text(
                                if (blocking) "BLOQUEADO" else "LIBRE",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (blocking) "Perfil: ${ui.state.profileName}" else "Sin restricciones activas",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (blocking) {
                        Spacer(Modifier.height(18.dp))
                        Text(Format.clock(ui.elapsedMillis), style = MaterialTheme.typography.displaySmall)
                        Text(
                            "${ui.state.blockedPackages.size} apps bloqueadas",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        if (ui.hasCards) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Contactless, null, tint = accent)
                                Spacer(Modifier.size(8.dp))
                                Text("Acerca tu tarjeta para terminar", style = MaterialTheme.typography.bodyMedium)
                            }
                        } else {
                            Button(onClick = onStop) { Text("Terminar sesión") }
                        }
                    }
                }
            }
        }

        if (!accessibilityReady) {
            item {
                WarningCard(
                    title = "El bloqueo no está operativo",
                    body = "Activa el servicio de accesibilidad de NFC KeyBlock para que pueda detectar y cubrir las apps bloqueadas.",
                    actionLabel = "Abrir ajustes",
                    onAction = onFixPermissions
                )
            }
        }
        if (!nfcReady) {
            item {
                WarningCard(
                    title = "NFC desactivado",
                    body = "Sin NFC no se pueden leer las tarjetas. Puedes seguir usando los perfiles manualmente.",
                    actionLabel = "Activar NFC",
                    onAction = onFixPermissions
                )
            }
        }

        if (blocking && ui.hasCards) {
            item {
                EmergencyCard(
                    remaining = ui.emergencyRemainingMillis,
                    ready = ui.emergencyReady,
                    onRequest = onRequestEmergency,
                    onCancel = onCancelEmergency,
                    onConfirm = onConfirmEmergency
                )
            }
        }

        if (!blocking) {
            item {
                Text(
                    "Iniciar manualmente",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp)
                )
            }
            items(ui.profiles, key = { it.id }) { profile -> ProfileRow(profile, onStart) }
        }
    }
}

@Composable
private fun ProfileRow(profile: Profile, onStart: (Long) -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("${profile.emoji}  ${profile.name}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${profile.blockedPackages.size} apps",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(
                onClick = { onStart(profile.id) },
                enabled = profile.blockedPackages.isNotEmpty()
            ) { Text("Activar") }
        }
    }
}

@Composable
private fun WarningCard(title: String, body: String, actionLabel: String, onAction: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun EmergencyCard(
    remaining: Long?,
    ready: Boolean,
    onRequest: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("¿Has perdido la tarjeta?", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            when {
                ready -> {
                    Text("El desbloqueo de emergencia ya está disponible.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onConfirm) { Text("Desbloquear ahora") }
                }
                remaining != null -> {
                    Text(
                        "Disponible en ${Format.clock(remaining)}. Puedes cancelarlo si aparece la tarjeta.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onCancel) { Text("Cancelar solicitud") }
                }
                else -> {
                    Text(
                        "Puedes pedir un desbloqueo con retardo. La espera es intencionada: evita rendirse por impulso.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onRequest) { Text("Solicitar desbloqueo") }
                }
            }
        }
    }
}
