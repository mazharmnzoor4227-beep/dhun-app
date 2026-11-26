// SimilarRecommendation.kt

package com.mazharansari.vibe.models

import com.music.innertube.models.YTItem
import com.mazharansari.vibe.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
