package com.vm.soundra.update

import kotlinx.serialization.Serializable

@Serializable
data class VersionInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val changelog: List<String>
)
