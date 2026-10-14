
package com.example.player

import android.content.Context
import android.content.Intent
import com.example.data.Track
import com.example.ui.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Отправляет системный broadcast [ACTION_TRACK_STATE] о текущем треке внешним слушателям.
 *
 * Контракт для внешних приложений описан в README. Слушатель обязан регистрировать
 * [android.content.BroadcastReceiver] в рантайме (неявные broadcast-ы не доставляются в
 * manifest-ресиверы начиная с Android 8), а на Android 14+ передавать флаг
 * `RECEIVER_EXPORTED`/`RECEIVER_NOT_EXPORTED`.
 *
 * События:
 *  - [EVENT_TRACK_CHANGED] — сменился трек;
 *  - [EVENT_PLAYBACK_STATE] — изменилось play/pause или режим повтора/случайного выбора;
 *  - [EVENT_POSITION] — heartbeat позиции раз в секунду во время воспроизведения;
 *  - [EVENT_STOPPED] — воспроизведение остановлено (трек сброшен).
 *
 * Инициализация идемпотентна, как у [ListeningStatsManager]. Вызывать из [MusicPlayerEngine].
 */
object TrackBroadcaster {

    /** Action широковещательного сообщения о текущем треке. */
    const val ACTION_TRACK_STATE = "erech14.freemusical.app.action.TRACK_STATE"

    const val EVENT_TRACK_CHANGED = "TRACK_CHANGED"
    const val EVENT_PLAYBACK_STATE = "PLAYBACK_STATE"
    const val EVENT_POSITION = "POSITION"
    const val EVENT_STOPPED = "STOPPED"

    const val EXTRA_EVENT = "event"
    const val EXTRA_TITLE = "title"
    const val EXTRA_ARTIST = "artist"
    const val EXTRA_ALBUM = "album"
    const val EXTRA_URI = "uri"
    const val EXTRA_DURATION_MS = "durationMs"
    const val EXTRA_POSITION_MS = "positionMs"
    const val EXTRA_IS_PLAYING = "isPlaying"
    const val EXTRA_SHUFFLE = "shuffle"
    const val EXTRA_INDEX = "index"
    const val EXTRA_PLAYLIST_SIZE = "playlistSize"
    const val EXTRA_SESSION_ID = "sessionId"
    const val EXTRA_TIMESTAMP_MS = "timestampMs"

    private const val TAG = "Broadcast"

    /** Период heartbeat-а позиции во время воспроизведения. */
    private const val HEARTBEAT_MS = 1_000L

    /** Как часто (в heartbeat-тиках) писать heartbeat в лог, чтобы не спамить при DEBUG. */
    private const val POSITION_LOG_EVERY = 10L

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private val lock = Any()

    @Volatile
    private var appContext: Context? = null

    // Последнее отправленное состояние — чтобы отличать смену трека от смены play/pause.
    private var lastSentUri: String? = null
    private var lastSentPlaying: Boolean? = null
    private var lastSentShuffle: Boolean? = null

    private var observerJob: Job? = null
    private var heartbeatJob: Job? = null
    private var heartbeatTick = 0L

    /** Инициализация: запускает наблюдение за состоянием плеера и heartbeat позиции. Безопасно вызывать повторно. */
    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        Logger.debug("TrackBroadcaster инициализирован (action=$ACTION_TRACK_STATE)", TAG)

        observerJob = scope.launch {
            combine(
                MusicPlayerEngine.currentTrack,
                MusicPlayerEngine.isPlaying,
                MusicPlayerEngine.isShuffleEnabled
            ) { track, playing, shuffle -> Triple(track, playing, shuffle) }
                .collect { (track, playing, shuffle) -> onState(track, playing, shuffle) }
        }

        heartbeatJob = scope.launch {
            while (true) {
                delay(HEARTBEAT_MS)
                heartbeatTick++
                val track = MusicPlayerEngine.currentTrack.value ?: continue
                if (!MusicPlayerEngine.isPlaying.value) continue
                send(
                    event = EVENT_POSITION,
                    track = track,
                    uri = track.uriString,
                    playing = true,
                    shuffle = MusicPlayerEngine.isShuffleEnabled.value,
                    positionMs = MusicPlayerEngine.currentPosition.value,
                    durationMs = MusicPlayerEngine.duration.value,
                    log = heartbeatTick % POSITION_LOG_EVERY == 0L
                )
            }
        }
    }

    /** Останавливает наблюдение и heartbeat (например, для тестов). */
    fun stop() {
        observerJob?.cancel()
        observerJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun onState(track: Track?, playing: Boolean, shuffle: Boolean) {
        var event: String? = null
        synchronized(lock) {
            val uri = track?.uriString
            if (track == null) {
                // Ничего не играло — отдавать STOPPED не нужно.
                if (lastSentUri == null) return
                lastSentUri = null
                lastSentPlaying = null
                lastSentShuffle = null
                event = EVENT_STOPPED
            } else {
                val trackChanged = uri != lastSentUri
                val stateChanged = playing != lastSentPlaying || shuffle != lastSentShuffle
                if (!trackChanged && !stateChanged) return
                lastSentUri = uri
                lastSentPlaying = playing
                lastSentShuffle = shuffle
                event = if (trackChanged) EVENT_TRACK_CHANGED else EVENT_PLAYBACK_STATE
            }
        }
        val resolved = event ?: return
        send(
            event = resolved,
            track = track,
            uri = track?.uriString,
            playing = playing,
            shuffle = shuffle,
            positionMs = MusicPlayerEngine.currentPosition.value,
            durationMs = MusicPlayerEngine.duration.value,
            log = true
        )
    }

    private fun send(
        event: String,
        track: Track?,
        uri: String?,
        playing: Boolean,
        shuffle: Boolean,
        positionMs: Long,
        durationMs: Long,
        log: Boolean
    ) {
        val ctx = appContext ?: return
        val intent = Intent(ACTION_TRACK_STATE).apply {
            putExtra(EXTRA_EVENT, event)
            putExtra(EXTRA_TITLE, track?.title)
            putExtra(EXTRA_ARTIST, track?.artist)
            putExtra(EXTRA_ALBUM, track?.album)
            putExtra(EXTRA_URI, uri)
            putExtra(EXTRA_DURATION_MS, durationMs)
            putExtra(EXTRA_POSITION_MS, positionMs)
            putExtra(EXTRA_IS_PLAYING, playing)
            putExtra(EXTRA_SHUFFLE, shuffle)
            putExtra(EXTRA_INDEX, MusicPlayerEngine.playlistIndex)
            putExtra(EXTRA_PLAYLIST_SIZE, MusicPlayerEngine.playlistSize)
            putExtra(EXTRA_SESSION_ID, Logger.sessionId)
            putExtra(EXTRA_TIMESTAMP_MS, System.currentTimeMillis())
        }
        try {
            ctx.sendBroadcast(intent)
        } catch (e: Exception) {
            Logger.warn("Не удалось отправить broadcast $event: ${e.message}", TAG)
            return
        }
        if (log) {
            Logger.debug(
                "Broadcast $event: '${track?.title ?: "-"}' isPlaying=$playing " +
                    "$positionMs/$durationMs ms shuffle=$shuffle",
                TAG
            )
        }
    }
}
