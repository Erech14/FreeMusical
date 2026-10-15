package com.example.player

import android.content.Context
import android.content.SharedPreferences
import com.example.ui.Logger

/**
 * Настройки интеграций с внешними слушателями: системный broadcast ([TrackBroadcaster])
 * и локальный HTTP-сервер ([TrackHttpServer]).
 *
 * По умолчанию обе интеграции выключены, чтобы не расходовать ресурсы. Значения
 * хранятся в общих настройках приложения (`music_player_prefs`).
 */
object TrackApiConfig {

    private const val PREFS = "music_player_prefs"
    private const val KEY_BROADCAST_ENABLED = "broadcast_enabled"
    private const val KEY_HTTP_ENABLED = "http_server_enabled"
    private const val KEY_HTTP_PORT = "http_server_port"

    const val DEFAULT_ENABLED = false
    const val DEFAULT_HTTP_PORT = 31414
    const val MIN_PORT = 1024
    const val MAX_PORT = 65535

    private const val TAG = "TrackApi"

    fun isBroadcastEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BROADCAST_ENABLED, DEFAULT_ENABLED)

    fun isHttpEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HTTP_ENABLED, DEFAULT_ENABLED)

    fun httpPort(context: Context): Int {
        val port = prefs(context).getInt(KEY_HTTP_PORT, DEFAULT_HTTP_PORT)
        return if (port in MIN_PORT..MAX_PORT) port else DEFAULT_HTTP_PORT
    }

    /** Включает/выключает broadcast и сразу применяет настройку. */
    fun setBroadcastEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BROADCAST_ENABLED, enabled).apply()
        TrackBroadcaster.init(context)
        TrackBroadcaster.setEnabled(enabled)
        Logger.info("Broadcast ${if (enabled) "включён" else "выключен"} (настройки)", TAG)
    }

    /** Включает/выключает HTTP-сервер и сразу применяет настройку. */
    fun setHttpEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_HTTP_ENABLED, enabled).apply()
        TrackHttpServer.init(context)
        applyHttp(context)
        Logger.info("HTTP-сервер ${if (enabled) "включён" else "выключен"} (настройки)", TAG)
    }

    /** Меняет порт HTTP-сервера и применяет его (перезапускает сервер при необходимости). */
    fun setHttpPort(context: Context, port: Int) {
        val safe = if (port in MIN_PORT..MAX_PORT) port else DEFAULT_HTTP_PORT
        prefs(context).edit().putInt(KEY_HTTP_PORT, safe).apply()
        TrackHttpServer.init(context)
        applyHttp(context)
        Logger.info("Порт HTTP-сервера изменён: $safe", TAG)
    }

    /** Применяет сохранённые настройки к работающим сервисам. Идемпотентно. */
    fun apply(context: Context) {
        TrackBroadcaster.init(context)
        TrackHttpServer.init(context)
        TrackBroadcaster.setEnabled(isBroadcastEnabled(context))
        applyHttp(context)
    }

    private fun applyHttp(context: Context) {
        if (isHttpEnabled(context)) {
            TrackHttpServer.start(httpPort(context))
        } else if (TrackHttpServer.isRunning) {
            TrackHttpServer.stop()
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
