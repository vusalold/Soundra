package com.vusal.soundra.logic

import android.content.Context
import android.util.Log
import android.media.MediaScannerConnection
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.AndroidArtwork
import com.vusal.soundra.R
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class MetadataManager(private val context: Context, private val okHttpClient: OkHttpClient) {

    fun writeMetadata(file: File, metadata: VideoMetadata) {
        Log.d("METADATA", "--------------------------------------------------")
        Log.d("METADATA", "STARTING ID3 writing for: ${metadata.title}")
        Log.d("YOUTUBE_METADATA", "Title: ${metadata.title}, Artist: ${metadata.artist}, Album: ${metadata.album}")
        
        try {
            if (!file.exists()) {
                Log.e("METADATA", "ERROR: Target file does not exist: ${file.absolutePath}")
                return
            }

            val audioFile = AudioFileIO.read(file)
            // Configure JAudioTagger for broad Unicode support
            val tag = audioFile.tagOrCreateAndSetDefault
            
            // Critical Fields with clear priorities
            tag.setField(FieldKey.TITLE, metadata.title.trim())
            tag.setField(FieldKey.ARTIST, metadata.artist.trim())
            
            // Album fallback: Use detected album, or channel name, but never "Unknown"
            val albumName = metadata.album?.trim()?.ifBlank { null } ?: metadata.channelName.trim().ifBlank { null }
            albumName?.let { tag.setField(FieldKey.ALBUM, it) }
            
            tag.setField(FieldKey.ALBUM_ARTIST, (metadata.albumArtist ?: metadata.artist).trim())
            
            // Additional Fields
            metadata.genre?.trim()?.ifBlank { null }?.let { tag.setField(FieldKey.GENRE, it) }
            tag.setField(FieldKey.COMMENT, metadata.comment ?: context.getString(R.string.tag_comment))
            
            val producer = metadata.publisher?.trim()?.ifBlank { null } ?: metadata.channelName.trim().ifBlank { null }
            producer?.let { tag.setField(FieldKey.PRODUCER, it) }
            
            metadata.year?.let { 
                val yearStr = it.trim()
                if (yearStr.isNotBlank()) {
                    Log.d("ID3", "Writing Year: $yearStr")
                    tag.setField(FieldKey.YEAR, yearStr) 
                }
            }
            
            metadata.trackNumber?.trim()?.ifBlank { null }?.let { tag.setField(FieldKey.TRACK, it) }
            metadata.composer?.trim()?.ifBlank { null }?.let { tag.setField(FieldKey.COMPOSER, it) }
            
            // Premium Artwork embedding
            if (metadata.thumbnailUrl.isNotBlank()) {
                Log.d("ID3", "Attempting HQ Artwork embedding...")
                embedHighQualityAlbumArt(tag, metadata.thumbnailUrl)
            }

            audioFile.commit()
            Log.d("ID3", "ID3 tags committed successfully for: ${file.name}")
            
            // IMPORTANT: REMOVED early scan of cache file. 
            // The scan will happen in StorageUtils after the file is public.
        } catch (e: Exception) {
        } catch (e: Exception) {
            Log.e("METADATA", "CRITICAL ERROR during ID3 writing: ${e.message}", e)
        }
        Log.d("METADATA", "FINISHED ID3 process.")
        Log.d("METADATA", "--------------------------------------------------")
    }

    private fun embedHighQualityAlbumArt(tag: org.jaudiotagger.tag.Tag, thumbnailUrl: String) {
        try {
            // Try to force max resolution if it's a standard YouTube URL
            val highResUrl = if (thumbnailUrl.contains("i.ytimg.com/vi/")) {
                val videoId = thumbnailUrl.split("vi/")[1].split("/")[0]
                "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"
            } else if (thumbnailUrl.contains("ytimg.com")) {
                 // General ytimg handle
                 thumbnailUrl.replace(Regex("hqdefault|mqdefault|default"), "maxresdefault")
            } else thumbnailUrl

            Log.d("ID3", "Downloading artwork from: $highResUrl")
            var imageFile = downloadThumbnail(highResUrl)
            
            // Fallback if maxres doesn't exist (returns 404 or small file)
            if (imageFile == null || !imageFile.exists() || imageFile.length() < 1000) {
                Log.d("ID3", "High-res not available, falling back to original: $thumbnailUrl")
                imageFile = downloadThumbnail(thumbnailUrl)
            }

            if (imageFile != null && imageFile.exists()) {
                Log.d("ID3", "Embedding artwork file size: ${imageFile.length()} bytes")
                val artwork = AndroidArtwork()
                artwork.setFromFile(imageFile)
                tag.deleteArtworkField()
                tag.setField(artwork)
                imageFile.delete() 
                Log.d("ID3", "Artwork embedded successfully.")
            } else {
                Log.w("ID3", "Failed to download artwork file.")
            }
        } catch (e: Exception) {
            Log.e("ID3", "Artwork embedding FAILED: ${e.message}")
        }
    }

    private fun downloadThumbnail(url: String): File? {
        val tempFile = File(context.cacheDir, "thumb_${url.hashCode()}.jpg")
        try {
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w("ID3", "Download failed (HTTP ${response.code}): $url")
                    return null
                }
                val body = response.body ?: return null
                
                FileOutputStream(tempFile).use { out ->
                    body.byteStream().copyTo(out)
                }
                return tempFile
            }
        } catch (e: Exception) {
            Log.e("ID3", "Thumbnail download EXCEPTION: ${e.message}")
            return null
        }
    }
}
