package com.example.csc490seniorproject.objects

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class MultiplayerPlayerStats(
    val userId: String = "",
    val username: String = "",

    val score: Int = 0,
    val percentage: Double = 0.0,

    val combo: Int = 0,
    val maxCombo: Int = 0,

    val isOnFire: Boolean = false,
    val totalStars: Int = 0,

    val perfectCount: Int = 0,
    val goodCount: Int = 0,
    val missCount: Int = 0,

    val finished: Boolean = false,

    @ServerTimestamp
    val updatedAt: Date? = null
)