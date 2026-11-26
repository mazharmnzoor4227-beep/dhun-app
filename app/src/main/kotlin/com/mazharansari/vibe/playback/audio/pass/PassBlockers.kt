// PassBlockers.kt

package com.mazharansari.vibe.playback.audio.pass

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import com.mazharansari.vibe.constants.AudioNormalizationKey
import com.mazharansari.vibe.constants.CrossfadeEnabledKey
import com.mazharansari.vibe.constants.CrossmixMode
import com.mazharansari.vibe.constants.CrossmixModeKey
import com.mazharansari.vibe.constants.MusicHapticsEnabledKey
import com.mazharansari.vibe.constants.SkipSilenceKey
import com.mazharansari.vibe.constants.TransportEffect
import com.mazharansari.vibe.constants.TransportEffectKey
import com.mazharansari.vibe.constants.transitionModeOf
import com.mazharansari.vibe.eq.AudioLabStore
import com.mazharansari.vibe.eq.EqSettings
import com.mazharansari.vibe.eq.LoudnessSettings
import com.mazharansari.vibe.eq.PlaybackSettings
import com.mazharansari.vibe.eq.ReverbSettings
import com.mazharansari.vibe.eq.SpatialSettings
import com.mazharansari.vibe.eq.VolumeSettings
import com.mazharansari.vibe.extensions.toEnum
import kotlinx.serialization.Serializable

/** each of these changes the sound on mochy's side, and an output that takes the song untouched can't, so songs play the normal way while one is on */
enum class PassBlocker { EQUALIZER, AUDIO_FX, VOLUME, NORMALIZATION, SPEED, TRANSITIONS, SILENCE_OR_HAPTICS }

/** the pieces of audio lab the blockers are read from, gathered so one call can decide */
data class LabSound(
    val eq: EqSettings,
    val reverb: ReverbSettings,
    val loudness: LoudnessSettings,
    val spatial: SpatialSettings,
    val playback: PlaybackSettings,
    val volume: VolumeSettings,
    val stage: Boolean = false,
    val mixer: Boolean = false,
)

fun passBlockersOf(prefs: Preferences, lab: LabSound, playerVolume: Float): Set<PassBlocker> = buildSet {
    if (lab.eq.enabled) add(PassBlocker.EQUALIZER)
    if (lab.reverb.enabled || lab.loudness.enabled || lab.spatial.enabled || lab.spatial.mono || lab.stage || lab.mixer) add(PassBlocker.AUDIO_FX)
    if (playerVolume < 0.999f || (lab.volume.enabled && lab.volume.limit < 0.999f)) add(PassBlocker.VOLUME)
    if (prefs[AudioNormalizationKey] ?: true) add(PassBlocker.NORMALIZATION)
    if (!lab.playback.isNormal) add(PassBlocker.SPEED)
    if (transitionModeOf(prefs) != CrossmixMode.OFF || prefs[TransportEffectKey].toEnum(TransportEffect.OFF) != TransportEffect.OFF) {
        add(PassBlocker.TRANSITIONS)
    }
    if ((prefs[SkipSilenceKey] ?: false) || (prefs[MusicHapticsEnabledKey] ?: false)) add(PassBlocker.SILENCE_OR_HAPTICS)
}

/** how every blocker was set just before "turn these off", so "turn these back on" can put each one back as it was */
@Serializable
data class PassBlockersBefore(
    val normalization: Boolean?,
    val crossmixMode: String?,
    val crossfade: Boolean?,
    val transportEffect: String?,
    val skipSilence: Boolean?,
    val haptics: Boolean?,
    val eq: Boolean,
    val reverb: Boolean,
    val loudness: Boolean,
    val spatial: Boolean,
    val mono: Boolean,
    val playback: PlaybackSettings,
    val volumeLimit: Float,
    val playerVolume: Float,
    val stage: Boolean = false,
    val mixer: Boolean = false,
) {
    companion object {
        /** null keys stay null, so a setting that was never touched goes back to its default rather than to a guess */
        fun of(prefs: Preferences, store: AudioLabStore, playerVolume: Float) = PassBlockersBefore(
            normalization = prefs[AudioNormalizationKey],
            crossmixMode = prefs[CrossmixModeKey],
            crossfade = prefs[CrossfadeEnabledKey],
            transportEffect = prefs[TransportEffectKey],
            skipSilence = prefs[SkipSilenceKey],
            haptics = prefs[MusicHapticsEnabledKey],
            eq = store.eq.value.enabled,
            reverb = store.reverb.value.enabled,
            loudness = store.loudness.value.enabled,
            spatial = store.spatial.value.enabled,
            mono = store.spatial.value.mono,
            playback = store.playback.value,
            volumeLimit = store.volume.value.limit,
            playerVolume = playerVolume,
            stage = store.stage.value.enabled,
            mixer = store.mixer.state.value.enabled,
        )
    }
}

/** puts the preference side back as it was before "turn these off" */
fun MutablePreferences.restorePassBlockers(before: PassBlockersBefore) {
    fun <T> put(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else this[key] = value
    }
    put(AudioNormalizationKey, before.normalization)
    put(CrossmixModeKey, before.crossmixMode)
    put(CrossfadeEnabledKey, before.crossfade)
    put(TransportEffectKey, before.transportEffect)
    put(SkipSilenceKey, before.skipSilence)
    put(MusicHapticsEnabledKey, before.haptics)
}

/** the audio lab side of the same; only what "turn these off" touched comes back, anything set since stays */
fun AudioLabStore.restorePassBlockers(before: PassBlockersBefore) {
    updateEq { it.copy(enabled = before.eq) }
    updateReverb { it.copy(enabled = before.reverb) }
    updateLoudness { it.copy(enabled = before.loudness) }
    updateSpatial { it.copy(enabled = before.spatial, mono = before.mono) }
    updateStage { it.copy(enabled = before.stage) }
    mixer.setEnabled(before.mixer)
    updatePlayback { before.playback }
    updateVolume { it.copy(limit = before.volumeLimit) }
}

/** turns off the preference side of every blocker; audio lab's own settings and the volume go through their owners */
fun MutablePreferences.clearPassBlockers() {
    this[AudioNormalizationKey] = false
    this[CrossmixModeKey] = CrossmixMode.OFF.name
    this[CrossfadeEnabledKey] = false
    this[TransportEffectKey] = TransportEffect.OFF.name
    this[SkipSilenceKey] = false
    this[MusicHapticsEnabledKey] = false
}

/** the audio lab side of the same */
fun AudioLabStore.clearPassBlockers() {
    updateEq { it.copy(enabled = false) }
    updateReverb { it.copy(enabled = false) }
    updateLoudness { it.copy(enabled = false) }
    updateSpatial { it.copy(enabled = false, mono = false) }
    updateStage { it.copy(enabled = false) }
    mixer.setEnabled(false)
    updatePlayback { PlaybackSettings() }
    updateVolume { it.copy(limit = 1f) }
}
