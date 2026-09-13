package com.vm.soundra.logic

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NewPipeRequest
import org.schabi.newpipe.extractor.downloader.Response as NewPipeResponse
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class OkHttpDownloader(sharedClient: OkHttpClient) : Downloader() {
    private val cookieMap = mutableMapOf<String, List<Cookie>>()

    private val client: OkHttpClient = sharedClient.newBuilder()
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                cookieMap[url.host] = cookies
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieMap[url.host] ?: listOf()
            }
        })
        .build()

    @Throws(IOException::class)
    override fun execute(request: NewPipeRequest): NewPipeResponse {
        val url = request.url()
        Log.d("MP3_DOWNLOAD", "OkHttpDownloader: Executing request for $url")
        val headers = request.headers()
        val method = request.httpMethod()
        val bodyData = request.dataToSend()
        
        val builder = Request.Builder().url(url)
        
        headers.forEach { (name, values) ->
            values.forEach { value ->
                builder.addHeader(name, value)
            }
        }

        if (method.equals("POST", ignoreCase = true)) {
            val contentType = headers["Content-Type"]?.firstOrNull() ?: "application/json"
            val mediaType = contentType.toMediaTypeOrNull()
            val body = bodyData?.toRequestBody(mediaType) ?: "".toByteArray().toRequestBody(mediaType)
            builder.post(body)
        } else {
            builder.get()
        }

        val okRequest = builder.build()
        Log.d("MP3_DOWNLOAD", "OkHttpDownloader: Calling client.newCall.execute()")
        val okResponse = client.newCall(okRequest).execute()
        Log.d("MP3_DOWNLOAD", "OkHttpDownloader: Response received, code: ${okResponse.code}")
        
        val responseBody = okResponse.body?.string() ?: ""
        val responseCode = okResponse.code
        val responseMessage = okResponse.message
        val responseHeaders = okResponse.headers.toMultimap()

        if (okResponse.header("Content-Type")?.contains("text/html") == true && 
            (url.contains("youtubei") || url.contains("googlevideo"))) {
            Log.w("OkHttpDownloader", "HTML response for $url (Code: $responseCode)")
            Log.d("MP3_DOWNLOAD", "OkHttpDownloader WARNING: HTML detected in API response")
        }

        return NewPipeResponse(responseCode, responseMessage, responseHeaders, responseBody, url)
    }
}

