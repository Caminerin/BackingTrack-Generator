package com.caminerin.backingtrack

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.caminerin.backingtrack.ui.AppNavHost
import com.caminerin.backingtrack.ui.AppViewModel
import com.caminerin.backingtrack.ui.theme.BackingTrackTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val crash = CrashReporter.readAndClear(application)
        setContent {
            BackingTrackTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val vm: AppViewModel = viewModel()
                    val context = LocalContext.current

                    val status by vm.status.collectAsState()
                    LaunchedEffect(status) {
                        status?.let { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            vm.clearStatus()
                        }
                    }

                    var crashText by remember { mutableStateOf(crash) }
                    crashText?.let { text ->
                        AlertDialog(
                            onDismissRequest = { crashText = null },
                            confirmButton = {
                                TextButton(onClick = {
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "BackingTrack crash")
                                        putExtra(Intent.EXTRA_TEXT, text)
                                    }
                                    context.startActivity(Intent.createChooser(send, "Compartir error"))
                                }) { Text("Compartir") }
                            },
                            dismissButton = {
                                TextButton(onClick = { crashText = null }) { Text("Cerrar") }
                            },
                            title = { Text("Se detectó un cierre anterior") },
                            text = {
                                Text(
                                    text,
                                    modifier = Modifier.verticalScroll(rememberScrollState()),
                                )
                            },
                        )
                    }

                    AppNavHost(vm)
                }
            }
        }
    }
}
