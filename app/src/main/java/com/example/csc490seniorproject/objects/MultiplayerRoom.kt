package com.example.csc490seniorproject.objects

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class MultiplayerRoom(
    @DocumentId
    val id: String = "",

    val hostId: String = "",
    val opponentId: String = "",

    val playerIds: List<String> = emptyList(),

    val beatmapId: String = "",
    val songId: String = "",

    val status: String = "waiting",

    @ServerTimestamp
    val createdAt: Date? = null
)