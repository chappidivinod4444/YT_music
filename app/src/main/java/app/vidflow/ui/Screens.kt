@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.vidflow.ui

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vidflow.core.Feed
import app.vidflow.core.Library
import app.vidflow.core.Paged
import app.vidflow.core.Video
import app.vidflow.core.Yt
import app.vidflow.core.best
import app.vidflow.core.formatCount
import app.vidflow.core.friendlyError
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock

// -------------------------------------------------------------------- top

@Composable
fun AppBar(title: String, nav: Nav, showBack: Boolean = false, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            IconButton(onClick = nav.back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        } else {
            Spacer(Modifier.width(12.dp))
            // Vidflow's own mark: a red circle with a play arrow.
            Box(
                Modifier
                    .size(26.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Accent),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(6.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = if (showBack) FontWeight.SemiBold else FontWeight.Bold,
            letterSpacing = if (showBack) androidx.compose.ui.unit.TextUnit.Unspecified else (-0.8).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
        IconButton(onClick = nav.search) { Icon(Icons.Outlined.Search, contentDescription = "Search", modifier = Modifier.size(26.dp)) }
    }
}

// ------------------------------------------------------------------- home

@Composable
fun BellButton(onClick: () -> Unit) {
    val feed by Feed.videos.collectAsState()
    val seen by app.vidflow.core.Notifications.lastSeen.collectAsState()
    val hasNew = feed.orEmpty().any { (it.uploadedAtMillis ?: 0) > seen }
    Box {
        IconButton(onClick = onClick) { Icon(Icons.Outlined.Notifications, contentDescription = "New uploads", modifier = Modifier.size(26.dp)) }
        if (hasNew) Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 12.dp)
                .size(8.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color(0xFFFF4D4D))
        )
    }
}

@Composable
fun HomeScreen(nav: Nav, openExplore: () -> Unit, openNotifications: () -> Unit) {
    val topics by app.vidflow.core.Topics.list.collectAsState()
    var tab by rememberSaveable { mutableStateOf("All") }
    var editTopics by remember { mutableStateOf(false) }

    // Shorts for the grids on Home: a fresh set of 4 for each grid, kept between visits.
    LaunchedEffect(Unit) {
        HomeShorts.ensure(1)
        // Keep the subscriptions feed fresh in the background for the bell and Home.
        if (Feed.videos.value == null || Feed.stale()) withContext(Dispatchers.IO) { runCatching { Feed.refresh() } }
    }

    val header: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
        item(key = "appbar", contentType = "appbar") {
            AppBar("Vidflow", nav) { BellButton(openNotifications) }
        }
        stickyHeader(key = "chips", contentType = "chips") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(bottom = 8.dp),
            ) {
                item {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable(onClick = openExplore)
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) { Text("\uD83E\uDDED", fontSize = 16.sp) }
                }
                items(listOf("All") + topics) { t ->
                    TopicChip(t, selected = tab == t) { tab = t }
                }
                item {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { editTopics = true }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) { Text("+ Edit", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }

    if (tab == "All") {
        PagedColumn(
            key = "home-all", nav = nav, load = { Yt.recommended() }, header = header,
            previews = true,
            repeatEvery = 5,
            repeatInsert = { n ->
                LaunchedEffect(n) { HomeShorts.ensure(n + 2) }
                val pool by HomeShorts.pool.collectAsState()
                ShortsGrid(HomeShorts.grid(pool, n), nav)
            },
            cached = { app.vidflow.core.HomeCache.load() },
            onLoaded = { app.vidflow.core.HomeCache.save(it) },
            onRefresh = { HomeShorts.reset() },
        )
    } else {
        val t = tab
        PagedColumn(key = "topic-$t", nav = nav, load = { Yt.topic(t) }, header = header)
    }

    if (editTopics) TopicEditor(topics) { editTopics = false }
}

/** A growing pool of shorts for the grids on Home, remembered between visits. */
object HomeShorts {
    val pool = kotlinx.coroutines.flow.MutableStateFlow<List<Video>>(emptyList())
    private var next: (suspend () -> Paged)? = null
    private val lock = kotlinx.coroutines.sync.Mutex()
    const val PER_GRID = 6

    /** The 6 shorts for grid [n]; reuses older ones only if YouTube ran out of new ones. */
    fun grid(pool: List<Video>, n: Int): List<Video> = when {
        pool.size >= (n + 1) * PER_GRID -> pool.subList(n * PER_GRID, (n + 1) * PER_GRID)
        pool.size >= PER_GRID * 2 -> List(PER_GRID) { pool[(n * PER_GRID + it) % pool.size] }
        else -> emptyList()
    }

