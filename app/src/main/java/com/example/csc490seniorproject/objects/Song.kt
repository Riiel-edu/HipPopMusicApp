package com.example.csc490seniorproject.objects

import androidx.room.ColumnInfo
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date
import java.util.UUID

data class Song(
    @DocumentId val id: String = UUID.randomUUID().toString(),
    @ColumnInfo val ownerId: String = "",
    @ColumnInfo val published: Boolean = false,
    @ColumnInfo val friendsOnly: Boolean = false,
    @ColumnInfo val sharedWith: List<String> = emptyList(),
    @ColumnInfo val name: String = "Untitled Song",
    @ColumnInfo val rating: Double = 0.0,
    @ColumnInfo val genre: List<String> = emptyList(),
    @ColumnInfo val bpm: Int = 120,
    @ColumnInfo val stepsPerBeat: Int = 4,
    @ColumnInfo val beatsPerBar: Int = 4,
    @ColumnInfo val bars: Int = 64,
    @ColumnInfo val tracks: List<Track> = emptyList(),
    @ServerTimestamp val createdAt: Date? = null,
    @ServerTimestamp val updatedAt: Date? = null
)