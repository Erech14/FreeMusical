package com.example.player

import android.content.Context
import android.content.Intent
import com.example.ui.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Отправляет системный broadcast [ACTION_TRACK_STATE] о текущем треке внешним слушателям.
 *
 * Контракт для внешних приложений описан в README. Слушатель обязан регистрировать
 * [android.content.BroadcastReceiver] в рантайме (неявные broadcast-ы не доставляются в
 * manifest-ресиверы начиная с Android 8), а на Android 14+ передавать флаг
 * `RECEIVER_EXPORTED`/`RECEIVER_NOT_EXPORTED`.
 *
 * Данные и события берутся из общего источника [TrackStateStream] — того же, что
 * использует HTTP-сервер [TrackHttpServer]:
 *  - [EVENT_TRACK_CHANGED] — сменился трек;
 *  - [EVENT_PLAYBACK_STATE] — изменилось play/pause или режим повтора/случайного выбора;
 *  - [EVENT_POSITION] — heartbeat позиции раз в секунду во время воспроизведения;
 *  - [EVENT_STOPPED] — воспроизведение остановлено (трек сброшен).
 *
 * По умолчанию выключен. Включается/выключается из настроек ([TrackApiConfig]).
 */
object TrackBroadcaster {

    /** Action широковещательного сообщения о текущем треке. */
    const val ACTION_TRACK_STATE = "erech14.freemusical.app.action.TRACK_STATE"

    const val EVENT_TRACK_CHANGED = TrackStateStream.EVENT_TRACK_CHANGED
    const val EVENT_PLAYBACK_STATE = TrackStateStream.EVENT_PLAYBACK_STATE
    const val EVENT_POSITION = TrackStateStream.EVENT_POSITION
    const val EVENT_STOPPED = TrackStateStream.EVENT_STOPPED

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

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var job: Job? = null

    /** Инициализация (идемпотентна). Не запускает отправку — см. [setEnabled]. */
    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        TrackStateStream.init(context)
        Logger.debug("TrackBroadcaster инициализирован (action=$ACTION_TRACK_STATE)", TAG)
    }

    /** Включён ли сейчас broadcast. */
    fun isEnabled(): Boolean = job?.isActive == true

    /** Включает/выключает отправку broadcast. Идемпотентно. */
    @Synchronized
    fun setEnabled(enabled: Boolean) {
        if (enabled) {
            if (job?.isActive == true) return
            if (appContext == null) return
            Logger.info("Broadcast включён ($ACTION_TRACK_STATE)", TAG)
            job = scope.launch {
                TrackStateStream.subscribe().collect { send(it) }
            }
        } else {
            if (job != null) {
                Logger.info("Broadcast выключен", TAG)
            }
            job?.cancel()
            job = null
        }
    }

    /** Останавливает отправку broadcast. */
    fun stop() = setEnabled(false)

    private fun send(state: TrackState) {
        val ctx = appContext ?: return
        val intent = Intent(ACTION_TRACK_STATE).apply {
            putExtra(EXTRA_EVENT, state.event)
            putExtra(EXTRA_TITLE, state.title)
            putExtra(EXTRA_ARTIST, state.artist)
            putExtra(EXTRA_ALBUM, state.album)
            putExtra(EXTRA_URI, state.uri)
            putExtra(EXTRA_DURATION_MS, state.durationMs)
            putExtra(EXTRA_POSITION_MS, state.positionMs)
            putExtra(EXTRA_IS_PLAYING, state.isPlaying)
            putExtra(EXTRA_SHUFFLE, state.shuffle)
            putExtra(EXTRA_INDEX, state.index)
            putExtra(EXTRA_PLAYLIST_SIZE, state.playlistSize)
            putExtra(EXTRA_SESSION_ID, state.sessionId)
            putExtra(EXTRA_TIMESTAMP_MS, state.timestampMs)
        }
        try {
            ctx.sendBroadcast(intent)
        } catch (e: Exception) {
            Logger.warn("Не удалось отправить broadcast ${state.event}: ${e.message}", TAG)
        }
    }
}
