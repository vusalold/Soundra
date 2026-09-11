package com.vusal.soundra.logic

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.core.net.toUri
import java.io.File

data class LocalMusicFile(
    val uri: Uri,
    val displayName: String,
    val exists: Boolean,
    val size: Long = 0L
)

object LocalMusicResolver {
    private const val TAG = "LocalMusicResolver"

    fun resolve(context: Context, pathOrUri: String?): LocalMusicFile? {
        if (pathOrUri.isNullOrBlank()) return null

        val uri = try {
            if (pathOrUri.startsWith("content://") || pathOrUri.startsWith("file://")) {
                pathOrUri.toUri()
            } else {
                Uri.fromFile(File(pathOrUri))
            }
        } catch (e: Exception) {
            null
        } ?: return null

        return if (uri.scheme == "content") {
            resolveContentUri(context, uri)
        } else {
            resolveFileUri(uri)
        }
    }

    private fun resolveContentUri(context: Context, uri: Uri): LocalMusicFile {
        var exists = false
        var displayName = "Unknown"
        var size = 0L

        try {
            // Safe check using openFileDescriptor
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                exists = true
                size = it.statSize
            }
            
            // Get metadata
            val projection = arrayOf(MediaStore.Audio.Media.DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    displayName = cursor.getString(0) ?: "Unknown"
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to resolve content URI: $uri", e)
            exists = false
        }

        return LocalMusicFile(uri, displayName, exists, size)
    }

    private fun resolveFileUri(uri: Uri): LocalMusicFile {
        val file = File(uri.path ?: "")
        return LocalMusicFile(
            uri = uri,
            displayName = file.name,
            exists = file.exists() && file.length() > 0,
            size = file.length()
        )
    }
}
