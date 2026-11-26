package com.mazharansari.vibe.rooms

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** one person in the room */
data class RoomMember(val id: String, val name: String, val host: Boolean)

/** a song as the room passes it around */
data class RoomTrack(val id: String, val title: String, val artist: String, val thumb: String, val durationSec: Int)

data class RoomChat(val from: String, val text: String)

enum class RoomPhase { Idle, Connecting, InRoom }

data class RoomUiState(
    val phase: RoomPhase = RoomPhase.Idle,
    val code: String? = null,
    val myId: String? = null,
    val members: List<RoomMember> = emptyList(),
    val isHost: Boolean = false,
    /** the last problem, in words for the screen; cleared when a new attempt starts */
    val error: String? = null,
    val chat: List<RoomChat> = emptyList(),
)

/** what the playback side needs to act on; positions are milliseconds */
sealed interface RoomEvent {
    data class Snapshot(val track: RoomTrack?, val queue: List<RoomTrack>, val playing: Boolean, val posMs: Long) : RoomEvent
    data class Play(val posMs: Long) : RoomEvent
    data class Pause(val posMs: Long) : RoomEvent
    data class Seek(val posMs: Long) : RoomEvent
    data class Track(val track: RoomTrack, val queue: List<RoomTrack>, val playing: Boolean, val posMs: Long) : RoomEvent
    data class QueueAdd(val track: RoomTrack) : RoomEvent
}

/**
 * The client for a Dhun rooms relay (see dhun-rooms-server). Holds one connection at a time.
 * Nothing here touches the player; [RoomsSync] listens to [events] and talks to the player.
 */
object RoomsManager {
    private val client by lazy {
        OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val _state = MutableStateFlow(RoomUiState())
    val state: StateFlow<RoomUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<RoomEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<RoomEvent> = _events.asSharedFlow()

    @Volatile private var socket: WebSocket? = null

    val inRoom: Boolean get() = _state.value.phase == RoomPhase.InRoom

    fun create(serverUrl: String, key: String, name: String) = open(serverUrl, key, JSONObject().put("t", "create").put("name", name))

    fun join(serverUrl: String, key: String, name: String, code: String) =
        open(serverUrl, key, JSONObject().put("t", "join").put("name", name).put("code", code.trim().uppercase()))

    fun leave() {
        val s = socket
        socket = null
        if (s != null) {
            runCatching { s.send(JSONObject().put("t", "leave").toString()) }
            runCatching { s.close(1000, "leave") }
        }
        _state.value = RoomUiState()
    }

    fun sendPlay(posMs: Long) = send(JSONObject().put("t", "play").put("pos", posMs))
    fun sendPause(posMs: Long) = send(JSONObject().put("t", "pause").put("pos", posMs))
    fun sendSeek(posMs: Long) = send(JSONObject().put("t", "seek").put("pos", posMs))
    fun sendTrack(track: RoomTrack, queue: List<RoomTrack>, playing: Boolean, posMs: Long) {
        send(
            JSONObject().put("t", "track").put("track", track.toJson()).put("pos", posMs).put("playing", playing)
                .put("queue", JSONArray(queue.map { it.toJson() })),
        )
    }
    fun sendChat(text: String) = send(JSONObject().put("t", "chat").put("text", text))

    private fun send(o: JSONObject) {
        if (!inRoom) return
        socket?.send(o.toString())
    }

    private fun normalise(serverUrl: String, key: String): String? {
        var u = serverUrl.trim()
        if (u.isEmpty()) return null
        if (!u.contains("://")) u = "ws://$u"
        u = u.replaceFirst("http://", "ws://").replaceFirst("https://", "wss://")
        if (!u.substringAfter("://").contains("/")) u = "$u/ws"
        if (key.isNotBlank()) u += (if (u.contains("?")) "&" else "?") + "key=" + java.net.URLEncoder.encode(key.trim(), "UTF-8")
        return u
    }

    private fun open(serverUrl: String, key: String, hello: JSONObject) {
        leave()
        val url = normalise(serverUrl, key)
        if (url == null) {
            _state.value = RoomUiState(error = "Enter your rooms server address first")
            return
        }
        _state.value = RoomUiState(phase = RoomPhase.Connecting)
        val request = try {
            Request.Builder().url(url).build()
        } catch (e: IllegalArgumentException) {
            _state.value = RoomUiState(error = "That server address is not valid")
            return
        }
        lateinit var ws: WebSocket
        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(hello.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (socket != null && webSocket !== socket) return
                runCatching { handle(JSONObject(text)) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                lost(webSocket, "The connection to the room closed")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val why = if (response?.code == 401) "The server refused the key" else "Could not reach the rooms server"
                lost(webSocket, why)
            }
        })
        socket = ws
    }

