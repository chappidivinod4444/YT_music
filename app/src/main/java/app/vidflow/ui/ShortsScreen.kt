package app.vidflow.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vidflow.core.Library
import app.vidflow.core.Paged
import app.vidflow.core.SeenShorts
import app.vidflow.core.Video
import app.vidflow.core.Yt
import app.vidflow.core.best
import app.vidflow.core.formatCount
import app.vidflow.core.friendlyError
import app.vidflow.player.PlayerHolder
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * Full-screen vertical videos: swipe up for the next one. Each plays on repeat.
 * The default feed is endless and skips shorts you've already seen.
 */
@Composable
fun ShortsScreen(
    nav: Nav,
    key: String = "shorts-home",
    startIndex: Int = 0,
    initial: List<Video>? = null,
    load: () -> Paged = { Yt.takeWarmShorts() ?: Yt.shortsFeed(Library.subscriptions.value.map { it.url }) },
) {
    var generation by remember(key) { mutableIntStateOf(0) }
    val list = remember(key, generation) { PagedList() }
    val scope = rememberCoroutineScope()
    var error by remember(key, generation) { mutableStateOf<String?>(null) }

    fun reload() {
        error = null
        list.loading = true
        scope.launch {
            try {
                list.set(withContext(Dispatchers.IO) { load() })
                if (list.items.isEmpty()) error = "No shorts found right now."
            } catch (t: Throwable) {
                error = friendlyError(t)
            } finally {
                list.loading = false
            }
        }
    }
    LaunchedEffect(key, generation) {
        if (list.items.isEmpty()) {
            if (initial != null && generation == 0) {
                list.set(Paged(initial, null))
                // Keep going with the endless feed after the ones you tapped.
                list.next = { withContext(Dispatchers.IO) { load() } }
            } else {
                reload()
            }
        }
    }

    // Leaving Shorts stops the looping short.
    DisposableEffect(Unit) {
        onDispose { if (PlayerHolder.current.value?.short == true) PlayerHolder.stop() }
    }

    var commentsFor by remember(key) { mutableStateOf<String?>(null) }
    var current by remember(key) { mutableStateOf<Video?>(null) }
    var menu by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val videos = list.items.filterIsInstance<Video>()
        val err = error
        when {
            videos.isEmpty() && err != null -> Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(err, color = Color.White, textAlign = TextAlign.Center)
                TextButton(onClick = { reload() }) { Text("Try again") }
            }
            videos.isEmpty() -> ShortsSkeleton()
            else -> {
                val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, videos.lastIndex)) { videos.size }
                LaunchedEffect(pager.settledPage, videos.size) { current = videos.getOrNull(pager.settledPage) }
                LaunchedEffect(pager.currentPage, videos.size) {
                    // Load more well before the end, and pre-load the start of the next three
                    // so they begin playing the moment you swipe.
                    if (pager.currentPage >= videos.size - 5 && list.next != null) list.loadMore()
                    for (k in 0..2) {
                        val v = videos.getOrNull(pager.currentPage + k) ?: break
                        launch(Dispatchers.IO) {
                            runCatching { PlayerHolder.prefetch(Yt.stream(v.url), SHORTS_HEIGHT) }
                        }
                    }
                }
                VerticalPager(
                    state = pager,
                    beyondViewportPageCount = 1,
                    // No swiping to the next short while comments are open.
                    userScrollEnabled = commentsFor == null,
                    key = { videos.getOrNull(it)?.url ?: it },
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val v = videos[page]
                    ShortPage(
                        v, active = page == pager.settledPage, nav = nav,
                        commentsOpen = commentsFor == v.url,
                        setComments = { open -> commentsFor = if (open) v.url else null },
                    )
                }
            }
        }
        if (commentsFor == null) Row(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent)))
                .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Shorts", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.weight(1f))
            var muted by remember { mutableStateOf(PlayerHolder.player.volume == 0f) }
            IconButton(onClick = { muted = !muted; PlayerHolder.player.volume = if (muted) 0f else 1f }) {
                Icon(
                    if (muted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp,
                    contentDescription = if (muted) "Unmute" else "Mute", tint = Color.White,
                )
            }
            IconButton(onClick = nav.search) {
                Icon(Icons.Outlined.Search, contentDescription = "Search", tint = Color.White)
            }
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = Color.White)
            }
        }
        current?.let { v -> if (menu) ShortsMenu(v, nav, onRefresh = { generation++ }) { menu = false } }
    }
}

