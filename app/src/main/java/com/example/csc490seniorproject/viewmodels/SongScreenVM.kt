package com.example.csc490seniorproject.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class SongScreenVM(application: Application) : AndroidViewModel(application) {
    fun saveSongRating(
        songId: String,
        ratingValue: Int,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId == null) {
            onError("Please log in to rate this song.")
            return
        }

        if (songId.isBlank() || ratingValue !in 1..5) {
            onError("Rating must be between 1 and 5.")
            return
        }

        val ratingRef = db.collection("songs")
            .document(songId)
            .collection("ratings")
            .document(userId)

        val ratingData = mapOf(
            "userId" to userId,
            "rating" to ratingValue,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        ratingRef.set(ratingData)
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { e ->
                onError(e.localizedMessage ?: "Unable to save rating.")
            }
    }



    fun uploadSongPfp(songId: String, imageUri: Uri) {
        println("SONG: Starting image upload")

        val storageRef = FirebaseStorage.getInstance(
            "gs://senior-project-13e67.firebasestorage.app"
        )
            .reference
            .child("songPictures/$songId.jpg")

        storageRef.putFile(imageUri)
            .addOnSuccessListener {
                println("SONG: Image uploaded successfully to Storage!")
            }
            .addOnFailureListener { e ->
                println("SONG: Image upload failed: ${e.message}")
                e.printStackTrace()
            }
    }

    fun getSongPfp(songId: String, onResult: (String) -> Unit) {
        FirebaseStorage.getInstance(
            "gs://senior-project-13e67.firebasestorage.app"
        ).reference
            .child("songPictures/$songId.jpg")
            .downloadUrl
            .addOnSuccessListener {
                onResult(it.toString())
            }
            .addOnFailureListener {
                println("SONG: Failed to load song pfp: ${it.message}")
                onResult("")
            }
    }
}