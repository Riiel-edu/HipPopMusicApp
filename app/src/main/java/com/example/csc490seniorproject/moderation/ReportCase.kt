package com.example.csc490seniorproject.moderation

data class ReportCase(
    val targetUid: String = "",
    val targetUsername: String = "",
    val caseId: String = "",
    val reportCount: Int = 0,
    val reports: List<ModerationReport> = emptyList()
)