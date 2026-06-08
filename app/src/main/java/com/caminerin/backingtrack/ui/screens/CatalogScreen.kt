package com.caminerin.backingtrack.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.caminerin.backingtrack.model.FAVORITES_ID
import com.caminerin.backingtrack.model.Style
import com.caminerin.backingtrack.model.Track
import com.caminerin.backingtrack.ui.MainViewModel
import com.caminerin.backingtrack.ui.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(vm: MainViewModel, nav: NavController) {
    val query by vm.query.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val premium by vm.premium.collectAsState()

    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    var lockedDialog by remember { mutableStateOf<Track?>(null) }

    val favTracks = vm.styles.flatMap { it.tracks }.filter { favorites.contains(it.id) }

    val sections: List<Style> = remember(query, favorites) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            buildList {
                if (favTracks.isNotEmpty()) add(Style(FAVORITES_ID, "Favoritos", favTracks))
                addAll(vm.styles)
            }
        } else {
            vm.styles.mapNotNull { s ->
                val matched = s.tracks.filter { t ->
                    t.title.lowercase().contains(q) ||
                        t.styleName.lowercase().contains(q) ||
                        t.key.lowercase() == q || t.key.lowercase().startsWith(q) ||
                        t.bpm.toString().contains(q)
                }
                if (matched.isEmpty()) null else s.copy(tracks = matched)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Guitar Jam", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                placeholder = { Text("Buscar por estilo, tono (ej. A) o BPM (ej. 120)") },
            )

            LazyColumn(Modifier.fillMaxSize()) {
                sections.forEach { style ->
                    val isOpen = expanded[style.id] ?: (query.isNotBlank() || style.id == FAVORITES_ID)
                    item(key = "h_${style.id}") {
                        StyleHeader(style, isOpen) { expanded[style.id] = !isOpen }
                    }
                    if (isOpen) {
                        items(style.tracks, key = { "${style.id}_${it.id}" }) { track ->
                            val locked = !track.free && !premium
                            TrackRow(
                                track = track,
                                favorite = favorites.contains(track.id),
                                locked = locked,
                                onFavorite = { vm.toggleFavorite(track) },
                                onClick = {
                                    if (locked) {
                                        lockedDialog = track
                                    } else {
                                        vm.openTrack(track)
                                        nav.navigate(Routes.PLAYER)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    lockedDialog?.let { track ->
        AlertDialog(
            onDismissRequest = { lockedDialog = null },
            title = { Text("Pista de pago") },
            text = {
                Text(
                    "\"${track.title}\" pertenece a un paquete de pago del estilo " +
                        "${track.styleName}. Cómpralo para desbloquear todas sus pistas " +
                        "y las funciones de Tono y BPM."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.unlockPremiumDemo()
                    lockedDialog = null
                }) { Text("Desbloquear (demo)") }
            },
            dismissButton = {
                TextButton(onClick = { lockedDialog = null }) { Text("Cerrar") }
            },
        )
    }
}

@Composable
private fun StyleHeader(style: Style, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (style.id == FAVORITES_ID) {
            Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.primary)
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        }
        Text(
            style.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${style.tracks.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
        Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
    }
}

@Composable
private fun TrackRow(
    track: Track,
    favorite: Boolean,
    locked: Boolean,
    onFavorite: () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(track.title, fontWeight = FontWeight.SemiBold)
                Text(
                    "${track.styleName} • ${track.key} • ${track.bpm} BPM",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (locked) {
                Icon(Icons.Default.Lock, "De pago", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onFavorite) {
                Icon(
                    if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    "Favorito",
                    tint = if (favorite) MaterialTheme.colorScheme.primary else Color.Gray,
                )
            }
        }
    }
}
