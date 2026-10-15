package com.example.player

import android.content.Context
import com.example.data.Track
import com.example.ui.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/**
 * Снимок состояния плеера — те же данные и семантика событий, что и в broadcast
 * ([TrackBroadcaster.ACTION_TRACK_STATE]). Используется и системным broadcast-ом,
 * и локальным HTTP-сервером ([TrackHttpServer]).
 *
 * @param event одно из [TrackStateStream.EVENT_TRACK_CHANGED], [TrackStateStream.EVENT_PLAYBACK_STATE],
 *              [TrackStateStream.EVENT_POSITION], [TrackStateStream.EVENT_STOPPED]
 *              или [TrackStateStream.EVENT_STATUS] (мгновенный снимок для `GET /status`).
 */
data class TrackState(
    val event: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val uri: String?,
    val durationMs: Long,
    val positionMs: Long,
    val isPlaying: Boolean,
    val shuffle: Boolean,
    val index: Int,
    val playlistSize: Int,
    val sessionId: String,
    val timestampMs: Long
) {

    /** Сериализация в JSON с теми же ключами, что и extras broadcast. */
    fun toJson(): String = JSONObject().apply {
        put("event", event)
        put("title", title ?: JSONObject.NULL)
        put("artist", artist ?: JSONObject.NULL)
        put("album", album ?: JSONObject.NULL)
        put("uri", uri ?: JSONObject.NULL)
        put("durationMs", durationMs)
        put("positionMs", positionMs)
        put("isPlaying", isPlaying)
        put("shuffle", shuffle)
        put("index", index)
        put("playlistSize", playlistSize)
        put("sessionId", sessionId)
        put("timestampMs", timestampMs)
    }.toString()

    companion object {
        /** Полный набор ключей JSON — ровно совпадает с extras broadcast. */
        val JSON_KEYS = listOf(
            "event", "title", "artist", "album", "uri", "durationMs",
            "positionMs", "isPlaying", "shuffle", "index", "playlistSize",
            "sessionId", "timestampMs"
        )
    }
}

/**
 * Единый источник событий о текущем треке для broadcast и HTTP-сервера.
 *
 * Upstream (наблюдение за плеером + heartbeat позиции раз в секунду) работает
 * только пока есть хотя бы один подписчик, поэтому при выключенных broadcast и
 * HTTP-сервере ресурсы не расходуются.
 */
object TrackStateStream {

    const val EVENT_TRACK_CHANGED = "TRACK_CHANGED"
    const val EVENT_PLAYBACK_STATE = "PLAYBACK_STATE"
    const val EVENT_POSITION = "POSITION"
    const val EVENT_STOPPED = "STOPPED"

    /** Событие мгновенного снимка (`GET /status`), у broadcast аналога нет. */
    const val EVENT_STATUS = "STATUS"

    private const val TAG = "TrackState"

    /** Период heartbeat-а позиции во время воспроизведения. */
    private const val HEARTBEAT_MS = 1_000L

    /** Как часто (в heartbeat-тиках) писать heartbeat в лог, чтобы не спамить при DEBUG. */
    private const val POSITION_LOG_EVERY = 10L

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val lock = Any()

    @Volatile
    private var appContext: Context? = null

    // Последнее отправленное состояние — чтобы отличать смену трека от смены play/pause.
    private var lastSentUri: String? = null
    private var lastSentPlaying: Boolean? = null
    private var lastSentShuffle: Boolean? = null

    private var heartbeatTick = 0L

    private val _events = MutableSharedFlow<TrackState>(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Поток событий, идентичный broadcast. */
    val events: SharedFlow<TrackState> = _events.asSharedFlow()

    private val subscribers = AtomicInteger(0)
    private var upstreamJob: Job? = null

    /** Инициализация: идемпотентна, безопасно вызывать повторно. */
    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        Logger.debug("TrackStateStream инициализирован", TAG)
    }

    /**
     * Подписка на поток событий. Пока есть хотя бы один подписчик, работает upstream.
     * Коллектор обязан быть «отписан» (отмена Job), чтобы освободить ресурсы.
     */
    fun subscribe(): Flow<TrackState> = flow {
        acquire()
        try {
            _events.collect { emit(it) }
        } finally {
            release()
        }
    }

