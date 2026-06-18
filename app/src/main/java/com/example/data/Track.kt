package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class Track(
    @PrimaryKey val uriString: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long, // in milliseconds
    val fileName: String,
    val dateAdded: Long = System.currentTimeMillis()
) {
    // Elegant formatted duration string (e.g., "03:45" or "1:02:15")
    fun getFormattedDuration(): String {
        val totalSeconds = duration / 1000
        val hr = totalSeconds / 3600
        val min = (totalSeconds % 3600) / 60
        val sec = totalSeconds % 60
        return if (hr > 0) {
            String.format("%d:%02d:%02d", hr, min, sec)
        } else {
            String.format("%02d:%02d", min, sec)
        }
    }
}
