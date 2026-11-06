// CommunityLyricsProvider.kt

package com.mazharansari.dhun.lyrics

import android.content.Context
import com.mazharansari.dhun.constants.EnableCommunityLyricsKey
import com.mazharansari.dhun.utils.dataStore
import com.mazharansari.dhun.utils.get
import com.music.communitylyrics.CommunityLyrics

object CommunityLyricsProvider : LyricsProvider {
    override val name = "SimpMusic"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableCommunityLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = CommunityLyrics.getLyrics(id, duration)

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (String) -> Unit,
    ) {
        CommunityLyrics.getAllLyrics(id, duration, callback)
    }
}
