package com.example.csc490seniorproject.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.csc490seniorproject.audio.HighQualityPlaybackEngine
import com.example.csc490seniorproject.data.SongRepository
import com.example.csc490seniorproject.objects.CollaborationSession
import com.example.csc490seniorproject.objects.Instrument
import com.example.csc490seniorproject.objects.Note
import com.example.csc490seniorproject.objects.Song
import com.example.csc490seniorproject.objects.Track
import com.example.csc490seniorproject.objects.newSong
import com.example.csc490seniorproject.objects.totalSteps
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

interface CollabTrackSink {
    fun add(track: Track)
    fun update(track: Track)
    fun delete(trackId: String)
}

class EditorScreenVM(application: Application) : AndroidViewModel(application) {

    private val _song = MutableStateFlow(newSong(ownerId = ""))
    val song: StateFlow<Song> = _song.asStateFlow()

    private val _selectedTrackId = MutableStateFlow(_song.value.tracks.first().id)
    val selectedTrackId: StateFlow<String> = _selectedTrackId.asStateFlow()

    private var collabUid: String? = null
    private var collabSessionId: String? = null
    private var collabIsHost: Boolean = false
    private var collabSink: CollabTrackSink? = null
    private var collabSynced = false

    private val _collabNotice = MutableStateFlow<String?>(null)
    val collabNotice: StateFlow<String?> = _collabNotice.asStateFlow()

    fun clearCollabNotice() {
        _collabNotice.value = null
    }

    private fun canEdit(track: Track): Boolean =
        collabUid == null || track.ownerId == collabUid

    private fun lockedByPartner(trackId: String): Boolean {
        val track = _song.value.tracks.firstOrNull { it.id == trackId } ?: return false
        if (canEdit(track)) return false
        _collabNotice.value = "“${track.name}” belongs to your partner. Ask them to hand it over."
        return true
    }

    fun startCollaboration(session: CollaborationSession, uid: String, sink: CollabTrackSink) {
        if (collabUid == uid && collabSessionId == session.id) {
            collabSink = sink
            return
        }

        if (session.hostUserId == uid && _song.value.id != session.songId) return

        playbackEngine.stop()
        collabUid = uid
        collabSessionId = session.id
        collabIsHost = session.hostUserId == uid
        collabSink = sink
        collabSynced = false
        undoStack.clear()

        if (collabIsHost) {
            _song.update { it.copy(tracks = it.tracks.map { t -> t.copy(ownerId = uid) }) }
        } else {
            _song.value = Song(
                id = session.songId,
                ownerId = session.hostUserId,
                name = session.songName,
                bpm = session.bpm,
                stepsPerBeat = session.stepsPerBeat,
                beatsPerBar = session.beatsPerBar,
                bars = session.bars,
                tracks = emptyList()
            )
            _selectedTrackId.value = ""
        }
    }

    fun stopCollaboration() {
        val uid = collabUid ?: return
        val wasHost = collabIsHost

        collabUid = null
        collabSessionId = null
        collabIsHost = false
        collabSink = null
        collabSynced = false
        undoStack.clear()

        if (!wasHost) {
            playbackEngine.stop()
            loadSong(newSong(ownerId = uid))
            viewModelScope.launch { playbackEngine.preload(_song.value) }
        }
    }

    fun applyRemoteTracks(remote: List<Track>) {
        val uid = collabUid ?: return
        val local = _song.value.tracks
        val remoteById = remote.associateBy { it.id }
        val localIds = local.map { it.id }.toSet()

        val merged = if (!collabSynced) {
            if (remote.isEmpty()) return
            collabSynced = true
            local.mapNotNull { remoteById[it.id] } + remote.filter { it.id !in localIds }
        } else {
            val kept = local.mapNotNull { l ->
                val r = remoteById[l.id]
                when {
                    r == null -> if (l.ownerId == uid) l else null
                    r.ownerId == uid && l.ownerId == uid -> l
                    else -> r
                }
            }
            kept + remote.filter { it.id !in localIds }
        }

        if (merged == local) return

        val instrumentsChanged =
            merged.map { it.instrument }.toSet() != local.map { it.instrument }.toSet()

        _song.update { it.copy(tracks = merged) }

        if (merged.none { it.id == _selectedTrackId.value }) {
            _selectedTrackId.value =
                merged.firstOrNull { it.ownerId == uid }?.id ?: merged.firstOrNull()?.id ?: ""
        }

        if (instrumentsChanged) {
            viewModelScope.launch { playbackEngine.preload(_song.value) }
        }
    }