    /** Мгновенный снимок текущего состояния плеера (event = [EVENT_STATUS]). */
    fun snapshot(): TrackState {
        val track = MusicPlayerEngine.currentTrack.value
        return TrackState(
            event = EVENT_STATUS,
            title = track?.title,
            artist = track?.artist,
            album = track?.album,
            uri = track?.uriString,
            durationMs = MusicPlayerEngine.duration.value,
            positionMs = MusicPlayerEngine.currentPosition.value,
            isPlaying = MusicPlayerEngine.isPlaying.value,
            shuffle = MusicPlayerEngine.isShuffleEnabled.value,
            index = MusicPlayerEngine.playlistIndex,
            playlistSize = MusicPlayerEngine.playlistSize,
            sessionId = Logger.sessionId,
            timestampMs = System.currentTimeMillis()
        )
    }

    @Synchronized
    private fun acquire() {
        if (subscribers.incrementAndGet() == 1) {
            startUpstream()
        }
    }

    @Synchronized
    private fun release() {
        if (subscribers.decrementAndGet() <= 0) {
            subscribers.set(0)
            stopUpstream()
        }
    }

    private fun startUpstream() {
        if (upstreamJob?.isActive == true) return
        // Сбрасываем diff, чтобы после повторного включения отдать актуальное состояние.
        synchronized(lock) {
            lastSentUri = null
            lastSentPlaying = null
            lastSentShuffle = null
        }
        Logger.debug("TrackStateStream: upstream запущен", TAG)
        upstreamJob = scope.launch {
            val stateEvents = combine(
                MusicPlayerEngine.currentTrack,
                MusicPlayerEngine.isPlaying,
                MusicPlayerEngine.isShuffleEnabled
            ) { track, playing, shuffle -> Triple(track, playing, shuffle) }
                .mapNotNull { (track, playing, shuffle) -> onState(track, playing, shuffle) }

            val heartbeat = flow {
                while (true) {
                    delay(HEARTBEAT_MS)
                    heartbeatTick++
                    val track = MusicPlayerEngine.currentTrack.value ?: continue
                    if (!MusicPlayerEngine.isPlaying.value) continue
                    emit(
                        TrackState(
                            event = EVENT_POSITION,
                            title = track.title,
                            artist = track.artist,
                            album = track.album,
                            uri = track.uriString,
                            durationMs = MusicPlayerEngine.duration.value,
                            positionMs = MusicPlayerEngine.currentPosition.value,
                            isPlaying = true,
                            shuffle = MusicPlayerEngine.isShuffleEnabled.value,
                            index = MusicPlayerEngine.playlistIndex,
                            playlistSize = MusicPlayerEngine.playlistSize,
                            sessionId = Logger.sessionId,
                            timestampMs = System.currentTimeMillis()
                        )
                    )
                }
            }

            merge(stateEvents, heartbeat).collect { state ->
                _events.emit(state)
                if (state.event != EVENT_POSITION || heartbeatTick % POSITION_LOG_EVERY == 0L) {
                    Logger.debug(
                        "State ${state.event}: '${state.title ?: "-"}' isPlaying=${state.isPlaying} " +
                            "${state.positionMs}/${state.durationMs} ms shuffle=${state.shuffle}",
                        TAG
                    )
                }
            }
        }
    }

    private fun stopUpstream() {
        upstreamJob?.cancel()
        upstreamJob = null
        Logger.debug("TrackStateStream: upstream остановлен", TAG)
    }

    private fun onState(track: Track?, playing: Boolean, shuffle: Boolean): TrackState? {
        var event: String? = null
        synchronized(lock) {
            val uri = track?.uriString
            if (track == null) {
                // Ничего не играло — отдавать STOPPED не нужно.
                if (lastSentUri == null) return null
                lastSentUri = null
                lastSentPlaying = null
                lastSentShuffle = null
                event = EVENT_STOPPED
            } else {
                val trackChanged = uri != lastSentUri
                val stateChanged = playing != lastSentPlaying || shuffle != lastSentShuffle
                if (!trackChanged && !stateChanged) return null
                lastSentUri = uri
                lastSentPlaying = playing
                lastSentShuffle = shuffle
                event = if (trackChanged) EVENT_TRACK_CHANGED else EVENT_PLAYBACK_STATE
            }
        }
        val resolved = event ?: return null
        return TrackState(
            event = resolved,
            title = track?.title,
            artist = track?.artist,
            album = track?.album,
            uri = track?.uriString,
            durationMs = MusicPlayerEngine.duration.value,
            positionMs = MusicPlayerEngine.currentPosition.value,
            isPlaying = playing,
            shuffle = shuffle,
            index = MusicPlayerEngine.playlistIndex,
            playlistSize = MusicPlayerEngine.playlistSize,
            sessionId = Logger.sessionId,
            timestampMs = System.currentTimeMillis()
        )
    }
}
