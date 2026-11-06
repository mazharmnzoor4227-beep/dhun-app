// DeezerLyricsProvider.kt

package com.mazharansari.dhun.lyrics

import android.content.Context
import com.music.directlyrics.Deezer
import com.mazharansari.dhun.constants.EnableDeezerKey
import com.mazharansari.dhun.utils.dataStore
import com.mazharansari.dhun.utils.get

object DeezerLyricsProvider : LyricsProvider {
    override val name = "Deezer"
    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableDeezerKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = Deezer.getLyrics(title, artist, duration)
}
