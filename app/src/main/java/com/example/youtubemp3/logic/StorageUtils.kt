package com.vusal.soundra.logic

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.format.Formatter
import androidx.core.net.toUri
import com.vusal.soundra.R
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FileDetails(
    val title: String,
    val fileName: String,
    val duration: String,
    val bitrate: String,
    val sampleRate: String,
    val channels: String,
    val codec: String,
    val fileSize: String,
    val createdDate: String,
    val modifiedDate: String,
    val absolutePath: String,
    val contentUri: String
)

object StorageUtils {

    fun saveFileToMusic(context: Context, inputStream: InputStream, fileName: String, mimeType: String, metadata: VideoMetadata? = null): Uri? {
        val contentResolver = context.contentResolver
        val audioCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
            
            // Premium Metadata for MediaStore
            metadata?.let { meta ->
                put(MediaStore.Audio.Media.TITLE, meta.title)
                put(MediaStore.Audio.Media.ARTIST, meta.artist)
                
                val albumName = meta.album?.trim()?.ifBlank { null } ?: meta.channelName.trim().ifBlank { null }
                albumName?.let { put(MediaStore.Audio.Media.ALBUM, it) }
                
                if (meta.durationSeconds > 0) {
                    put(MediaStore.Audio.Media.DURATION, meta.durationSeconds * 1000)
                }
                
                meta.year?.trim()?.ifBlank { null }?.let { year ->
                    // Some Android versions expect an integer for YEAR in MediaStore
                    year.toIntOrNull()?.let { put(MediaStore.Audio.Media.YEAR, it) }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Soundra")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        val uri = contentResolver.insert(audioCollection, contentValues)
        android.util.Log.d("MEDIASTORE", "MediaStore: Inserted Uri: $uri")
        
        uri?.let { targetUri ->
            try {
                contentResolver.openOutputStream(targetUri).use { outputStream ->
                    inputStream.copyTo(outputStream!!, bufferSize = 64 * 1024)
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    contentResolver.update(targetUri, contentValues, null, null)
                }

                // Trigger MediaScanner for all devices to ensure the file appears in players immediately
                android.media.MediaScannerConnection.scanFile(context, arrayOf(targetUri.toString()), arrayOf(mimeType)) { path, uriResult ->
                    android.util.Log.d("MEDIASTORE", "Scan COMPLETE: $path -> $uriResult")
                }

                android.util.Log.d("MEDIASTORE", "MediaStore: File saved and published successfully")
            } catch (e: Exception) {
                android.util.Log.e("MEDIASTORE", "MediaStore: Failed to write file data", e)
                return null
            }
        } ?: android.util.Log.e("MEDIASTORE", "MediaStore: Failed to insert Uri")
        
        return uri
    }

    fun getTempFile(context: Context, extension: String): File {
        return File.createTempFile("yt_download", extension, context.cacheDir)
    }

    fun deletePhysicalFile(context: Context, uriString: String): Boolean {
        return try {
            val uri = uriString.toUri()
            context.contentResolver.delete(uri, null, null) > 0
        } catch (e: Exception) {
            android.util.Log.e("STORAGE_UTILS", "Failed to delete file: $uriString", e)
            false
        }
    }

    fun renameMediaStoreFile(context: Context, uriString: String, newName: String): String? {
        return try {
            val uri = uriString.toUri()
            val finalName = if (newName.lowercase().endsWith(".mp3")) newName else "$newName.mp3"
            
            val contentValues = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, finalName)
            }
            
            val rowsUpdated = context.contentResolver.update(uri, contentValues, null, null)
            if (rowsUpdated > 0) finalName else null
        } catch (e: Exception) {
            android.util.Log.e("STORAGE_UTILS", "Rename failed", e)
            null
        }
    }

    fun getFileDetails(context: Context, uriString: String): FileDetails {
        android.util.Log.d("PROPERTIES", "Opening properties...")
        val uri = try { uriString.toUri() } catch (e: Exception) { null }
        val unknown = context.getString(R.string.unknown)
        
        var title = unknown
        var fileName = unknown
        var duration = unknown
        var bitrate = unknown
        var sampleRate = unknown
        var channels = unknown
        var codec = "MP3"
        var fileSize = unknown
        var createdDate = unknown
        var modifiedDate = unknown
        var absolutePath = unknown

        if (uri == null) {
            return FileDetails(title, fileName, duration, bitrate, sampleRate, channels, codec, fileSize, createdDate, modifiedDate, absolutePath, uriString)
        }

        // 1. Try MediaStore query for file basic info
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.DATA
            )

            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    fileName = cursor.getString(0) ?: unknown
                    fileSize = Formatter.formatFileSize(context, cursor.getLong(1))
                    
                    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US)
                    createdDate = sdf.format(Date(cursor.getLong(2) * 1000))
                    modifiedDate = sdf.format(Date(cursor.getLong(3) * 1000))
                    absolutePath = cursor.getString(4) ?: unknown
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PROPERTIES", "MediaStore query failed: ${e.message}")
        }

        // 2. Try MediaMetadataRetriever for technical info
        val retriever = MediaMetadataRetriever()
        try {
            android.util.Log.d("PROPERTIES", "Reading metadata...")
            retriever.setDataSource(context, uri)
            
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: fileName.replace(".mp3", "")
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (durationMs > 0) {
                duration = formatDuration(durationMs / 1000)
            }
            
            val bitrateBps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
            if (bitrateBps > 0) {
                bitrate = context.getString(R.string.bitrate_kbps, (bitrateBps / 1000).toInt())
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE) ?: unknown
            }
            
            channels = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS) ?: unknown
            
            android.util.Log.d("PROPERTIES", "Metadata loaded")
        } catch (e: SecurityException) {
            android.util.Log.e("PROPERTIES", "Metadata failed: SecurityException - ${e.message}")
        } catch (e: IllegalArgumentException) {
            android.util.Log.e("PROPERTIES", "Metadata failed: IllegalArgumentException - ${e.message}")
        } catch (e: java.io.IOException) {
            android.util.Log.e("PROPERTIES", "Metadata failed: IOException - ${e.message}")
        } catch (e: RuntimeException) {
            android.util.Log.e("PROPERTIES", "Metadata failed: RuntimeException - ${e.message}")
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore release errors
            }
        }

        return FileDetails(
            title = title,
            fileName = fileName,
            duration = duration,
            bitrate = bitrate,
            sampleRate = sampleRate,
            channels = channels,
            codec = codec,
            fileSize = fileSize,
            createdDate = createdDate,
            modifiedDate = modifiedDate,
            absolutePath = absolutePath,
            contentUri = uriString
        )
    }

    private fun formatDuration(seconds: Long): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}

