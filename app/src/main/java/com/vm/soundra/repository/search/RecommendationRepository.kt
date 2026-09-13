package com.vm.soundra.repository.search

import com.vm.soundra.model.search.SearchResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class RecommendationRepository(private val searchRepository: SearchRepository) {

    /**
     * Fetches results for multiple queries and merges them.
     */
    suspend fun getRecommendations(queries: List<String>): List<SearchResult> = coroutineScope {
        if (queries.isEmpty()) {
            // Fallback for new users: General music trends
            return@coroutineScope searchRepository.search("pop music 2026")
        }

        // Parallel searches for each query
        val deferredResults = queries.map { query ->
            async { 
                try {
                    searchRepository.search(query).take(10)
                } catch (e: Exception) {
                    emptyList<SearchResult>()
                }
            }
        }

        val allResults = deferredResults.awaitAll().flatten()

        // Deduplicate by ID and shuffle
        allResults.distinctBy { it.id }.shuffled()
    }
}