    /** New shorts for every grid (pull to refresh on Home). */
    suspend fun reset() {
        lock.withLock {
            pool.value = emptyList()
            next = null
        }
        ensure(3)
    }

    /** Makes sure there are enough shorts for [grids] grids. Waits its turn instead of skipping. */
    suspend fun ensure(grids: Int) {
        if (pool.value.size >= grids * PER_GRID) return
        lock.withLock {
            withContext(Dispatchers.IO) {
                var tries = 0
                while (pool.value.size < grids * PER_GRID && tries++ < 5) {
                    val page = runCatching { next?.invoke() ?: Yt.shortsFeed(Library.subscriptions.value.map { it.url }) }.getOrNull() ?: continue
                    next = page.next
                    val seen = pool.value.map { it.url }.toHashSet()
                    pool.value = pool.value + page.items.filterIsInstance<Video>().filter { it.url !in seen }
                }
            }
        }
    }
}

@Composable
fun TopicChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        fontWeight = FontWeight.SemiBold,
        color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun TopicEditor(current: List<String>, onClose: () -> Unit) {
    var text by remember { mutableStateOf(current.joinToString("\n")) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Your topics") },
        text = {
            Column {
                Text(
                    "One per line. Each becomes a chip on Home, for example: Telugu cinema, Cricket, Tech reviews.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    minLines = 5, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { app.vidflow.core.Topics.set(text.split('\n')); onClose() }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

/** Shorts shelf in the feed: 2 across, 3 down, tall cards like YouTube's. */
@Composable
fun ShortsGrid(shorts: List<Video>, nav: Nav) {
    if (shorts.isEmpty()) return
    Column(
        Modifier
            .padding(bottom = 16.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(top = 12.dp, bottom = 4.dp)
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            ShortsMark(24)
            Spacer(Modifier.width(8.dp))
            Text("Shorts", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        shorts.take(6).chunked(2).forEachIndexed { row, pair ->
            Row(
                Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                pair.forEachIndexed { col, s ->
                    val index = row * 2 + col
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(9f / 15f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable { nav.shorts(shorts, index) }
                    ) {
                        AsyncImage(model = s.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        Box(Modifier.align(Alignment.TopEnd)) {
                            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides Color.White) {
                                VideoMenu(s.copy(short = true), nav)
                            }
                        }
                        Column(
                            Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000))))
                                .padding(start = 10.dp, end = 10.dp, top = 24.dp, bottom = 10.dp)
                        ) {
                            Text(
                                s.title, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 19.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                            if (s.views >= 0) Text(formatCount(s.views) + " views", color = Color(0xE6FFFFFF), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** Vidflow's Shorts mark: a red rounded shape with a white play arrow. */
@Composable
fun ShortsMark(size: Int) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 3).dp))
            .background(Accent),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size((size * 0.7).dp)) }
}

/** Popular lists (the old categories), reached from the compass chip. */
@Composable
fun ExploreScreen(nav: Nav) {
    val kiosks = remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) { kiosks.value = withContext(Dispatchers.IO) { Yt.kiosks() } }
    var tab by rememberSaveable { mutableStateOf("") }
    if (tab.isEmpty() && kiosks.value.isNotEmpty()) tab = kiosks.value.first()
    val header: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
        item { AppBar("Explore", nav, showBack = true) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                items(kiosks.value) { k -> TopicChip(Yt.kioskName(k), tab == k) { tab = k } }
            }
        }
    }
    if (tab.isEmpty()) Column { AppBar("Explore", nav, showBack = true); SkeletonCards() }
    else PagedColumn(key = "kiosk-$tab", nav = nav, load = { Yt.kiosk(tab) }, header = header)
}

/** New uploads from your channels since you last looked. */
@Composable
fun NotificationsScreen(nav: Nav) {
    val feed by Feed.videos.collectAsState()
    val seenAt = remember { app.vidflow.core.Notifications.lastSeen.value }
    DisposableEffect(Unit) { onDispose { app.vidflow.core.Notifications.markSeen() } }
    LazyColumn(contentPadding = PaddingValues(bottom = 90.dp), modifier = Modifier.fillMaxSize()) {
        item { AppBar("New uploads", nav, showBack = true) }
        val list = feed
        when {
            list == null -> item { SkeletonRows() }
            list.isEmpty() -> item {
                Text(
                    "Subscribe to channels to see their new videos here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            }
            else -> items(list.take(60), key = { "n-" + it.url }) { v ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .padding(start = 8.dp)
                            .size(6.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(if ((v.uploadedAtMillis ?: 0) > seenAt) MaterialTheme.colorScheme.primary else Color.Transparent)
                    )
                    Box(Modifier.weight(1f)) { VideoRow(v, nav) }
                }
            }
        }
    }
}

// ---------------------------------------------------------- subscriptions

@Composable
fun SubscriptionsScreen(nav: Nav) {
    val subs by Library.subscriptions.collectAsState()
    val videos by Feed.videos.collectAsState()
    val loading by Feed.loading.collectAsState()
    val scope = rememberCoroutineScope()
    var channel by rememberSaveable { mutableStateOf<String?>(null) }
    val state = remember { ListCache.state("subs-tab") }
    LaunchedEffect(Unit) {
        if (videos == null || Feed.stale()) withContext(Dispatchers.IO) { runCatching { Feed.refresh() } }
    }
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = loading,
        onRefresh = { scope.launch(Dispatchers.IO) { runCatching { Feed.refresh() } } },
    ) {
        LazyColumn(state = state, contentPadding = PaddingValues(bottom = 90.dp), modifier = Modifier.fillMaxSize()) {
            item { AppBar("Subscriptions", nav) }
            if (subs.isNotEmpty()) {
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(subs, key = { it.url }) { s ->
                            Column(
                                Modifier
                                    .width(64.dp)
                                    .clickable { nav.channel(s.url) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Avatar(s.avatar, 56)
                                Text(s.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
                item {
                    LazyRow(contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { TopicChip("All", channel == null) { channel = null } }
                        item { TopicChip("Today", channel == "today") { channel = "today" } }
                        item { TopicChip("Shorts", channel == "shorts") { channel = "shorts" } }
                    }
                }
            }
            val all = videos
            val dayAgo = System.currentTimeMillis() - 24 * 3600 * 1000
            val list = when (channel) {
                "today" -> all?.filter { (it.uploadedAtMillis ?: 0) > dayAgo }
                "shorts" -> all?.filter { it.short || it.durationSec in 1..60 }
                else -> all?.filter { !it.short }
            }
            when {
                subs.isEmpty() -> item {
                    Text(
                        "You haven't subscribed to any channels yet. Open a channel and tap Subscribe, or import your subscriptions in the You tab.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
                list == null -> item { SkeletonCards() }
                list.isEmpty() -> item {
                    Text("Nothing new here.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp))
                }
                else -> items(list, key = { "s-" + it.url }, contentType = { "video" }) { VideoCard(it, nav) }
            }
        }
    }
}

// ----------------------------------------------------------------- search

@Composable
fun SearchScreen(nav: Nav) {
    var text by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { if (query.isEmpty()) runCatching { focus.requestFocus() } }
    LaunchedEffect(text) {
        if (text.isBlank() || text == query) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(250)
        suggestions = withContext(Dispatchers.IO) { Yt.suggestions(text) }
    }

    fun go(q: String) {
        if (q.isBlank()) return
        text = q
        query = q.trim()
        suggestions = emptyList()
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = nav.back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Search videos and channels") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { go(text) }),
                trailingIcon = {
                    if (text.isNotEmpty()) IconButton(onClick = { text = ""; runCatching { focus.requestFocus() } }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear")
                    }
                },
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus),
            )
            Spacer(Modifier.width(8.dp))
        }
        if (suggestions.isNotEmpty()) {
            LazyColumn {
                items(suggestions) { s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { go(s) }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(16.dp))
                        Text(s)
                    }
                }
            }
        } else if (query.isNotEmpty()) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(null to "All", "videos" to "Videos", "channels" to "Channels", "playlists" to "Playlists").forEach { (f, label) ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(label) })
                }
            }
            val q = query
            val f = filter
            PagedColumn(key = "search-$q-$f", nav = nav, load = { Yt.search(q, f) }, bigVideos = false)
        }
    }
}

// ---------------------------------------------------------------- channel

@Composable
fun ChannelScreen(url: String, nav: Nav) {
    var page by remember(url) { mutableStateOf<Yt.ChannelPage?>(null) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        error = null
        scope.launch {
            try {
                page = withContext(Dispatchers.IO) { Yt.channel(url) }
            } catch (t: Throwable) {
                error = friendlyError(t)
            }
        }
    }
    LaunchedEffect(url) { if (page == null) load() }

    val p = page
    val err = error
    when {
        err != null -> Column { AppBar("", nav, showBack = true); ErrorBox(err) { load() } }
        p == null -> Column { AppBar("", nav, showBack = true); ChannelSkeleton() }
        else -> ChannelContent(url, p, nav)
    }
}

@Composable
private fun ChannelSkeleton() {
    Column(Modifier.padding(12.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(4f).clip(RoundedCornerShape(12.dp)).shimmer())
        Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(72.dp).clip(androidx.compose.foundation.shape.CircleShape).shimmer())
            Spacer(Modifier.width(16.dp))
            Column {
                Box(Modifier.width(180.dp).height(20.dp).clip(RoundedCornerShape(6.dp)).shimmer())
                Spacer(Modifier.height(8.dp))
                Box(Modifier.width(120.dp).height(14.dp).clip(RoundedCornerShape(6.dp)).shimmer())
            }
        }
        SkeletonCards(2)
    }
}

