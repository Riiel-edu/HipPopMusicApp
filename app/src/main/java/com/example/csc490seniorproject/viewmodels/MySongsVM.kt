package com.example.csc490seniorproject.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.csc490seniorproject.data.SongRepository
import com.example.csc490seniorproject.objects.Beatmap
import com.example.csc490seniorproject.objects.Song
import com.example.csc490seniorproject.objects.newSong
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class MySongsVM : ViewModel() {
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun refresh() = viewModelScope.launch {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
        try {
            _songs.value = SongRepository.mySongs(uid).sortedByDescending { it.updatedAt?.time ?: 0L }
        } catch (e: Exception) {
            _error.value = "Couldn't load songs: ${e.message}"
        }
    }

    /** Creates the song AND its beatmap up front, then hands back the id to open in the editor. */
    fun createSong(
        name: String,
        tags: List<String>,
        description: String,
        onCreated: (String) -> Unit
    ) = viewModelScope.launch {
        val uid = FirebaseAuth.getInstance().currentUser?.uid

        if (uid == null) {
            _error.value = "Not signed in"
            return@launch
        }

        try {
            val song = newSong(ownerId = uid).copy(
                name = name.ifBlank { "Untitled Song" }
            )

            val map = Beatmap(
                songId = song.id,
                ownerId = uid,
                title = song.name,
                bpm = song.bpm,
                stepsPerBeat = song.stepsPerBeat,
                beatsPerBar = song.beatsPerBar,
                bars = song.bars
            )

            SongRepository.saveAll(song, map)

            // Save the editable song details directly to Firestore.
            FirebaseFirestore.getInstance()
                .collection("songs")
                .document(song.id)
                .update(
                    mapOf(
                        "tags" to tags,
                        "description" to description.trim()
                    )
                )
                .await()

            refresh()
            onCreated(song.id)

        } catch (e: Exception) {
            _error.value = "Couldn't create song: ${e.message}"
        }
    }

    fun createSongFromTemplate(templateId: String, onCreated: (String) -> Unit) =
        viewModelScope.launch {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid == null) {
                _error.value = "Not signed in"
                return@launch
            }

            try {
                val db = FirebaseFirestore.getInstance()

                // Get the selected template
                val templateSnapshot = db.collection("templates")
                    .document(templateId)
                    .get()
                    .await()

                val templateData = templateSnapshot.data?.toMutableMap()
                    ?: throw Exception("Template not found")

                // Set ownership and creation time
                templateData["ownerId"] = uid
                templateData["createdAt"] = FieldValue.serverTimestamp()
                templateData["tags"] =
                    (templateData["tags"] as? List<*>)
                        ?.filterIsInstance<String>()
                        ?: (templateData["genre"] as? List<*>)
                            ?.filterIsInstance<String>()
                                ?: emptyList<String>()

                templateData["description"] =
                    templateData["description"] as? String ?: ""

                // Create a new song document
                val newSongRef = db.collection("songs").document()
                newSongRef.set(templateData).await()

                // Create an empty beatmap using the template's song settings
                val emptyBeatmap = Beatmap(
                    songId = newSongRef.id,
                    ownerId = uid,
                    title = templateData["name"] as? String ?: "Untitled Song",
                    bpm = (templateData["bpm"] as? Number)?.toInt() ?: 120,
                    stepsPerBeat = (templateData["stepsPerBeat"] as? Number)?.toInt() ?: 4,
                    beatsPerBar = (templateData["beatsPerBar"] as? Number)?.toInt() ?: 4,
                    bars = (templateData["bars"] as? Number)?.toInt() ?: 4
                )

                // Save the empty beatmap
                SongRepository.saveBeatmap(newSongRef.id, uid, emptyBeatmap)

                println("FIREBASE: Template copied with empty beatmap: ${newSongRef.id}")

                refresh()
                onCreated(newSongRef.id)

            } catch (e: Exception) {
                _error.value = "Couldn't copy template: ${e.message}"
                println("FIREBASE: Template copy failed: ${e.message}")
            }
        }

    fun getTemplates(onLoaded: (List<Pair<String, String>>) -> Unit) =
        viewModelScope.launch {
            try {
                val snapshot = FirebaseFirestore.getInstance()
                    .collection("templates")
                    .get()
                    .await()

                val templates = snapshot.documents.map { document ->
                    Pair(
                        document.id,
                        document.getString("name") ?: "Untitled Template"
                    )
                }

                onLoaded(templates)

            } catch (e: Exception) {
                _error.value = "Couldn't load templates: ${e.message}"
                println("FIREBASE: Template loading failed: ${e.message}")
            }
        }
    fun updateDetails(
        id: String,
        name: String,
        tags: List<String>,
        description: String
    ) = viewModelScope.launch {
        try {
            FirebaseFirestore.getInstance()
                .collection("songs")
                .document(id)
                .update(
                    mapOf(
                        "name" to name.ifBlank { "Untitled Song" },
                        "tags" to tags,
                        "description" to description.trim()
                    )
                )
                .await()

            refresh()

        } catch (e: Exception) {
            _error.value = "Couldn't update song details: ${e.message}"
        }
    }

    fun togglePublish(song: Song) = viewModelScope.launch {
        try { SongRepository.setPublished(song.id, !song.published); refresh() }
        catch (e: Exception) { _error.value = "Couldn't publish: ${e.message}" }
    }

    fun toggleFriendsOnly(song: Song) = viewModelScope.launch {
        try { SongRepository.setFriendsOnly(song.id, !song.friendsOnly); refresh() }
        catch (e: Exception) { _error.value = "Couldn't update visibility: ${e.message}" }
    }

    fun delete(id: String) = viewModelScope.launch {
        try { SongRepository.deleteSong(id); refresh() }
        catch (e: Exception) { _error.value = "Couldn't delete: ${e.message}" }
    }
}