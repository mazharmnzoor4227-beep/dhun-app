package com.mazharansari.dhun.rooms

import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.mazharansari.dhun.extensions.metadata
import com.mazharansari.dhun.extensions.toMediaItem
import com.mazharansari.dhun.models.MediaMetadata
import com.mazharansari.dhun.playback.queues.ListQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Keeps the local player and the room together. What this phone does (play, pause, seek, a new song)
 * goes out; what the room does is applied here, with [applying] holding back the echo.
 * Songs are passed as YouTube ids, so every phone resolves the stream itself.
 */
class RoomsSync(
    private val scope: CoroutineScope,
    private val player: () -> Player,
    private val playQueue: (ListQueue, Boolean) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var applying = false
    private var releaseApplying: Runnable? = null

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (applying || !RoomsManager.inRoom) return
            val p = player()
            val track = p.currentMediaItem?.metadata?.toRoomTrack() ?: return
            RoomsManager.sendTrack(track, upcoming(p), p.playWhenReady, p.currentPosition.coerceAtLeast(0))
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (applying || !RoomsManager.inRoom) return
            val pos = player().currentPosition.coerceAtLeast(0)
            if (playWhenReady) RoomsManager.sendPlay(pos) else RoomsManager.sendPause(pos)
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (applying || !RoomsManager.inRoom) return
            if (reason == Player.DISCONTINUITY_REASON_SEEK && oldPosition.mediaItemIndex == newPosition.mediaItemIndex) {
                RoomsManager.sendSeek(newPosition.positionMs.coerceAtLeast(0))
            }
        }
    }

    fun start() {
        scope.launch {
            RoomsManager.events.collect { e ->
                runCatching { apply(e) }
            }
        }
        scope.launch {
            // the player can be replaced; keep our listener on whichever one is current
            var attached: Player? = null
            while (true) {
                val p = runCatching { player() }.getOrNull()
                if (p != null && p !== attached) {
                    attached?.removeListener(listener)
                    p.addListener(listener)
                    attached = p
                }
                kotlinx.coroutines.delay(2_000)
            }
        }
    }

    private fun hold() {
        applying = true
        releaseApplying?.let { main.removeCallbacks(it) }
        val r = Runnable { applying = false }
        releaseApplying = r
        main.postDelayed(r, 1_500)
    }

    private fun apply(e: RoomEvent) {
        val p = player()
        when (e) {
            is RoomEvent.Snapshot -> {
                val t = e.track ?: return
                hold()
                playQueue(ListQueue(title = null, items = listOf(t.toItem()) + e.queue.map { it.toItem() }, startIndex = 0, position = e.posMs), e.playing)
            }
            is RoomEvent.Track -> {
                hold()
                if (p.currentMediaItem?.mediaId == e.track.id) {
                    if (abs(p.currentPosition - e.posMs) > 2_500) p.seekTo(e.posMs)
                    p.playWhenReady = e.playing
                } else {
                    playQueue(ListQueue(title = null, items = listOf(e.track.toItem()) + e.queue.map { it.toItem() }, startIndex = 0, position = e.posMs), e.playing)
                }
            }
            is RoomEvent.Play -> {
                hold()
                if (abs(p.currentPosition - e.posMs) > 1_500) p.seekTo(e.posMs)
                p.playWhenReady = true
            }
            is RoomEvent.Pause -> {
                hold()
                p.playWhenReady = false
                if (abs(p.currentPosition - e.posMs) > 1_500) p.seekTo(e.posMs)
            }
            is RoomEvent.Seek -> {
                hold()
                p.seekTo(e.posMs)
            }
            is RoomEvent.QueueAdd -> {
                hold()
                p.addMediaItem(e.track.toItem())
            }
        }
    }

    private fun upcoming(p: Player): List<RoomTrack> {
        val out = ArrayList<RoomTrack>()
        var i = p.currentMediaItemIndex + 1
        while (i < p.mediaItemCount && out.size < 30) {
            p.getMediaItemAt(i).metadata?.toRoomTrack()?.let { out += it }
            i++
        }
        return out
    }

    private fun MediaMetadata.toRoomTrack() = RoomTrack(
        id = id,
        title = title,
        artist = artists.joinToString { it.name },
        thumb = thumbnailUrl.orEmpty(),
        durationSec = duration,
    )

    private fun RoomTrack.toItem(): MediaItem = MediaMetadata(
        id = id,
        title = title,
        artists = listOf(MediaMetadata.Artist(id = null, name = artist)),
        duration = durationSec,
        thumbnailUrl = thumb.ifBlank { null },
    ).toMediaItem()
}
