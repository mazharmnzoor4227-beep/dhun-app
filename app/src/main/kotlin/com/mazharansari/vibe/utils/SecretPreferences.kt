// SecretPreferences.kt

package com.mazharansari.vibe.utils

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.mazharansari.vibe.constants.DataSyncIdKey
import com.mazharansari.vibe.constants.DeeplApiKey
import com.mazharansari.vibe.constants.DiscordAccessTokenKey
import com.mazharansari.vibe.constants.DiscordLoginStateKey
import com.mazharansari.vibe.constants.DiscordLoginVerifierKey
import com.mazharansari.vibe.constants.DiscordRefreshTokenKey
import com.mazharansari.vibe.constants.InnerTubeCookieKey
import com.mazharansari.vibe.constants.LastFMSessionKey
import com.mazharansari.vibe.constants.LibreFmSessionKey
import com.mazharansari.vibe.constants.MonochromePlaybackApiTokenKey
import com.mazharansari.vibe.constants.MonochromeTurnstileJwtKey
import com.mazharansari.vibe.constants.OpenRouterApiKey
import com.mazharansari.vibe.constants.ProxyPasswordKey
import com.mazharansari.vibe.constants.TurnstileBypassTokenKey
import com.mazharansari.vibe.constants.VisitorDataKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// the settings store with its secrets sealed on disk and plain in memory, so every reader and writer in the app sees them as before
class SecretPreferences(private val raw: DataStore<Preferences>) : DataStore<Preferences> {
    override val data: Flow<Preferences> = raw.data.map(::unseal)

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val stored = raw.updateData { stored -> seal(transform(unseal(stored)), stored) }
        return unseal(stored)
    }

    // upgrade plain or old enc1 secrets to the new random-key vault on boot
    suspend fun sealLeftovers() {
        val stored = raw.data.first()
        if (SecretKeys.none { key -> stored[key]?.let { SecretVault.needsUpgrade(it) } == true }) return
        raw.updateData { seal(unseal(it), it) }
    }

    companion object {
        // yt cookies, tokens, api keys — stuff nobody else should ever see
        val SecretKeys: List<Preferences.Key<String>> = listOf(
            InnerTubeCookieKey,
            VisitorDataKey,
            DataSyncIdKey,
            DiscordAccessTokenKey,
            DiscordRefreshTokenKey,
            DiscordLoginStateKey,
            DiscordLoginVerifierKey,
            LastFMSessionKey,
            LibreFmSessionKey,
            OpenRouterApiKey,
            DeeplApiKey,
            MonochromePlaybackApiTokenKey,
            MonochromeTurnstileJwtKey,
            TurnstileBypassTokenKey,
            ProxyPasswordKey,
        )

        // open secrets for the app and keep SecretGuard updated so modules can't smuggle them out
        fun unseal(stored: Preferences): Preferences {
            val out = stored.toMutablePreferences()
            val active = ArrayList<String>(SecretKeys.size)
            for (key in SecretKeys) {
                val value = stored[key] ?: continue
                val plain = SecretVault.open(value, SecretVault.SettingsDomain)
                if (plain == null) {
                    out.remove(key)
                } else {
                    if (plain != value) out[key] = plain
                    if (key != VisitorDataKey && plain.isNotBlank()) active += plain
                }
            }
            com.mazharansari.vibe.modules.security.SecretGuard.register("settings", *active.toTypedArray())
            return out
        }

        fun seal(plain: Preferences, stored: Preferences): Preferences {
            val out = plain.toMutablePreferences()
            for (key in SecretKeys) {
                val value = plain[key] ?: continue
                val before = stored[key]
                out[key] = if (before != null && !SecretVault.needsUpgrade(before) && SecretVault.open(before, SecretVault.SettingsDomain) == value) {
                    before
                } else {
                    SecretVault.seal(value, SecretVault.SettingsDomain)
                }
            }
            return out
        }
    }
}
