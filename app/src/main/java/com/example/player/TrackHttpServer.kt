package com.example.player

import android.content.Context
import com.example.ui.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Response
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Локальный HTTP-сервер (NanoHTTPD), отдающий те же данные и события о текущем треке,
 * что и системный broadcast [TrackBroadcaster].
 *
 * Сервер слушает **только локальный IP** устройства (Wi-Fi / site-local IPv4), поэтому
 * доступен из локальной сети и не публикуется наружу. По умолчанию выключен и
 * управляется из настроек ([TrackApiConfig]).
 *
 * Эндпоинты:
 *  - `GET /status` — мгновенный JSON-снимок состояния (поля те же, что в extras broadcast);
 *  - `GET /events` — поток Server-Sent Events с теми же событиями, что и broadcast
 *    (`TRACK_CHANGED`, `PLAYBACK_STATE`, `POSITION`, `STOPPED`).
 */
object TrackHttpServer {

    private const val TAG = "HttpServer"

    @Volatile
    private var server: Server? = null

    @Volatile
    private var runningHost: String? = null

    @Volatile
    private var runningPort: Int = -1

    @Volatile
    private var initialized: Boolean = false

    /** Работает ли сервер прямо сейчас. */
    val isRunning: Boolean get() = server != null

    /** Порт, на котором работает сервер (-1, если остановлен). */
    val port: Int get() = runningPort

    fun init(context: Context) {
        // Сервер не требует контекста; метод сохранён для единообразия с остальными интеграциями.
        if (initialized) return
        initialized = true
        Logger.debug("TrackHttpServer инициализирован", TAG)
    }

    /**
     * Запускает сервер на указанном порту. Идемпотентно: повторный вызов с тем же
     * портом ничего не делает. Если локального IP нет — сервер не стартует.
     *
     * Сокетные операции выполняются вне главного потока (иначе Android бросает
     * `NetworkOnMainThreadException`).
     */
    @Synchronized
    fun start(port: Int): Boolean {
        if (server != null && runningPort == port) return true
        return runBlocking(Dispatchers.IO) {
            stopInternal()

            val ip = resolveLocalIpv4()
            if (ip == null) {
                Logger.warn("HTTP-сервер не запущен: нет локального IP (подключитесь к Wi-Fi)", TAG)
                return@runBlocking false
            }

            try {
                val s = Server(ip, port)
                s.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                server = s
                runningHost = ip
                runningPort = port
                Logger.info("HTTP-сервер запущен: http://$ip:$port/", TAG)
                true
            } catch (e: Exception) {
                Logger.error("Не удалось запустить HTTP-сервер на $ip:$port", e, TAG)
                server = null
                runningHost = null
                runningPort = -1
                false
            }
        }
    }

    @Synchronized
    fun stop() {
        runBlocking(Dispatchers.IO) { stopInternal() }
    }

    private fun stopInternal() {
        server?.let {
            try {
                it.stop()
            } catch (e: Exception) {
                Logger.warn("Ошибка остановки HTTP-сервера: ${e.message}", TAG)
            }
        }
        server = null
        runningHost = null
        runningPort = -1
        SseHub.stop()
    }

    /** Базовый URL сервера для отображения в UI (например, `http://192.168.0.5:31414/`). */
    fun currentUrl(): String? {
        val host = runningHost ?: return null
        if (server == null) return null
        return "http://$host:$runningPort/"
    }

    /** Локальный IPv4 устройства (Wi-Fi / site-local) или null, если его нет. */
    fun localIpv4(): String? = resolveLocalIpv4()

