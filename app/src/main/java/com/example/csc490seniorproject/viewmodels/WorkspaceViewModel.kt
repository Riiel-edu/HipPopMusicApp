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
        viewModelScope.launch { songPlaybackEngine.preload(song) }
    }

    fun setCurrentBeatmap(beatmap: Beatmap) {
        _currentBeatmap.value = beatmap
    }

    private val songPlaybackEngine = HighQualityPlaybackEngine(application, viewModelScope)
    val isSongPlaying: StateFlow<Boolean> = songPlaybackEngine.isPlaying

    val songCurrentStep: StateFlow<Int> = songPlaybackEngine.currentStep

    fun playSong() {
        _currentSong.value?.let { songPlaybackEngine.play(it) }
    }

    fun playSongFromStart(startDelayMs: Long = 0L) {
        _currentSong.value?.let { songPlaybackEngine.play(it, fromStep = 0, startDelayMs = startDelayMs) }
    }

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