    private fun publishCollabChanges(old: Song, new: Song) {
        val uid = collabUid ?: return
        val sink = collabSink ?: return

        val oldById = old.tracks.associateBy { it.id }
        val newIds = new.tracks.map { it.id }.toSet()

        new.tracks.forEach { track ->
            if (track.ownerId != uid) return@forEach
            val previous = oldById[track.id]
            when {
                previous == null -> sink.add(track)
                previous != track -> sink.update(track)
            }
        }
        old.tracks.forEach { track ->
            if (track.ownerId == uid && track.id !in newIds) sink.delete(track.id)
        }
    }

    private val _noteLengthSteps = MutableStateFlow(1)
    val noteLengthSteps: StateFlow<Int> = _noteLengthSteps.asStateFlow()

    fun setNoteLength(steps: Int) {
        _noteLengthSteps.value = steps.coerceIn(1, 16)
    }

    private val _activeOctaveByTrack = MutableStateFlow<Map<String, Int>>(emptyMap())
    val activeOctaveByTrack: StateFlow<Map<String, Int>> = _activeOctaveByTrack.asStateFlow()

    fun setActiveOctave(trackId: String, octave: Int) {
        _activeOctaveByTrack.value = _activeOctaveByTrack.value + (trackId to octave.coerceIn(2, 7))
    }

    /** Plays a pitch once without placing a note. */
    fun previewPitch(instrument: Instrument, pitch: Int) = playbackEngine.previewNote(instrument, pitch)

    private val playbackEngine = HighQualityPlaybackEngine(getApplication(), viewModelScope)
    val isPlaying: StateFlow<Boolean> = playbackEngine.isPlaying
    val currentStep: StateFlow<Int> = playbackEngine.currentStep

    init {
        viewModelScope.launch { playbackEngine.preload(_song.value) }
    }

    fun playSong() = playbackEngine.play(_song.value)

    fun stopPlayback() = playbackEngine.stop()

    fun seekTo(step: Int) = playbackEngine.seekTo(step, _song.value.totalSteps())

    fun restartFromStart() = seekTo(0)

    override fun onCleared() {
        playbackEngine.release()
        super.onCleared()
    }

    private val undoStack = ArrayDeque<Song>()
    private val maxUndo = 50

    val canUndo: Boolean get() = collabUid == null && undoStack.isNotEmpty()

    fun loadSong(song: Song) {
        undoStack.clear()
        _song.value = song
        _selectedTrackId.value = song.tracks.firstOrNull()?.id ?: ""
    }

    fun setOwnerId(ownerId: String) {
        _song.value = _song.value.copy(ownerId = ownerId)
    }

    fun renameSong(name: String) = mutate { it.copy(name = name) }

    fun setBpm(bpm: Int) {
        if (collabUid != null) {
            _collabNotice.value = "Tempo can't be changed during a collaboration session."
            return
        }
        mutate { it.copy(bpm = bpm.coerceIn(20, 300)) }
    }

    fun setBars(bars: Int) {
        if (collabUid != null) {
            _collabNotice.value = "Song length can't be changed during a collaboration session."
            return
        }
        mutate { it.copy(bars = bars.coerceIn(1, 64)) }
    }
    fun addTrack(name: String, instrument: Instrument) {
        val uid = collabUid
        mutate { song ->
            val track = Track(name = name, instrument = instrument, ownerId = uid ?: "")
            _selectedTrackId.value = track.id
            song.copy(tracks = song.tracks + track)
        }
        viewModelScope.launch { playbackEngine.preload(_song.value) }
    }

    fun deleteTrack(trackId: String) {
        if (lockedByPartner(trackId)) return
        mutate { song ->
            val remaining = song.tracks.filterNot { it.id == trackId }
            if (_selectedTrackId.value == trackId) {
                _selectedTrackId.value = remaining.firstOrNull()?.id ?: ""
            }
            song.copy(tracks = remaining)
        }
    }

    fun selectTrack(trackId: String) {
        _selectedTrackId.value = trackId
    }

