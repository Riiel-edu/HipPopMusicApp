package com.example.csc490seniorproject.data

import com.example.csc490seniorproject.objects.Beatmap
import com.example.csc490seniorproject.objects.Song
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.toObject
import com.google.firebase.firestore.toObjects
import kotlinx.coroutines.tasks.await

object SongRepository {
    private val db = Firebase.firestore
    private val songs = db.collection("songs")
    private val beatmaps = db.collection("beatmaps")

    // ---------- Songs ----------

    suspend fun saveSong(song: Song) {
        songs.document(song.id).set(song.copy(updatedAt = null)).await()
    }

    suspend fun loadSong(id: String): Song? =
        songs.document(id).get().await().toObject<Song>()

    suspend fun mySongs(uid: String): List<Song> =
        songs.whereEqualTo("ownerId", uid).get().await().toObjects<Song>()

    // Deletes the song and its beatmap atomically (no query needed now)
    suspend fun deleteSong(songId: String) {
        db.runBatch { batch ->
            batch.delete(beatmaps.document(songId))
            batch.delete(songs.document(songId))
        }.await()
    }

    // ---------- Beatmap (document ID = song ID) ----------

    suspend fun saveBeatmap(songId: String, ownerId: String, map: Beatmap) {
        beatmaps.document(songId)
            .set(map.copy(songId = songId, ownerId = ownerId, updatedAt = null))
            .await()
    }

    suspend fun loadBeatmap(songId: String): Beatmap? =
        beatmaps.document(songId).get().await().toObject<Beatmap>()

    // ---------- Both together ----------

    // Song first, because the beatmap rule looks up the parent song
    suspend fun saveAll(song: Song, beatmap: Beatmap) {
        saveSong(song)
        saveBeatmap(song.id, song.ownerId, beatmap)
    }

    suspend fun updateSongDetails(id: String, name: String, genre: List<String>) {
        songs.document(id).update(
            mapOf("name" to name, "genre" to genre, "updatedAt" to FieldValue.serverTimestamp())
        ).await()
    }

    suspend fun setPublished(id: String, published: Boolean) {
        songs.document(id).update(
            mapOf(
                "published" to published,
                "friendsOnly" to false,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    suspend fun setFriendsOnly(id: String, friendsOnly: Boolean) {
        songs.document(id).update(
            mapOf(
                "friendsOnly" to friendsOnly,
                "published" to false,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }
}