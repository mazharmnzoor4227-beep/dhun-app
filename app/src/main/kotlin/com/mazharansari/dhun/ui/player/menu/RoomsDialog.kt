package com.mazharansari.dhun.ui.player.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mazharansari.dhun.constants.RoomsNameKey
import com.mazharansari.dhun.constants.RoomsServerKeyKey
import com.mazharansari.dhun.constants.RoomsServerUrlKey
import com.mazharansari.dhun.rooms.RoomPhase
import com.mazharansari.dhun.rooms.RoomsManager
import com.mazharansari.dhun.utils.rememberPreference

/** One place that opens the rooms screen: the player menu and Settings both call [show]. */
object RoomsUi {
    var visible by mutableStateOf(false)
        private set

    fun show() { visible = true }
    fun hide() { visible = false }
}

@Composable
fun RoomsDialogHost() {
    if (RoomsUi.visible) RoomsDialog(onDismiss = RoomsUi::hide)
}

@Composable
private fun RoomsDialog(onDismiss: () -> Unit) {
    val state by RoomsManager.state.collectAsState()
    val (serverUrl, setServerUrl) = rememberPreference(RoomsServerUrlKey, defaultValue = "")
    val (serverKey, setServerKey) = rememberPreference(RoomsServerKeyKey, defaultValue = "")
    val (savedName, setSavedName) = rememberPreference(RoomsNameKey, defaultValue = "")
    var codeInput by remember { mutableStateOf("") }
    var chatInput by remember { mutableStateOf("") }
    val name = savedName.ifBlank { "Guest" }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Color(0xFF121212)),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Rooms", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                Text(
                    "Close", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onDismiss).padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                state.error?.let {
                    Text(it, color = Color(0xFFFF8A80), fontSize = 14.sp, modifier = Modifier.padding(bottom = 12.dp))
                }
                when (state.phase) {
                    RoomPhase.InRoom -> {
                        Text("Room code", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                        Text(state.code.orEmpty(), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 6.sp)
                        Text("Share this code. Everyone who joins hears the same song at the same time.", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
                        Spacer(Modifier.height(20.dp))
                        Text("In the room (${state.members.size})", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Spacer(Modifier.height(6.dp))
                        state.members.forEach { m ->
                            Text(
                                buildString {
                                    append(m.name)
                                    if (m.host) append("  (host)")
                                    if (m.id == state.myId) append("  (you)")
                                },
                                color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(vertical = 3.dp),
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                        Text("Chat", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Spacer(Modifier.height(6.dp))
                        state.chat.takeLast(8).forEach {
                            Text("${it.from}: ${it.text}", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                            TextField(value = chatInput, onValueChange = { chatInput = it }, singleLine = true, placeholder = { Text("Message") }, modifier = Modifier.weight(1f))
                            Spacer(Modifier.size(8.dp))
                            RoomButton("Send") {
                                if (chatInput.isNotBlank()) { RoomsManager.sendChat(chatInput.trim()); chatInput = "" }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        RoomButton("Leave room") { RoomsManager.leave() }
                    }
                    RoomPhase.Connecting -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                    }
                    RoomPhase.Idle -> {
                        Text("Listen together: one person creates a room, others join with its code, and playback stays in sync.", fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
                        Spacer(Modifier.height(16.dp))
                        Label("Your name")
                        TextField(value = savedName, onValueChange = setSavedName, singleLine = true, placeholder = { Text("Guest") }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        Label("Rooms server")
                        TextField(
                            value = serverUrl, onValueChange = setServerUrl, singleLine = true,
                            placeholder = { Text("ws://your-server:8787  or  wss://rooms.example.com") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "Rooms need a Dhun rooms server that you run yourself; none is built in.",
                            fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(top = 4.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Label("Server key (only if the server has one)")
                        TextField(value = serverKey, onValueChange = setServerKey, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(20.dp))
                        RoomButton("Create room") { RoomsManager.create(serverUrl, serverKey, name) }
                        Spacer(Modifier.height(20.dp))
                        Label("Join with a code")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextField(
                                value = codeInput, onValueChange = { codeInput = it.uppercase().take(8) }, singleLine = true,
                                placeholder = { Text("ABC123") },
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.size(8.dp))
                            RoomButton("Join") { if (codeInput.isNotBlank()) RoomsManager.join(serverUrl, serverKey, name, codeInput) }
                        }
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun RoomButton(text: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFFB547))
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
    ) {
        Text(text, color = Color(0xFF0A3638), fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}
