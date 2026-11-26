// NetEaseLyricsProvider.kt

package com.mazharansari.vibe.lyrics

import android.content.Context
import com.music.directlyrics.NetEase
import com.mazharansari.vibe.constants.EnableNetEaseKey
import com.mazharansari.vibe.utils.dataStore
import com.mazharansari.vibe.utils.get

object NetEaseLyricsProvider : LyricsProvider {
    override val name = "NetEase"
    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableNetEaseKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = NetEase.getLyrics(title, artist, duration)
}
