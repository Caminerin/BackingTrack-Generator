package com.caminerin.backingtrack.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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

private val KEY_ORDER = listOf(
    "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B",
)

private fun keyRank(key: String): Int {
    val minor = key.endsWith("m")
    val root = if (minor) key.dropLast(1) else key
    val idx = KEY_ORDER.indexOf(root)
    return (if (idx < 0) 99 else idx) * 2 + if (minor) 1 else 0
}

private data class BpmBucket(val label: String, val min: Int, val max: Int)

private val BPM_BUCKETS = listOf(
    BpmBucket("< 90", 0, 89),
    BpmBucket("90–109", 90, 109),
    BpmBucket("110–129", 110, 129),
    BpmBucket("130–149", 130, 149),
    BpmBucket("150+", 150, 10000),
)

private fun bucketOf(bpm: Int): BpmBucket = BPM_BUCKETS.first { bpm in it.min..it.max }

private val SIG_ORDER = listOf("4/4", "3/4", "6/8", "2/4")
private val FEEL_ORDER = listOf("Straight", "Swing")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(vm: MainViewModel, nav: NavController) {
    val favorites by vm.favorites.collectAsState()
    val premium by vm.premium.collectAsState()

    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    var lockedDialog by remember { mutableStateOf<Track?>(null) }

    val selStyles = remember { mutableStateListOf<String>() }
    val selKeys = remember { mutableStateListOf<String>() }
    val selBpm = remember { mutableStateListOf<String>() }
    val selSig = remember { mutableStateListOf<String>() }
    val selFeel = remember { mutableStateListOf<String>() }

    val allTracks = remember { vm.styles.flatMap { it.tracks } }
    val styleNames = remember { vm.styles.map { it.name } }
    val keyOptions = remember { allTracks.map { it.key }.distinct().sortedBy { keyRank(it) } }
    val bpmOptions = remember {
        BPM_BUCKETS.filter { b -> allTracks.any { it.bpm in b.min..b.max } }.map { it.label }
    }
    val sigOptions = remember {
        SIG_ORDER.filter { sig -> allTracks.any { it.timeSignature == sig } }
    }
    val feelOptions = remember {
        FEEL_ORDER.filter { f -> allTracks.any { it.feel == f } }
    }

    val favTracks = vm.styles.flatMap { it.tracks }.filter { favorites.contains(it.id) }
    val anyFilter = selStyles.isNotEmpty() || selKeys.isNotEmpty() || selBpm.isNotEmpty() ||
        selSig.isNotEmpty() || selFeel.isNotEmpty()

    val sections: List<Style> =
        if (!anyFilter) {
            buildList {
                if (favTracks.isNotEmpty()) add(Style(FAVORITES_ID, "Favoritos", favTracks))
                addAll(vm.styles)
            }
        } else {
            vm.styles.mapNotNull { s ->
                if (selStyles.isNotEmpty() && !selStyles.contains(s.name)) return@mapNotNull null
                val matched = s.tracks.filter { t ->
                    (selKeys.isEmpty() || selKeys.contains(t.key)) &&
                        (selBpm.isEmpty() || selBpm.contains(bucketOf(t.bpm).label)) &&
                        (selSig.isEmpty() || selSig.contains(t.timeSignature)) &&
                        (selFeel.isEmpty() || selFeel.contains(t.feel))
                }
                if (matched.isEmpty()) null else s.copy(tracks = matched)
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
            FilterPanel(
                styleNames = styleNames,
                keyOptions = keyOptions,
                bpmOptions = bpmOptions,
                sigOptions = sigOptions,
                feelOptions = feelOptions,
                selStyles = selStyles,
                selKeys = selKeys,
                selBpm = selBpm,
                selSig = selSig,
                selFeel = selFeel,
                onClear = {
                    selStyles.clear(); selKeys.clear(); selBpm.clear()
                    selSig.clear(); selFeel.clear()
                },
            )

            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                if (sections.isEmpty()) {
                    item {
                        Text(
                            "No hay pistas con esos filtros.",
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                sections.forEach { style ->
                    val isOpen = expanded[style.id] ?: (anyFilter || style.id == FAVORITES_ID)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterPanel(
    styleNames: List<String>,
    keyOptions: List<String>,
    bpmOptions: List<String>,
    sigOptions: List<String>,
    feelOptions: List<String>,
    selStyles: MutableList<String>,
    selKeys: MutableList<String>,
    selBpm: MutableList<String>,
    selSig: MutableList<String>,
    selFeel: MutableList<String>,
    onClear: () -> Unit,
) {
    val anyFilter = selStyles.isNotEmpty() || selKeys.isNotEmpty() || selBpm.isNotEmpty() ||
        selSig.isNotEmpty() || selFeel.isNotEmpty()
    val activeCount = selStyles.size + selKeys.size + selBpm.size + selSig.size + selFeel.size
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (activeCount > 0) "Filtros ($activeCount)" else "Filtros",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (anyFilter) {
                AssistChip(
                    onClick = onClear,
                    label = { Text("Limpiar") },
                    leadingIcon = { Icon(Icons.Default.Clear, null, Modifier.size(16.dp)) },
                )
                Spacer(Modifier.size(8.dp))
            }
            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
        }
        if (open) {
            ChipGrid("Estilo", styleNames, selStyles)
            ChipGrid("Tonalidad", keyOptions, selKeys)
            ChipGrid("BPM", bpmOptions, selBpm)
            ChipGrid("Compás", sigOptions, selSig)
            ChipGrid("Feel", feelOptions, selFeel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChipGrid(label: String, options: List<String>, selected: MutableList<String>) {
    if (options.isEmpty()) return
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp),
    )
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.chunked(2).forEach { rowOpts ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowOpts.forEach { opt ->
                    val isSel = selected.contains(opt)
                    FilterChip(
                        selected = isSel,
                        onClick = { if (isSel) selected.remove(opt) else selected.add(opt) },
                        label = { Text(opt) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowOpts.size == 1) Spacer(Modifier.weight(1f))
            }
        }
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
            Spacer(Modifier.size(8.dp))
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
        Spacer(Modifier.size(6.dp))
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
                    "${track.styleName} • ${track.key} • ${track.bpm} BPM • " +
                        "${track.timeSignature} • ${track.feel}",
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
