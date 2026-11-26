package com.mazharansari.vibe.di

import com.mazharansari.vibe.eq.AudioLabStore
import com.mazharansari.vibe.playback.SongTuning
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** for UI outside a Hilt view model (the player's sheets) that shares Audio Lab's settings */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AudioLabEntryPoint {
    fun audioLabStore(): AudioLabStore
    fun songTuning(): SongTuning
}
