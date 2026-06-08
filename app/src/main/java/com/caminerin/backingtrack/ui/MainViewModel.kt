package com.caminerin.backingtrack.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.caminerin.backingtrack.audio.BackingPlayer
import com.caminerin.backingtrack.audio.Metronome
import com.caminerin.backingtrack.data.CatalogRepository
import com.caminerin.backingtrack.data.Prefs
import com.caminerin.backingtrack.model.Style
import com.caminerin.backingtrack.model.Subdivision
import com.caminerin.backingtrack.model.Track
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class Playback(
    val track: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val semitones: Int = 0,
    val bpm: Int = 120,
    val baseBpm: Int = 120,
    val loop: Boolean = false,
    val metronome: Boolean = false,
    val subdivision: Subdivision = Subdivision.QUARTER,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val player = BackingPlayer(app)
    private val metronome = Metronome(app)

    val styles: List<Style> = CatalogRepository.load(app)

    private val _favorites = MutableStateFlow(prefs.favorites())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _premium = MutableStateFlow(prefs.premium)
    val premium: StateFlow<Boolean> = _premium.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _playback = MutableStateFlow(Playback())
    val playback: StateFlow<Playback> = _playback.asStateFlow()

    init {
        player.onCompletion = { pushState() }
        metronome.bpmProvider = { player.currentBpm() }
        viewModelScope.launch {
            while (true) {
                if (player.isReady) {
                    val p = _playback.value
                    _playback.value = p.copy(
                        positionMs = player.positionMs(),
                        durationMs = player.durationMs(),
                        isPlaying = player.isPlaying,
                    )
                }
                delay(250)
            }
        }
    }

    fun setQuery(q: String) { _query.value = q }

    fun isFavorite(id: String) = _favorites.value.contains(id)

    fun toggleFavorite(track: Track) {
        val fav = !isFavorite(track.id)
        prefs.setFavorite(track.id, fav)
        _favorites.value = prefs.favorites()
    }

    fun unlockPremiumDemo() {
        prefs.premium = true
        _premium.value = true
    }

    /** Loads a track into the player and resets controls. */
    fun openTrack(track: Track) {
        player.onReady = {
            player.play()
            pushState()
        }
        player.load(track.audio, track.bpm)
        player.setLoop(false)
        metronome.stop()
        _playback.value = Playback(
            track = track,
            isPlaying = false,
            semitones = 0,
            bpm = track.bpm,
            baseBpm = track.bpm,
            loop = false,
            metronome = false,
            subdivision = Subdivision.QUARTER,
        )
    }

    fun togglePlay() {
        player.togglePlay()
        if (player.isPlaying && _playback.value.metronome && !metronome.isRunning) {
            metronome.start(viewModelScope)
        }
        pushState()
    }

    fun setLoop(enabled: Boolean) {
        player.setLoop(enabled)
        pushState()
    }

    fun setSemitones(n: Int) {
        player.setSemitones(n)
        pushState()
    }

    fun setBpm(bpm: Int) {
        player.setBpm(bpm)
        pushState()
    }

    fun toggleMetronome() {
        val on = !_playback.value.metronome
        if (on) {
            if (player.isPlaying) metronome.start(viewModelScope)
        } else {
            metronome.stop()
        }
        _playback.value = _playback.value.copy(metronome = on)
    }

    fun setSubdivision(sub: Subdivision) {
        metronome.subdivision = sub
        _playback.value = _playback.value.copy(subdivision = sub)
    }

    fun stopPlayback() {
        metronome.stop()
        player.pause()
        pushState()
    }

    private fun pushState() {
        _playback.value = _playback.value.copy(
            isPlaying = player.isPlaying,
            semitones = player.semitones(),
            bpm = player.currentBpm(),
            baseBpm = player.baseBpm(),
            loop = player.loopEnabled(),
            positionMs = player.positionMs(),
            durationMs = player.durationMs(),
        )
    }

    override fun onCleared() {
        super.onCleared()
        metronome.release()
        player.release()
    }
}
