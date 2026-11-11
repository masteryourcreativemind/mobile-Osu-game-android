package com.osutiles.game.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.osutiles.game.model.Song
import com.osutiles.game.model.SongSource
import java.io.File
import java.util.UUID

/**
 * Copies a user-picked audio file (via the Storage Access Framework) into the
 * app's private storage so it remains playable after the picker grant expires.
 *
 * There are deliberately NO restrictions on file size or audio type: whatever
 * the picker returns is copied verbatim. If Android can later decode it, a map
 * is generated from the audio; if not, the game falls back to a steady rhythm so
 * the file is still playable.
 */
object SongImporter {

    fun import(context: Context, uri: Uri): Song? {
        return try {
            val displayName = queryDisplayName(context, uri) ?: "Imported Song"
            val id = "import_" + UUID.randomUUID().toString()
            val dir = File(context.filesDir, "songs").apply { mkdirs() }
            val dest = File(dir, "$id.audio")

            val copied = try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    }
                } != null
            } catch (_: Throwable) {
                false
            }
            if (!copied) {
                // Never leave a partial copy behind — it would be invisible to
                // the library and leak private storage.
                dest.delete()
                return null
            }

            val (title, artist) = splitTitleArtist(displayName)
            Song(
                id = id,
                title = title,
                artist = artist,
                uri = Uri.fromFile(dest).toString(),
                source = SongSource.IMPORTED
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        } catch (_: Throwable) {
            null
        }
    }

    /** Best-effort parse of "Artist - Title.ext" file names. */
    private fun splitTitleArtist(name: String): Pair<String, String> {
        val base = name.substringBeforeLast('.')
        val parts = base.split(" - ", limit = 2)
        return if (parts.size == 2) parts[1].trim() to parts[0].trim()
        else base.trim() to "Unknown"
    }
}
