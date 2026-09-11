package com.vusal.soundra.update

sealed class UpdateResult {
    data class Success(val versionInfo: VersionInfo) : UpdateResult()
    object NoUpdate : UpdateResult()
    data class Error(val type: ErrorType, val message: String? = null) : UpdateResult()

    enum class ErrorType {
        NETWORK_ERROR,
        PARSE_ERROR,
        UNKNOWN_ERROR
    }
}
