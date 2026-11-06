// NetEaseLyricsProvider.kt

package com.mazharansari.dhun.lyrics

import android.content.Context
import com.music.directlyrics.NetEase
import com.mazharansari.dhun.constants.EnableNetEaseKey
import com.mazharansari.dhun.utils.dataStore
import com.mazharansari.dhun.utils.get

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