    private fun resolveLocalIpv4(): String? {
        return try {
            val candidates = mutableListOf<Pair<String, String>>()
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (nif in Collections.list(interfaces)) {
                if (!nif.isUp || nif.isLoopback) continue
                val name = nif.name ?: ""
                for (addr in Collections.list(nif.inetAddresses)) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                        candidates.add(name to (addr.hostAddress ?: continue))
                    }
                }
            }
            candidates
                .sortedBy { (name, _) ->
                    when {
                        name.startsWith("wlan") -> 0
                        name.startsWith("ap") -> 1
                        name.startsWith("eth") -> 2
                        name.startsWith("rndis") -> 3
                        else -> 4
                    }
                }
                .firstOrNull()
                ?.second
        } catch (e: Exception) {
            Logger.warn("Не удалось определить локальный IP: ${e.message}", TAG)
            null
        }
    }

    // ─────────────────────────── HTTP ───────────────────────────

    private class Server(host: String, port: Int) : NanoHTTPD(host, port) {

        /**
         * Gzip нельзя применять к SSE: он буферизует поток и события не доходят вовремя.
         * Ответы сервера маленькие, так что сжатие отключено полностью.
         */
        override fun useGzipWhenAccepted(r: Response): Boolean = false

        override fun serve(session: IHTTPSession): Response {
            return when (session.uri) {
                "/status" -> statusResponse()
                "/events" -> sseResponse()
                "/", "" -> textResponse(indexText(), Response.Status.OK)
                else -> textResponse("Not found", Response.Status.NOT_FOUND)
            }
        }

        private fun statusResponse(): Response {
            val json = TrackStateStream.snapshot().toJson()
            val response = NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", json)
            response.addHeader("Cache-Control", "no-store")
            response.addHeader("Access-Control-Allow-Origin", "*")
            return response
        }

        private fun textResponse(body: String, status: Response.Status): Response {
            val response = NanoHTTPD.newFixedLengthResponse(status, "text/plain; charset=utf-8", body)
            response.addHeader("Access-Control-Allow-Origin", "*")
            return response
        }

        private fun sseResponse(): Response {
            val client = SseHub.open()
            val stream = object : InputStream() {
                private var buf: ByteArray = ByteArray(0)
                private var pos = 0

                private fun fill(): Boolean {
                    val item = client.take()
                    if (item === SseHub.CLOSE) return false
                    buf = (item as String).toByteArray(Charsets.UTF_8)
                    pos = 0
                    return true
                }

                override fun read(): Int {
                    if (pos >= buf.size && !fill()) return -1
                    return buf[pos++].toInt() and 0xFF
                }

                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    if (len == 0) return 0
                    if (pos >= buf.size && !fill()) return -1
                    val n = minOf(len, buf.size - pos)
                    System.arraycopy(buf, pos, b, off, n)
                    pos += n
                    return n
                }

                override fun close() {
                    SseHub.close(client)
                }
            }

            val response = NanoHTTPD.newChunkedResponse(Response.Status.OK, "text/event-stream; charset=utf-8", stream)
            response.addHeader("Cache-Control", "no-cache")
            response.addHeader("Access-Control-Allow-Origin", "*")
            response.addHeader("X-Accel-Buffering", "no")
            // Один поток на клиента: соединение закрывается, когда поток заканчивается.
            response.closeConnection(true)
            return response
        }

        private fun indexText(): String = buildString {
            append("FreeMusical track API\n\n")
            append("GET /status  - current track snapshot (JSON)\n")
            append("GET /events  - server-sent events stream (text/event-stream)\n")
        }
    }

    // ─────────────────────────── SSE hub ───────────────────────────

    /**
     * Рассылка событий всем активным SSE-клиентам. Подписка на [TrackStateStream]
     * живёт только пока есть подключённые клиенты.
     */
    private object SseHub {

        /** Токен завершения потока клиента. */
        val CLOSE = Any()

        private const val TAG = "HttpServer.sse"
        private const val KEEP_ALIVE_MS = 15_000L

        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        private val clients = Collections.synchronizedSet(mutableSetOf<SseClient>())

        private var pumpJob: Job? = null
        private var keepAliveJob: Job? = null

        @Synchronized
        fun open(): SseClient {
            val client = SseClient()
            clients.add(client)
            ensurePump()
            // Мгновенно отдаём текущее состояние, чтобы клиент не ждал первого события.
            client.offer("retry: 3000\n\n")
            client.offer("data: ${TrackStateStream.snapshot().toJson()}\n\n")
            Logger.info("SSE-клиент подключился (всего ${clients.size})", TAG)
            return client
        }

        @Synchronized
        fun close(client: SseClient) {
            val removed = clients.remove(client)
            client.close()
            if (removed) {
                Logger.info("SSE-клиент отключился (осталось ${clients.size})", TAG)
            }
            if (clients.isEmpty()) stopPump()
        }

        @Synchronized
        fun stop() {
            stopPump()
        }

        private fun ensurePump() {
            if (pumpJob?.isActive != true) {
                pumpJob = scope.launch {
                    TrackStateStream.subscribe().collect { state ->
                        broadcast("data: ${state.toJson()}\n\n")
                    }
                }
            }
            if (keepAliveJob?.isActive != true) {
                keepAliveJob = scope.launch {
                    while (true) {
                        delay(KEEP_ALIVE_MS)
                        broadcast(": keep-alive\n\n")
                    }
                }
            }
        }

        private fun stopPump() {
            pumpJob?.cancel()
            pumpJob = null
            keepAliveJob?.cancel()
            keepAliveJob = null
            synchronized(clients) {
                clients.forEach { it.close() }
                clients.clear()
            }
        }

        private fun broadcast(payload: String) {
            synchronized(clients) {
                clients.forEach { it.offer(payload) }
            }
        }
    }

    /** Очередь одного SSE-клиента. */
    private class SseClient {

        private val queue = ArrayBlockingQueue<Any>(CLIENT_QUEUE_CAPACITY)

        @Volatile
        private var closed = false

        fun offer(payload: String) {
            if (closed) return
            if (!queue.offer(payload)) {
                // Медленный клиент: выбрасываем самый старый элемент, чтобы не копить.
                queue.poll()
                queue.offer(payload)
            }
        }

        /** Блокируется до появления данных; при закрытии возвращает [SseHub.CLOSE]. */
        fun take(): Any {
            while (true) {
                val item = queue.poll(1, TimeUnit.SECONDS)
                if (item != null) return item
                if (closed) return SseHub.CLOSE
            }
        }

        fun close() {
            closed = true
        }

        private companion object {
            const val CLIENT_QUEUE_CAPACITY = 64
        }
    }
}