@Composable
private fun ShortsSkeleton() {
    Box(Modifier.fillMaxSize().shimmer(dark = true)) {
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(180.dp, 14.dp).background(Color(0x33FFFFFF), RoundedCornerShape(6.dp)))
            Box(Modifier.size(260.dp, 14.dp).background(Color(0x33FFFFFF), RoundedCornerShape(6.dp)))
        }
    }
}

@Composable
private fun ShortPage(v: Video, active: Boolean, nav: Nav, commentsOpen: Boolean, setComments: (Boolean) -> Unit) {
    val context = LocalContext.current
    val current by PlayerHolder.current.collectAsState()
    val playError by PlayerHolder.error.collectAsState()
    val subs by Library.subscriptions.collectAsState()
    var info by remember(v.url) { mutableStateOf<StreamInfo?>(null) }
    var error by remember(v.url) { mutableStateOf<String?>(null) }
    var paused by remember(v.url) { mutableStateOf(false) }
    var progress by remember(v.url) { mutableFloatStateOf(0f) }

    LaunchedEffect(active, v.url) {
        if (!active) return@LaunchedEffect
        paused = false
        SeenShorts.mark(v.url)
        try {
            val i = info ?: withContext(Dispatchers.IO) { Yt.stream(v.url) }.also { info = it }
            val full = v.copy(
                title = i.name ?: v.title,
                channel = i.uploaderName ?: v.channel,
                channelUrl = i.uploaderUrl ?: v.channelUrl,
                channelAvatar = i.uploaderAvatars.best(200) ?: v.channelAvatar,
                short = true,
            )
            PlayerHolder.play(full, i, height = SHORTS_HEIGHT, resumeAt = 0, loop = true)
            launch(Dispatchers.Default) { Yt.addSuggestions(i) }
        } catch (t: Throwable) {
            error = friendlyError(t)
        }
    }

    val playingThis = active && current?.url == v.url
    LaunchedEffect(playingThis) {
        while (playingThis) {
            val p = PlayerHolder.player
            val d = p.duration
            progress = if (d > 0) (p.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
            delay(200)
        }
    }

    // With comments open the video shrinks to the top and the comments fill the rest (like YouTube).
    val videoWeight by androidx.compose.animation.core.animateFloatAsState(if (commentsOpen) 0.36f else 1f, label = "video")
    Column(Modifier.fillMaxSize()) {
    Box(
        Modifier
            .fillMaxWidth()
            .weight(videoWeight)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (commentsOpen) return@clickable setComments(false)
                if (!playingThis) return@clickable
                val p = PlayerHolder.player
                if (p.isPlaying) p.pause() else p.play()
                paused = !p.playWhenReady
            }
    ) {
        AsyncImage(
            model = v.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (playingThis) PlayerSurface(Modifier.fillMaxSize(), controls = false, fit = true)
        when {
            error != null || (playingThis && playError != null) -> Text(
                error ?: playError ?: "",
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
            active && !playingThis -> CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.align(Alignment.Center).size(40.dp))
        }
        if (paused) Box(Modifier.align(Alignment.Center)) {
            Box(
                Modifier.size(72.dp).background(Color(0x66000000), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp)) }
        }

        val channelUrl = info?.uploaderUrl ?: v.channelUrl
        val subscribed = remember(subs, channelUrl) { Library.isSubscribed(channelUrl) }
        var showSave by remember(v.url) { mutableStateOf(false) }
        var showDownload by remember(v.url) { mutableStateOf(false) }
        if (showSave) SaveToPlaylistDialog(v, shorts = true) { showSave = false }
        if (showDownload) DownloadDialog(v, listOf(1080, 720, 480)) { showDownload = false }

        val avatar = info?.uploaderAvatars?.best(200) ?: v.channelAvatar
        val channelName = info?.uploaderName ?: v.channel

        var comments by remember(v.url) { mutableStateOf(-1L) }
        LaunchedEffect(active, v.url) { if (active && comments < 0) comments = commentCount(v.url) }

        // Bottom: actions column on the right, channel and title on the left (like YouTube).
        if (!commentsOpen) Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x80000000))))
                .padding(start = 12.dp, end = 4.dp, top = 30.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f).padding(end = 6.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Channel pill: picture + subscribe (+) / subscribed (✓), like YouTube.
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0x40FFFFFF))
                            .padding(3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.clip(CircleShape).clickable(enabled = channelUrl != null) { channelUrl?.let(nav.channel) }) {
                            Avatar(avatar, 34)
                        }
                        if (channelUrl != null) {
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .clickable { Library.toggle(channelUrl, channelName, avatar) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (subscribed) Icons.Filled.Check else Icons.Filled.Add,
                                    contentDescription = if (subscribed) "Subscribed" else "Subscribe",
                                    tint = Color.White, modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        androidx.compose.ui.text.buildAnnotatedString {
                            pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold))
                            append(channelName)
                            pop()
                            append("  ")
                            append(info?.name ?: v.title)
                        },
                        color = Color.White, fontSize = 14.sp, lineHeight = 19.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, style = Shadowed,
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                val likes = info?.likeCount ?: -1
                val likedList by app.vidflow.core.Saved.liked.collectAsState()
                val liked = likedList.any { it.url == v.url }
                SideAction(
                    if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    if (likes >= 0) formatCount(likes + if (liked) 1 else 0) else "Like",
                    tint = if (liked) Color(0xFFFF3B5C) else Color.White,
                ) { app.vidflow.core.Saved.toggleLike(v) }
                SideAction(Icons.Outlined.Comment, if (comments >= 0) formatCount(comments) else "Comments") { setComments(true) }
                val lists by app.vidflow.core.Saved.playlists.collectAsState()
                val saved = lists.any { p -> p.videos.any { it.url == v.url } }
                SideAction(if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, "Save") { showSave = true }
                SideAction(Icons.Outlined.Reply, "Share", mirror = true) {
                    context.startActivity(
                        Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, v.url), "Share")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                SideAction(Icons.Outlined.FileDownload, "Download") { showDownload = true }
                Box(
                    Modifier
                        .padding(top = 2.dp)
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(2.dp, Color.White, RoundedCornerShape(10.dp))
                        .clickable(enabled = channelUrl != null) { channelUrl?.let(nav.channel) },
                ) {
                    AsyncImage(model = avatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
        }

        if (playingThis && !commentsOpen) {
            LinearProgressIndicator(
                progress = { progress },
                color = Color(0xFFFF0033),
                trackColor = Color(0x40FFFFFF),
                drawStopIndicator = {},
                gapSize = 0.dp,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp),
            )
        }
    }
    if (commentsOpen) {
        CommentsPanel(v.url, { setComments(false) }, Modifier.fillMaxWidth().weight(1f - 0.36f))
    }
    }
}

