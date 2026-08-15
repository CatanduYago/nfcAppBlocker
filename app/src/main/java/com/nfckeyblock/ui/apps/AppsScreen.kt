package com.nfckeyblock.ui.apps

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import com.nfckeyblock.ui.components.AppIcon

@Composable
fun AppsScreen(
    state: AppsUiState,
    onQuery: (String) -> Unit,
    onSelectProfile: (Long) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onToggleSystem: () -> Unit,
    onSelectSuggested: () -> Unit,
    contentPadding: PaddingValues
) {
    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            state.profiles.forEach { profile ->
                FilterChip(
                    selected = profile.id == state.selectedProfileId,
                    onClick = { onSelectProfile(profile.id) },
                    label = { Text("${profile.emoji} ${profile.name}") }
                )
            }
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            placeholder = { Text("Buscar aplicación") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onSelectSuggested) { Text("Marcar sugeridas") }
            TextButton(onClick = onToggleSystem) {
                Text(if (state.showSystemApps) "Ocultar del sistema" else "Ver apps del sistema")
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 24.dp)
        ) {
            items(state.visibleApps, key = { it.packageName }) { app ->
                val checked = app.packageName in state.blocked
                ListItem(
                    leadingContent = { AppIcon(app.packageName, Modifier.size(40.dp)) },
                    headlineContent = { Text(app.label) },
                    supportingContent = {
                        Text(
                            app.packageName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        Switch(checked = checked, onCheckedChange = { onToggle(app.packageName, it) })
                    }
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}
