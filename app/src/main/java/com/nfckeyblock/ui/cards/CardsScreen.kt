package com.nfckeyblock.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nfckeyblock.domain.model.CardAction
import com.nfckeyblock.domain.model.NfcCard

@Composable
fun CardsScreen(
    state: CardsUiState,
    nfcAvailable: Boolean,
    onStartScan: () -> Unit,
    onCancelScan: () -> Unit,
    onLabel: (String) -> Unit,
    onAction: (CardAction) -> Unit,
    onProfile: (Long?) -> Unit,
    onWriteToggle: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDelete: (NfcCard) -> Unit,
    contentPadding: PaddingValues
) {
    LazyColumn(
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
                    Text("Tus llaves físicas", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (nfcAvailable) {
                            "Registra una tarjeta, una pegatina o un llavero NFC. Se guarda solo una huella criptográfica, nunca el identificador en claro."
                        } else {
                            "Este dispositivo no tiene NFC disponible. Puedes usar los perfiles manualmente."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onStartScan, enabled = nfcAvailable) {
                        Icon(Icons.Filled.Contactless, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Registrar tarjeta")
                    }
                }
            }
        }

        items(state.cards, key = { it.id }) { card ->
            Card(Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text(card.label) },
                    supportingContent = {
                        Text(
                            buildString {
                                append(
                                    when (card.action) {
                                        CardAction.TOGGLE -> "Alterna bloqueo"
                                        CardAction.ACTIVATE_ONLY -> "Solo activa"
                                        CardAction.DEACTIVATE_ONLY -> "Solo desactiva"
                                    }
                                )
                                val profile = state.profiles.firstOrNull { it.id == card.profileId }
                                if (profile != null) append(" · ${profile.name}")
                                if (card.tokenFingerprint != null) append(" · token escrito")
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingContent = { Icon(Icons.Filled.Contactless, null) },
                    trailingContent = {
                        IconButton(onClick = { onDelete(card) }) { Icon(Icons.Filled.Delete, "Eliminar") }
                    }
                )
            }
        }

        if (state.cards.isEmpty()) {
            item {
                Text(
                    "Aún no hay ninguna tarjeta registrada.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    when (state.phase) {
        ScanPhase.WAITING -> ScanDialog(
            title = "Acerca la tarjeta",
            body = "Apoya la tarjeta en la parte trasera del teléfono, normalmente cerca de la cámara.",
            onCancel = onCancelScan
        )
        ScanPhase.WAITING_WRITE -> ScanDialog(
            title = "Acércala otra vez",
            body = "Vuelve a apoyar la tarjeta para escribir el token. Mantenla quieta un segundo: si se separa, la escritura falla.",
            onCancel = onCancelScan
        )
        ScanPhase.DETECTED -> RegisterDialog(state, onLabel, onAction, onProfile, onWriteToggle, onSave, onCancelScan)
        else -> Unit
    }
}

@Composable
private fun ScanDialog(title: String, body: String, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(Icons.Filled.Contactless, null) },
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(body)
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator()
            }
        },
        confirmButton = { TextButton(onClick = onCancel) { Text("Cancelar") } }
    )
}

@Composable
private fun RegisterDialog(
    state: CardsUiState,
    onLabel: (String) -> Unit,
    onAction: (CardAction) -> Unit,
    onProfile: (Long?) -> Unit,
    onWriteToggle: (Boolean) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val identity = state.detected ?: return
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (state.detectedAlreadyRegistered) "Tarjeta ya registrada" else "Configurar tarjeta") },
        text = {
            Column {
                Text(
                    "Tecnologías: ${identity.techList.joinToString()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.label,
                    onValueChange = onLabel,
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("Acción", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CardAction.entries.forEach { action ->
                        FilterChip(
                            selected = state.action == action,
                            onClick = { onAction(action) },
                            label = {
                                Text(
                                    when (action) {
                                        CardAction.TOGGLE -> "Alternar"
                                        CardAction.ACTIVATE_ONLY -> "Activar"
                                        CardAction.DEACTIVATE_ONLY -> "Desactivar"
                                    }
                                )
                            }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Perfil", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.profiles.take(4).forEach { profile ->
                        AssistChip(onClick = { onProfile(profile.id) }, label = { Text(profile.name) })
                    }
                }
                if (identity.isWritable) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = state.writeToCard, onCheckedChange = onWriteToggle)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Escribir token en la tarjeta (recomendado: hace que se abra la app sola)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar") } }
    )
}
