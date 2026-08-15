package com.nfckeyblock.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nfckeyblock.domain.model.StatsSummary
import com.nfckeyblock.ui.components.AppIcon
import com.nfckeyblock.util.Format

@Composable
fun StatsScreen(stats: StatsSummary, contentPadding: PaddingValues) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Tiempo concentrado", Format.duration(stats.totalBlockedMillis), Modifier.weight(1f))
                StatTile("Sesiones", stats.sessionCount.toString(), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Racha", "${stats.currentStreakDays} días", Modifier.weight(1f))
                StatTile("Sesión más larga", Format.duration(stats.longestSessionMillis), Modifier.weight(1f))
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Intentos de abrir apps bloqueadas", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${stats.attemptCount} en total",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        val max = stats.topAttempts.maxOfOrNull { it.second } ?: 1
        items(stats.topAttempts, key = { it.first }) { (pkg, hits) ->
            Card(Modifier.fillMaxWidth()) {
                ListItem(
                    leadingContent = { AppIcon(pkg, Modifier.size(36.dp)) },
                    headlineContent = { Text(pkg.substringAfterLast('.')) },
                    supportingContent = {
                        Column {
                            Text("$hits intentos", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { hits.toFloat() / max },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                )
            }
        }
        item {
            Text(
                "El «tiempo de uso evitado» no se estima: Android no ofrece un dato fiable de lo que habrías usado. " +
                    "Lo que ves son medidas reales: tiempo bloqueado e intentos registrados.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}