    private fun lost(webSocket: WebSocket, why: String) {
        if (webSocket !== socket) return
        socket = null
        val cur = _state.value
        _state.value = RoomUiState(error = if (cur.phase == RoomPhase.Idle) null else why)
    }

    private fun JSONObject.str(k: String) = optString(k, "")

    private fun parseTrack(o: JSONObject?): RoomTrack? {
        if (o == null || o.str("id").isEmpty()) return null
        return RoomTrack(o.str("id"), o.str("title"), o.str("artist"), o.str("thumb"), o.optInt("duration", 0))
    }

    private fun parseQueue(a: JSONArray?): List<RoomTrack> =
        (0 until (a?.length() ?: 0)).mapNotNull { parseTrack(a!!.optJSONObject(it)) }

    private fun parseMembers(a: JSONArray?): List<RoomMember> =
        (0 until (a?.length() ?: 0)).mapNotNull {
            val o = a!!.optJSONObject(it) ?: return@mapNotNull null
            RoomMember(o.str("id"), o.str("name"), o.optBoolean("host"))
        }

    private fun RoomTrack.toJson() = JSONObject()
        .put("id", id).put("title", title).put("artist", artist).put("thumb", thumb).put("duration", durationSec)

    private fun handle(m: JSONObject) {
        when (m.str("t")) {
            "created", "joined" -> {
                val members = parseMembers(m.optJSONArray("members"))
                val myId = m.str("id")
                _state.value = RoomUiState(
                    phase = RoomPhase.InRoom,
                    code = m.str("code"),
                    myId = myId,
                    members = members,
                    isHost = m.optBoolean("host"),
                )
                val st = m.optJSONObject("state")
                if (m.str("t") == "joined" && st != null) {
                    _events.tryEmit(
                        RoomEvent.Snapshot(
                            parseTrack(st.optJSONObject("track")),
                            parseQueue(st.optJSONArray("queue")),
                            st.optBoolean("playing"),
                            st.optLong("pos"),
                        ),
                    )
                }
            }
            "joined_member", "left" -> {
                val members = parseMembers(m.optJSONArray("members"))
                _state.value = _state.value.let { it.copy(members = members, isHost = members.any { x -> x.id == it.myId && x.host }) }
            }
            "host" -> _state.value = _state.value.let { it.copy(isHost = it.myId == m.str("id")) }
            "play" -> _events.tryEmit(RoomEvent.Play(m.optLong("pos")))
            "pause" -> _events.tryEmit(RoomEvent.Pause(m.optLong("pos")))
            "seek" -> _events.tryEmit(RoomEvent.Seek(m.optLong("pos")))
            "track" -> parseTrack(m.optJSONObject("track"))?.let {
                _events.tryEmit(RoomEvent.Track(it, parseQueue(m.optJSONArray("queue")), m.optBoolean("playing", true), m.optLong("pos")))
            }
            "queue_add" -> parseTrack(m.optJSONObject("track"))?.let { _events.tryEmit(RoomEvent.QueueAdd(it)) }
            "chat" -> _state.value = _state.value.let {
                it.copy(chat = (it.chat + RoomChat(m.str("name"), m.str("text"))).takeLast(100))
            }
            "error" -> {
                val e = m.str("error")
                val words = when (e) {
                    "no_such_room" -> "No room with that code"
                    "room_full" -> "That room is full"
                    "slow_down" -> null
                    else -> "Room error: $e"
                }
                if (words != null) {
                    if (_state.value.phase == RoomPhase.InRoom) _state.value = _state.value.copy(error = words)
                    else {
                        val s = socket
                        socket = null
                        runCatching { s?.close(1000, null) }
                        _state.value = RoomUiState(error = words)
                    }
                }
            }
        }
    }
}