private const val SHORTS_HEIGHT = 720

private val Shadowed = androidx.compose.ui.text.TextStyle(
    shadow = androidx.compose.ui.graphics.Shadow(Color(0xCC000000), blurRadius = 8f),
)

/** One of the buttons on the right: a plain icon with a soft shadow and a label, like YouTube. */
@Composable
private fun SideAction(icon: ImageVector, label: String, tint: Color = Color.White, mirror: Boolean = false, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(58.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
    ) {
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            val flip = Modifier.graphicsLayer { if (mirror) scaleX = -1f }
            // Shadow: a blurred dark copy behind the icon keeps it readable on bright videos.
            Icon(icon, contentDescription = null, tint = Color(0x66000000), modifier = flip.size(28.dp).blur(2.dp))
            Icon(icon, contentDescription = label, tint = tint, modifier = flip.size(28.dp))
        }
        Text(
            label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Normal,
            maxLines = 1, style = Shadowed, modifier = Modifier.padding(top = 1.dp),
        )
    }
}

/** The ⋮ menu at the top of Shorts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShortsMenu(v: Video, nav: Nav, onRefresh: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var save by remember { mutableStateOf(false) }
    var download by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    if (save) SaveToPlaylistDialog(v, shorts = true) { save = false; onClose() }
    if (download) DownloadDialog(v, listOf(1080, 720, 480)) { download = false; onClose() }
    if (save || download) return
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color(0xFF212121), contentColor = Color.White) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
            if (about) {
                Text(v.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
                Text(
                    listOfNotNull(v.channel, v.views.takeIf { it >= 0 }?.let { formatCount(it) + " views" }, v.uploaded).joinToString(" · "),
                    color = Color(0xFFAAAAAA), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                return@Column
            }
            MenuRow(Icons.Outlined.Info, "Description") { about = true }
            MenuRow(Icons.Outlined.BookmarkBorder, "Save to playlist") { save = true }
            MenuRow(Icons.Outlined.FileDownload, "Download") { download = true }
            MenuRow(Icons.Outlined.Share, "Share") {
                context.startActivity(
                    Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, v.url), "Share")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                onClose()
            }
            MenuRow(Icons.Outlined.Link, "Copy link") {
                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("link", v.url))
                android.widget.Toast.makeText(context, "Link copied", android.widget.Toast.LENGTH_SHORT).show()
                onClose()
            }
            v.channelUrl?.let { url -> MenuRow(Icons.Outlined.AccountCircle, "Go to channel") { onClose(); nav.channel(url) } }
            MenuRow(Icons.Outlined.Refresh, "Show new shorts") { onClose(); onRefresh() }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Text(text, fontSize = 16.sp)
    }
}
