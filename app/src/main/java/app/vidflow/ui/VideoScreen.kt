@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.vidflow.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import app.vidflow.core.plainText
import app.vidflow.core.uploadedAgo
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import app.vidflow.core.Item
import app.vidflow.core.Library
import app.vidflow.core.Video
import app.vidflow.core.Yt
import app.vidflow.core.best
import app.vidflow.core.formatCount
import app.vidflow.core.friendlyError
import app.vidflow.player.PlayerHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.StreamInfo

/** The video surface. Shared by the watch page, full screen and picture-in-picture. */
@Composable
fun PlayerSurface(modifier: Modifier = Modifier, onFullscreen: (() -> Unit)? = null, controls: Boolean = true, fit: Boolean = true) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(android.graphics.Color.BLACK)
                player = PlayerHolder.player
                useController = controls
                setShowNextButton(false)
                setShowPreviousButton(false)
                setShowSubtitleButton(true)
                keepScreenOn = true
                resizeMode = if (fit) androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                else androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                if (onFullscreen != null) setFullscreenButtonClickListener { onFullscreen() }
            }
        },
        update = { view ->
            if (view.player !== PlayerHolder.player) view.player = PlayerHolder.player
            view.useController = controls
        },
        onRelease = { it.player = null },
        modifier = modifier.background(Color.Black),
    )
}

@Composable
fun VideoScreen(video: Video, nav: Nav, onFullscreen: () -> Unit) {
    val scope = rememberCoroutineScope()
    val info by PlayerHolder.info.collectAsState()
    val current by PlayerHolder.current.collectAsState()
    val playError by PlayerHolder.error.collectAsState()
    var loadError by remember(video.url) { mutableStateOf<String?>(null) }
    var loading by remember(video.url) { mutableStateOf(false) }

    fun load(fresh: Boolean = false) {
        loadError = null
        loading = true
        scope.launch {
            try {
                val i = withContext(Dispatchers.IO) { Yt.stream(video.url, fresh) }
                val full = video.copy(
                    title = i.name ?: video.title,
                    channel = i.uploaderName ?: video.channel,
                    channelUrl = i.uploaderUrl ?: video.channelUrl,
                    channelAvatar = i.uploaderAvatars.best(200) ?: video.channelAvatar,
                    thumbnail = i.thumbnails.best() ?: video.thumbnail,
                    durationSec = if (i.duration > 0) i.duration else video.durationSec,
                    views = if (i.viewCount >= 0) i.viewCount else video.views,
                )
                Library.watched(full, Library.position(video.url))
                PlayerHolder.play(full, i)
            } catch (t: Throwable) {
                loadError = friendlyError(t)
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(video.url) {
        if (current?.url != video.url || info == null) load()
    }

    val i = info?.takeIf { current?.url == video.url }

    val related: List<Item> = remember(i) { i?.let { Yt.related(it) }.orEmpty() }
    val nextVideo = related.filterIsInstance<Video>().firstOrNull()
    val ended by PlayerHolder.endedCount.collectAsState()
    val autoplay by PlayerHolder.autoplay.collectAsState()
    var lastEnded by remember(video.url) { mutableStateOf(ended) }
    LaunchedEffect(ended) {
        if (ended != lastEnded) {
            lastEnded = ended
            if (autoplay && nextVideo != null && current?.url == video.url) nav.video(nextVideo)
        }
    }
    val previous = PlayerHolder.session.dropLast(1).lastOrNull()?.takeIf { it.url != video.url }

    val ambient by PlayerHolder.ambient.collectAsState()
    Box(Modifier.fillMaxSize()) {
    if (ambient) {
        // Ambient mode: a soft glow of the video's colours under the player.
        coil3.compose.AsyncImage(
            model = i?.thumbnails?.best() ?: video.thumbnail,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .blur(70.dp)
                .graphicsLayer { alpha = 0.45f }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0.35f to Color.Transparent, 1f to Color.Black,
                        ),
                        blendMode = androidx.compose.ui.graphics.BlendMode.DstOut,
                    )
                },
        )
    }
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (i != null) {
                VideoPlayer(
                    modifier = Modifier.fillMaxSize(),
                    fullscreen = false,
                    onFullscreen = onFullscreen,
                    onMinimize = nav.back,
                    onNext = nextVideo?.let { n -> { nav.video(n) } },
                    onPrevious = previous?.let { p -> { PlayerHolder.session.removeAt(PlayerHolder.session.lastIndex); nav.video(p) } },
                    onBackground = { nav.background(video) },
                )
            } else if (video.thumbnail != null) {
                coil3.compose.AsyncImage(
                    model = video.thumbnail, contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
            }
            when {
                loading -> CircularProgressIndicator(color = Color.White)
                loadError != null || playError != null -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.background(Color(0xAA000000)).padding(16.dp),
                ) {
                    Text(
                        loadError ?: playError ?: "",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    Button(
                        onClick = { load(fresh = true) },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text("Retry", color = Color.Black, fontWeight = FontWeight.SemiBold) }
                }
            }
        }

        // Chips under the video: All / From <channel> / Related.
        var filter by remember(video.url) { mutableStateOf("all") }
        var fromChannel by remember(video.url) { mutableStateOf<List<Item>?>(null) }
        val channelUrl = i?.uploaderUrl ?: video.channelUrl
        LaunchedEffect(filter, channelUrl) {
            if (filter == "channel" && fromChannel == null && channelUrl != null) {
                fromChannel = withContext(Dispatchers.IO) {
                    runCatching { Yt.channel(channelUrl).videos.items }.getOrDefault(emptyList())
                }.filter { it.url != video.url }
            }
        }
        val shown = when (filter) {
            "channel" -> fromChannel
            "related" -> related.filter { it is Video && it.channelUrl != channelUrl }
            else -> related
        }
        var showComments by remember(video.url) { mutableStateOf(false) }

        // Comments open under the video, in place of the details, so the video stays in view.
        if (showComments) CommentsPanel(video.url, { showComments = false }, Modifier.fillMaxSize())
        else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
            item { Details(video, i, nav) }
            item { CommentsPreview(video.url) { showComments = true } }
            item {
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { TopicChip("All", filter == "all") { filter = "all" } }
                    if (channelUrl != null) item {
                        TopicChip("From ${i?.uploaderName ?: video.channel}", filter == "channel") { filter = "channel" }
                    }
                    item { TopicChip("Related", filter == "related") { filter = "related" } }
                }
            }
            if ((i == null && loading) || (filter == "channel" && fromChannel == null)) item { SkeletonCards(2) }
            items(shown.orEmpty(), key = { "r-$filter-" + it.url }, contentType = { it::class }) { ItemView(it, nav, bigVideos = true) }
        }
    }
    }
}

