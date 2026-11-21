package com.example.csc490seniorproject.moderation

import com.google.firebase.Timestamp
data class ModerationReport(
    val reporterId: String = "",
    val targetUid: String = "",
    val caseId: String = "",
    val reason: String = "",
    val evidence: String = "",
    val createdAt: Timestamp? = null
)