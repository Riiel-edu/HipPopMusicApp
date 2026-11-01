package com.example.csc490seniorproject.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.csc490seniorproject.audio.HighQualityPlaybackEngine
import com.example.csc490seniorproject.objects.Beatmap
import com.example.csc490seniorproject.objects.Song
import com.example.csc490seniorproject.objects.totalSteps
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class WorkspaceViewModel(application: Application) : AndroidViewModel(application) {

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _currentBeatmap = MutableStateFlow<Beatmap?>(null)
    val currentBeatmap: StateFlow<Beatmap?> = _currentBeatmap.asStateFlow()

    fun setCurrentSong(song: Song) {
        _currentSong.value = song
        // Warm up this song's samples right away, so the first press of
        // "play" from the beatmap editor or the game screen isn't stuck
        // waiting on a cold load.
        viewModelScope.launch { songPlaybackEngine.preload(song) }
    }

    fun setCurrentBeatmap(beatmap: Beatmap) {
        _currentBeatmap.value = beatmap
    }

    // --- Song audio playback, shared by the Beatmap editor and Game screen ---

    private val songPlaybackEngine = HighQualityPlaybackEngine(application, viewModelScope)
    val isSongPlaying: StateFlow<Boolean> = songPlaybackEngine.isPlaying

    /** The song's playhead, in the SONG's own steps. Drives the play bar in the beatmap editor. */
    val songCurrentStep: StateFlow<Int> = songPlaybackEngine.currentStep

    /** Plays the current song (if one is set) from wherever its playhead currently is. Does nothing if no song has been set yet. */
    fun playSong() {
        _currentSong.value?.let { songPlaybackEngine.play(it) }
    }

    /**
     * Plays the current song from its very first step, regardless of where the
     * playhead was left. [startDelayMs] holds the first step back (see
     * HighQualityPlaybackEngine.play) so it can be lined up with a game's lead-in.
     */
    fun playSongFromStart(startDelayMs: Long = 0L) {
        _currentSong.value?.let { songPlaybackEngine.play(it, fromStep = 0, startDelayMs = startDelayMs) }
    }

    /** Moves the song's playhead to [step] (in the song's own steps). Works whether playing or stopped. */
    fun seekSong(step: Int) {
        _currentSong.value?.let { songPlaybackEngine.seekTo(step, it.totalSteps()) }
    }

    suspend fun loadSongById(songId: String): Boolean {
        return try {
            val document = FirebaseFirestore.getInstance()
                .collection("songs")
                .document(songId)
                .get()
                .await()

            val song = document.toObject(Song::class.java)

            if (song != null) {
                setCurrentSong(song)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** Suspends until every sample the current song needs is loaded, so playback can start with no cold-load gap. */
    suspend fun prepareSong() {
        _currentSong.value?.let { songPlaybackEngine.preload(it) }
    }

    fun stopSong() {
        songPlaybackEngine.stop()
    }

    override fun onCleared() {
        songPlaybackEngine.release()
        super.onCleared()
    }
}