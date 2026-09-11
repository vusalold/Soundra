package com.vusal.soundra.logic

import com.vusal.soundra.data.DownloadTaskEntity

class RecommendationEngine {

    /**
     * Analyzes user behavior and returns a list of keywords to search.
     */
    fun generateRecommendationQueries(
        recentSearches: List<String>,
        completedDownloads: List<DownloadTaskEntity>
    ): List<String> {
        val scores = mutableMapOf<String, Double>()

        // 1. Analyze Download History (Higher Priority)
        completedDownloads.forEachIndexed { index, task ->
            val weight = (1.0 - (index.toDouble() / maxOf(completedDownloads.size, 1))) * 5.0
            
            // Score the artist
            val artist = task.artist.trim()
            if (artist.isNotBlank() && !artist.equals("Unknown", true)) {
                scores[artist] = (scores[artist] ?: 0.0) + weight
            }

            // Score keywords from title (simple split for now)
            val titleKeywords = extractKeywords(task.title)
            titleKeywords.forEach { kw ->
                scores[kw] = (scores[kw] ?: 0.0) + (weight * 0.5)
            }
        }

        // 2. Analyze Search History
        recentSearches.forEachIndexed { index, query ->
            val weight = (1.0 - (index.toDouble() / maxOf(recentSearches.size, 1))) * 2.0
            scores[query] = (scores[query] ?: 0.0) + weight

            extractKeywords(query).forEach { kw ->
                scores[kw] = (scores[kw] ?: 0.0) + (weight * 0.3)
            }
        }

        // 3. Filter and Sort
        return scores.entries
            .filter { it.key.length > 2 }
            .sortedByDescending { it.value }
            .map { it.key }
            .take(4) // Pick top 4 keywords/artists
    }

    private fun extractKeywords(text: String): List<String> {
        return text.split(" ", "-", "(", ")", "[", "]", ",", ".", "|")
            .map { it.trim().lowercase() }
            .filter { it.length > 3 && !isStopWord(it) }
    }

    private fun isStopWord(word: String): Boolean {
        val stopWords = setOf(
            "mp3", "download", "yüklə", "indir", "official", "video", "audio", "lyrics", "mahni", "şarkı", 
            "2023", "2024", "2025", "2026", "yeni", "new", "full", "hd", "hq", "remix", "cover"
        )
        return stopWords.contains(word)
    }
}
