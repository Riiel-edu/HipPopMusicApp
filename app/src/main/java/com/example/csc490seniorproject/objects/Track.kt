package com.example.csc490seniorproject.objects

data class Track(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "Track",
    val instrument: Instrument = Instrument.PIANO,
    val volume: Float = 0.8f,
    val muted: Boolean = false,
    val solo: Boolean = false,
    val notes: List<Note> = emptyList(),
    val ownerId: String = ""
)
