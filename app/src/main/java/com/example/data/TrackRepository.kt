package com.example.data

import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class TrackRepository(
    private val context: Context,
    private val trackDao: TrackDao
) {
    val allTracks: Flow<List<Track>> = trackDao.getAllTracks()

    suspend fun clearLibrary() = withContext(Dispatchers.IO) {
        trackDao.clearAll()
    }

    suspend fun insertTracks(tracks: List<Track>) = withContext(Dispatchers.IO) {
        trackDao.insertTracks(tracks)
    }

    /**
     * Highly optimized recursive scanner using ContentResolver and DocumentsContract queries.
     * This avoids costly DocumentFile object instantiation overhead.
     */
    suspend fun scanDirectoryForMusic(treeUri: Uri): List<Track> = withContext(Dispatchers.IO) {
        val discoveredTracks = mutableListOf<Track>()
        try {
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            scanDocumentDir(treeUri, rootDocId, discoveredTracks)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext discoveredTracks
    }

    private fun scanDocumentDir(treeUri: Uri, parentDocId: String, outList: MutableList<Track>) {
        val resolver = context.contentResolver
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        var cursor: Cursor? = null
        try {
            cursor = resolver.query(childrenUri, projection, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                val modifiedIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                do {
                    val docId = cursor.getString(idIdx)
                    val displayName = cursor.getString(nameIdx) ?: "Трек"
                    val mimeType = cursor.getString(mimeIdx)
                    val sizeValue = if (sizeIdx != -1) cursor.getLong(sizeIdx) else 0L
                    val modifiedValue = if (modifiedIdx != -1) cursor.getLong(modifiedIdx) else 0L

                    if (DocumentsContract.Document.MIME_TYPE_DIR == mimeType) {
                        // Recursively scan subdirectory
                        scanDocumentDir(treeUri, docId, outList)
                    } else if (isAudioFile(displayName, mimeType)) {
                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        val track = extractTrackMetadata(fileUri, displayName, sizeValue, modifiedValue)
                        outList.add(track)
                    }
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
    }

    private fun isAudioFile(fileName: String, mimeType: String?): Boolean {
        if (mimeType?.startsWith("audio/", ignoreCase = true) == true) return true
        val extensions = listOf(".mp3", ".m4a", ".wav", ".ogg", ".flac", ".aac", ".wma", ".mid")
        return extensions.any { fileName.endsWith(it, ignoreCase = true) }
    }

    private fun extractTrackMetadata(uri: Uri, displayName: String, sizeValue: Long, modifiedValue: Long): Track {
        val retriever = MediaMetadataRetriever()
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var duration: Long = 0

        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                duration = durationStr?.toLongOrNull() ?: 0L
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore
            }
        }

        // Clean up title (remove extension if title is blank)
        val cleanTitle = if (!title.isNullOrBlank()) {
            title!!
        } else {
            val lastDot = displayName.lastIndexOf('.')
            if (lastDot > 0) displayName.substring(0, lastDot) else displayName
        }

        val cleanArtist = if (!artist.isNullOrBlank()) artist!! else "Неизвестный исполнитель"
        val cleanAlbum = if (!album.isNullOrBlank()) album!! else "Неизвестный альбом"

        return Track(
            uriString = uri.toString(),
            title = cleanTitle,
            artist = cleanArtist,
            album = cleanAlbum,
            duration = duration,
            fileName = displayName,
            fileSize = sizeValue,
            lastModified = modifiedValue
        )
    }
}
