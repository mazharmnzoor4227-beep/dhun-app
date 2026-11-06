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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.music.innertube.YouTube
import com.music.innertube.models.comment.CommentThreadRenderer
import kotlinx.coroutines.launch

/** one comment, flattened for display */
private data class CommentRow(
    val id: String,
    val author: String,
    val avatar: String?,
    val text: String,
    val likes: String,
    val published: String,
    val replyCount: Int,
)

private fun CommentThreadRenderer.toRow(): CommentRow? {
    val r = comment?.commentRenderer ?: return null
    val text = r.contentText?.runs?.joinToString("") { it.text }?.takeIf { it.isNotBlank() } ?: return null
    return CommentRow(
        id = r.commentId ?: text.hashCode().toString(),
        author = r.authorText?.runs?.joinToString("") { it.text }.orEmpty().trim(),
        avatar = r.authorThumbnail?.thumbnails?.lastOrNull()?.url,
        text = text,
        likes = r.voteCount?.runs?.joinToString("") { it.text }.orEmpty(),
        published = r.publishedTimeText?.runs?.joinToString("") { it.text }.orEmpty(),
        replyCount = r.replyCount ?: 0,
    )
}

/**
 * Read-only YouTube comments for the playing video. Loads the first page when it opens and more as the
 * list is scrolled to its end. Signing in to like or post is not part of this screen.
 */
@Composable
fun CommentsDialog(videoId: String, title: String, onDismiss: () -> Unit) {
    val rows = remember(videoId) { mutableStateListOf<CommentRow>() }
    var token by remember(videoId) { mutableStateOf<String?>(null) }
    var loading by remember(videoId) { mutableStateOf(true) }
    var failed by remember(videoId) { mutableStateOf(false) }
    var firstDone by remember(videoId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun loadMore() {
        if (!firstDone) return
        val t = token ?: return
        if (loading) return
        loading = true
        scope.launch {
            YouTube.commentContinuation(t)
                .onSuccess { (threads, next) ->
                    val seen = rows.map { it.id }.toHashSet()
                    rows += threads.mapNotNull { it.toRow() }.filter { seen.add(it.id) }
                    token = next
                }
                .onFailure { token = null }
            loading = false
        }
    }

    LaunchedEffect(videoId) {
        loading = true
        YouTube.comments(videoId)
            .onSuccess { (threads, next) ->
                rows.clear()
                rows += threads.mapNotNull { it.toRow() }.distinctBy { it.id }
                token = next
            }
            .onFailure { failed = true }
        firstDone = true
        loading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(Color(0xFF121212)),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Comments", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        title,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "Close",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onDismiss).padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            Box(Modifier.fillMaxSize()) {
                when {
                    firstDone && failed && rows.isEmpty() -> Text(
                        "Comments could not be loaded for this video.",
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )
                    firstDone && rows.isEmpty() -> Text(
                        "No comments.",
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.Center),
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(rows, key = { it.id }) { c ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                                AsyncImage(
                                    model = c.avatar,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF2A2A2A)),
                                )
                                Spacer(Modifier.size(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        listOf(c.author, c.published).filter { it.isNotBlank() }.joinToString(" \u00b7 "),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White.copy(alpha = 0.65f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(c.text, fontSize = 14.sp, lineHeight = 19.sp, color = Color.White)
                                    val meta = listOfNotNull(
                                        c.likes.takeIf { it.isNotBlank() }?.let { "$it likes" },
                                        c.replyCount.takeIf { it > 0 }?.let { "$it replies" },
                                    ).joinToString(" \u00b7 ")
                                    if (meta.isNotEmpty()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(meta, fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        }
                        item(key = "end") {
                            if (loading) {
                                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                                }
                            } else if (token != null) {
                                LaunchedEffect(token) { loadMore() }
                            }
                        }
                    }
                }
                if (!firstDone) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), color = Color.White, strokeWidth = 2.dp)
                }
            }
        }
    }
}
