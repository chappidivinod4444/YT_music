package app.vidflow.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.composed
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vidflow.core.Channel
import app.vidflow.core.Item
import app.vidflow.core.Paged
import app.vidflow.core.Playlist
import app.vidflow.core.Video
import app.vidflow.core.formatCount
import app.vidflow.core.formatDuration
import app.vidflow.core.friendlyError
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ------------------------------------------------------------------- theme

/** YouTube-style red. */
val Accent = Color(0xFFFF0033)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFF0033),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A0A12),
    onPrimaryContainer = Color(0xFFFFDADC),
    secondary = Color(0xFF3EA6FF),
    background = Color(0xFF0F0F0F),
    onBackground = Color(0xFFF1F1F1),
    surface = Color(0xFF0F0F0F),
    onSurface = Color(0xFFF1F1F1),
    surfaceVariant = Color(0xFF272727),
    onSurfaceVariant = Color(0xFFAAAAAA),
    surfaceContainer = Color(0xFF212121),
    surfaceContainerHigh = Color(0xFF272727),
    surfaceContainerHighest = Color(0xFF3F3F3F),
    surfaceContainerLow = Color(0xFF181818),
    outlineVariant = Color(0xFF303030),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFFFF0033),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE5E8),
    onPrimaryContainer = Color(0xFF410008),
    secondary = Color(0xFF065FD4),
    background = Color.White,
    surface = Color.White,
    onSurface = Color(0xFF0F0F0F),
    surfaceVariant = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0xFF606060),
    surfaceContainer = Color(0xFFF2F2F2),
    surfaceContainerHigh = Color(0xFFE5E5E5),
    surfaceContainerHighest = Color(0xFFD9D9D9),
    surfaceContainerLow = Color(0xFFF9F9F9),
    outlineVariant = Color(0xFFE5E5E5),
)

@Composable
fun VidflowTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme, content = content)
}

// -------------------------------------------------------------- navigation

/** What the screens can ask the app to open. */
class Nav(
    val video: (Video) -> Unit,
    val channel: (String) -> Unit,
    val playlist: (String) -> Unit,
    val search: () -> Unit,
    val back: () -> Unit,
    /** Play only the sound and leave the screen. */
    val background: (Video) -> Unit = {},
    /** Open the Shorts player at one of these. */
    val shorts: (List<Video>, Int) -> Unit = { l, i -> video(l[i]) },
)

fun Nav.open(item: Item) = when (item) {
    is Video -> video(item)
    is Channel -> channel(item.url)
    is Playlist -> playlist(item.url)
}

// --------------------------------------------------------------- cards

fun videoSubtitle(v: Video, withChannel: Boolean = true): String = listOfNotNull(
    v.channel.takeIf { withChannel && it.isNotBlank() },
    if (v.views >= 0) formatCount(v.views) + if (v.live) " watching" else " views" else null,
    v.uploaded?.takeIf { it.isNotBlank() },
).joinToString(" · ")

