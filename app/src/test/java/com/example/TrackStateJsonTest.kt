package com.example

import com.example.player.TrackState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Проверяет, что HTTP/JSON-представление состояния трека содержит ровно те же
 * поля, что и extras системного broadcast (контракт из README).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrackStateJsonTest {

    @Test
    fun `json contains all broadcast fields with correct values`() {
        val state = TrackState(
            event = "TRACK_CHANGED",
            title = "Title",
            artist = "Artist",
            album = "Album",
            uri = "content://track/1",
            durationMs = 123_000L,
            positionMs = 4_500L,
            isPlaying = true,
            shuffle = false,
            index = 2,
            playlistSize = 10,
            sessionId = "session",
            timestampMs = 1_700_000_000_000L
        )

        val json = JSONObject(state.toJson())

        for (key in TrackState.JSON_KEYS) {
            assertTrue("missing key: $key", json.has(key))
        }
        assertEquals("TRACK_CHANGED", json.getString("event"))
        assertEquals("Title", json.getString("title"))
        assertEquals("Artist", json.getString("artist"))
        assertEquals("Album", json.getString("album"))
        assertEquals("content://track/1", json.getString("uri"))
        assertEquals(123_000L, json.getLong("durationMs"))
        assertEquals(4_500L, json.getLong("positionMs"))
        assertTrue(json.getBoolean("isPlaying"))
        assertEquals(2, json.getInt("index"))
        assertEquals(10, json.getInt("playlistSize"))
    }

    @Test
    fun `null fields are serialized as explicit null`() {
        val state = TrackState(
            event = "STOPPED",
            title = null,
            artist = null,
            album = null,
            uri = null,
            durationMs = 0L,
            positionMs = 0L,
            isPlaying = false,
            shuffle = false,
            index = -1,
            playlistSize = 0,
            sessionId = "s",
            timestampMs = 0L
        )

        val json = JSONObject(state.toJson())

        for (key in TrackState.JSON_KEYS) {
            assertTrue("missing key: $key", json.has(key))
        }
        assertTrue(json.isNull("title"))
        assertTrue(json.isNull("artist"))
        assertTrue(json.isNull("album"))
        assertTrue(json.isNull("uri"))
    }
}
