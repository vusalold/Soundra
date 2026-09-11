package com.vusal.soundra.logic

import android.content.Context
import android.util.Log
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.net.InetAddress
import java.util.concurrent.TimeUnit

object ExtractorHelper {
    private var isInitialized = false

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            .dns { hostname ->
                val addresses = InetAddress.getAllByName(hostname).toList()
                addresses.sortedBy { it.address.size }
            }
            .build()
    }

    fun init(context: Context) {
        // Just prepare the downloader, don't set system default localization here.
        // Localization will be set via LanguageManager.applyInitialLanguage() on startup.
        isInitialized = true
        Log.d("ExtractorHelper", "NewPipe Extractor base initialized")
    }

    fun updateLocalization(language: String, country: String) {
        try {
            val downloader = NewPipe.getDownloader() ?: OkHttpDownloader(httpClient)
            NewPipe.init(
                downloader, 
                Localization(language, country), 
                ContentCountry(country)
            )
            isInitialized = true
            Log.d("ExtractorHelper", "NewPipe Extractor updated with $language-$country")
        } catch (e: Exception) {
            Log.e("ExtractorHelper", "NewPipe Extractor update failed", e)
        }
    }
}
