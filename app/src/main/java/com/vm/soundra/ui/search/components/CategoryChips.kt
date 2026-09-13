package com.vm.soundra.ui.search.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vm.soundra.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryChips(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = listOf(
        "all" to R.string.cat_all,
        "music" to R.string.cat_music,
        "gaming" to R.string.cat_gaming,
        "live" to R.string.cat_live,
        "news" to R.string.cat_news,
        "mixes" to R.string.cat_mixes,
        "recs" to R.string.cat_recs,
        "popular" to R.string.cat_popular
    )
    
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { (id, resId) ->
            FilterChip(
                selected = selectedCategory == id,
                onClick = { onCategorySelected(id) },
                label = { Text(stringResource(resId)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = null
            )
        }
    }
}
