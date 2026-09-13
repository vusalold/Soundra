package com.vm.soundra.logic

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

class ParallelDownloader(
    private val httpClient: OkHttpClient,
    private val context: Context,
    private val isCancelled: () -> Boolean,
    private val getCancelReason: () -> String?
) {
    private val TAG = "PARALLEL_DOWNLOAD"
    private val MAX_SEGMENTS = 4
    private val BUFFER_SIZE = 512 * 1024 // 512 KB
    private val MAX_RETRIES = 3

    suspend fun download(
        url: String,
        destFile: File,
        onProgress: (progress: Int, downloadedBytes: Long, totalBytes: Long, speed: String, eta: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val downloadStartTime = System.currentTimeMillis()
        
        Log.d(TAG, "HEAD request started: $url")
        val headRequest = Request.Builder()
            .url(url)
            .head()
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
            .build()

        var contentLength = -1L
        var acceptRanges = false

        try {
            httpClient.newCall(headRequest).execute().use { response ->
                contentLength = response.header("Content-Length")?.toLongOrNull() ?: -1L
                acceptRanges = response.header("Accept-Ranges") == "bytes"
                Log.d(TAG, "HEAD response: Content-Length=$contentLength, Accept-Ranges=${response.header("Accept-Ranges")}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "HEAD request failed, falling back to single connection", e)
        }

        if (acceptRanges && contentLength > 0) {
            Log.d(TAG, "Range supported. Starting parallel download with $MAX_SEGMENTS segments.")
            try {
                downloadParallel(url, destFile, contentLength, downloadStartTime, onProgress)
            } catch (e: Exception) {
                if (isCancelled()) {
                    Log.d(TAG, "Parallel download cancelled.")
                    cleanupPartsIfNecessary(destFile)
                } else {
                    Log.e(TAG, "Parallel download failed, cleaning up parts and falling back to single connection", e)
                    // Force delete parts on error fallback
                    for (i in 0 until MAX_SEGMENTS) {
                        File(context.cacheDir, "${destFile.name}.part$i.tmp").delete()
                    }
                    downloadSingle(url, destFile, downloadStartTime, onProgress)
                }
            }
        } else {
            Log.d(TAG, "Fallback to single connection: Range not supported or length unknown")
            downloadSingle(url, destFile, downloadStartTime, onProgress)
        }
    }

    private suspend fun downloadParallel(
        url: String,
        destFile: File,
        totalBytes: Long,
        startTime: Long,
        onProgress: (progress: Int, downloadedBytes: Long, totalBytes: Long, speed: String, eta: String) -> Unit
    ) = coroutineScope {
        val segmentSize = totalBytes / MAX_SEGMENTS
        val downloadedBytes = AtomicLong(0)
        val partFiles = Array(MAX_SEGMENTS) { i ->
            File(context.cacheDir, "${destFile.name}.part$i.tmp")
        }

        // Initialize downloadedBytes from existing part files if resuming
        for (i in 0 until MAX_SEGMENTS) {
            if (partFiles[i].exists()) {
                downloadedBytes.addAndGet(partFiles[i].length())
            }
        }

        val jobs = List(MAX_SEGMENTS) { i ->
            val segmentStartBase = i * segmentSize
            val segmentEnd = if (i == MAX_SEGMENTS - 1) totalBytes - 1 else (i + 1) * segmentSize - 1
            
            launch {
                val currentFileLength = if (partFiles[i].exists()) partFiles[i].length() else 0L
                val start = segmentStartBase + currentFileLength
                
                if (start <= segmentEnd) {
                    downloadSegmentWithRetry(url, partFiles[i], start, segmentEnd, downloadedBytes, totalBytes, startTime, onProgress)
                } else {
                    Log.d(TAG, "Segment $i already completed.")
                }
            }
        }

        jobs.joinAll()

        if (isCancelled()) {
            Log.d(TAG, "Download cancelled, keeping parts if paused.")
            cleanupPartsIfNecessary(destFile)
            return@coroutineScope
        }

        // Verify all segments completed
        var currentTotal = 0L
        for (i in 0 until MAX_SEGMENTS) {
            if (partFiles[i].exists()) {
                currentTotal += partFiles[i].length()
            }
        }

        if (currentTotal != totalBytes) {
            Log.e(TAG, "Integrity check failed: Total downloaded size ($currentTotal) != Content-Length ($totalBytes)")
            throw Exception("Integrity check failed")
        }

        // Merge segments
        Log.d(TAG, "Merge started")
        FileOutputStream(destFile).use { output ->
            partFiles.forEach { part ->
                part.inputStream().use { input ->
                    input.copyTo(output, BUFFER_SIZE)
                }
            }
        }
        Log.d(TAG, "Merge completed. Final size: ${destFile.length()}")

        // Delete parts
        partFiles.forEach { it.delete() }
        
        val totalTime = System.currentTimeMillis() - startTime
        val avgSpeed = if (totalTime > 0) totalBytes.toDouble() / (totalTime / 1000.0) else 0.0
        Log.d(TAG, "Download completed. Total speed: ${formatSpeed(avgSpeed)}")
        
        onProgress(100, totalBytes, totalBytes, "0 KB/s", "00:00")
    }

    private fun cleanupPartsIfNecessary(destFile: File) {
        if (getCancelReason() != "pause") {
            Log.d(TAG, "Cleaning up part files (not a pause)")
            for (i in 0 until MAX_SEGMENTS) {
                File(context.cacheDir, "${destFile.name}.part$i.tmp").delete()
            }
        } else {
            Log.d(TAG, "Preserving part files for pause/resume")
        }
    }

    private suspend fun downloadSegmentWithRetry(
        url: String,
        partFile: File,
        start: Long,
        end: Long,
        totalDownloaded: AtomicLong,
        totalBytes: Long,
        startTime: Long,
        onProgress: (progress: Int, downloadedBytes: Long, totalBytes: Long, speed: String, eta: String) -> Unit
    ) {
        var retries = 0
        while (retries <= MAX_RETRIES) {
            try {
                downloadSegment(url, partFile, start, end, totalDownloaded, totalBytes, startTime, onProgress)
                Log.d(TAG, "Segment finished: ${partFile.name}")
                return
            } catch (e: Exception) {
                if (isCancelled()) return
                retries++
                Log.w(TAG, "Retry $retries for ${partFile.name}: ${e.message}")
                if (retries > MAX_RETRIES) throw e
                delay(1000L * retries)
            }
        }
    }

    private suspend fun downloadSegment(
        url: String,
        partFile: File,
        start: Long,
        end: Long,
        totalDownloaded: AtomicLong,
        totalBytes: Long,
        startTime: Long,
        onProgress: (progress: Int, downloadedBytes: Long, totalBytes: Long, speed: String, eta: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Segment started: ${partFile.name} range=$start-$end")
        
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=$start-$end")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 206) throw Exception("HTTP ${response.code}")
            
            val body = response.body ?: throw Exception("Body is null")
            val input = body.byteStream()
            
            FileOutputStream(partFile, true).use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                var lastUpdate = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    if (isCancelled()) return@withContext
                    
                    output.write(buffer, 0, bytesRead)
                    val currentTotal = totalDownloaded.addAndGet(bytesRead.toLong())

                    val now = System.currentTimeMillis()
                    if (now - lastUpdate > 800) {
                        updateProgress(currentTotal, totalBytes, startTime, onProgress)
                        lastUpdate = now
                    }
                }
            }
        }
    }

    private fun updateProgress(
        currentTotal: Long,
        totalBytes: Long,
        startTime: Long,
        onProgress: (progress: Int, downloadedBytes: Long, totalBytes: Long, speed: String, eta: String) -> Unit
    ) {
        val progress = if (totalBytes > 0) ((currentTotal * 100) / totalBytes).toInt() else 0
        val now = System.currentTimeMillis()
        val elapsedSeconds = (now - startTime) / 1000.0
        val speedBytesPerSec = if (elapsedSeconds > 0) currentTotal / elapsedSeconds else 0.0
        val speedStr = formatSpeed(speedBytesPerSec)

        val etaStr = if (speedBytesPerSec > 0 && totalBytes > 0) {
            val remainingBytes = totalBytes - currentTotal
            val remainingSeconds = (remainingBytes / speedBytesPerSec).toLong()
            formatETA(remainingSeconds)
        } else "--:--"

        onProgress(progress, currentTotal, totalBytes, speedStr, etaStr)
    }

    private fun downloadSingle(
        url: String,
        destFile: File,
        startTime: Long,
        onProgress: (progress: Int, downloadedBytes: Long, totalBytes: Long, speed: String, eta: String) -> Unit
    ) {
        Log.d(TAG, "Starting single connection download")
        var existingLength = if (destFile.exists()) destFile.length() else 0L
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")

        if (existingLength > 0) {
            builder.header("Range", "bytes=$existingLength-")
        }

        httpClient.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")

            if (existingLength > 0 && response.code == 200) {
                destFile.delete()
                existingLength = 0L
            }

            val body = response.body ?: throw Exception("Body is null")
            val contentLength = body.contentLength()
            val totalLength = if (existingLength > 0 && contentLength > 0) existingLength + contentLength else contentLength

            body.byteStream().use { input ->
                FileOutputStream(destFile, true).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    var totalRead = existingLength
                    var lastUpdate = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (isCancelled()) return

                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 800) {
                            updateProgress(totalRead, totalLength, startTime, onProgress)
                            lastUpdate = now
                        }
                    }
                    onProgress(100, totalRead, totalLength, "0 KB/s", "00:00")
                }
            }
        }
    }

    private fun formatSpeed(bytesPerSec: Double): String {
        val kbps = bytesPerSec / 1024.0
        val mbps = kbps / 1024.0
        return if (mbps >= 1) {
            String.format(Locale.US, "%.2f MB/s", mbps)
        } else {
            String.format(Locale.US, "%.2f KB/s", kbps)
        }
    }

    private fun formatETA(seconds: Long): String {
        if (seconds <= 0) return "00:00"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%02d:%02d", m, s)
        }
    }
}
