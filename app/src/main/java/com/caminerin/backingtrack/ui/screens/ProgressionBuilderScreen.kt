package com.caminerin.backingtrack.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.caminerin.backingtrack.model.Chord
import com.caminerin.backingtrack.model.ChordQuality
import com.caminerin.backingtrack.model.NOTE_NAMES
import com.caminerin.backingtrack.model.Progressions
import com.caminerin.backingtrack.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressionBuilderScreen(vm: AppViewModel, nav: NavController) {
    val recipe by vm.recipe.collectAsState()
    var selectedCell by remember { mutableIntStateOf(-1) }

    Column(Modifier.fillMaxWidth()) {
        TopAppBar(
            title = { Text("Progresión") },
            navigationIcon = {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                }
            },
        )
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Presets para ${recipe.style.displayName}",
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Progressions.forStyle(recipe.style).forEach { preset ->
                    FilterChip(
                        selected = false,
                        onClick = { vm.setProgression(Progressions.realise(preset, recipe.keySemitone)) },
                        label = { Text(preset.name) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Acordes (toca para editar)", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(220.dp),
            ) {
                itemsIndexed(recipe.progression) { index, chord ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (index == selectedCell)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        modifier = Modifier
                            .height(60.dp)
                            .clickableCell { selectedCell = if (selectedCell == index) -1 else index },
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(chord.displayName, fontWeight = FontWeight.Bold)
                            Text("${chord.durationBars}c", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val newChord = Chord(recipe.keySemitone, ChordQuality.DOM7, 1)
                    vm.setProgression(recipe.progression + newChord)
                }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Añadir")
                }
                if (selectedCell in recipe.progression.indices) {
                    Button(onClick = {
                        vm.setProgression(recipe.progression.filterIndexed { i, _ -> i != selectedCell })
                        selectedCell = -1
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Borrar")
                    }
                }
            }

            if (selectedCell in recipe.progression.indices) {
                Spacer(Modifier.height(16.dp))
                val chord = recipe.progression[selectedCell]
                Text("Raíz", fontWeight = FontWeight.SemiBold)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    (0 until 12).forEach { st ->
                        FilterChip(
                            selected = chord.rootSemitone == st,
                            onClick = { editChord(vm, recipe.progression, selectedCell) { it.copy(rootSemitone = st) } },
                            label = { Text(NOTE_NAMES[st]) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Tipo", fontWeight = FontWeight.SemiBold)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ChordQuality.values().forEach { q ->
                        FilterChip(
                            selected = chord.quality == q,
                            onClick = { editChord(vm, recipe.progression, selectedCell) { it.copy(quality = q) } },
                            label = { Text(if (q.displayName.isEmpty()) "maj" else q.displayName) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Duración: ${chord.durationBars} compás(es)", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..4).forEach { bars ->
                        FilterChip(
                            selected = chord.durationBars == bars,
                            onClick = { editChord(vm, recipe.progression, selectedCell) { it.copy(durationBars = bars) } },
                            label = { Text("$bars") },
                        )
                    }
                }
            }
        }
    }
}

private fun editChord(
    vm: AppViewModel,
    progression: List<Chord>,
    index: Int,
    transform: (Chord) -> Chord,
) {
    vm.setProgression(progression.mapIndexed { i, c -> if (i == index) transform(c) else c })
}

private fun Modifier.clickableCell(onClick: () -> Unit): Modifier =
    this.then(androidx.compose.foundation.clickable(onClick = onClick))