@Composable
private fun Pill(icon: ImageVector?, text: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = text, modifier = Modifier.size(20.dp))
        if (icon != null && text != null) Spacer(Modifier.width(6.dp))
        if (text != null) Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Details(video: Video, i: StreamInfo?, nav: Nav) {
    var expanded by remember(video.url) { mutableStateOf(false) }
    val subs by Library.subscriptions.collectAsState()
    val channelUrl = i?.uploaderUrl ?: video.channelUrl
    val subscribed = remember(subs, channelUrl) { Library.isSubscribed(channelUrl) }
    val qualities by PlayerHolder.qualities.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val title = i?.name ?: video.title
    val views = (i?.viewCount ?: video.views).takeIf { it >= 0 }
    val ago = uploadedAgo(i, video.uploaded)
    val description = plainText(i?.description?.content.orEmpty())

    Column(Modifier.padding(top = 12.dp).animateContentSize()) {
        // Title and one meta line; tap either for the full description.
        Column(Modifier.clickable { expanded = !expanded }.padding(horizontal = 16.dp)) {
            Text(
                title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(views?.let { formatCount(it) + " views" }, ago).joinToString("  ") + "  " + if (expanded) "less" else "...more",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // Channel: avatar, name and subscriber count, Subscribe.
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .clickable(enabled = channelUrl != null) { channelUrl?.let(nav.channel) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(i?.uploaderAvatars?.best(200) ?: video.channelAvatar, 38)
                Spacer(Modifier.width(10.dp))
                Text(i?.uploaderName ?: video.channel, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                val subsCount = i?.uploaderSubscriberCount ?: -1
                if (subsCount >= 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(formatCount(subsCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            if (channelUrl != null) {
                Spacer(Modifier.width(10.dp))
                Text(
                    if (subscribed) "Subscribed" else "Subscribe",
                    color = if (subscribed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface,
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (subscribed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f) else MaterialTheme.colorScheme.onSurface)
                        .clickable { Library.toggle(channelUrl, i?.uploaderName ?: video.channel, i?.uploaderAvatars?.best(200) ?: video.channelAvatar) }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }

        // Actions: like | dislike, share, save, download, background.
        Row(
            Modifier
                .padding(top = 12.dp)
                .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val likes = i?.likeCount ?: -1
            val likedList by app.vidflow.core.Saved.liked.collectAsState()
            val liked = likedList.any { it.url == video.url }
            var disliked by remember(video.url) { mutableStateOf(false) }
            Row(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .clickable {
                            disliked = false
                            app.vidflow.core.Saved.toggleLike(PlayerHolder.current.value?.takeIf { it.url == video.url } ?: video)
                        }
                        .padding(start = 14.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, contentDescription = "Like", modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (likes >= 0) formatCount(likes + if (liked) 1 else 0) else "Like",
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                    )
                }
                Box(Modifier.width(1.dp).height(22.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)))
                Box(
                    Modifier
                        .clickable {
                            disliked = !disliked
                            if (disliked && liked) app.vidflow.core.Saved.toggleLike(video)
                        }
                        .padding(start = 12.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    Icon(if (disliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown, contentDescription = "Dislike", modifier = Modifier.size(20.dp))
                }
            }
            var saveOpen by remember { mutableStateOf(false) }
            var downloadOpen by remember { mutableStateOf(false) }
            Pill(Icons.Outlined.Share, "Share") {
                context.startActivity(
                    android.content.Intent.createChooser(
                        android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, video.url),
                        "Share",
                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            Pill(Icons.Outlined.FileDownload, "Download") { downloadOpen = true }
            Pill(Icons.Outlined.BookmarkBorder, "Save") { saveOpen = true }
            Pill(Icons.Outlined.Headphones, "Background") { nav.background(video) }
            if (saveOpen) SaveToPlaylistDialog(video) { saveOpen = false }
            if (downloadOpen) DownloadDialog(video, qualities) { downloadOpen = false }
        }

        // Full description when expanded.
        if (expanded) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    .clickable { expanded = false }
                    .padding(12.dp)
            ) {
                Text(
                    listOfNotNull(
                        views?.let { java.text.NumberFormat.getIntegerInstance().format(it) + " views" },
                        i?.textualUploadDate?.takeIf { !it.contains("T") } ?: ago,
                    ).joinToString("  "),
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                )
                if (description.isNotBlank()) {
                    Text(description, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
                Text("Show less", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
