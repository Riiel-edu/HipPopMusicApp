
package com.example.csc490seniorproject.objects

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class CollaborationParticipant(
    val userId: String = "",
    val displayName: String = "",
    val isActive: Boolean = true,
    @ServerTimestamp val joinedAt: Date? = null
)
