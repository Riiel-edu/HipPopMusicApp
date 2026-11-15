package com.example.csc490seniorproject.objects

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class CollaborationSession(
    val id: String = "",
    val songId: String = "",
    val songName: String = "Untitled Song",
    val hostUserId: String = "",
    val participantIds: List<String> = emptyList(),
    val bpm: Int = 120,
    val stepsPerBeat: Int = 4,
    val beatsPerBar: Int = 4,
    val bars: Int = 64,
    val status: String = "active",
    @ServerTimestamp val createdAt: Date? = null,
    @ServerTimestamp val updatedAt: Date? = null
)