@Composable
private fun ChannelContent(url: String, p: Yt.ChannelPage, nav: Nav) {
    val info = p.info
    val subs by Library.subscriptions.collectAsState()
    val subscribed = remember(subs) { Library.isSubscribed(url) }
    val tabs = listOf("Home") + p.tabs.map { it.first }
    var tab by rememberSaveable(url) { mutableStateOf("Home") }
    var aboutOpen by remember { mutableStateOf(false) }
    val handle = Regex("""/(@[^/?]+)""").find(url)?.groupValues?.get(1)
        ?: Regex("""/(@[^/?]+)""").find(info.url ?: "")?.groupValues?.get(1)

    val header: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
        item(key = "ch-top") {
            Column {
                AppBar("", nav, showBack = true)
                info.banners.best(400)?.let {
                    AsyncImage(
                        model = it, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth().aspectRatio(3.7f).clip(RoundedCornerShape(12.dp)),
                    )
                }
                Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(info.avatars.best(300), 80)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(info.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (handle != null) Text(handle, style = MaterialTheme.typography.bodyMedium)
                        if (info.subscriberCount >= 0) {
                            Text(
                                "${formatCount(info.subscriberCount)} subscribers",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                val about = info.description.orEmpty()
                if (about.isNotBlank()) {
                    Text(
                        about,
                        maxLines = if (aboutOpen) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                            .clickable { aboutOpen = !aboutOpen },
                    )
                }
                val subscribeColor = if (subscribed) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.onSurface
                Text(
                    if (subscribed) "Subscribed \u2713" else "Subscribe",
                    color = if (subscribed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.background,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(subscribeColor)
                        .clickable { Library.toggle(url, info.name, info.avatars.best(200)) }
                        .padding(vertical = 11.dp),
                )
            }
        }
        stickyHeader(key = "ch-tabs") {
            Column(Modifier.background(MaterialTheme.colorScheme.background)) {
                LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                    items(tabs) { t ->
                        Column(
                            Modifier.clickable { tab = t }.padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                t,
                                fontWeight = FontWeight.SemiBold,
                                color = if (tab == t) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Box(
                                Modifier.width(36.dp).height(2.dp)
                                    .background(if (tab == t) MaterialTheme.colorScheme.onSurface else Color.Transparent)
                            )
                        }
                    }
                }
                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }

    when (tab) {
        "Home" -> {
            // Latest videos, with a Shorts row from this channel after the first few.
            var shorts by remember(url) { mutableStateOf<List<Video>>(emptyList()) }
            LaunchedEffect(url) {
                val t = p.shortsTab ?: return@LaunchedEffect
                shorts = withContext(Dispatchers.IO) {
                    runCatching { Yt.channelTab(t, shorts = true).items.filterIsInstance<Video>().take(4) }.getOrDefault(emptyList())
                }
            }
            PagedColumn(
                key = "ch-home-$url", nav = nav, load = { p.videos }, bigVideos = true,
                insert = { ShortsGrid(shorts, nav) }, header = header,
            )
        }
        "Shorts" -> {
            val handler = p.tabs.first { it.first == "Shorts" }.second
            ShortsGridTab("ch-shorts-$url", nav, header) { Yt.channelTab(handler, shorts = true) }
        }
        else -> {
            val handler = p.tabs.first { it.first == tab }.second
            PagedColumn(
                key = "ch-$tab-$url", nav = nav, load = { Yt.channelTab(handler) },
                bigVideos = tab != "Playlists", header = header,
            )
        }
    }
}

/** Three-column grid of a channel's shorts. */
@Composable
private fun ShortsGridTab(
    key: String,
    nav: Nav,
    header: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
    load: () -> Paged,
) {
    val list = remember(key) { ListCache.list(key) }
    val state = remember(key) { ListCache.state(key) }
    LaunchedEffect(key) {
        if (list.items.isEmpty()) {
            list.loading = true
            try {
                list.set(withContext(Dispatchers.IO) { load() })
            } catch (t: Throwable) {
                list.error = friendlyError(t)
            } finally {
                list.loading = false
            }
        }
    }
    val videos = list.items.filterIsInstance<Video>()
    LazyColumn(state = state, contentPadding = PaddingValues(bottom = 90.dp), modifier = Modifier.fillMaxSize()) {
        header()
        if (list.loading && videos.isEmpty()) item { SkeletonRows(3) }
        items(videos.chunked(3).size) { row ->
            val trio = videos.chunked(3)[row]
            Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                trio.forEachIndexed { col, v ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(9f / 16f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable { nav.shorts(videos, row * 3 + col) }
                    ) {
                        AsyncImage(model = v.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        if (v.views >= 0) Text(
                            "\u25B7 " + formatCount(v.views),
                            color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                        )
                    }
                }
                repeat(3 - trio.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (videos.isNotEmpty() && list.next != null) item {
            LaunchedEffect(videos.size) { list.loadMore() }
            Loading()
        }
    }
}

@Composable
fun PlaybackSettings() {
    val mode by app.vidflow.core.Prefs.leaveMode.collectAsState()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("When you leave the app during a video", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        app.vidflow.core.LeaveMode.entries.forEach { m ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { app.vidflow.core.Prefs.setLeaveMode(m) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.RadioButton(selected = mode == m, onClick = { app.vidflow.core.Prefs.setLeaveMode(m) })
                Text(m.label)
            }
        }
    }
}

@Composable
fun BackHandlerCompat(onBack: () -> Unit) = androidx.activity.compose.BackHandler(onBack = onBack)

@Composable
fun SubscribeButton(subscribed: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (subscribed) {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text("Subscribed ✓") }
    } else {
        Button(onClick = onClick, modifier = modifier) { Text("Subscribe") }
    }
}

// --------------------------------------------------------------- playlist

@Composable
fun PlaylistScreen(url: String, nav: Nav) {
    var page by remember(url) { mutableStateOf<Yt.PlaylistPage?>(null) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun load() {
        error = null
        scope.launch {
            try {
                page = withContext(Dispatchers.IO) { Yt.playlist(url) }
            } catch (t: Throwable) {
                error = friendlyError(t)
            }
        }
    }
    LaunchedEffect(url) { load() }
    Column(Modifier.fillMaxSize()) {
        AppBar(page?.info?.name ?: "Playlist", nav, showBack = true)
        val p = page
        val err = error
        when {
            err != null -> ErrorBox(err) { load() }
            p == null -> Loading()
            else -> PagedColumn(key = "playlist-$url", nav = nav, load = { p.videos }, bigVideos = false) {
                item {
                    Text(
                        "${p.info.uploaderName ?: ""} · ${if (p.info.streamCount >= 0) "${p.info.streamCount} videos" else ""}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- library

@Composable
fun LibraryScreen(
    nav: Nav,
    openLiked: () -> Unit = {},
    openPlaylists: () -> Unit = {},
    say: (String) -> Unit = {},
    openLocalLiked: () -> Unit = {},
    openLocalPlaylist: (String) -> Unit = {},
    openSetup: () -> Unit = {},
) {
    val subs by Library.subscriptions.collectAsState()
    val history by Library.history.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(bottom = 90.dp), modifier = Modifier.fillMaxSize()) {
        item { AppBar("You", nav) }
        item { AccountSection(nav, openLiked, openPlaylists, say) }
        item { YouSections(nav, openLocalPlaylist, openLocalLiked, openSetup) }
        item {
            Text("Subscriptions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 8.dp))
        }
        if (subs.isEmpty()) {
            item {
                Text(
                    "Open a channel and tap Subscribe. Subscriptions are saved on this phone only, no account needed.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        } else {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(subs, key = { it.url }) { s ->
                        Column(
                            Modifier
                                .width(72.dp)
                                .clickable { nav.channel(s.url) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Avatar(s.avatar, 60)
                            Text(s.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (history.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Clear") }
            }
        }
        if (history.isEmpty()) {
            item {
                Text("Videos you watch show up here.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        items(history, key = { "h-" + it.video.url }) { w -> VideoRow(w.video, nav) }
        item { PlaybackSettings() }
        item {
            Text(
                "Vidflow has no account and no ads. Your subscriptions and history never leave this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(24.dp),
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear watch history?") },
            confirmButton = { TextButton(onClick = { Library.clearHistory(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

// Placeholder used while unavailable data loads.
@Suppress("unused")
private fun emptyPaged() = Paged(emptyList<Video>(), null)
