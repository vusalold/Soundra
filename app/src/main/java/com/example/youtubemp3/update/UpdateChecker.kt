package com.vusal.soundra.update

import com.vusal.soundra.BuildConfig

class UpdateChecker(private val service: GitHubUpdateService) {

    suspend fun checkForUpdate(): UpdateResult {
        val result = service.fetchVersionInfo()
        
        if (result is UpdateResult.Success) {
            val currentVersionCode = BuildConfig.VERSION_CODE
            val githubVersionCode = result.versionInfo.versionCode
            
            return if (githubVersionCode > currentVersionCode) {
                result
            } else {
                UpdateResult.NoUpdate
            }
        }
        
        return result
    }
}
