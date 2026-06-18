package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(tableName = "tracks")
data class Track(
    @PrimaryKey val uriString: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long, // in milliseconds
    val fileName: String,
    val dateAdded: Long = System.currentTimeMillis(),
    val fileSize: Long = 0L,
    val lastModified: Long = 0L
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

    // Formatted file size like "3,17 МБ"
    fun getFormattedFileSize(): String {
        if (fileSize <= 0) return "0.00 МБ"
        val kb = fileSize / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            String.format(Locale.getDefault(), "%.2f МБ", mb)
        } else {
            String.format(Locale.getDefault(), "%.1f КБ", kb)
        }
    }

    // Formatted date like "09.06.2026"
    fun getFormattedDate(): String {
        val time = if (lastModified > 0) lastModified else dateAdded
        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        return sdf.format(Date(time))
    }
}
