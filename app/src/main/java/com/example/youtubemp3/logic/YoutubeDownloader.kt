package com.vusal.soundra.logic

import android.content.Context
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.vusal.soundra.R
import com.vusal.soundra.data.DownloadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress
import java.util.concurrent.TimeUnit

data class AudioStreamMetadata(
    val bitrateKbps: Int,
    val codec: String,
    val sampleRate: Int,
    val mimeType: String,
    val format: String,
    val sizeBytes: Long,
    val url: String
)

data class VideoMetadata(
    val title: String,
    val artist: String,
    val thumbnailUrl: String,
    val videoUrl: String,
    val durationSeconds: Long,
    val viewCount: Long = 0,
    val channelName: String = "",
    val audioStreams: List<AudioStreamMetadata> = emptyList(),
    val album: String? = null,
    val year: String? = null,
    val albumArtist: String? = null,
    val genre: String? = null,
    val trackNumber: String? = null,
    val composer: String? = null,
    val publisher: String? = null,
    val copyright: String? = null,
    val comment: String? = null
)

class YoutubeDownloader(private val context: Context) {
    private var isCancelled = false
    // New: reason for cancel (null | "pause" | "cancel")
    private var cancelReason: String? = null

    private val metadataCache = object : LinkedHashMap<String, StreamInfo>(15, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, StreamInfo>?): Boolean {
            return size > 15
        }
    }
    private val TAG = "DOWNLOAD_PERFORMANCE"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
        .dns { hostname ->
            val addresses = InetAddress.getAllByName(hostname).toList()
            addresses.sortedBy { it.address.size }
        }
        .build()

    private suspend fun initExtractor() = withContext(Dispatchers.IO) {
        ExtractorHelper.init(context)
    }

    private fun cleanUrl(url: String): String {
        return try {
            if (url.contains("watch?v=")) {
                val id = url.split("v=")[1].split("&")[0]
                "https://www.youtube.com/watch?v=$id"
            } else if (url.contains("youtu.be/")) {
                val id = url.split("youtu.be/")[1].split("?")[0]
                "https://www.youtube.com/watch?v=$id"
            } else url
        } catch (e: Exception) {
            url
        }
    }

    suspend fun getVideoMetadata(url: String): VideoMetadata? = withContext(Dispatchers.IO) {
        try {
            initExtractor()
            val cleaned = cleanUrl(url)
            Log.d("SMART_DOWNLOAD", "AudioAnalysisStarted: $cleaned")

            val loadStartTime = System.currentTimeMillis()
            val info = synchronized(metadataCache) {
                metadataCache[cleaned]
            } ?: run {
                val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, cleaned)
                synchronized(metadataCache) {
                    metadataCache[cleaned] = streamInfo
                }
                streamInfo
            }
            Log.d(TAG, "Metadata Loaded in ${System.currentTimeMillis() - loadStartTime}ms")

            val audioStreams = info.audioStreams.map { stream ->
                AudioStreamMetadata(
                    bitrateKbps = stream.bitrate / 1000,
                    codec = stream.codec ?: "Unknown",
                    sampleRate = 0,
                    mimeType = stream.getFormat()?.name ?: "Unknown",
                    format = stream.getFormat()?.suffix ?: "Unknown",
                    sizeBytes = -1L,
                    url = stream.url ?: ""
                )
            }.sortedByDescending { it.bitrateKbps }

            // Pick highest resolution thumbnail
            val bestThumbnail = info.thumbnails.maxByOrNull { it.width * it.height }?.url ?: ""

            // Try to split artist and title if they are in "Artist - Title" format
            var finalTitle = info.name
            var finalArtist = info.uploaderName ?: context.getString(R.string.unknown)
            
            if (info.name.contains(" - ")) {
                val parts = info.name.split(" - ", limit = 2)
                finalArtist = parts[0].trim()
                finalTitle = parts[1].trim()
                
                // Clean up title (remove (Official Video), [HQ], etc.)
                finalTitle = finalTitle.replace(Regex("(?i)[\\[\\(](official|video|hq|hd|audio|lyrics|4k|mv|music|lyric|high definition).*?[\\]\\)]"), "").trim()
            }
            
            // Year extraction
            val yearRegex = "\\b(19|20)\\d{2}\\b".toRegex()
            val year = yearRegex.find(info.name)?.value ?: yearRegex.find(info.description?.content ?: "")?.value

            // Smart Metadata Detection logic
            val description = info.description?.content ?: ""
            val albumRegex = "(?i)album[:\\-]?\\s*(.*)".toRegex()
            val albumMatch = albumRegex.find(description)?.groupValues?.get(1)?.trim()
            
            val genreRegex = "(?i)genre[:\\-]?\\s*(.*)".toRegex()
            val genreMatch = genreRegex.find(description)?.groupValues?.get(1)?.trim()
            
            val composerRegex = "(?i)composer[:\\-]?\\s*(.*)".toRegex()
            val composerMatch = composerRegex.find(description)?.groupValues?.get(1)?.trim()

            val publisherRegex = "(?i)publisher[:\\-]?\\s*(.*)".toRegex()
            val publisherMatch = publisherRegex.find(description)?.groupValues?.get(1)?.trim()

            return@withContext VideoMetadata(
                title = finalTitle,
                artist = finalArtist,
                thumbnailUrl = bestThumbnail,
                videoUrl = cleaned,
                durationSeconds = info.duration,
                viewCount = info.viewCount,
                channelName = info.uploaderName ?: context.getString(R.string.unknown),
                audioStreams = audioStreams,
                year = year,
                albumArtist = finalArtist,
                album = albumMatch ?: info.uploaderName ?: context.getString(R.string.tag_album),
                genre = genreMatch ?: "Music",
                comment = context.getString(R.string.tag_comment),
                copyright = info.uploaderName,
                composer = composerMatch,
                publisher = publisherMatch ?: info.uploaderName
            )
        } catch (e: Exception) {
            Log.e("YoutubeDownloader", "Metadata error", e)
            null
        }
    }

    suspend fun getAudioStreamUrl(url: String): String? = withContext(Dispatchers.IO) {
        try {
            initExtractor()
            val cleaned = cleanUrl(url)
            val streamInfo = synchronized(metadataCache) {
                metadataCache[cleaned]
            } ?: StreamInfo.getInfo(ServiceList.YouTube, cleaned)
            
            // Prioritize m4a for MediaPlayer compatibility
            val streams = streamInfo.audioStreams
            val m4aStream = streams.filter { it.getFormat()?.name?.lowercase() == "m4a" }.maxByOrNull { it.bitrate }
            
            return@withContext (m4aStream ?: streams.maxByOrNull { it.bitrate })?.url
        } catch (e: Exception) {
            Log.e("YoutubeDownloader", "Stream URL error", e)
            null
        }
    }

    /**
     * downloadAndConvert now accepts an optional existingTempFile. If provided, downloader will
     * attempt to resume by issuing an HTTP Range request. If the server doesn't support Range,
     * the temp file will be deleted and a fresh download will start (per requirements).
     */
    suspend fun downloadAndConvert(
        url: String,
        bitrate: String,
        onStatus: (String) -> Unit,
        onProgress: (progress: Int, downloadedBytes: Long, totalBytes: Long, speed: String, eta: String) -> Unit,
        onStateChange: (DownloadStatus) -> Unit = {},
        existingTempFile: File? = null
    ): DownloadResult = withContext(Dispatchers.IO) {
        val totalStartTime = System.currentTimeMillis()
        isCancelled = false
        cancelReason = null
        Log.d("SMART_DOWNLOAD", "DownloadStarted: $url with quality $bitrate")
        Log.d("MP3_DOWNLOAD", "Download Started for: $url")
        try {
            initExtractor()
            if (isCancelled) return@withContext DownloadResult.Error("Cancelled")
            val cleaned = cleanUrl(url)
            onStatus(context.getString(R.string.status_reading_metadata))
            onStateChange(DownloadStatus.PREPARING)

            val metadataLoadStart = System.currentTimeMillis()
            val streamInfo = synchronized(metadataCache) {
                metadataCache[cleaned]
            } ?: run {
                val info = StreamInfo.getInfo(ServiceList.YouTube, cleaned)
                synchronized(metadataCache) {
                    metadataCache[cleaned] = info
                }
                info
            }
            if (isCancelled) return@withContext DownloadResult.Error("Cancelled")
            Log.d("MP3_DOWNLOAD", "Metadata Loaded in ${System.currentTimeMillis() - metadataLoadStart}ms")

            // Parse bitrate number from string like "160 kbps" or "192k"
            val targetBitrate = try {
                bitrate.filter { it.isDigit() }.toInt()
            } catch (e: Exception) { -1 }

            val audioStream = if (targetBitrate != -1) {
                // Try to find exact match in detected streams
                streamInfo.audioStreams.find { it.bitrate / 1000 == targetBitrate }
                    ?: streamInfo.audioStreams.maxByOrNull { it.bitrate }
            } else {
                streamInfo.audioStreams.maxByOrNull { it.bitrate }
            } ?: return@withContext DownloadResult.Error("Audio stream not found")

            Log.d("SMART_DOWNLOAD", "SelectedAudioStream: ${audioStream.getFormat()?.name} - ${audioStream.bitrate / 1000}kbps")
            Log.d("MP3_DOWNLOAD", "Audio Stream Selected: ${audioStream.getFormat()?.name} - ${audioStream.bitrate}bps")

            onStatus(context.getString(R.string.status_downloading))
            onStateChange(DownloadStatus.DOWNLOADING)
            val tempFile = existingTempFile ?: StorageUtils.getTempFile(context, ".tmp")

            val downloadStartTime = System.currentTimeMillis()
            val downloader = ParallelDownloader(httpClient, context, isCancelled = { isCancelled }, getCancelReason = { cancelReason })
            downloader.download(audioStream.url ?: return@withContext DownloadResult.Error("Audio URL is empty"), tempFile, onProgress)

            if (isCancelled) {
                // Preserve temp file if user requested pause
                if (cancelReason != "pause") tempFile.delete()
                return@withContext DownloadResult.Error("Cancelled")
            }

            val downloadDuration = System.currentTimeMillis() - downloadStartTime
            Log.d("MP3_DOWNLOAD", "Download Completed in ${downloadDuration}ms")

            onStatus(context.getString(R.string.status_converting, bitrate))
            onStateChange(DownloadStatus.CONVERTING)
            val conversionStartTime = System.currentTimeMillis()
            val mp3FileName = streamInfo.name.replace("[\\/:*?\"<>|]".toRegex(), "_") + ".mp3"

            val mp3File = File(context.cacheDir, mp3FileName)

            val ffmpegCommand = "-y -threads 0 -i \"${tempFile.absolutePath}\" -vn -acodec libmp3lame -q:a 0 \"${mp3File.absolutePath}\""
            Log.d("MP3_DOWNLOAD", "FFmpeg Command: $ffmpegCommand")

            val session = FFmpegKit.execute(ffmpegCommand)
            val conversionDuration = System.currentTimeMillis() - conversionStartTime

            if (isCancelled) {
                // If paused, keep temp file; otherwise delete both
                if (cancelReason != "pause") {
                    tempFile.delete()
                    mp3File.delete()
                }
                return@withContext DownloadResult.Error("Cancelled")
            }

            Log.d("MP3_DOWNLOAD", "Output File: ${mp3File.absolutePath}")
            Log.d("MP3_DOWNLOAD", "Exists: ${mp3File.exists()}")
            Log.d("MP3_DOWNLOAD", "Size: ${mp3File.length()}")

            if (ReturnCode.isSuccess(session.returnCode)) {
                if (!mp3File.exists()) {
                    Log.e("MP3_DOWNLOAD", "Returning Error: Output file not found")
                    return@withContext DownloadResult.Error("Output file not found")
                }
                if (mp3File.length() == 0L) {
                    Log.e("MP3_DOWNLOAD", "Returning Error: Output file is empty")
                    return@withContext DownloadResult.Error("Output file is empty")
                }

                Log.d("MP3_DOWNLOAD", "Conversion Finished successfully in ${conversionDuration}ms")

                onStatus(context.getString(R.string.status_writing_metadata))
                onStateChange(DownloadStatus.WRITING_METADATA)

                // 1. Fetch Fresh Metadata for ID3
                val freshMetadata = getVideoMetadata(url)
                
                // 2. Write ID3 tags directly to the CACHE file
                if (freshMetadata != null) {
                    try {
                        MetadataManager(context, httpClient).writeMetadata(mp3File, freshMetadata)
                        Log.d("METADATA", "ID3 tags and artwork written to cache file.")
                    } catch (e: Exception) {
                        Log.e("METADATA", "Failed to write ID3 tags, continuing with binary copy.", e)
                    }
                }

                // 3. Save to MediaStore (Premium logic with IS_PENDING and Music-specific columns)
                val sizeInBytes = mp3File.length()
                val fileSizeStr = formatFileSize(sizeInBytes)

                var savedUri: String? = null
                mp3File.inputStream().buffered(64 * 1024).use { input ->
                    savedUri = StorageUtils.saveFileToMusic(context, input, mp3FileName, "audio/mpeg", freshMetadata)?.toString()
                }

                Log.d("MEDIASTORE", "File saved to public storage: $savedUri")

                // 4. Verification
                if (savedUri != null) {
                    // Check if file is actually there and readable (binary safe)
                    if (mp3File.exists() && mp3File.length() > 0) {
                        Log.d("VERIFICATION", "Final verification passed: $savedUri")
                    }
                }

                // 5. Cleanup
                if (cancelReason != "pause") tempFile.delete()
                mp3File.delete()

                if (isCancelled) {
                    return@withContext DownloadResult.Error("Cancelled")
                }

                if (savedUri != null) {
                    onStatus(context.getString(R.string.status_completed))
                    onStateChange(DownloadStatus.COMPLETED)
                    val totalDuration = System.currentTimeMillis() - totalStartTime
                    Log.d("MP3_DOWNLOAD", "Download Process Finished. Total Time: ${totalDuration}ms")

                    return@withContext DownloadResult.Success(
                        com.vusal.soundra.data.DownloadHistoryEntity(
                            title = freshMetadata?.title ?: streamInfo.name,
                            artist = freshMetadata?.artist ?: streamInfo.uploaderName ?: context.getString(R.string.unknown),
                            thumbnailUrl = freshMetadata?.thumbnailUrl ?: streamInfo.thumbnails.firstOrNull()?.url ?: "",
                            localFilePath = savedUri!!,
                            fileSize = fileSizeStr,
                            duration = formatDuration(streamInfo.duration),
                            bitrate = bitrate,
                            downloadDate = System.currentTimeMillis()
                        )
                    )
                } else {
                    Log.e("MP3_DOWNLOAD", "Returning Error: MediaStore save failed")
                    onStatus(context.getString(R.string.err_rename_failed)) // Reusing a failure string
                    return@withContext DownloadResult.Error("MediaStore save failed")
                }
            } else {
                Log.e("MP3_DOWNLOAD", "Returning Error: FFmpeg conversion failed (Code: ${session.returnCode})")
                Log.e("MP3_DOWNLOAD", "FFmpeg Logs: ${session.allLogsAsString}")
                onStatus(context.getString(R.string.notif_failed))
                // If conversion failed, keep temp file for debugging/resume is not helpful; delete it
                if (cancelReason != "pause") tempFile.delete()
                return@withContext DownloadResult.Error("FFmpeg conversion failed")
            }
        } catch (t: Throwable) {
            Log.e("MP3_DOWNLOAD", "Returning Error: ${t.localizedMessage}")
            onStatus("${context.getString(R.string.state_failed)}: ${t.localizedMessage}")
            return@withContext DownloadResult.Error(t.localizedMessage ?: context.getString(R.string.unknown))
        }
    }

    /**
     * Cancel with optional reason. "pause" means keep temp file for a later resume.
     */
    fun cancel(reason: String = "cancel") {
        isCancelled = true
        cancelReason = reason
        FFmpegKit.cancel()
    }

    fun pause() {
        cancel("pause")
    }

    private fun formatFileSize(size: Long): String {
        return android.text.format.Formatter.formatFileSize(context, size)
    }

    private fun formatDuration(seconds: Long): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", mins, secs)
    }
}
