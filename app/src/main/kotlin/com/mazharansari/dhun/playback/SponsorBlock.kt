package com.mazharansari.dhun.playback

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import com.mazharansari.dhun.constants.SponsorBlockEnabledKey
import com.mazharansari.dhun.constants.SponsorBlockCategories
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * SponsorBlock (https://sponsor.ajay.app): community-submitted segments of a YouTube video that are
 * not the music itself (sponsor reads, self-promotion, "like and subscribe", intros / outros, non-music
 * sections). While a song plays, the segments for it are fetched once and the player jumps past the ones
 * the listener turned on in Settings.
 *
 * Dhun's own implementation against the public segment API, documented at
 * https://wiki.sponsor.ajay.app/w/API_Docs
 */
data class SponsorSegment(
    val start: Double,
    val end: Double,
    val category: String,
    val uuid: String,
)

object SponsorBlockApi {
    private const val BASE = "https://sponsor.ajay.app/api/skipSegments"

    /** only real YouTube video ids are looked up; local files and other providers' ids never leave the phone */
    private val VideoId = Regex("^[A-Za-z0-9_-]{11}$")

    fun isLookupable(mediaId: String): Boolean = VideoId.matches(mediaId)

    /**
     * The skip segments for [videoId] in [categories]. Uses the privacy-preserving endpoint: only the first
     * 4 hex digits of the video id's SHA-256 are sent, and the matching video is picked out of the answer here.
     * Returns an empty list when SponsorBlock has nothing for the video (it answers 404) or on any failure.
     */
    fun fetch(videoId: String, categories: Set<String>): List<SponsorSegment> {
        if (categories.isEmpty() || !isLookupable(videoId)) return emptyList()
        val hash = MessageDigest.getInstance("SHA-256").digest(videoId.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(4)
        val cats = URLEncoder.encode(JSONArray(categories.toList()).toString(), "UTF-8")
        val actions = URLEncoder.encode("[\"skip\"]", "UTF-8")
        val url = URL("$BASE/$hash?categories=$cats&actionTypes=$actions&service=YouTube")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 6_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Dhun/1.0.0")
        }
        return try {
            if (conn.responseCode != 200) return emptyList()
            val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            val out = ArrayList<SponsorSegment>()
            for (i in 0 until arr.length()) {
                val video: JSONObject = arr.getJSONObject(i)
                if (video.optString("videoID") != videoId) continue
                val segments = video.optJSONArray("segments") ?: continue
                for (j in 0 until segments.length()) {
                    val s = segments.getJSONObject(j)
                    if (s.optString("actionType", "skip") != "skip") continue
                    val range = s.optJSONArray("segment") ?: continue
                    if (range.length() < 2) continue
                    val start = range.getDouble(0)
                    val end = range.getDouble(1)
                    // too short to skip cleanly, and a seek that lands short of the end would re-trigger forever
                    if (end - start < MIN_SEGMENT_SECONDS) continue
                    out += SponsorSegment(start, end, s.optString("category"), s.optString("UUID"))
                }
            }
            out.sortedBy { it.start }
        } catch (e: Exception) {
            Timber.tag("SponsorBlock").w(e, "segment lookup failed for %s", videoId)
            emptyList()
        } finally {
            conn.disconnect()
        }
    }

    const val MIN_SEGMENT_SECONDS = 1.0
}

/**
 * Watches whatever [player] currently is (the service swaps players for crossfade) and seeks past segments.
 * It does nothing at all while the switch is off: no timer runs and nothing is fetched.
 */
class SponsorBlockSkipper(
    private val scope: CoroutineScope,
    private val dataStore: DataStore<Preferences>,
    private val player: () -> ExoPlayer,
    private val onSkipped: (SponsorSegment) -> Unit = {},
) {
    @Volatile private var enabled = false
    @Volatile private var categories: Set<String> = emptySet()

    private val cache = object : LinkedHashMap<String, List<SponsorSegment>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<SponsorSegment>>?) = size > 40
    }
    private var loop: Job? = null
    private var loadedFor: String? = null
    private var segments: List<SponsorSegment> = emptyList()
    private var lastSkippedUuid: String? = null
    private var lastSkippedAt = 0L

    fun start() {
        scope.launch {
            dataStore.data
                .map { prefs ->
                    val on = prefs[SponsorBlockEnabledKey] ?: false
                    val cats = SponsorBlockCategories.filter { c -> prefs[c.key] ?: c.defaultOn }.flatMap { it.apiNames }.toSet()
                    on to cats
                }
                .distinctUntilChanged()
                .collect { (on, cats) ->
                    enabled = on
                    categories = cats
                    // categories changed: segments fetched for the old set are stale
                    synchronized(cache) { cache.clear() }
                    loadedFor = null
                    if (on && loop?.isActive != true) loop = scope.launch { run() }
                    if (!on) { loop?.cancel(); loop = null }
                }
        }
    }

    private suspend fun run() {
        while (enabled) {
            val p = player()
            if (!p.isPlaying) { delay(1_000); continue }
            val id = p.currentMediaItem?.mediaId
            if (id != null && id != loadedFor) {
                loadedFor = id
                segments = emptyList()
                val cached = synchronized(cache) { cache[id] }
                if (cached != null) segments = cached
                else if (SponsorBlockApi.isLookupable(id)) {
                    val cats = categories
                    val fetched = withContext(Dispatchers.IO) { SponsorBlockApi.fetch(id, cats) }
                    // still the same song after the network call?
                    if (player().currentMediaItem?.mediaId == id) {
                        synchronized(cache) { cache[id] = fetched }
                        segments = fetched
                    }
                }
            }
            if (segments.isNotEmpty()) skipIfInside(p)
            delay(if (segments.isEmpty()) 1_500 else 350)
        }
    }

    private fun skipIfInside(p: ExoPlayer) {
        val now = p.currentPosition / 1000.0
        val seg = segments.firstOrNull { now >= it.start && now < it.end } ?: return
        // the same segment is never skipped twice within a few seconds, so a seek that lands short cannot loop
        val t = System.currentTimeMillis()
        if (seg.uuid == lastSkippedUuid && t - lastSkippedAt < 3_000) return
        lastSkippedUuid = seg.uuid
        lastSkippedAt = t
        val durationMs = p.duration
        val targetMs = ((seg.end + 0.15) * 1000).toLong()
        if (durationMs != C.TIME_UNSET && targetMs >= durationMs - 800) {
            // the segment runs to the end of the video: move on to the next song if there is one
            if (p.hasNextMediaItem()) p.seekToNextMediaItem() else p.seekTo(maxOf(0L, durationMs - 300))
        } else {
            p.seekTo(targetMs)
        }
        onSkipped(seg)
    }
}
