// QQMusicLyricsProvider.kt

package com.mazharansari.vibe.lyrics

import android.content.Context
import com.music.directlyrics.QQMusic
import com.mazharansari.vibe.constants.EnableQQMusicKey
import com.mazharansari.vibe.utils.dataStore
import com.mazharansari.vibe.utils.get

object QQMusicLyricsProvider : LyricsProvider {
    override val name = "QQMusic"
    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableQQMusicKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = QQMusic.getLyrics(title, artist, duration)
}