    fun setTrackVolume(trackId: String, volume: Float) = mutate { song ->
        song.withTrack(trackId) { it.copy(volume = volume.coerceIn(0f, 1f)) }
    }

    fun toggleMute(trackId: String) = mutate { song ->
        song.withTrack(trackId) { it.copy(muted = !it.muted) }
    }

    fun toggleSolo(trackId: String) = mutate { song ->
        song.withTrack(trackId) { it.copy(solo = !it.solo) }
    }

    // --- Note editing -----------------------------------------------------------

    fun placeNote(trackId: String, pitch: Int, startStep: Int, lengthSteps: Int = _noteLengthSteps.value, velocity: Int = 100) {
        if (lockedByPartner(trackId)) return
        mutate { song ->
            song.withTrack(trackId) { track ->
                val withoutOverlap = track.notes.filterNot { it.pitch == pitch && it.startStep == startStep }
                track.copy(notes = withoutOverlap + Note(pitch = pitch, startStep = startStep, lengthSteps = lengthSteps, velocity = velocity))
            }
        }
        val instrument = _song.value.tracks.firstOrNull { it.id == trackId }?.instrument ?: return
        playbackEngine.previewNote(instrument, pitch, velocity)
    }

    fun deleteNote(trackId: String, noteId: String) {
        if (lockedByPartner(trackId)) return
        mutate { song ->
            song.withTrack(trackId) { track ->
                track.copy(notes = track.notes.filterNot { it.id == noteId })
            }
        }
    }

    fun moveNote(trackId: String, noteId: String, newPitch: Int, newStartStep: Int) = mutate { song ->
        song.withTrack(trackId) { track ->
            val notes = track.notes.map { note ->
                if (note.id == noteId) note.copy(pitch = newPitch, startStep = newStartStep) else note
            }
            track.copy(notes = notes)
        }
    }

    fun resizeNote(trackId: String, noteId: String, newLengthSteps: Int) = mutate { song ->
        song.withTrack(trackId) { track ->
            val notes = track.notes.map { note ->
                if (note.id == noteId) note.copy(lengthSteps = newLengthSteps.coerceAtLeast(1)) else note
            }
            track.copy(notes = notes)
        }
    }

    fun undo() {
        if (collabUid != null) return
        val previous = undoStack.removeLastOrNull() ?: return
        _song.value = previous
    }

    private inline fun mutate(transform: (Song) -> Song) {
        val current = _song.value
        val updated = transform(current)
        if (updated == current) return // no-op edit, don't pollute undo history
        undoStack.addLast(current)
        if (undoStack.size > maxUndo) undoStack.removeFirst()
        _song.value = updated
        publishCollabChanges(current, updated)
    }

    private fun Song.withTrack(trackId: String, transform: (Track) -> Track): Song =
        copy(tracks = tracks.map { if (it.id == trackId && canEdit(it)) transform(it) else it })

    private val _saveStatus = MutableStateFlow<String?>(null)
    val saveStatus = _saveStatus.asStateFlow()

    fun openSong(songId: String) {
        if (collabSessionId != null) return
        if (_song.value.id == songId) return

        viewModelScope.launch {
            try {
                val s = SongRepository.loadSong(songId)
                if (collabSessionId != null) return@launch
                if (s == null) { _saveStatus.value = "Song not found"; return@launch }
                loadSong(s)
                playbackEngine.preload(s)
            } catch (e: Exception) {
                if (collabSessionId != null) return@launch
                Log.e("SongSave", "load failed", e)
                _saveStatus.value = "Load failed: ${e.message}"
            }
        }
    }

    fun saveCurrentSong() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) { _saveStatus.value = "Not signed in"; return }
        if (collabUid != null && !collabIsHost) {
            _saveStatus.value = "Only the host can save this song"
            return
        }

        viewModelScope.launch {
            _saveStatus.value = "Saving…"
            try {
                val s = _song.value.copy(ownerId = uid)
                SongRepository.saveSong(s)

                val saved = SongRepository.loadSong(s.id)
                _song.update { it.copy(ownerId = uid, createdAt = saved?.createdAt, updatedAt = saved?.updatedAt) }
                _saveStatus.value = "Saved"
            } catch (e: Exception) {
                Log.e("SongSave", "failed", e)
                _saveStatus.value = "Save failed: ${e.message}"
            }
        }
    }
}