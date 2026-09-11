package com.vusal.soundra.logic

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.vusal.soundra.MainActivity
import com.vusal.soundra.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class DownloadNotificationManager(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "mp3_downloads_channel"
    private val completedChannelId = "download_completed"
    private val groupKey = "com.vusal.soundra.DOWNLOAD_GROUP"
    private val summaryId = 0
    private var lastUpdateTime = mutableMapOf<Int, Long>()
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 1. Progress Channel (Silent)
            val channel = NotificationChannel(
                channelId,
                context.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)

            // 2. Completion Channel (With Sound)
            val soundUri = android.net.Uri.parse("android.resource://${context.packageName}/${R.raw.download_complete}")
            val completedChannel = NotificationChannel(
                completedChannelId,
                context.getString(R.string.notif_completed_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_download_complete)
                val audioAttributes = android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .build()
                setSound(soundUri, audioAttributes)
            }
            notificationManager.createNotificationChannel(completedChannel)
            
            if (notificationManager.getNotificationChannel(channelId) == null) {
                Log.e("FG_SERVICE", "Progress channel not found after creation")
            }
        }
    }

    fun getInitialNotification(title: String, artist: String, thumbnail: String, taskId: Int): Notification {
        return buildNotification(title, artist, thumbnail, context.getString(R.string.status_preparing_download), 0, 100, true, null, null, taskId)
    }

    fun updateProgress(
        title: String,
        artist: String,
        thumbnail: String,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        speed: String,
        eta: String,
        taskId: Int,
        activeCount: Int = 1
    ) {
        val now = System.currentTimeMillis()
        val lastUpdate = lastUpdateTime[taskId] ?: 0L
        if (now - lastUpdate < 1000 && progress < 100) return
        lastUpdateTime[taskId] = now

        val contentText = if (totalBytes > 0) {
            val downloadedStr = android.text.format.Formatter.formatFileSize(context, downloadedBytes)
            val totalStr = android.text.format.Formatter.formatFileSize(context, totalBytes)
            "$downloadedStr / $totalStr • $speed"
        } else {
            "${context.getString(R.string.notif_downloading)} • $speed"
        }

        val subText = if (eta.isNotEmpty() && eta != "--:--") context.getString(R.string.eta_label, eta) else null

        val notification = buildNotification(title, artist, thumbnail, contentText, progress, 100, progress == 0, subText, speed, taskId)
        Log.d("SMART_NOTIFICATION", "Progress Updated: $taskId ($progress%)")
        notificationManager.notify(taskId, notification)
        updateSummary(activeCount)
    }

    fun showPaused(title: String, artist: String, thumbnail: String, taskId: Int, activeCount: Int = 0) {
        val notification = buildNotification(title, artist, thumbnail, context.getString(R.string.notif_paused), 0, 100, false, null, null, taskId, isPaused = true)
        notificationManager.notify(taskId, notification)
        updateSummary(activeCount)
    }

    fun showCompleted(title: String, artist: String, thumbnail: String, uriString: String?, taskId: Int, activeCount: Int = 0) {
        val viewIntent = if (uriString != null) {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(android.net.Uri.parse(uriString), "audio/mpeg")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 
            taskId, 
            viewIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/mpeg"
            putExtra(Intent.EXTRA_STREAM, android.net.Uri.parse(uriString))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, context.getString(R.string.btn_share)).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val sharePendingIntent = PendingIntent.getActivity(
            context, taskId + 3000, chooser,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = android.net.Uri.parse("android.resource://${context.packageName}/${R.raw.download_complete}")

        val builder = NotificationCompat.Builder(context, completedChannelId)
            .setSmallIcon(R.drawable.ic_notif_logo)
            .setContentTitle(context.getString(R.string.notif_completed))
            .setContentText("$artist - $title")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setGroup(groupKey)
            .setOngoing(false)
            .setSound(soundUri)
            .addAction(android.R.drawable.ic_menu_view, context.getString(R.string.btn_open), pendingIntent)
            .addAction(android.R.drawable.ic_menu_share, context.getString(R.string.btn_share), sharePendingIntent)

        if (thumbnail.isNotEmpty()) {
            loadThumbnailAsLargeIcon(builder, thumbnail, taskId)
        }

        notificationManager.notify(taskId, builder.build())
        updateSummary(activeCount)
    }

    fun showFailed(title: String, artist: String, thumbnail: String, error: String, taskId: Int, activeCount: Int = 0) {
        val retryIntent = Intent(context, DownloadForegroundService::class.java).apply {
            action = DownloadForegroundService.ACTION_RETRY
            putExtra(DownloadForegroundService.EXTRA_TASK_ID, taskId)
        }
        val retryPendingIntent = PendingIntent.getService(
            context, taskId + 1000, retryIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val deleteIntent = Intent(context, DownloadForegroundService::class.java).apply {
            action = DownloadForegroundService.ACTION_DELETE
            putExtra(DownloadForegroundService.EXTRA_TASK_ID, taskId)
        }
        val deletePendingIntent = PendingIntent.getService(
            context, taskId + 4000, deleteIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notif_logo)
            .setContentTitle(context.getString(R.string.notif_failed))
            .setContentText("$artist - $title: $error")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setGroup(groupKey)
            .addAction(android.R.drawable.ic_menu_rotate, context.getString(R.string.btn_retry), retryPendingIntent)
            .addAction(android.R.drawable.ic_menu_delete, context.getString(R.string.btn_delete), deletePendingIntent)

        if (thumbnail.isNotEmpty()) {
            loadThumbnailAsLargeIcon(builder, thumbnail, taskId)
        }

        notificationManager.notify(taskId, builder.build())
        updateSummary(activeCount)
    }

    private fun updateSummary(activeCount: Int = 0) {
        val title = if (activeCount > 1) "$activeCount ${context.getString(R.string.notif_downloading)}" else "Soundra ${context.getString(R.string.downloads_title)}"
        
        val summaryNotification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notif_logo)
            .setContentTitle(title)
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        notificationManager.notify(summaryId, summaryNotification)
    }

    fun cancelNotification(taskId: Int) {
        notificationManager.cancel(taskId)
        updateSummary(0)
    }

    private fun buildNotification(
        title: String,
        artist: String,
        thumbnail: String,
        contentText: String,
        progress: Int,
        max: Int,
        indeterminate: Boolean,
        subText: String?,
        speed: String?,
        taskId: Int,
        isPaused: Boolean = false
    ): Notification {
        val cancelIntent = Intent(context, DownloadForegroundService::class.java).apply {
            action = DownloadForegroundService.ACTION_CANCEL
            putExtra(DownloadForegroundService.EXTRA_TASK_ID, taskId)
        }
        val cancelPendingIntent = PendingIntent.getService(
            context, taskId, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val actionIntent = Intent(context, DownloadForegroundService::class.java).apply {
            action = if (isPaused) DownloadForegroundService.ACTION_RESUME else DownloadForegroundService.ACTION_PAUSE
            putExtra(DownloadForegroundService.EXTRA_TASK_ID, taskId)
        }
        val actionPendingIntent = PendingIntent.getService(
            context, taskId + 2000, actionIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mainIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notif_logo)
            .setContentTitle(title)
            .setContentText("$artist • $contentText")
            .setSubText(subText)
            .setProgress(max, progress, indeterminate)
            .setOngoing(!isPaused)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setGroup(groupKey)
            .addAction(
                if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (isPaused) context.getString(R.string.btn_resume) else context.getString(R.string.btn_pause),
                actionPendingIntent
            )
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(R.string.btn_cancel), cancelPendingIntent)

        // Load Large Icon asynchronously
        if (thumbnail.isNotEmpty()) {
            loadThumbnailAsLargeIcon(builder, thumbnail, taskId)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }

        return builder.build()
    }

    private fun loadThumbnailAsLargeIcon(builder: NotificationCompat.Builder, url: String, taskId: Int) {
        scope.launch {
            try {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .build()
                
                val result = (loader.execute(request) as? SuccessResult)?.drawable
                val bitmap = (result as? BitmapDrawable)?.bitmap
                
                if (bitmap != null) {
                    builder.setLargeIcon(bitmap)
                    withContext(Dispatchers.Main) {
                        notificationManager.notify(taskId, builder.build())
                    }
                }
            } catch (e: Exception) {
                Log.e("SMART_NOTIFICATION", "Large icon load failed", e)
            }
        }
    }

    companion object {
        const val NOTIFICATION_ID = 1001
    }
}
