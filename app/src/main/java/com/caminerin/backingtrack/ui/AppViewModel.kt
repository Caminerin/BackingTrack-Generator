package com.caminerin.backingtrack.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.caminerin.backingtrack.audio.AudioExporter
import com.caminerin.backingtrack.audio.Renderer
import com.caminerin.backingtrack.audio.SamplePack
import com.caminerin.backingtrack.audio.TrackPlayer
import com.caminerin.backingtrack.data.LibraryRepository
import com.caminerin.backingtrack.model.Chord
import com.caminerin.backingtrack.model.JamRecipe
import com.caminerin.backingtrack.model.LibraryItem
import com.caminerin.backingtrack.model.Progressions
import com.caminerin.backingtrack.model.Style
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = LibraryRepository.get(app)
    val library: StateFlow<List<LibraryItem>> = repo.items

    private val player = TrackPlayer()

    private var pack: SamplePack? = null
    private var renderer: Renderer? = null

    private val _engineReady = MutableStateFlow(false)
    val engineReady: StateFlow<Boolean> = _engineReady.asStateFlow()

    private val _recipe = MutableStateFlow(defaultRecipe())
    val recipe: StateFlow<JamRecipe> = _recipe.asStateFlow()

    private val _isRendering = MutableStateFlow(false)
    val isRendering: StateFlow<Boolean> = _isRendering.asStateFlow()

    private val _renderProgress = MutableStateFlow(0f)
    val renderProgress: StateFlow<Float> = _renderProgress.asStateFlow()

    private val _hasRendered = MutableStateFlow(false)
    val hasRendered: StateFlow<Boolean> = _hasRendered.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playHead = MutableStateFlow(0f)
    val playHead: StateFlow<Float> = _playHead.asStateFlow()

    private val _loop = MutableStateFlow(true)
    val loop: StateFlow<Boolean> = _loop.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private var lastRender: Renderer.RenderResult? = null

    init {
        player.onPosition = { _playHead.value = it }
        player.onComplete = { _isPlaying.value = player.isPlaying }
        player.onError = { _isPlaying.value = false; _status.value = "Error de reproducción: ${it.javaClass.simpleName}: ${it.message}" }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { SamplePack(getApplication()) }
                .onSuccess {
                    pack = it
                    renderer = Renderer(it)
                    _engineReady.value = true
                }
                .onFailure { _status.value = "Error cargando samples: ${it.message}" }
        }
    }

    private fun defaultRecipe(): JamRecipe {
        val style = Style.BLUES
        val key = 9 // A
        return JamRecipe(
            name = "Nuevo backing",
            style = style,
            keySemitone = key,
            bpm = style.defaultBpm,
            feel = style.defaultFeel,
            progression = Progressions.defaultFor(style, key),
            durationBars = 12,
        )
    }

    // ---- Recipe editing ----

    fun updateRecipe(transform: (JamRecipe) -> JamRecipe) {
        _recipe.value = transform(_recipe.value).copy(updatedAt = System.currentTimeMillis())
    }

    fun setStyle(style: Style) = updateRecipe {
        it.copy(
            style = style,
            feel = style.defaultFeel,
            bpm = style.defaultBpm,
            progression = Progressions.defaultFor(style, it.keySemitone),
        )
    }

    fun setKey(key: Int) = updateRecipe { r ->
        // Transpose existing progression to the new key.
        val delta = key - r.keySemitone
        val newProg = r.progression.map { it.copy(rootSemitone = (it.rootSemitone + delta + 12) % 12) }
        r.copy(keySemitone = key, progression = newProg)
    }

    fun setProgression(chords: List<Chord>) = updateRecipe { it.copy(progression = chords) }

    fun setInstrumentEnabled(inst: String, enabled: Boolean) = updateRecipe { r ->
        val m = r.instruments.toMutableMap()
        m[inst] = (m[inst] ?: com.caminerin.backingtrack.model.InstrumentMix()).copy(enabled = enabled)
        r.copy(instruments = m)
    }

    fun setInstrumentVolume(inst: String, volume: Float) = updateRecipe { r ->
        val m = r.instruments.toMutableMap()
        m[inst] = (m[inst] ?: com.caminerin.backingtrack.model.InstrumentMix()).copy(volume = volume)
        r.copy(instruments = m)
    }

    fun reseed() = updateRecipe { it.copy(seed = System.currentTimeMillis()) }

    // ---- Render & playback ----

    fun generate() {
        val r = renderer ?: run { _status.value = "Motor no listo"; return }
        if (_isRendering.value) return
        player.stop()
        _isPlaying.value = false
        _isRendering.value = true
        _renderProgress.value = 0f
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                val result = r.render(_recipe.value) { p -> _renderProgress.value = p }
                lastRender = result
                player.load(result.pcm, result.sampleRate)
                player.loop = _loop.value
                withContext(Dispatchers.Main) {
                    _hasRendered.value = true
                    _isRendering.value = false
                    play()
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    _isRendering.value = false
                    _status.value = "Error al generar: ${it.javaClass.simpleName}: ${it.message}"
                }
            }
        }
    }

    fun play() {
        if (!_hasRendered.value) return
        player.loop = _loop.value
        runCatching {
            player.play()
            _isPlaying.value = true
        }.onFailure {
            _isPlaying.value = false
            _status.value = "Error de audio: ${it.javaClass.simpleName}: ${it.message}"
        }
    }

    fun pause() {
        player.pause()
        _isPlaying.value = false
    }

    fun togglePlay() = if (_isPlaying.value) pause() else play()

    fun toggleLoop() {
        _loop.value = !_loop.value
        player.loop = _loop.value
    }

    fun seek(fraction: Float) = player.seekTo(fraction)

    // ---- Library ----

    fun saveCurrentToLibrary(format: String = "wav", onDone: (LibraryItem?) -> Unit = {}) {
        val result = lastRender ?: run { _status.value = "Genera la pista primero"; onDone(null); return }
        val r = _recipe.value
        viewModelScope.launch(Dispatchers.IO) {
            val item = runCatching {
                val ext = if (format == "m4a") "m4a" else "wav"
                val out = repo.audioFile(r.id, ext)
                if (format == "m4a") {
                    AudioExporter.writeM4a(out, result.pcm, result.sampleRate)
                } else {
                    AudioExporter.writeWav(out, result.pcm, result.sampleRate)
                }
                val item = LibraryItem(
                    id = r.id,
                    title = trackTitle(r),
                    recipe = r,
                    audioFilePath = out.absolutePath,
                    durationMs = result.durationMs,
                    format = ext,
                )
                repo.upsert(item)
                item
            }.getOrNull()
            withContext(Dispatchers.Main) {
                _status.value = if (item != null) "Guardado en biblioteca" else "Error al guardar"
                onDone(item)
            }
        }
    }

    fun exportFile(format: String, onDone: (File?) -> Unit) {
        val result = lastRender ?: run { _status.value = "Genera la pista primero"; onDone(null); return }
        val r = _recipe.value
        viewModelScope.launch(Dispatchers.IO) {
            val file = runCatching {
                val ext = if (format == "m4a") "m4a" else "wav"
                val dir = File(getApplication<Application>().cacheDir, "exports").apply { mkdirs() }
                val out = File(dir, "${trackTitle(r)}.$ext")
                if (format == "m4a") AudioExporter.writeM4a(out, result.pcm, result.sampleRate)
                else AudioExporter.writeWav(out, result.pcm, result.sampleRate)
                out
            }.getOrNull()
            withContext(Dispatchers.Main) { onDone(file) }
        }
    }

    fun loadFromLibrary(id: String) {
        val item = repo.get(id) ?: return
        _recipe.value = item.recipe
        repo.markPlayed(id)
        generate()
    }

    fun toggleFavorite(id: String) = repo.toggleFavorite(id)
    fun deleteItem(id: String) = repo.delete(id)

    fun clearStatus() { _status.value = null }

    fun newRecipe() {
        _recipe.value = defaultRecipe().copy(seed = System.currentTimeMillis())
        _hasRendered.value = false
        lastRender = null
        player.stop()
        _isPlaying.value = false
    }

    private fun trackTitle(r: JamRecipe): String {
        val keyName = com.caminerin.backingtrack.model.NOTE_NAMES[r.keySemitone % 12]
        return if (r.name.isNotBlank()) r.name
        else "${r.style.displayName} – $keyName – ${r.bpm} BPM"
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }
}
