package com.nfckeyblock.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nfckeyblock.domain.model.Profile

@Composable
fun ProfilesScreen(
    state: ProfilesUiState,
    onNew: () -> Unit,
    onEdit: (Profile) -> Unit,
    onDelete: (Profile) -> Unit,
    onDraftChange: ((Profile) -> Profile) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    contentPadding: PaddingValues
) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 88.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.profiles, key = { it.id }) { profile ->
                Card(Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text("${profile.emoji}  ${profile.name}") },
                        supportingContent = {
                            Text(
                                buildString {
                                    append("${profile.blockedPackages.size} apps")
                                    if (profile.autoEndMinutes > 0) append(" · máx ${profile.autoEndMinutes} min")
                                    if (profile.blockWebDomains) append(" · web")
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { onEdit(profile) }) { Icon(Icons.Filled.Edit, "Editar") }
                                IconButton(onClick = { onDelete(profile) }) { Icon(Icons.Filled.Delete, "Eliminar") }
                            }
                        }
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onNew,
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("Nuevo perfil") },
            modifier = Modifier.align(Alignment.End).padding(16.dp)
        )
    }

    state.editing?.let { draft ->
        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text(if (draft.id == 0L) "Nuevo perfil" else "Editar perfil") },
            text = {
                Column(Modifier.heightIn(max = 420.dp)) {
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { value -> onDraftChange { it.copy(name = value) } },
                        label = { Text("Nombre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = draft.guardSystemSettings,
                            onCheckedChange = { value -> onDraftChange { it.copy(guardSystemSettings = value) } }
                        )
                        Text("  Proteger los ajustes de accesibilidad", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = draft.blockWebDomains,
                            onCheckedChange = { value -> onDraftChange { it.copy(blockWebDomains = value) } }
                        )
                        Text("  Bloquear también webs en navegadores", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (draft.autoEndMinutes == 0) "Sin duración máxima"
                        else "Termina sola a los ${draft.autoEndMinutes} min",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = draft.autoEndMinutes.toFloat(),
                        onValueChange = { value -> onDraftChange { it.copy(autoEndMinutes = value.toInt()) } },
                        valueRange = 0f..240f,
                        steps = 15
                    )
                    Text(
                        "Las apps del perfil se eligen en la pestaña Apps.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { TextButton(onClick = onSave, enabled = draft.name.isNotBlank()) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar") } }
        )
    }
}
