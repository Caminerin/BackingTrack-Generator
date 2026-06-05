package com.caminerin.backingtrack.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.caminerin.backingtrack.model.NOTE_NAMES
import com.caminerin.backingtrack.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(vm: AppViewModel, nav: NavController) {
    val recipe by vm.recipe.collectAsState()
    val isRendering by vm.isRendering.collectAsState()
    val renderProgress by vm.renderProgress.collectAsState()
    val hasRendered by vm.hasRendered.collectAsState()
    val isPlaying by vm.isPlaying.collectAsState()
    val playHead by vm.playHead.collectAsState()
    val loop by vm.loop.collectAsState()
    val context = LocalContext.current

    val keyName = NOTE_NAMES[recipe.keySemitone % 12]

    Column(Modifier.fillMaxWidth()) {
        TopAppBar(
            title = { Text("${recipe.style.displayName} · $keyName") },
            navigationIcon = {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                }
            },
        )

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .weight(1f),
        ) {
            Spacer(Modifier.height(8.dp))
            Text("${recipe.bpm} BPM · ${recipe.feel.displayName} · ${recipe.durationBars} compases",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))

            // Chord grid
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recipe.progression) { chord ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Box(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                            Text(
                                chord.displayName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            if (isRendering) {
                Text("Generando… ${(renderProgress * 100).toInt()}%")
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = renderProgress,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(
                    progress = playHead,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(20.dp))

            // Transport
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.toggleLoop() }) {
                    Icon(
                        Icons.Default.Loop,
                        contentDescription = "Loop",
                        tint = if (loop) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledIconButton(
                    onClick = { vm.togglePlay() },
                    enabled = hasRendered && !isRendering,
                    modifier = Modifier.size(72.dp),
                ) {
                    if (isRendering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
                IconButton(onClick = { vm.reseed(); vm.generate() }) {
                    Icon(Icons.Default.Casino, contentDescription = "Regenerar")
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Mezclador", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            MixerRow(vm, recipe, "drums", "Batería")
            MixerRow(vm, recipe, "bass", "Bajo")
            MixerRow(vm, recipe, "guitar", "Guitarra")
            MixerRow(vm, recipe, "keys", "Piano")

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { vm.saveCurrentToLibrary("wav") },
                    enabled = hasRendered,
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Guardar")
                }
                Button(
                    onClick = {
                        vm.exportFile("m4a") { file ->
                            if (file != null) shareFile(context, file)
                        }
                    },
                    enabled = hasRendered,
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Exportar M4A")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MixerRow(
    vm: AppViewModel,
    recipe: com.caminerin.backingtrack.model.JamRecipe,
    key: String,
    label: String,
) {
    val mix = recipe.instruments[key]
    val enabled = mix?.enabled == true
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Switch(checked = enabled, onCheckedChange = { vm.setInstrumentEnabled(key, it) })
        Spacer(Modifier.size(10.dp))
        Text(label, Modifier.width(72.dp), fontSize = 14.sp)
        androidx.compose.material3.Slider(
            value = mix?.volume ?: 0.75f,
            onValueChange = { vm.setInstrumentVolume(key, it) },
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun shareFile(context: android.content.Context, file: java.io.File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "audio/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir backing track"))
}
