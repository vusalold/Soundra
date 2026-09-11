package com.vusal.soundra.update

sealed class UpdateState {
    object Idle : UpdateState()
    object Loading : UpdateState()
    data class Success(val updateAvailable: Boolean, val versionInfo: VersionInfo? = null) : UpdateState()
    data class Error(val message: String) : UpdateState()
}
