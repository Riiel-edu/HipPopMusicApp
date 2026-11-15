package com.example.csc490seniorproject.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.csc490seniorproject.data.CollaborationRepository
import com.example.csc490seniorproject.objects.CollaborationSession
import com.example.csc490seniorproject.objects.Song
import com.example.csc490seniorproject.objects.Track
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class CollaborationViewModel : ViewModel() {

    private val _session = MutableStateFlow<CollaborationSession?>(null)
    val session: StateFlow<CollaborationSession?> = _session.asStateFlow()

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    private val _isCollaborating = MutableStateFlow(false)
    val isCollaborating: StateFlow<Boolean> = _isCollaborating.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    val currentUserId: String?
        get() = FirebaseAuth.getInstance().currentUser?.uid

    val isHost: Boolean
        get() = _session.value?.hostUserId == currentUserId

    private var sessionJob: Job? = null
    private var tracksJob: Job? = null

    // ---- Ordered write queue ------------------------------------------------
    // Editing fires many writes in quick succession. Launching each in its own
    // coroutine lets an older write land AFTER a newer one and overwrite it, so
    // every write goes through one worker, in order. Repeated updates to the
    // same track are coalesced: only the latest state is sent. Touched on the
    // main thread only (viewModelScope runs on Main).
    private val writeQueue = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val pendingUpdates = LinkedHashMap<String, Track>()

    init {
        viewModelScope.launch {
            for (job in writeQueue) {
                try {
                    job()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _error.value = e.message
                }
            }
        }
    }

    private fun enqueue(job: suspend () -> Unit) {
        writeQueue.trySend(job)
    }

    // ---- Session lifecycle --------------------------------------------------

    fun createSession(song: Song) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val sessionId = CollaborationRepository.createSession(song)
                startObserving(sessionId)
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Joins by session code. [onJoined] receives the session's songId (for opening the editor). */
    fun joinSession(sessionId: String, onJoined: (songId: String) -> Unit = {}) {
        val id = sessionId.trim()
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val songId = CollaborationRepository.joinSession(id)
                startObserving(id)
                onJoined(songId)
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun startObserving(sessionId: String) {
        stopObserving()

        _currentSessionId.value = sessionId
        _isCollaborating.value = true

        // Without .catch, a listener error (e.g. permission denied after being
        // removed from a session) is rethrown inside launch and crashes the app.
        sessionJob = viewModelScope.launch {
            CollaborationRepository.observeSession(sessionId)
                .catch { e ->
                    _error.value = e.message
                    _isCollaborating.value = false
                }
                .collect { updated ->
                    _session.value = updated
                    if (updated == null || updated.status != "active") {
                        _isCollaborating.value = false
                    }
                }
        }

        tracksJob = viewModelScope.launch {
            CollaborationRepository.observeTracks(sessionId)
                .catch { e -> _error.value = e.message }
                .collect { updated -> _tracks.value = updated }
        }
    }

    private fun stopObserving() {
        sessionJob?.cancel()
        tracksJob?.cancel()
        sessionJob = null
        tracksJob = null
    }

    // ---- Track writes (all ordered through the queue) ------------------------

    fun addTrack(track: Track) {
        val sessionId = _currentSessionId.value ?: return
        enqueue { CollaborationRepository.addTrack(sessionId, track) }
    }

    fun updateTrack(track: Track) {
        val sessionId = _currentSessionId.value ?: return
        val alreadyQueued = pendingUpdates.containsKey(track.id)
        pendingUpdates[track.id] = track
        if (alreadyQueued) return // the queued job will pick up the latest state

        enqueue {
            val latest = pendingUpdates.remove(track.id) ?: return@enqueue
            CollaborationRepository.updateTrack(sessionId, latest)
        }
    }

    fun deleteTrack(trackId: String) {
        val sessionId = _currentSessionId.value ?: return
        enqueue { CollaborationRepository.deleteTrack(sessionId, trackId) }
    }

    fun assignTrack(trackId: String, newOwnerId: String) {
        val sessionId = _currentSessionId.value ?: return
        enqueue { CollaborationRepository.assignTrack(sessionId, trackId, newOwnerId) }
    }

    fun leaveSession() {
        val sessionId = _currentSessionId.value ?: return

        // Stop listening BEFORE leaving: once the user is removed from the
        // session, the listeners would otherwise get permission errors.
        stopObserving()

        viewModelScope.launch {
            try {
                // Let any queued edits land first (they'd be denied afterwards).
                val drained = CompletableDeferred<Unit>()
                enqueue { drained.complete(Unit) }
                drained.await()

                CollaborationRepository.leaveSession(sessionId)
                clearSession()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.message
                startObserving(sessionId) // leave failed; resume listening
            }
        }
    }

    fun clearSession() {
        stopObserving()
        pendingUpdates.clear()
        _session.value = null
        _tracks.value = emptyList()
        _currentSessionId.value = null
        _isCollaborating.value = false
    }

    fun clearError() {
        _error.value = null
    }
}