package com.example.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogCategory {
    APP,
    API
}

enum class LogLevel {
    INFO,
    WARN,
    ERROR,
    DEBUG
}

data class LogEntry(
    val id: Long,
    val timestamp: String,
    val category: LogCategory,
    val level: LogLevel,
    val tag: String,
    val message: String
) {
    fun toFormattedString(): String = "[$timestamp] [$category/$level] [$tag] $message"
}

object Logger {
    private var nextId = 1L

    private val _appLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    val appLogs = _appLogs.asStateFlow()

    private val _apiLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    val apiLogs = _apiLogs.asStateFlow()

    // Combined logs for backwards compatibility
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()

    init {
        logApp("Logging system initialized", tag = "System")
    }

    fun logApp(message: String, level: LogLevel = LogLevel.INFO, tag: String = "App") {
        val entry = createEntry(LogCategory.APP, level, tag, message)
        _appLogs.value = (_appLogs.value + entry).takeLast(500)
        updateLegacyLogs(entry)
    }

    fun logApi(message: String, level: LogLevel = LogLevel.INFO, tag: String = "API") {
        val entry = createEntry(LogCategory.API, level, tag, message)
        _apiLogs.value = (_apiLogs.value + entry).takeLast(500)
        updateLegacyLogs(entry)
    }

    // Generic fallback log function
    fun log(message: String) {
        if (message.startsWith("HTTP") || message.contains("fetchApi") || message.contains("ApiClient") || message.contains("API")) {
            val level = when {
                message.contains("Error") || message.contains("Exception") || message.contains("HTTP 4") || message.contains("HTTP 5") -> LogLevel.ERROR
                message.contains("Warn") -> LogLevel.WARN
                else -> LogLevel.INFO
            }
            logApi(message, level = level)
        } else {
            val level = when {
                message.contains("Error") || message.contains("Exception") || message.contains("Failed") -> LogLevel.ERROR
                message.contains("Warn") -> LogLevel.WARN
                else -> LogLevel.INFO
            }
            logApp(message, level = level)
        }
    }

    private fun createEntry(category: LogCategory, level: LogLevel, tag: String, message: String): LogEntry {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        return LogEntry(
            id = nextId++,
            timestamp = timestamp,
            category = category,
            level = level,
            tag = tag,
            message = message
        )
    }

    private fun updateLegacyLogs(entry: LogEntry) {
        _logs.value = (_logs.value + entry.toFormattedString()).takeLast(1000)
    }

    fun clearAppLogs() {
        _appLogs.value = emptyList()
        rebuildLegacyLogs()
    }

    fun clearApiLogs() {
        _apiLogs.value = emptyList()
        rebuildLegacyLogs()
    }

    fun clearAll() {
        _appLogs.value = emptyList()
        _apiLogs.value = emptyList()
        _logs.value = emptyList()
    }

    // Backwards compatibility clear
    fun clear() {
        clearAll()
    }

    private fun rebuildLegacyLogs() {
        val combined = (_appLogs.value + _apiLogs.value).sortedBy { it.id }
        _logs.value = combined.map { it.toFormattedString() }
    }
}
