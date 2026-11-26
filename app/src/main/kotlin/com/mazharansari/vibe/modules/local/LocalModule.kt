// a module added from a zip with no service behind it: a look, a player, a font, a glass, pages, menu rows, nothing that ever reaches the network

package com.mazharansari.vibe.modules.local

import com.mazharansari.vibe.module.AccountState
import com.mazharansari.vibe.module.LookModule
import com.mazharansari.vibe.module.ModuleArt
import com.mazharansari.vibe.module.ModuleFeature
import com.mazharansari.vibe.module.ModuleField
import com.mazharansari.vibe.module.ModuleFields
import com.mazharansari.vibe.module.ModuleHost
import com.mazharansari.vibe.module.ModuleInfo
import com.mazharansari.vibe.module.NO_ACCOUNT_LABEL
import com.mazharansari.vibe.modules.AudioModule
import com.mazharansari.vibe.modules.ModuleAudio
import com.mazharansari.vibe.modules.skin.ModuleSkin
import com.mazharansari.vibe.modules.skin.SkinGlass
import com.mazharansari.vibe.modules.skin.SkinParser
import com.mazharansari.vibe.modules.skin.SkinSource
import com.mazharansari.vibe.modules.skin.appliedLook
import com.mazharansari.vibe.modules.web.FieldRange
import com.mazharansari.vibe.modules.web.FieldRanges
import com.mazharansari.vibe.modules.web.ManifestSettings
import com.mazharansari.vibe.modules.web.WebModuleManifest
import com.mazharansari.vibe.modules.web.resolveModuleArt
import java.io.File

internal class LocalModule(private val manifest: WebModuleManifest, files: File?) : LookModule, ModuleArt, ModuleFields, SkinSource, FieldRanges, AudioModule {

    override val info = ModuleInfo(
        id = idFor(manifest),
        name = manifest.name.ifBlank { manifest.id },
        version = manifest.version.ifBlank { "1" },
        description = manifest.description.ifBlank { "Added from a file" },
        author = manifest.author,
    )

    override val skin: ModuleSkin? = SkinParser.parse(
        info.id, info.name, manifest.player, manifest.font, manifest.pages, files, base = null,
        manifest.actions, manifest.glass, manifest.players, manifest.bar, manifest.mini,
        floating = manifest.floating, background = manifest.background, appBackground = manifest.appBackground,
        sheet = manifest.sheet, menu = manifest.menu, sheets = manifest.sheets,
    )

    // a zip of effects alone is no look, so it isn't listed among them
    override val adds: Set<ModuleFeature> = buildSet {
        if (manifest.changesLook || !manifest.bringsAudio) add(ModuleFeature.Look)
        if (manifest.bringsAudio) add(ModuleFeature.AudioProcessor)
    }

    override val audio: ModuleAudio? = manifest.audio?.let { ModuleAudio(info.id, info.name, it, files, nativeDir = null) }

    override val icon: String? = resolveModuleArt(manifest.icon, files, base = null)

    override val banner: String? = resolveModuleArt(manifest.banner, files, base = null)

    private val settings = ManifestSettings(manifest)

    override val fields: List<ModuleField> get() = settings.fields

    override val ranges: Map<String, FieldRange> get() = settings.ranges

    override fun onFields(values: Map<String, String>) {
        settings.values = values
    }

    // the base look with the picked settings laid over it, so changing an option on the page changes the look
    override val look: Map<String, String> get() = appliedLook(settings.look(manifest.lookValues), skin)

    override val glass: SkinGlass? get() = settings.glass(skin?.glass)

    override fun attach(host: ModuleHost) = Unit

    // there's no server to ask, it lives on this phone
    override suspend fun verifyAccount(): AccountState = AccountState.SignedIn(NO_ACCOUNT_LABEL)

    companion object {
        // local. in front, so a file can never take the place of a built-in module or one added from a link
        fun idFor(manifest: WebModuleManifest): String =
            "local." + manifest.id.ifBlank { manifest.name }.lowercase().replace(Regex("[^a-z0-9._-]"), "-")
    }
}
