package com.vm.soundra.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class GitHubUpdateService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    suspend fun fetchVersionInfo(): UpdateResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(UpdateConstants.VERSION_JSON_URL)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext UpdateResult.Error(
                        UpdateResult.ErrorType.NETWORK_ERROR,
                        "HTTP ${response.code}"
                    )
                }

                val body = response.body?.string()
                if (body.isNullOrBlank()) {
                    return@withContext UpdateResult.Error(
                        UpdateResult.ErrorType.PARSE_ERROR,
                        "Empty response body"
                    )
                }

                try {
                    val versionInfo = json.decodeFromString<VersionInfo>(body)
                    UpdateResult.Success(versionInfo)
                } catch (e: Exception) {
                    UpdateResult.Error(UpdateResult.ErrorType.PARSE_ERROR, e.message)
                }
            }
        } catch (e: IOException) {
            UpdateResult.Error(UpdateResult.ErrorType.NETWORK_ERROR, e.message)
        } catch (e: Exception) {
            UpdateResult.Error(UpdateResult.ErrorType.UNKNOWN_ERROR, e.message)
        }
    }
}