@Composable
fun Thumb(url: String?, durationSec: Long, live: Boolean, modifier: Modifier = Modifier, corner: Int = 12) {
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(corner.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        val label = if (live) "LIVE" else formatDuration(durationSec)
        if (label.isNotEmpty()) {
            Text(
                label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(if (live) Color(0xFFE53935) else Color(0xCC000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
fun Avatar(url: String?, size: Int = 36) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

/** Big card: edge-to-edge thumbnail with title below. */
@Composable
fun VideoCard(v: Video, nav: Nav) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { nav.video(v) }
            .padding(bottom = 18.dp)
    ) {
        Thumb(v.thumbnail, v.durationSec, v.live, Modifier.fillMaxWidth(), corner = 0)
        Row(Modifier.padding(start = 12.dp, end = 0.dp, top = 12.dp)) {
            Box(Modifier.clickable(enabled = v.channelUrl != null) { v.channelUrl?.let(nav.channel) }) {
                Avatar(v.channelAvatar, 36)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    v.title, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    videoSubtitle(v), fontSize = 12.sp, lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            VideoMenu(v, nav)
        }
    }
}

/** The ⋮ menu on a video: a sheet from the bottom, like YouTube. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun VideoMenu(v: Video, nav: Nav) {
    var open by remember { mutableStateOf(false) }
    var save by remember { mutableStateOf(false) }
    var download by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    if (save) SaveToPlaylistDialog(v, shorts = v.short) { save = false }
    if (download) DownloadDialog(v, emptyList()) { download = false }
    androidx.compose.material3.IconButton(onClick = { open = true }, modifier = Modifier.size(40.dp)) {
        androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.Filled.MoreVert, contentDescription = "More",
            modifier = Modifier.size(20.dp),
        )
    }
    if (open) androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = { open = false },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            SheetItem(androidx.compose.material.icons.Icons.Outlined.Headphones, "Play in background") { open = false; nav.background(v) }
            SheetItem(androidx.compose.material.icons.Icons.Outlined.BookmarkBorder, "Save to playlist") { open = false; save = true }
            SheetItem(androidx.compose.material.icons.Icons.Outlined.FileDownload, "Download video") { open = false; download = true }
            SheetItem(androidx.compose.material.icons.Icons.Outlined.Share, "Share") {
                open = false
                context.startActivity(
                    android.content.Intent.createChooser(
                        android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, v.url),
                        "Share",
                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            if (v.channelUrl != null) {
                SheetItem(androidx.compose.material.icons.Icons.Outlined.AccountCircle, "Go to channel") { open = false; v.channelUrl.let(nav.channel) }
            }
        }
    }
}

/** One line in a bottom-sheet menu: icon and text. */
@Composable
fun SheetItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Text(text, fontSize = 15.sp)
    }
}

/** A row of vertical short cards. */
@Composable
fun ShortsShelf(shorts: List<Video>, nav: Nav) {
    if (shorts.isEmpty()) return
    Column(Modifier.padding(bottom = 18.dp)) {
        Text(
            "\u26A1 Shorts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 12.dp, bottom = 10.dp),
        )
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            items(shorts.size, key = { shorts[it].url }) { i ->
                val s = shorts[i]
                Box(
                    Modifier
                        .width(150.dp)
                        .aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable { nav.shorts(shorts, i) }
                ) {
                    AsyncImage(model = s.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000))))
                            .padding(8.dp)
                    ) {
                        Text(s.title, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (s.views >= 0) Text(formatCount(s.views) + " views", color = Color(0xCCFFFFFF), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

/** Gentle moving highlight used for loading placeholders. */
fun Modifier.shimmer(dark: Boolean = false): Modifier = composed {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(
        initialValue = -400f, targetValue = 1400f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1100)),
        label = "x",
    )
    val base = if (dark) Color(0xFF1A1A1F) else MaterialTheme.colorScheme.surfaceContainerHigh
    val hi = if (dark) Color(0xFF2A2A31) else MaterialTheme.colorScheme.surfaceContainerHighest
    background(
        androidx.compose.ui.graphics.Brush.linearGradient(
            listOf(base, hi, base),
            start = androidx.compose.ui.geometry.Offset(x, 0f),
            end = androidx.compose.ui.geometry.Offset(x + 400f, 400f),
        )
    )
}

@Composable
fun SkeletonCards(count: Int = 3) {
    Column {
        repeat(count) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).shimmer())
            Row(Modifier.padding(12.dp)) {
                Box(Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape).shimmer())
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth(0.9f).height(14.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(0.6f).height(12.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun SkeletonRows(count: Int = 6) {
    Column {
        repeat(count) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Box(Modifier.width(160.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).shimmer())
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth().height(13.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth(0.7f).height(13.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(0.5f).height(11.dp).clip(RoundedCornerShape(4.dp)).shimmer())
                }
            }
        }
    }
}

/** Compact row: small thumbnail on the left. */
@Composable
fun VideoRow(v: Video, nav: Nav, withChannel: Boolean = true) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { nav.video(v) }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Thumb(v.thumbnail, v.durationSec, v.live, Modifier.width(160.dp), corner = 10)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(v.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(videoSubtitle(v, withChannel), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        VideoMenu(v, nav)
    }
}

@Composable
fun ChannelRow(c: Channel, nav: Nav) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { nav.channel(c.url) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(c.thumbnail, 64)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(c.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    if (c.subscribers >= 0) formatCount(c.subscribers) + " subscribers" else null,
                    if (c.videos > 0) "${formatCount(c.videos)} videos" else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun PlaylistRow(p: Playlist, nav: Nav) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { nav.playlist(p.url) }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(Modifier.width(160.dp)) {
            Thumb(p.thumbnail, 0, false, Modifier.fillMaxWidth(), corner = 10)
            Text(
                "▶ ${if (p.count >= 0) p.count else ""}",
                color = Color.White, fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(p.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Playlist · ${p.owner}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ItemView(item: Item, nav: Nav, bigVideos: Boolean) {
    when (item) {
        is Video -> if (bigVideos) VideoCard(item, nav) else VideoRow(item, nav)
        is Channel -> ChannelRow(item, nav)
        is Playlist -> PlaylistRow(item, nav)
    }
}

// --------------------------------------------------------- loading states

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("⚠", fontSize = 32.sp)
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onRetry) { Text("Try again") }
    }
}

/** Holds a growing list that loads more when you reach the end. */
class PagedList {
    val items = mutableStateListOf<Item>()
    var next: (suspend () -> Paged)? by mutableStateOf(null)
    var loading by mutableStateOf(false)
    var refreshing by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    /** Loading more failed; the list shows a retry button. */
    var moreFailed by mutableStateOf(false)
    var loadedAt = 0L

    fun set(p: Paged) {
        items.clear()
        items.addAll(p.items)
        next = p.next
        moreFailed = false
        loadedAt = System.currentTimeMillis()
    }

    suspend fun loadMore() {
        val n = next ?: return
        if (loading) return
        loading = true
        moreFailed = false
        try {
            // Keep asking until a page brings something new (pages can repeat what's shown).
            var step: (suspend () -> Paged)? = n
            var tries = 0
            while (step != null && tries < 4) {
                val p = withContext(Dispatchers.IO) { step!!() }
                val seen = items.map { it.url }.toHashSet()
                val fresh = p.items.filter { it.url !in seen }
                items.addAll(fresh)
                step = p.next
                next = step
                tries++
                if (fresh.isNotEmpty()) break
            }
        } catch (t: Throwable) {
            // Keep [next] so it can be tried again.
            moreFailed = true
        } finally {
            loading = false
        }
    }
}

/**
 * Lists and scroll positions survive switching tabs and opening videos,
 * so going back is instant instead of loading again.
 */
object ListCache {
    private val lists = HashMap<String, PagedList>()
    private val states = HashMap<String, androidx.compose.foundation.lazy.LazyListState>()

    fun list(key: String): PagedList = lists.getOrPut(key) { PagedList() }
    fun state(key: String) = states.getOrPut(key) { androidx.compose.foundation.lazy.LazyListState() }
    fun drop(key: String) {
        lists.remove(key)
        states.remove(key)
    }
}

/** A scrolling list that loads the first page with [load] and more pages automatically. Pull down to refresh. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PagedColumn(
    key: Any,
    nav: Nav,
    load: () -> Paged,
    bigVideos: Boolean = true,
    /** Shown after the 3rd item (e.g. a Shorts shelf). */
    insert: (@Composable () -> Unit)? = null,
    /** Shown every [repeatEvery] items; gets the shelf number (0, 1, 2...). */
    repeatInsert: (@Composable (Int) -> Unit)? = null,
    repeatEvery: Int = 8,
    /** Shows saved items instantly while fresh ones load (e.g. Home from last time). */
    cached: (() -> List<Item>)? = null,
    onLoaded: ((List<Item>) -> Unit)? = null,
    /** Extra work when you pull down to refresh. */
    onRefresh: (suspend () -> Unit)? = null,
    /** Play the video in view silently when scrolling stops (Home). */
    previews: Boolean = false,
    header: LazyListScope.() -> Unit = {},
) {
    val k = key.toString()
    val list = remember(k) { ListCache.list(k) }
    val state = remember(k) { ListCache.state(k) }
    val scope = rememberCoroutineScope()

    fun reload(pull: Boolean = false) {
        list.error = null
        if (pull) list.refreshing = true else list.loading = true
        scope.launch {
            try {
                val p = withContext(Dispatchers.IO) { load() }
                list.set(p)
                onLoaded?.invoke(p.items)
                if (pull) state.scrollToItem(0)
            } catch (t: Throwable) {
                if (list.items.isEmpty()) list.error = friendlyError(t)
            } finally {
                list.loading = false
                list.refreshing = false
            }
        }
    }

    LaunchedEffect(k) {
        if (list.items.isEmpty()) {
            val saved = cached?.invoke().orEmpty()
            if (saved.isNotEmpty()) {
                list.items.addAll(saved)
                reload(pull = true)
            } else {
                reload()
            }
        } else if (System.currentTimeMillis() - list.loadedAt > 30 * 60 * 1000) {
            reload(pull = true)
        }
    }

    val nearEnd by remember(k) {
        derivedStateOf {
            val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.layoutInfo.totalItemsCount - 5
        }
    }
    // The video to preview: the first one fully in view once scrolling has stopped.
    val previewUrl by remember(k) {
        derivedStateOf {
            if (!previews || state.isScrollInProgress) null
            else {
                val info = state.layoutInfo
                val videos = list.items.filterIsInstance<Video>().filter { !it.live }.associateBy { it.url }
                info.visibleItemsInfo.firstOrNull { item ->
                    val key = item.key as? String
                    key != null && key in videos &&
                        item.offset >= info.viewportStartOffset - 4 &&
                        item.offset + item.size * 0.55f <= info.viewportEndOffset
                }?.key as? String
            }
        }
    }
    @Composable
    fun ListRow(item: Item) {
        if (previews && item is Video && item.url == previewUrl) {
            Box {
                ItemView(item, nav, bigVideos)
                PreviewOverlay(item, nav, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            }
        } else ItemView(item, nav, bigVideos)
    }

    LaunchedEffect(nearEnd, list.items.size, list.loading, list.moreFailed) {
        if (nearEnd && list.next != null && !list.loading && !list.refreshing && !list.moreFailed) list.loadMore()
    }

    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = list.refreshing,
        onRefresh = {
            reload(pull = true)
            onRefresh?.let { r -> scope.launch { r() } }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(state = state, contentPadding = PaddingValues(bottom = 90.dp), modifier = Modifier.fillMaxSize()) {
            header()
            val err = list.error
            when {
                err != null && list.items.isEmpty() -> item { ErrorBox(err) { reload() } }
                list.items.isEmpty() && list.loading -> item { if (bigVideos) SkeletonCards() else SkeletonRows() }
                list.items.isEmpty() && !list.refreshing -> item {
                    Text(
                        "Nothing here yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (repeatInsert != null) {
                // Videos in groups, with a shelf after the 3rd and then every [repeatEvery].
                val all = list.items.toList()
                var start = 0
                var shelf = 0
                while (start < all.size) {
                    val end = minOf(all.size, if (shelf == 0) 3 else start + repeatEvery)
                    items(all.subList(start, end), key = { it.url }, contentType = { it::class }) { ListRow(it) }
                    if (end - start == (if (shelf == 0) 3 else repeatEvery)) {
                        val n = shelf
                        item(key = "shelf-$n", contentType = "shelf") { repeatInsert(n) }
                    }
                    start = end
                    shelf++
                }
            } else {
                val split = if (insert != null) minOf(3, list.items.size) else list.items.size
                items(list.items.subList(0, split), key = { it.url }, contentType = { it::class }) { ListRow(it) }
                if (insert != null && list.items.isNotEmpty()) item(key = "insert", contentType = "insert") { insert() }
                if (split < list.items.size) {
                    items(list.items.subList(split, list.items.size), key = { it.url }, contentType = { it::class }) { ListRow(it) }
                }
            }
            if (list.loading && list.items.isNotEmpty()) item { Loading() }
            if (list.moreFailed && list.items.isNotEmpty()) item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.OutlinedButton(onClick = { scope.launch { list.loadMore() } }) {
                        Text("Couldn't load more · Retry")
                    }
                }
            }
        }
    }
}

/** The silent preview drawn over a video's thumbnail; taps still open the video. */
@Composable
private fun PreviewOverlay(v: Video, nav: Nav, modifier: Modifier) {
    val showing by app.vidflow.player.Preview.showing.collectAsState()
    val position by app.vidflow.player.Preview.position.collectAsState()
    LaunchedEffect(v.url) {
        kotlinx.coroutines.delay(700)
        app.vidflow.player.Preview.start(v)
    }
    DisposableEffect(v.url) {
        onDispose { if (app.vidflow.player.Preview.url.value == v.url) app.vidflow.player.Preview.stop() }
    }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) app.vidflow.player.Preview.stop()
        }
        lifecycle.lifecycle.addObserver(obs)
        onDispose { lifecycle.lifecycle.removeObserver(obs) }
    }
    Box(modifier) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx -> android.view.TextureView(ctx).also { app.vidflow.player.Preview.player().setVideoTextureView(it) } },
            onRelease = { app.vidflow.player.Preview.player().clearVideoTextureView(it) },
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (showing) 1f else 0f },
        )
        if (showing) {
            val left = ((v.durationSec * 1000L - position) / 1000L).coerceAtLeast(0)
            Row(
                Modifier.align(Alignment.BottomEnd).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Filled.VolumeOff, contentDescription = "Muted",
                        tint = Color.White, modifier = Modifier.size(16.dp),
                    )
                }
                if (v.durationSec > 0) Text(
                    formatDuration(left), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.background(Color(0x99000000), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
            // Thin red progress line along the bottom, like YouTube.
            if (v.durationSec > 0) Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth(((position / 1000f) / v.durationSec).coerceIn(0f, 1f))
                    .height(3.dp).background(Color(0xFFFF0033))
            )
        }
        Box(Modifier.matchParentSize().clickable { app.vidflow.player.Preview.stop(); nav.video(v) })
    }
}
