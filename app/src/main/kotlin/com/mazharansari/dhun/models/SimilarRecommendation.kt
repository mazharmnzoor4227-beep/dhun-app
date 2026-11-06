// SimilarRecommendation.kt

package com.mazharansari.dhun.models

import com.music.innertube.models.YTItem
import com.mazharansari.dhun.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
