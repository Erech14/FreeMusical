package com.example.ui

import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

enum class LogCategory {
    APP,
    API
}

/** Уровни логирования: от подробного к самому важному. */
enum class LogLevel(val priority: Int) {
    DEBUG(0),
    INFO(1),
    WARN(2),
    ERROR(3)
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

/**
 * Централизованный логгер приложения.
 *
 * Пишет структурированные записи в память (их показывает экран «Логи») и дублирует
 * в Logcat под тегом [LOGCAT_TAG]. Порог подробности настраивается [setMinLevel];
 * в debug-сборке по умолчанию показывается всё (DEBUG), в release — от INFO.
 *
 * Используйте теги по компонентам: "Player", "AudioFocus", "Scanner", "Storage",
 * "Playlist", "Covers", "Stats", "Network", "Settings", "UI", "App".
 */
object Logger {

    const val LOGCAT_TAG = "FreeMusical"

    private const val MAX_APP_LOGS = 500
    private const val MAX_API_LOGS = 500
    private const val MAX_LEGACY_LOGS = 1000

    private val nextId = AtomicLong(1L)
    private val lock = Any()

    // Cached per-thread formatter: creating SimpleDateFormat on every log call was costly.
    private val timeFormatter = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    }

    /** Текущий минимальный уровень: записи ниже порога отбрасываются. */
    @Volatile
    var minLevel: LogLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.INFO
        private set

    fun setMinLevel(level: LogLevel) {
        minLevel = level
    }

    fun isEnabled(level: LogLevel): Boolean = level.priority >= minLevel.priority

    fun isDebugEnabled(): Boolean = isEnabled(LogLevel.DEBUG)

    private val _appLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    val appLogs = _appLogs.asStateFlow()

    private val _apiLogs = MutableStateFlow<List<LogEntry>>(emptyList())
    val apiLogs = _apiLogs.asStateFlow()

    // Combined logs for backwards compatibility
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()

    init {
        info("Логирование запущено (minLevel=$minLevel, debug=${BuildConfig.DEBUG})", tag = "System")
    }

    // --- Удобные методы для прикладных логов (категория APP) ---

    fun debug(message: String, tag: String = "App") = logApp(message, LogLevel.DEBUG, tag)

    fun info(message: String, tag: String = "App") = logApp(message, LogLevel.INFO, tag)

    fun warn(message: String, tag: String = "App") = logApp(message, LogLevel.WARN, tag)

    fun error(message: String, tag: String = "App") = logApp(message, LogLevel.ERROR, tag)

    /** Логирует ошибку вместе с короткой трассировкой исключения. */
    fun error(message: String, throwable: Throwable?, tag: String = "App") {
        logApp(formatThrowable(message, throwable), LogLevel.ERROR, tag)
    }

    // --- Основные методы ---

    fun logApp(message: String, level: LogLevel = LogLevel.INFO, tag: String = "App") {
        if (!isEnabled(level)) return
        val entry = createEntry(LogCategory.APP, level, tag, message)
        synchronized(lock) {
            _appLogs.update { (it + entry).takeLast(MAX_APP_LOGS) }
            _logs.update { (it + entry.toFormattedString()).takeLast(MAX_LEGACY_LOGS) }
        }
    }

    fun logApi(message: String, level: LogLevel = LogLevel.INFO, tag: String = "API") {
        if (!isEnabled(level)) return
        val entry = createEntry(LogCategory.API, level, tag, message)
        synchronized(lock) {
            _apiLogs.update { (it + entry).takeLast(MAX_API_LOGS) }
            _logs.update { (it + entry.toFormattedString()).takeLast(MAX_LEGACY_LOGS) }
        }
    }

    // Generic fallback log function
    fun log(message: String) {
        val lower = message.lowercase()
        val isApi = lower.startsWith("http") ||
            lower.contains("fetchapi") ||
            lower.contains("apiclient") ||
            lower.contains("api ")
        val level = when {
            lower.contains("error") ||
                lower.contains("exception") ||
                lower.contains("failed") ||
                lower.contains("http 4") ||
                lower.contains("http 5") -> LogLevel.ERROR
            lower.contains("warn") -> LogLevel.WARN
            else -> LogLevel.INFO
        }
        if (isApi) logApi(message, level) else logApp(message, level)
    }

    // --- Тайминги для измерения производительности операций ---

    /** Монотонная метка времени (мс) для замеров длительности. */
    fun now(): Long = android.os.SystemClock.elapsedRealtime()

    fun logTiming(tag: String, label: String, startMs: Long, level: LogLevel = LogLevel.INFO) {
        logApp("$label: ${now() - startMs} ms", level, tag)
    }

    // --- Управление ---

    fun clearAppLogs() {
        synchronized(lock) {
            _appLogs.value = emptyList()
            rebuildLegacyLogs()
        }
    }

    fun clearApiLogs() {
        synchronized(lock) {
            _apiLogs.value = emptyList()
            rebuildLegacyLogs()
        }
    }

    fun clearAll() {
        synchronized(lock) {
            _appLogs.value = emptyList()
            _apiLogs.value = emptyList()
            _logs.value = emptyList()
        }
    }

    // Backwards compatibility clear
    fun clear() = clearAll()

    private fun createEntry(category: LogCategory, level: LogLevel, tag: String, message: String): LogEntry {
        val timestamp = timeFormatter.get()!!.format(Date())
        val logcatLevel = when (level) {
            LogLevel.DEBUG -> android.util.Log.DEBUG
            LogLevel.INFO -> android.util.Log.INFO
            LogLevel.WARN -> android.util.Log.WARN
            LogLevel.ERROR -> android.util.Log.ERROR
        }
        android.util.Log.println(logcatLevel, LOGCAT_TAG, "[$tag] $message")
        return LogEntry(
            id = nextId.getAndIncrement(),
            timestamp = timestamp,
            category = category,
            level = level,
            tag = tag,
            message = message
        )
    }

    private fun formatThrowable(message: String, throwable: Throwable?): String {
        if (throwable == null) return message
        val frames = throwable.stackTrace.take(4).joinToString(" <- ") {
            "${it.className.substringAfterLast('.')}.${it.methodName}(${it.fileName}:${it.lineNumber})"
        }
        return buildString {
            append(message)
            append(" | ")
            append(throwable.javaClass.simpleName)
            throwable.message?.let { append(": ").append(it) }
            if (frames.isNotEmpty()) append(" @ ").append(frames)
        }
    }

    private fun rebuildLegacyLogs() {
        val combined = (_appLogs.value + _apiLogs.value).sortedBy { it.id }
        _logs.value = combined.map { it.toFormattedString() }
    }
}
