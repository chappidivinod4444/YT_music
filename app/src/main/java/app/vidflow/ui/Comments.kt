package app.vidflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vidflow.core.GoogleAccount
import app.vidflow.core.Yt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import app.vidflow.core.formatCount
import app.vidflow.core.friendlyError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** First page of comments, cached per video so the preview and the sheet share it. */
private object CommentCache {
    val pages = HashMap<String, Yt.CommentPage>()
}

private suspend fun firstPage(url: String): Yt.CommentPage =
    CommentCache.pages[url] ?: withContext(Dispatchers.IO) { Yt.comments(url) }.also { CommentCache.pages[url] = it }

/** The "Comments" box under the video, showing the top comment. */
@Composable
fun CommentsPreview(url: String, onOpen: () -> Unit) {
    var page by remember(url) { mutableStateOf<Yt.CommentPage?>(CommentCache.pages[url]) }
    var failed by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url) {
        if (page == null) page = runCatching { firstPage(url) }.getOrElse { failed = true; null }
    }
    val p = page
    if (failed || p?.disabled == true) return
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
            .clickable(enabled = p != null, onClick = onOpen)
            .padding(12.dp)
    ) {
        Text("Comments", fontWeight = FontWeight.Bold)
        val top = p?.comments?.firstOrNull()
        if (top == null) {
            Text(
                if (p == null) "Loading…" else "No comments yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        } else {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(top.avatar, 24)
                Spacer(Modifier.width(8.dp))
                Text(top.text, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Number of comments for the Shorts button, from the shared first page. */
suspend fun commentCount(url: String): Long = runCatching { firstPage(url).count }.getOrDefault(-1L)

/**
 * Comments as a panel that sits under the video (like YouTube), so the video stays visible.
 * Back closes it.
 */
@Composable
fun CommentsPanel(url: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    androidx.activity.compose.BackHandler(onBack = onClose)
    var sort by remember(url) { mutableStateOf("top") }
    val comments = remember(url, sort) { mutableStateListOf<Yt.Comment>() }
    var next by remember(url, sort) { mutableStateOf<(suspend () -> Yt.CommentPage)?>(null) }
    var loading by remember(url, sort) { mutableStateOf(true) }
    var error by remember(url, sort) { mutableStateOf<String?>(null) }
    var total by remember(url) { mutableStateOf(CommentCache.pages[url]?.count ?: -1L) }
    val replies = remember(url) { mutableStateMapOf<Yt.Comment, List<Yt.Comment>>() }
    val mine = remember(url) { mutableStateListOf<Yt.Comment>() }
    val scope = rememberCoroutineScope()
    val state = rememberLazyListState()
    val videoId = remember(url) { Yt.videoId(url) }

    // Writing: Google sign-in is only asked for when you send something.
    val signedIn by GoogleAccount.signedIn.collectAsState()
    val myAvatar by GoogleAccount.avatar.collectAsState()
    var draft by remember(url) { mutableStateOf("") }
    var replyTo by remember(url) { mutableStateOf<Yt.Comment?>(null) }
    var sending by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val google = rememberGoogleSignIn()
    var afterToken by remember { mutableStateOf<(() -> Unit)?>(null) }
    google.onToken = { t ->
        scope.launch {
            try {
                withContext(Dispatchers.IO) { GoogleAccount.onToken(t, google.pickedEmail) }
                afterToken?.invoke()
            } catch (e: Throwable) {
                notice = e.message ?: friendlyError(e)
                sending = false
            }
            afterToken = null
        }
    }
    google.onError = { notice = it; sending = false; afterToken = null }

    /** Runs [action] with a working token, asking Google first if needed. */
    fun withToken(action: () -> Unit) {
        if (GoogleAccount.token != null) return action()
        afterToken = action
        val mail = GoogleAccount.email.value
        if (mail != null && GoogleAccount.signedIn.value) google.request(mail) else google.pickAccount()
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || sending) return
        val vid = videoId ?: run { notice = "Can't comment on this video."; return }
        sending = true
        notice = null
        lateinit var attempt: (Boolean) -> Unit
        attempt = { retried ->
            withToken {
                scope.launch {
                    try {
                        val parent = replyTo
                        val c = withContext(Dispatchers.IO) {
                            if (parent?.id != null) GoogleAccount.reply(parent.id, text) else GoogleAccount.postComment(vid, text)
                        }
                        if (parent != null) replies[parent] = (replies[parent].orEmpty()) + c else mine.add(0, c)
                        draft = ""
                        replyTo = null
                        sending = false
                        if (parent == null) state.animateScrollToItem(0)
                    } catch (e: GoogleAccount.ApiException) {
                        if (e.needsSignIn && !retried) attempt(true) else { notice = e.message; sending = false }
                    } catch (t: Throwable) {
                        notice = friendlyError(t)
                        sending = false
                    }
                }
            }
        }
        attempt(false)
    }

    LaunchedEffect(url, sort) {
        try {
            if (sort == "top") {
                val p = firstPage(url)
                comments.addAll(p.comments)
                next = p.next
                if (p.count >= 0) total = p.count
            } else {
                val vid = videoId ?: throw IllegalStateException("No video ID")
                fun page(token: String?): suspend () -> Yt.CommentPage = {
                    val (list, nextToken) = withContext(Dispatchers.IO) { GoogleAccount.newestComments(vid, token) }
                    Yt.CommentPage(list, nextToken?.let { page(it) })
                }
                if (GoogleAccount.token == null) {
                    error = "Sign in with Google to see the newest comments."
                } else {
                    val p = page(null)()
                    comments.addAll(p.comments)
                    next = p.next
                }
            }
        } catch (t: Throwable) {
            error = (t as? GoogleAccount.ApiException)?.message ?: friendlyError(t)
        } finally {
            loading = false
        }
    }
    val nearEnd by remember(url, sort) { derivedStateOf { (state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= comments.size - 4 } }
    LaunchedEffect(nearEnd, comments.size, sort) {
        val n = next
        if (nearEnd && n != null && !loading) {
            loading = true
            try {
                val p = withContext(Dispatchers.IO) { n() }
                val seen = comments.mapNotNull { it.id }.toHashSet()
                comments.addAll(p.comments.filter { it.id == null || it.id !in seen })
                next = p.next
            } catch (_: Throwable) {
                next = null
            } finally {
                loading = false
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides Color.White) {
        Column(
            modifier
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(Color(0xFF0F0F0F))
                // Swallow taps so they don't reach the video underneath.
                .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
                .imePadding()
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x66FFFFFF)))
            }
            // Header: title, count, close.
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Comments", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (total > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(formatCount(total), fontSize = 15.sp, color = Color(0xFFAAAAAA))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SortChip("Top", sort == "top") { sort = "top" }
                SortChip("Newest", sort == "new") {
                    if (sort != "new") {
                        if (GoogleAccount.token == null && signedIn) withToken { sort = "new" } else sort = "new"
                    }
                }
            }
            HorizontalDivider(color = Color(0x26FFFFFF))

            LazyColumn(state = state, contentPadding = PaddingValues(bottom = 12.dp), modifier = Modifier.weight(1f)) {
                items(mine, key = { "mine-" + System.identityHashCode(it) }) { c -> CommentRow(c, onReply = null) }
                error?.let { e -> item { Text(e, modifier = Modifier.padding(16.dp), color = Color(0xFFAAAAAA)) } }
                if (!loading && error == null && comments.isEmpty() && mine.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (CommentCache.pages[url]?.disabled == true) "Comments are turned off" else "No comments yet", fontSize = 17.sp)
                        Text("Say something to start the conversation", color = Color(0xFFAAAAAA), modifier = Modifier.padding(top = 6.dp))
                    }
                }
                items(comments, key = { "c-" + System.identityHashCode(it) }) { c ->
                    CommentRow(c, onReply = if (c.id != null) ({ replyTo = c }) else null)
                    val shown = replies[c]
                    if (shown != null) {
                        shown.forEach { r -> Row { Spacer(Modifier.width(44.dp)); CommentRow(r, small = true, onReply = null) } }
                    } else if (c.replyCount > 0 && c.replies != null) {
                        Row(
                            Modifier
                                .padding(start = 60.dp, bottom = 10.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    scope.launch {
                                        replies[c] = runCatching {
                                            withContext(Dispatchers.IO) { Yt.replies(url, c.replies).comments }
                                        }.getOrDefault(emptyList())
                                    }
                                }
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${c.replyCount} ${if (c.replyCount == 1) "reply" else "replies"}",
                                color = Color(0xFF3EA6FF), fontWeight = FontWeight.SemiBold,
                            )
                            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFF3EA6FF))
                        }
                    }
                }
                if (loading) item { Loading() }
            }

            // Writing a comment.
            HorizontalDivider(color = Color(0x26FFFFFF))
            notice?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) }
            replyTo?.let { r ->
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Replying to ${r.author}", color = Color(0xFFAAAAAA), fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { replyTo = null }) { Icon(Icons.Filled.Close, contentDescription = "Cancel reply", modifier = Modifier.size(18.dp)) }
                }
            }
            var focused by remember { mutableStateOf(false) }
            if (focused) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("❤️", "😂", "🎉", "😢", "😮", "😅", "😊", "🔥").forEach { e ->
                        Text(e, fontSize = 24.sp, modifier = Modifier.clip(CircleShape).clickable { draft += e }.padding(4.dp))
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (signedIn) {
                    Avatar(myAvatar, 32)
                    Spacer(Modifier.width(10.dp))
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    enabled = !sending,
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                    maxLines = 5,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { focused = it.isFocused }
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF272727))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    decorationBox = { inner ->
                        Box {
                            if (draft.isEmpty()) Text(if (replyTo != null) "Add a reply…" else "Comment…", color = Color(0xFF909090), fontSize = 15.sp)
                            inner()
                        }
                    },
                )
                if (draft.isNotBlank() || sending) {
                    Spacer(Modifier.width(6.dp))
                    if (sending) {
                        CircularProgressIndicator(Modifier.size(24.dp).padding(2.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { send() }) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color(0xFF3EA6FF)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = if (selected) Color.Black else Color.White,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color.White else Color(0xFF272727))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun CommentRow(c: Yt.Comment, small: Boolean = false, onReply: (() -> Unit)?) {
    var expanded by remember { mutableStateOf(false) }
    var liked by remember { mutableStateOf(false) }
    var disliked by remember { mutableStateOf(false) }
    val grey = Color(0xFFAAAAAA)
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 4.dp)) {
        Avatar(c.avatar, if (small) 26 else 36)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                listOfNotNull(c.author.takeIf { it.isNotBlank() }, c.date).joinToString(" • ") + if (c.pinned) "  📌" else "",
                fontSize = 12.sp, color = grey, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                c.text,
                fontSize = 14.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp).clickable { expanded = !expanded },
            )
            Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { liked = !liked; if (liked) disliked = false }, modifier = Modifier.size(36.dp)) {
                    Icon(if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, contentDescription = "Like", modifier = Modifier.size(18.dp))
                }
                val likes = c.likes + if (liked) 1 else 0
                if (likes > 0) Text(formatCount(likes.toLong()), fontSize = 12.sp, color = grey)
                Spacer(Modifier.width(10.dp))
                IconButton(onClick = { disliked = !disliked; if (disliked) liked = false }, modifier = Modifier.size(36.dp)) {
                    Icon(if (disliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown, contentDescription = "Dislike", modifier = Modifier.size(18.dp))
                }
                if (c.hearted) Text("  ❤️", fontSize = 12.sp)
                if (onReply != null) {
                    Spacer(Modifier.width(10.dp))
                    IconButton(onClick = onReply, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.Comment, contentDescription = "Reply", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
