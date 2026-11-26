// pairs a signed code module with its manifest's look, player, settings and art so a code zip can ship a custom player and live surface

package com.mazharansari.vibe.modules.code

import com.mazharansari.vibe.module.AccountState
import com.mazharansari.vibe.module.DiagnosticStep
import com.mazharansari.vibe.module.LookModule
import com.mazharansari.vibe.module.LyricsModule
import com.mazharansari.vibe.module.MetadataModule
import com.mazharansari.vibe.module.MochyModule
import com.mazharansari.vibe.module.ModuleArt
import com.mazharansari.vibe.module.ModuleChoice
import com.mazharansari.vibe.module.ModuleFeature
import com.mazharansari.vibe.module.ModuleField
import com.mazharansari.vibe.module.ModuleFields
import com.mazharansari.vibe.module.ModuleHost
import com.mazharansari.vibe.module.ModuleInfo
import com.mazharansari.vibe.module.ResolveResult
import com.mazharansari.vibe.module.SignInSpec
import com.mazharansari.vibe.module.SourceModule
import com.mazharansari.vibe.module.TrackMetadata
import com.mazharansari.vibe.module.TrackQuery
import com.mazharansari.vibe.module.bridge.BridgedModule
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
import com.mazharansari.vibe.ui.player.styles.SurfaceSession
import java.io.File

internal class CodeSkinModule private constructor(
    private val inner: BridgedModule,
    private val manifest: WebModuleManifest,
    files: File?,
    nativeDir: File?,
) : SourceModule, LyricsModule, LookModule, MetadataModule, ModuleArt, ModuleFields, SkinSource, FieldRanges, AudioModule, AutoCloseable {

    override val info: ModuleInfo get() = inner.info

    override val skin: ModuleSkin? = SkinParser.parse(
        inner.info.id, inner.info.name, manifest.player, manifest.font, manifest.pages,
        files, base = null, manifest.actions, manifest.glass, manifest.players, manifest.bar, manifest.mini,
        surface = inner.surface, floating = manifest.floating, background = manifest.background,
        appBackground = manifest.appBackground, sheet = manifest.sheet, menu = manifest.menu, sheets = manifest.sheets,
    )

    override val adds: Set<ModuleFeature> = buildSet {
        addAll(inner.adds)
        if (skin != null || manifest.lookValues.isNotEmpty()) add(ModuleFeature.Look)
        if (manifest.bringsAudio) add(ModuleFeature.AudioProcessor)
    }

    // its own code may bring a native library of effects as well as scripts
    override val audio: ModuleAudio? = manifest.audio?.let { ModuleAudio(inner.info.id, inner.info.name, it, files, nativeDir) }

    override val signIn: SignInSpec? get() = inner.signIn

    override val choices: List<ModuleChoice> get() = inner.choices

    override val icon: String? = resolveModuleArt(manifest.icon, files, base = null) ?: inner.icon

    override val banner: String? = resolveModuleArt(manifest.banner, files, base = null) ?: inner.banner

    private val settings = ManifestSettings(manifest)

    override val fields: List<ModuleField> get() = settings.fields.ifEmpty { inner.fields }

    override val ranges: Map<String, FieldRange> get() = settings.ranges

    override fun onFields(values: Map<String, String>) {
        settings.values = values
        inner.onFields(values)
    }

    override val look: Map<String, String>
        get() = appliedLook(inner.look + settings.look(manifest.lookValues), skin)

    override val glass: SkinGlass? get() = settings.glass(skin?.glass)

    override fun attach(host: ModuleHost) = inner.attach(host)

    override fun accountState(): AccountState = inner.accountState()

    override fun onSignInHeader(name: String, value: String, url: String): Boolean = inner.onSignInHeader(name, value, url)

    override suspend fun verifyAccount(): AccountState = inner.verifyAccount()

    override fun signOut() = inner.signOut()

    override suspend fun resolve(track: TrackQuery, picks: Map<String, String>): ResolveResult = inner.resolve(track, picks)

    override suspend fun diagnose(track: TrackQuery, picks: Map<String, String>, report: (DiagnosticStep) -> Unit): ResolveResult =
        inner.diagnose(track, picks, report)

    override suspend fun lyrics(track: TrackQuery): String? = inner.lyrics(track)

    override suspend fun metadata(track: TrackQuery): TrackMetadata? = inner.metadata(track)

    override fun close() {
        SurfaceSession.release(inner.surface)
        inner.close()
    }

    companion object {
        /** returns [inner] untouched when the manifest has no skin, look or art of its own (so older code zips like js modules stay as-is) */
        fun wrap(inner: BridgedModule, manifest: WebModuleManifest?, files: File?, nativeDir: File? = null): MochyModule {
            if (manifest == null) return inner
            val hasExtras = manifest.changesLook || manifest.icon != null || manifest.banner != null || manifest.settings.isNotEmpty() || manifest.bringsAudio
            return if (hasExtras) CodeSkinModule(inner, manifest, files, nativeDir) else inner
        }
    }
}
