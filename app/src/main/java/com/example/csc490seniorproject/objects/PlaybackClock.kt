package com.example.csc490seniorproject.objects

object PlaybackClock {
    @Volatile
    var startTimeNanos: Long = 0L

    fun start(delayMs: Long = 100L) {
        startTimeNanos =
            System.nanoTime() +
                    delayMs * 1_000_000L
    }
}