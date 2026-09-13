package com.vm.soundra.repository.search

import com.vm.soundra.model.search.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder

class SearchRepository {

    private val httpClient = OkHttpClient()

    suspend fun getYouTubeHomeFeed(): List<SearchResult> = withContext(Dispatchers.IO) {
        try {
            val service = ServiceList.YouTube
            val kioskId = service.kioskList.defaultKioskId
            val kioskInfo = KioskInfo.getInfo(service, kioskId)
            mapInfoItems(kioskInfo.relatedItems)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        
        try {
            val service = ServiceList.YouTube
            val handler = YoutubeSearchQueryHandlerFactory.getInstance().fromQuery(query)
            val searchInfo = SearchInfo.getInfo(service, handler)
            
            mapInfoItems(searchInfo.relatedItems)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://suggestqueries.google.com/complete/search?client=youtube&ds=yt&q=$encodedQuery"
            val request = Request.Builder().url(url).build()
            
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                
                // YouTube suggestion response format: 
                // window.google.ac.h(["query",[["suggestion1",0],["suggestion2",0]],{"k":1}])
                val jsonStartIndex = body.indexOf("([") + 1
                val jsonEndIndex = body.lastIndexOf("])") + 1
                if (jsonStartIndex <= 0 || jsonEndIndex <= 0) return@withContext emptyList()
                
                val jsonArray = JSONArray(body.substring(jsonStartIndex, jsonEndIndex))
                val suggestionsArray = jsonArray.getJSONArray(1)
                
                val result = mutableListOf<String>()
                for (i in 0 until suggestionsArray.length()) {
                    result.add(suggestionsArray.getJSONArray(i).getString(0))
                }
                result
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun mapInfoItems(items: List<InfoItem>): List<SearchResult> {
        return items.mapNotNull { item ->
            if (item is StreamInfoItem) {
                SearchResult(
                    id = extractIdFromUrl(item.url),
                    title = item.name ?: "",
                    uploaderName = item.uploaderName ?: "Bilinmir",
                    duration = formatDuration(item.duration),
                    thumbnailUrl = item.thumbnails.firstOrNull()?.url ?: "",
                    url = item.url ?: "",
                    viewCount = item.viewCount,
                    uploadDate = item.textualUploadDate ?: "",
                    isHd = false
                )
            } else null
        }
    }

    private fun extractIdFromUrl(url: String?): String {
        if (url == null) return ""
        return try {
            if (url.contains("v=")) {
                url.split("v=")[1].split("&")[0]
            } else if (url.contains("youtu.be/")) {
                url.split("youtu.be/")[1].split("?")[0]
            } else ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return "00:00"
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format(java.util.Locale.US, "%02d:%02d", mins, secs)
    }
}
