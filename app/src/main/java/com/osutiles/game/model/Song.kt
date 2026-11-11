package com.osutiles.game.model

/**
 * Where a song came from. This enum is a deliberate extension point for the
 * (currently local-only) community backend: today everything is [BUILT_IN] or
 * [IMPORTED], but a hosted map server would surface songs as [REMOTE].
 */
enum class SongSource { BUILT_IN, IMPORTED, REMOTE }

/**
 * A song the player can map and play.
 *
 * @param uri     a content:// (imported), android.resource:// (built-in) or
 *                https:// (future remote) string pointing at the audio.
 * @param durationMs best-known duration; 0 until probed.
 */
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val uri: String,
    val source: SongSource,
    val durationMs: Long = 0L
)
