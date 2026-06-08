package com.caminerin.backingtrack.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.caminerin.backingtrack.model.ChordEvent
import com.caminerin.backingtrack.model.Subdivision
import com.caminerin.backingtrack.ui.LoopMode
import com.caminerin.backingtrack.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(vm: MainViewModel, nav: NavController) {
    val pb by vm.playback.collectAsState()
    val premium by vm.premium.collectAsState()
    val track = pb.track
    var showLock by remember { mutableStateOf(false) }
    var metroOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reproductor") },
                navigationIcon = {
                    IconButton(onClick = { vm.stopPlayback(); nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Atrás")
                    }
                },
            )
        }
    ) { pad ->
        if (track == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("Sin pista seleccionada")
            }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(track.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "${track.styleName} • Tono original ${track.key} • ${track.bpm} BPM",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            // Chords on screen: 6-cell grid, the current one lit, advancing by page.
            if (track.chords.isNotEmpty()) {
                // Collapse consecutive duplicate chord names for a clean chart.
                val chart = remember(track.id) {
                    val out = ArrayList<com.caminerin.backingtrack.model.ChordEvent>()
                    track.chords.forEach { c ->
                        if (out.isEmpty() || out.last().name != c.name) out.add(c)
                    }
                    out
                }
                val beatsElapsed = pb.positionMs / 1000.0 * track.bpm / 60.0
                val curIdx = chart.indexOfLast { it.beat <= beatsElapsed }.let { if (it < 0) 0 else it }
                val page = curIdx / 6
                val window = chart.drop(page * 6).take(6)
                ChordGrid(window = window, activeInWindow = curIdx - page * 6)
                Spacer(Modifier.height(16.dp))
            }
            Spacer(Modifier.height(8.dp))

            // Progress
            val dur = pb.durationMs.coerceAtLeast(1)
            val pos = pb.positionMs.coerceIn(0, dur)
            Slider(
                value = pos.toFloat() / dur,
                onValueChange = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt(pos), style = MaterialTheme.typography.labelMedium)
                Text(fmt(dur), style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(16.dp))

            // Transport: play/pause + loop
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                FilledIconButton(
                    onClick = { vm.togglePlay() },
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                ) {
                    Icon(
                        if (pb.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (pb.isPlaying) "Pausa" else "Reproducir",
                        modifier = Modifier.size(40.dp),
                    )
                }
                IconButton(
                    onClick = { vm.setLoop(!pb.loop) },
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = if (pb.loop) MaterialTheme.colorScheme.primary else Color.Gray
                    ),
                ) {
                    Icon(Icons.Default.Loop, "Bucle", modifier = Modifier.size(30.dp))
                }
            }

            // Loop options: full song vs A-B section.
            if (pb.loop) {
                Spacer(Modifier.height(12.dp))
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text("Bucle", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = pb.loopMode == LoopMode.FULL,
                                onClick = { vm.setLoopMode(LoopMode.FULL) },
                                label = { Text("Canción entera") },
                            )
                            FilterChip(
                                selected = pb.loopMode == LoopMode.SECTION,
                                onClick = { vm.setLoopMode(LoopMode.SECTION) },
                                label = { Text("A → B") },
                            )
                        }
                        if (pb.loopMode == LoopMode.SECTION) {
                            Spacer(Modifier.height(10.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { vm.setLoopA() },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Fijar A" + (pb.loopAms?.let { "\n${fmt(it)}" } ?: ""), textAlign = TextAlign.Center) }
                                OutlinedButton(
                                    onClick = { vm.setLoopB() },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Fijar B" + (pb.loopBms?.let { "\n${fmt(it)}" } ?: ""), textAlign = TextAlign.Center) }
                            }
                            val a = pb.loopAms
                            val b = pb.loopBms
                            Text(
                                if (a != null && b != null && b > a)
                                    "Repitiendo ${fmt(a)} → ${fmt(b)}"
                                else
                                    "Pon A y B mientras suena para repetir ese trozo.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            // Instruments (stems) on/off
            if (pb.stems.size > 1) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text("Instrumentos", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Activa o silencia cada instrumento",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pb.stems.forEachIndexed { i, stem ->
                                FilterChip(
                                    selected = stem.enabled,
                                    onClick = { vm.toggleStem(i) },
                                    label = { Text(stem.instrument) },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Metronome
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clickable { metroOpen = !metroOpen },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Metrónomo", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { vm.toggleMetronome() }) {
                            Text(if (pb.metronome) "ON" else "OFF")
                        }
                        Spacer(Modifier.size(8.dp))
                        Icon(if (metroOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                    }
                    if (metroOpen) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Subdivision.values().forEach { sub ->
                                FilterChip(
                                    selected = pb.subdivision == sub,
                                    onClick = { vm.setSubdivision(sub) },
                                    label = {
                                        if (sub == Subdivision.TRIPLET) {
                                            TripletIcon(
                                                modifier = Modifier.semantics { contentDescription = sub.label },
                                            )
                                        } else {
                                            Text(
                                                sub.symbol,
                                                style = MaterialTheme.typography.titleLarge,
                                                modifier = Modifier.semantics { contentDescription = sub.label },
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // Key (transpose) — paid
            ControlCard(
                title = "Tono",
                value = transposeKey(track.key, pb.semitones) +
                    (if (pb.semitones != 0) "  (${if (pb.semitones > 0) "+" else ""}${pb.semitones})" else ""),
                locked = !premium,
                onMinus = { vm.setSemitones(pb.semitones - 1) },
                onPlus = { vm.setSemitones(pb.semitones + 1) },
                onLockedClick = { showLock = true },
            )
            Spacer(Modifier.height(12.dp))

            // BPM — paid
            ControlCard(
                title = "BPM",
                value = "${pb.bpm}" + (if (pb.bpm != pb.baseBpm) "  (orig. ${pb.baseBpm})" else ""),
                locked = !premium,
                onMinus = { vm.setBpm(pb.bpm - 1) },
                onPlus = { vm.setBpm(pb.bpm + 1) },
                onLockedClick = { showLock = true },
            )
        }
    }

    if (showLock) {
        AlertDialog(
            onDismissRequest = { showLock = false },
            title = { Text("Función de pago") },
            text = { Text("Cambiar el Tono y el BPM son funciones de pago. Desbloquéalas para ajustar la pista a tu gusto.") },
            confirmButton = {
                TextButton(onClick = { vm.unlockPremiumDemo(); showLock = false }) { Text("Desbloquear (demo)") }
            },
            dismissButton = { TextButton(onClick = { showLock = false }) { Text("Cerrar") } },
        )
    }
}

/** Triplet figure: three eighth-note stems joined by a beam (♫ con 3 patas). */
@Composable
private fun TripletIcon(modifier: Modifier = Modifier) {
    val tint = LocalContentColor.current
    Canvas(modifier = modifier.size(width = 26.dp, height = 22.dp)) {
        val stemW = 2.dp.toPx()
        val beamH = 3.dp.toPx()
        val headRx = 3.2.dp.toPx()
        val headRy = 2.4.dp.toPx()
        val top = size.height * 0.12f
        val bottom = size.height * 0.86f
        val xs = listOf(size.width * 0.18f, size.width * 0.5f, size.width * 0.82f)
        // beam joining the three stems
        drawLine(
            color = tint,
            start = Offset(xs.first(), top + beamH / 2f),
            end = Offset(xs.last(), top + beamH / 2f),
            strokeWidth = beamH,
            cap = StrokeCap.Round,
        )
        xs.forEach { x ->
            // stem
            drawLine(
                color = tint,
                start = Offset(x, top),
                end = Offset(x, bottom - headRy),
                strokeWidth = stemW,
                cap = StrokeCap.Round,
            )
            // filled note head
            drawOval(
                color = tint,
                topLeft = Offset(x - headRx * 1.4f, bottom - headRy * 2f),
                size = Size(headRx * 2f, headRy * 2f),
            )
        }
    }
}

@Composable
private fun ControlCard(
    title: String,
    value: String,
    locked: Boolean,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onLockedClick: () -> Unit,
) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    if (locked) {
                        Spacer(Modifier.size(6.dp))
                        Icon(Icons.Default.Lock, "De pago", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            if (locked) {
                OutlinedButton(onClick = onLockedClick) { Text("Desbloquear") }
            } else {
                OutlinedButton(onClick = onMinus) { Text("–") }
                Spacer(Modifier.size(8.dp))
                OutlinedButton(onClick = onPlus) { Text("+") }
            }
        }
    }
}

@Composable
private fun ChordGrid(window: List<ChordEvent>, activeInWindow: Int) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Always lay out 6 slots (2 rows x 3) so the grid size is stable.
            for (r in 0 until 2) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in 0 until 3) {
                        val idx = r * 3 + c
                        val chord = window.getOrNull(idx)
                        val active = idx == activeInWindow
                        val bg = when {
                            active -> MaterialTheme.colorScheme.primary
                            chord != null -> MaterialTheme.colorScheme.surface
                            else -> Color.Transparent
                        }
                        val fg = when {
                            active -> MaterialTheme.colorScheme.onPrimary
                            chord != null -> MaterialTheme.colorScheme.onSurface
                            else -> Color.Transparent
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(bg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                chord?.name ?: "",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = fg,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val SHARP = listOf("A", "A#", "B", "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#")

/** Transpose a key name (e.g. "A", "C#", "Dm") by n semitones, keeping minor. */
fun transposeKey(key: String, n: Int): String {
    val minor = key.endsWith("m")
    val root = if (minor) key.dropLast(1) else key
    val idx = SHARP.indexOf(root)
    if (idx < 0) return key
    val ni = ((idx + n) % 12 + 12) % 12
    return SHARP[ni] + if (minor) "m" else ""
}

private fun fmt(ms: Int): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
