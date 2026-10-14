package com.example.ui

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

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
    val elapsedRealtime: Long,
    val timestamp: String,
    val category: LogCategory,
    val level: LogLevel,
    val tag: String,
    val thread: String,
    val message: String
) {
    fun toFormattedString(): String =
        "[$timestamp] [${category.name}/${level.name}] [$tag] (${thread}) $message"

    /** Строка для экспорта с абсолютным временем и ленивой трассировкой не хранится. */
    fun toExportString(): String = toFormattedString()
}

/**
 * Централизованный логгер приложения.
 *
 * Возможности:
 *  - структурированные записи в память (экран «Логи») и дублирование в Logcat ([LOGCAT_TAG]);
 *  - уровни и порог подробности (в debug-сборке DEBUG, в release INFO), переключается в рантайме;
 *  - сервисная информация (версия, устройство, память, экран, локаль, сессия) — [systemInfo];
 *  - дозапись в файл `files/logs/app.log` с ротацией (не блокирует вызывающий поток);
 *  - перехват необработанных исключений с сохранением отчёта в `files/logs/crash_*.log`;
 *  - экспорт всего содержимого в текст для отправки/сохранения — [exportText].
 *
 * Инициализация: вызвать [init] из Application.onCreate.
 */
object Logger {

    const val LOGCAT_TAG = "FreeMusical"

    private const val MAX_APP_LOGS = 500
    private const val MAX_API_LOGS = 500
    private const val MAX_LEGACY_LOGS = 1000
    private const val MAX_LOG_FILE_BYTES = 512L * 1024
    private const val LOG_DIR_NAME = "logs"
    private const val LOG_FILE_NAME = "app.log"

    private val nextId = AtomicLong(1L)
    private val lock = Any()

