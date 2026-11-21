package com.example.csc490seniorproject.moderation

import com.google.firebase.Timestamp

data class FinishedReport(
    val targetUid: String = "",
    val caseId: String = "",
    val decision: String = "",
    val reason: String = "",
    val moderatorId: String = "",
    val reportCount: Int = 0,
    val createdAt: Timestamp? = null
)