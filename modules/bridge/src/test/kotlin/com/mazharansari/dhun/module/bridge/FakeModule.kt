package com.mazharansari.dhun.module.bridge

import com.mazharansari.dhun.module.AccountState
import com.mazharansari.dhun.module.DiagnosticStep
import com.mazharansari.dhun.module.HttpRequest
import com.mazharansari.dhun.module.ItemListModule
import com.mazharansari.dhun.module.ItemProblem
import com.mazharansari.dhun.module.ItemsAdded
import com.mazharansari.dhun.module.LicenseServer
import com.mazharansari.dhun.module.MatchedTrack
import com.mazharansari.dhun.module.ModuleChoice
import com.mazharansari.dhun.module.ModuleHost
import com.mazharansari.dhun.module.ModuleInfo
import com.mazharansari.dhun.module.ModuleItem
import com.mazharansari.dhun.module.ResolveResult
import com.mazharansari.dhun.module.SourceModule
import com.mazharansari.dhun.module.SourceStream
import com.mazharansari.dhun.module.StreamDrm
import com.mazharansari.dhun.module.StreamFormat
import com.mazharansari.dhun.module.StreamOption
import com.mazharansari.dhun.module.TrackQuery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation

// a module the bridge test loads on its own class loader, it reports back only through dhun's host
class FakeModule : SourceModule, ItemListModule {

    override val info = ModuleInfo("fake", "Fake", "1.0", "Answers the bridge test")

    override val itemsLabel = "Scripts"
    override val itemsAbout = ""

    override val choices = listOf(ModuleChoice("quality", "Stream", listOf(StreamOption("lossless", "Lossless", "FLAC"))))

    private lateinit var host: ModuleHost
    private val kept = ArrayList<ModuleItem>()

    override fun attach(host: ModuleHost) {
        this.host = host
        host.storage.set("folder", host.folder.path)
    }

    override fun accountState(): AccountState = AccountState.SignedIn("ready")

    override suspend fun resolve(track: TrackQuery, picks: Map<String, String>): ResolveResult {
        if (track.title == "boom") throw IllegalStateException("the module broke")
        if (track.title == "slow") {
            try {
                awaitCancellation()
            } catch (e: CancellationException) {
                host.log("slow search cancelled")
                throw e
            }
        }
        val answer = host.http(HttpRequest("https://fake.example/search?q=${track.title}"))
        host.log("search said ${answer.header("Content-Type")} ${answer.text}")
        return ResolveResult.Found(
            SourceStream(
                url = "https://cdn.example/espresso.mpd?q=${picks["quality"]}",
                format = StreamFormat.Dash,
                headers = mapOf("Referer" to "https://fake.example"),
                drm = StreamDrm.Widevine(LicenseServer { it.reversedArray() }, listOf(byteArrayOf(1, 2, 3))),
                sourceLabel = "Fake",
                qualityLabel = "FLAC · 24-bit · 96 kHz",
                matched = MatchedTrack("1", track.title, track.artists.joinToString(), null, 175_000, 0.9f),
                codec = "FLAC",
                sampleRateHz = 96_000,
            ),
        )
    }

    override suspend fun diagnose(track: TrackQuery, picks: Map<String, String>, report: (DiagnosticStep) -> Unit): ResolveResult {
        report(DiagnosticStep("Find", null, "Looking…"))
        report(DiagnosticStep("Find", false, "Not there"))
        return ResolveResult.NotFound("Not there")
    }

    override val items: List<ModuleItem> get() = kept.toList()

    override suspend fun addZip(fileName: String, bytes: ByteArray): ItemsAdded {
        val name = "$fileName:${bytes.size}"
        kept += ModuleItem(name, name, "")
        return ItemsAdded.Added(listOf(name))
    }

    override suspend fun addLink(link: String): ItemsAdded = ItemsAdded.Problem(ItemProblem.NotALink)

    override fun removeItem(id: String) {
        kept.removeAll { it.id == id }
    }
}

// a module built against an older api, which dhun has to turn away
class OldModule : SourceModule {
    override val info = ModuleInfo("old", "Old", "1.0", "Too old", apiVersion = 2)
    override fun attach(host: ModuleHost) = Unit
    override suspend fun resolve(track: TrackQuery, picks: Map<String, String>): ResolveResult = ResolveResult.NotFound("")
    override suspend fun diagnose(track: TrackQuery, picks: Map<String, String>, report: (DiagnosticStep) -> Unit): ResolveResult = ResolveResult.NotFound("")
}
