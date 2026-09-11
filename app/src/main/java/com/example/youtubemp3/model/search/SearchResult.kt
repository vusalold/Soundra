package com.vusal.soundra.model.search

data class SearchResult(
    val id: String,
    val title: String,
    val uploaderName: String,
    val duration: String,
    val thumbnailUrl: String,
    val url: String,
    val viewCount: Long = 0,
    val uploadDate: String = "",
    val isHd: Boolean = false
)
