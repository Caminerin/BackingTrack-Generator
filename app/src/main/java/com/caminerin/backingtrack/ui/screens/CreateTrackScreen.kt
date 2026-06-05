package com.caminerin.backingtrack.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.caminerin.backingtrack.model.Feel
import com.caminerin.backingtrack.model.NOTE_NAMES
import com.caminerin.backingtrack.model.Style
import com.caminerin.backingtrack.ui.AppViewModel
import com.caminerin.backingtrack.ui.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTrackScreen(vm: AppViewModel, nav: NavController) {
    val recipe by vm.recipe.collectAsState()
    val ready by vm.engineReady.collectAsState()

    Column(Modifier.fillMaxWidth()) {
        TopAppBar(
            title = { Text("Crear backing track") },
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
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SectionLabel("Estilo")
            ChipRow(
                options = Style.values().map { it.displayName },
                selectedIndex = Style.values().indexOf(recipe.style),
                onSelect = { vm.setStyle(Style.values()[it]) },
            )

            SectionLabel("Tonalidad")
            ChipRow(
                options = (0 until 12).map { NOTE_NAMES[it] },
                selectedIndex = recipe.keySemitone % 12,
                onSelect = { vm.setKey(it) },
            )

            SectionLabel("Tempo: ${recipe.bpm} BPM")
            Slider(
                value = recipe.bpm.toFloat(),
                onValueChange = { vm.updateRecipe { r -> r.copy(bpm = it.toInt()) } },
                valueRange = 50f..200f,
            )

            SectionLabel("Feel")
            ChipRow(
                options = Feel.values().map { it.displayName },
                selectedIndex = Feel.values().indexOf(recipe.feel),
                onSelect = { vm.updateRecipe { r -> r.copy(feel = Feel.values()[it]) } },
            )

            SectionLabel("Compases: ${recipe.durationBars}")
            Slider(
                value = recipe.durationBars.toFloat(),
                onValueChange = { vm.updateRecipe { r -> r.copy(durationBars = it.toInt()) } },
                valueRange = 4f..48f,
            )

            SectionLabel("Progresión")
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        recipe.progression.joinToString(" · ") { it.displayName },
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { nav.navigate(Routes.PROGRESSION) }) {
                        Text("Editar progresión")
                    }
                }
            }

            SectionLabel("Instrumentos")
            InstrumentToggle(vm, recipe, "drums", "Batería")
            InstrumentToggle(vm, recipe, "bass", "Bajo")
            InstrumentToggle(vm, recipe, "guitar", "Guitarra rítmica")
            InstrumentToggle(vm, recipe, "keys", "Piano / Teclas")

            Spacer(Modifier.height(80.dp))
        }

        Button(
            onClick = {
                vm.generate()
                nav.navigate(Routes.PLAYER)
            },
            enabled = ready,
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .height(58.dp),
        ) {
            Icon(Icons.Default.GraphicEq, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(if (ready) "Generar y reproducir" else "Cargando instrumentos…", fontSize = 17.sp)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChipRow(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { i, label ->
            FilterChip(
                selected = i == selectedIndex,
                onClick = { onSelect(i) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun InstrumentToggle(
    vm: AppViewModel,
    recipe: com.caminerin.backingtrack.model.JamRecipe,
    key: String,
    label: String,
) {
    val mix = recipe.instruments[key]
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(
            checked = mix?.enabled == true,
            onCheckedChange = { vm.setInstrumentEnabled(key, it) },
        )
    }
}
