package com.vm.soundra.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.vm.soundra.R
import java.io.File

class ApkDownloader(private val context: Context) {
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val TAG = "APK_DOWNLOADER"

    fun startDownload(url: String, versionName: String): Long {
        Log.d(TAG, "Download Started for version: $versionName")
        
        val fileName = "Soundra_v$versionName.apk"
        val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Soundra")
        
        if (!directory.exists()) {
            directory.mkdirs()
        }

        val file = File(directory, fileName)
        if (file.exists()) {
            Log.d(TAG, "Existing APK found, deleting: ${file.absolutePath}")
            file.delete()
        }

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(context.getString(R.string.update_notif_title))
            .setDescription(context.getString(R.string.status_downloading))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Soundra/$fileName")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        return downloadManager.enqueue(request)
    }

    fun getDownloadProgress(downloadId: Long): DownloadState {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        
        if (cursor != null && cursor.moveToFirst()) {
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val bytesTotal = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            
            // Log raw status for debugging
            Log.d(TAG, "Raw Status: $status, Downloaded: $bytesDownloaded, Total: $bytesTotal")

            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    Log.d(TAG, "DownloadManager reported SUCCESSFUL")
                    
                    // Try to get file via URI first
                    val uri = downloadManager.getUriForDownloadedFile(downloadId)
                    Log.d(TAG, "Downloaded File URI: $uri")
                    
                    val file = if (uri != null) {
                        if (uri.scheme == "file") {
                            File(uri.path ?: "")
                        } else {
                            // On some versions, it returns a content URI, we might need to fallback to manual path
                            findFileInDownloads(downloadId)
                        }
                    } else {
                        findFileInDownloads(downloadId)
                    }

                    if (file != null && file.exists()) {
                        Log.d(TAG, "Final APK File Found: ${file.absolutePath}")
                        DownloadState.Completed(file)
                    } else {
                        Log.e(TAG, "File not found even after success!")
                        DownloadState.Failed(context.getString(R.string.err_file_not_found))
                    }
                }
                DownloadManager.STATUS_FAILED -> {
                    val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    Log.e(TAG, "Download Failed. Reason: $reason")
                    DownloadState.Failed(context.getString(R.string.err_download_error_code, reason))
                }
                DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING -> {
                    val progress = if (bytesTotal > 0) ((bytesDownloaded * 100) / bytesTotal).toInt() else 0
                    DownloadState.Downloading(progress, bytesDownloaded, bytesTotal)
                }
                DownloadManager.STATUS_PAUSED -> {
                    val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    DownloadState.Downloading(0, bytesDownloaded, bytesTotal, "Paused ($reason)")
                }
                else -> DownloadState.Idle
            }.also {
                cursor.close()
            }
        }
        cursor?.close()
        return DownloadState.Idle
    }

    private fun findFileInDownloads(downloadId: Long): File? {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        var file: File? = null
        if (cursor != null && cursor.moveToFirst()) {
            val localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
            Log.d(TAG, "Fallback: COLUMN_LOCAL_URI = $localUri")
            if (localUri != null) {
                val path = Uri.parse(localUri).path
                if (path != null) file = File(path)
            }
            cursor.close()
        }
        return file
    }
    
    fun cancelDownload(downloadId: Long) {
        Log.d(TAG, "Download Cancelled")
        downloadManager.remove(downloadId)
    }
}
