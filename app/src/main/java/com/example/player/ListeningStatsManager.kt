package com.example.player

import android.content.Context
import com.example.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object ListeningStatsManager {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var appContext: Context? = null
    
    // In-memory history of last 6 tracks
    private val history = mutableListOf<TrackStat>()
    private var currentRecordId: Int = 1
    
    // Tracking current track progress
    private var currentTrackTitle: String = ""
    private var maxPositionReached: Long = 0L
    private var currentDuration: Long = 0L

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        
        // Determine the next ID based on the existing file
        scope.launch {
            val file = File(appContext!!.filesDir, "listening_stats.json")
            if (file.exists()) {
                try {
                    val array = JSONArray(file.readText())
                    if (array.length() > 0) {
                        val lastObj = array.getJSONObject(array.length() - 1)
                        currentRecordId = lastObj.optInt("id", 0) + 1
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun onTrackCompleted() {
        scope.launch {
            if (currentTrackTitle.isNotEmpty() && history.isNotEmpty()) {
                val lastIdx = history.size - 1
                maxPositionReached = currentDuration // Force 100%
                history[lastIdx] = TrackStat(currentTrackTitle, 100, "прослушан полностью")
                saveToJson()
            }
        }
    }

    fun onTrackChanged(track: Track?) {
        scope.launch {
            if (currentTrackTitle.isNotEmpty()) {
                // Finalize previous track in history
                val lastStatus = if (history.isNotEmpty()) history.last().status else ""
                if (lastStatus != "прослушан полностью") {
                    val pct = if (currentDuration > 0) ((maxPositionReached.toDouble() / currentDuration) * 100).toInt().coerceIn(0, 100) else 0
                    val status = if (pct == 100) "прослушан полностью" else if (pct >= 5) "прервано" else "пропущено"
                    
                    // Update the last element in the history
                    if (history.isNotEmpty()) {
                        val lastIdx = history.size - 1
                        history[lastIdx] = TrackStat(currentTrackTitle, pct, status)
                    }
                }
            }
            
            if (track != null) {
                currentTrackTitle = track.title
                currentDuration = track.duration
                maxPositionReached = 0L
                
                // Add new current track to history with 0% initially
                history.add(TrackStat(currentTrackTitle, 0, "текущий трек, который сейчас слушает пользователь"))
                while (history.size > 6) {
                    history.removeAt(0)
                }
                
                // We increment ID for a new track listen record
                currentRecordId++
                
                saveToJson()
            } else {
                currentTrackTitle = ""
            }
        }
    }

    fun onProgressUpdated(position: Long) {
        if (position > maxPositionReached) {
            maxPositionReached = position
        }
    }
    
    fun onPlaybackPaused() {
        // Save current progress when paused
        scope.launch {
            if (currentTrackTitle.isNotEmpty()) {
                updateCurrentHistoryAndSave()
            }
        }
    }
    
    private fun updateCurrentHistoryAndSave() {
        val pct = if (currentDuration > 0) ((maxPositionReached.toDouble() / currentDuration) * 100).toInt().coerceIn(0, 100) else 0
        if (history.isNotEmpty()) {
            val lastIdx = history.size - 1
            if (history[lastIdx].status != "прослушан полностью") {
                history[lastIdx] = TrackStat(currentTrackTitle, pct, "текущий трек, который сейчас слушает пользователь")
            }
        }
        saveToJson()
    }

    @Synchronized
    private fun saveToJson() {
        val context = appContext ?: return
        if (currentTrackTitle.isEmpty()) return
        
        try {
            val file = File(context.filesDir, "listening_stats.json")
            val jsonArray = if (file.exists()) {
                try {
                    JSONArray(file.readText())
                } catch (e: Exception) {
                    JSONArray()
                }
            } else {
                JSONArray()
            }
            
            val pct = if (currentDuration > 0) ((maxPositionReached.toDouble() / currentDuration) * 100).toInt().coerceIn(0, 100) else 0
            
            // Generate timestamp rounded to nearest 15 mins
            val cal = Calendar.getInstance()
            val minute = cal.get(Calendar.MINUTE)
            val mod = minute % 15
            cal.add(Calendar.MINUTE, if (mod < 8) -mod else (15 - mod))
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val timestamp = sdf.format(cal.time)
            
            val recordObj = JSONObject()
            recordObj.put("id", currentRecordId)
            recordObj.put("track_title", currentTrackTitle)
            recordObj.put("listening_percentage", pct)
            recordObj.put("timestamp", timestamp)
            
            val historyArray = JSONArray()
            for (i in 0 until history.size) {
                val stat = history[i]
                val statObj = JSONObject()
                statObj.put("title", stat.title)
                if (i == history.size - 1 && stat.status.startsWith("текущий")) {
                    statObj.put("percentage", pct)
                } else {
                    statObj.put("percentage", stat.percentage)
                }
                statObj.put("status", stat.status)
                historyArray.put(statObj)
            }
            recordObj.put("history", historyArray)
            
            // Check if last record has the same ID. If so, update it. If not, append.
            var updated = false
            if (jsonArray.length() > 0) {
                val lastObj = jsonArray.getJSONObject(jsonArray.length() - 1)
                if (lastObj.optInt("id") == currentRecordId) {
                    jsonArray.put(jsonArray.length() - 1, recordObj)
                    updated = true
                }
            }
            
            if (!updated) {
                jsonArray.put(recordObj)
            }
            
            file.writeText(jsonArray.toString(4))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

data class TrackStat(val title: String, val percentage: Int, val status: String)