    private val displayFormatter = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    }

    private val exportFormatter = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    }

    /** Идентификатор текущего запуска приложения — помогает отличать сессии. */
    @Volatile
    var sessionId: String = "-"
        private set

    /** Текущий минимальный уровень: записи ниже порога отбрасываются. */
    @Volatile
    var minLevel: LogLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.INFO
        private set

    fun setMinLevel(level: LogLevel) {
        minLevel = level
        info("Уровень логирования изменён на $level", tag = "System")
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

    // --- Файловое логирование ---

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var logFile: File? = null

    private val fileExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            runnable.run()
        }, "logger-file").apply { isDaemon = true }
    }

    init {
        info("Логгер инициализирован (minLevel=$minLevel, debug=${BuildConfig.DEBUG})", tag = "System")
    }

    /**
     * Инициализация файлового логирования, сервисной информации и перехвата крашей.
     * Безопасно вызывать повторно.
     */
    fun init(context: Context) {
        if (appContext != null) return
        val ctx = context.applicationContext
        appContext = ctx
        sessionId = newSessionId()
        logFile = File(File(ctx.filesDir, LOG_DIR_NAME).apply { mkdirs() }, LOG_FILE_NAME)
        installCrashHandler()
        info("────── Сессия $sessionId ──────", tag = "System")
        info(systemInfo(), tag = "System")
        info("Файл логов: ${logFile?.absolutePath ?: "-"}", tag = "System")
    }

    /** Путь к текущему файлу логов или null, если файловое логирование не инициализировано. */
    fun logFilePath(): String? = logFile?.absolutePath

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
        appendToFile(entry)
    }

    fun logApi(message: String, level: LogLevel = LogLevel.INFO, tag: String = "API") {
        if (!isEnabled(level)) return
        val entry = createEntry(LogCategory.API, level, tag, message)
        synchronized(lock) {
            _apiLogs.update { (it + entry).takeLast(MAX_API_LOGS) }
            _logs.update { (it + entry.toFormattedString()).takeLast(MAX_LEGACY_LOGS) }
        }
        appendToFile(entry)
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

    // --- Сервисная информация и экспорт ---

    /**
     * Человекочитаемая сводка о приложении, устройстве, памяти и окружении.
     * Используется и в логах, и на экране «Логи» (вкладка Info).
     */
    @Suppress("DEPRECATION")
    fun systemInfo(): String {
        val ctx = appContext
        val sb = StringBuilder()
        sb.appendLine("Приложение : ${ctx?.packageName ?: "?"}")
        sb.appendLine("Версия     : ${appVersion(ctx)} (build ${BuildConfig.BUILD_TYPE}, debug=${BuildConfig.DEBUG})")
        sb.appendLine("Сессия     : $sessionId")
        sb.appendLine("Активность : ${formatSeconds(android.os.SystemClock.elapsedRealtime())} с момента загрузки")
        sb.appendLine("Время      : ${exportFormatter.get()!!.format(Date())} (${TimeZone.getDefault().id})")
        sb.appendLine("Устройство : ${Build.MANUFACTURER} ${Build.MODEL} (device=${Build.DEVICE}, product=${Build.PRODUCT})")
        sb.appendLine("Android    : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}, ${Build.VERSION.CODENAME})")
        sb.appendLine("ABI        : ${Build.SUPPORTED_ABIS.joinToString(", ")}")
        sb.appendLine("Локаль     : ${Locale.getDefault()}")
        sb.appendLine("CPU        : ${Runtime.getRuntime().availableProcessors()} ядер")
        sb.appendLine("Heap       : max=${mb(Runtime.getRuntime().maxMemory())}, total=${mb(Runtime.getRuntime().totalMemory())}, free=${mb(Runtime.getRuntime().freeMemory())}")
        val ram = memoryInfo(ctx)
        if (ram != null) {
            sb.appendLine("RAM        : всего=${mb(ram.totalMem)}, доступно=${mb(ram.availMem)}, порог=${mb(ram.threshold)}, мало=${ram.lowMemory}")
        }
        ctx?.resources?.displayMetrics?.let { dm ->
            sb.appendLine("Экран      : ${dm.widthPixels}x${dm.heightPixels} px, density=${dm.density}, dpi=${dm.densityDpi}")
        }
        sb.appendLine("Логи       : ${logFile?.absolutePath ?: "-"} (minLevel=$minLevel)")
        return sb.toString().trimEnd()
    }

    /** Полный дамп памяти логов (заголовок + App + API) для экспорта/отправки. */
    fun exportText(): String {
        val sb = StringBuilder()
        sb.appendLine("========== FreeMusical диагностика ==========")
        sb.appendLine(systemInfo())
        val app = synchronized(lock) { _appLogs.value.toList() }
        val api = synchronized(lock) { _apiLogs.value.toList() }
        sb.appendLine("========== APP (${app.size}) ==========")
        app.forEach { sb.appendLine(it.toExportString()) }
        sb.appendLine("========== API (${api.size}) ==========")
        api.forEach { sb.appendLine(it.toExportString()) }
        return sb.toString()
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

    // --- Внутреннее ---

    private fun createEntry(category: LogCategory, level: LogLevel, tag: String, message: String): LogEntry {
        val nowRealtime = android.os.SystemClock.elapsedRealtime()
        val timestamp = displayFormatter.get()!!.format(Date())
        val thread = Thread.currentThread().name
        val logcatLevel = when (level) {
            LogLevel.DEBUG -> android.util.Log.DEBUG
            LogLevel.INFO -> android.util.Log.INFO
            LogLevel.WARN -> android.util.Log.WARN
            LogLevel.ERROR -> android.util.Log.ERROR
        }
        android.util.Log.println(logcatLevel, LOGCAT_TAG, "[$tag] ($thread) $message")
        return LogEntry(
            id = nextId.getAndIncrement(),
            elapsedRealtime = nowRealtime,
            timestamp = timestamp,
            category = category,
            level = level,
            tag = tag,
            thread = thread,
            message = message
        )
    }

    private fun appendToFile(entry: LogEntry) {
        val file = logFile ?: return
        val line = entry.toExportString()
        fileExecutor.execute {
            try {
                if (file.length() > MAX_LOG_FILE_BYTES) {
                    val rotated = File(file.parentFile, "app.previous.log")
                    if (rotated.exists()) rotated.delete()
                    file.renameTo(rotated)
                }
                file.appendText(line + "\n")
            } catch (_: Exception) {
                // Файловое логирование не должно влиять на работу приложения.
            }
        }
    }

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                error("НЕОБРАБОТАННОЕ ИСКЛЮЧЕНИЕ в потоке '${thread.name}'", throwable, "Crash")
                writeCrashReport(thread, throwable)
            } catch (_: Exception) {
                // Никогда не мешаем обработке краша.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrashReport(thread: Thread, throwable: Throwable) {
        val ctx = appContext ?: return
        try {
            val dir = File(ctx.filesDir, LOG_DIR_NAME).apply { mkdirs() }
            val file = File(dir, "crash_${System.currentTimeMillis()}.log")
            file.writeText(buildString {
                appendLine("=== CRASH ===")
                appendLine("Поток: ${thread.name}")
                appendLine(systemInfo())
                appendLine("--- Трассировка ---")
                appendLine(throwable.stackTraceToString())
                appendLine("--- Последние логи (${_appLogs.value.size}) ---")
                synchronized(lock) { _appLogs.value.forEach { appendLine(it.toExportString()) } }
            })
        } catch (_: Exception) {
            // ignore
        }
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

    private fun newSessionId(): String {
        val hex = (1..3).joinToString("") { "%02x".format(Random.nextInt(256)) }
        return hex.uppercase(Locale.US)
    }

    @Suppress("DEPRECATION")
    private fun appVersion(ctx: Context?): String {
        if (ctx == null) return "?"
        return try {
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
            "${info.versionName} ($code)"
        } catch (_: Exception) {
            "?"
        }
    }

    private fun memoryInfo(ctx: Context?): ActivityManager.MemoryInfo? {
        if (ctx == null) return null
        return try {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return null
            ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun mb(bytes: Long): String = "%.1f МБ".format(Locale.US, bytes / 1024.0 / 1024.0)

    private fun formatSeconds(millis: Long): String = "%.1f".format(Locale.US, millis / 1000.0)
}
