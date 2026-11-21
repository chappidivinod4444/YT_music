package app.vidflow.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vidflow.core.Channel
import app.vidflow.core.GoogleAccount
import app.vidflow.core.Item
import app.vidflow.core.Music
import app.vidflow.core.MusicHistory
import app.vidflow.core.MusicQueue
import app.vidflow.core.Playlist
import app.vidflow.core.Saved
import app.vidflow.core.Shelf
import app.vidflow.core.Video
import app.vidflow.core.Yt
import app.vidflow.core.artistName
import app.vidflow.core.formatCount
import app.vidflow.core.friendlyError
import app.vidflow.player.PlayerHolder
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------- theme

private val Bg = Color(0xFF030303)
private val Sub = Color(0xFFAAAAAA)
private val Chip = Color(0xFF212121)
private val Red = Color(0xFFFF0033)

@Composable
fun MusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color.White, onPrimary = Color.Black,
            background = Bg, onBackground = Color.White,
            surface = Bg, onSurface = Color.White,
            surfaceVariant = Chip, onSurfaceVariant = Sub,
            surfaceContainer = Color(0xFF212121), surfaceContainerHigh = Color(0xFF282828),
            surfaceContainerHighest = Color(0xFF3A3A3A), surfaceContainerLow = Color(0xFF121212),
            secondary = Color(0xFF3EA6FF), outlineVariant = Color(0xFF2A2A2A),
        ),
        content = content,
    )
}

// ---------------------------------------------------------------- navigation

private sealed interface Page {
    data object Home : Page
    data object Explore : Page
    data object Library : Page
    data object Search : Page
    data class Album(val url: String, val title: String? = null) : Page
    data class AccountList(val id: String, val title: String) : Page
    data class Artist(val url: String) : Page
    data class Local(val id: String) : Page
    data object Liked : Page
    data object Recent : Page
    data object Settings : Page
    data object Setup : Page
}

private class MNav(
    val open: (Page) -> Unit,
    val back: () -> Unit,
    val item: (Item, List<Item>) -> Unit,
)

@Composable
fun MusicApp() {
    val stack = remember { mutableStateListOf<Page>(Page.Home) }
    var nowPlaying by remember { mutableStateOf(false) }
    val current by PlayerHolder.current.collectAsState()
    val top = stack.last()
    val nav = remember {
        MNav(
            open = { stack.add(it) },
            back = { if (stack.size > 1) stack.removeAt(stack.lastIndex) },
            item = { item, all ->
                when (item) {
                    is Video -> {
                        val songs = all.filterIsInstance<Video>()
                        MusicQueue.play(songs.ifEmpty { listOf(item) }, songs.indexOf(item).coerceAtLeast(0))
                        if (songs.size <= 1) MusicQueue.startRadio(item)
                    }
                    is Channel -> stack.add(Page.Artist(item.url))
                    is Playlist -> stack.add(Page.Album(item.url, item.title))
                }
            },
        )
    }
    BackHandler(enabled = stack.size > 1 && !nowPlaying) { stack.removeAt(stack.lastIndex) }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Box(Modifier.weight(1f)) {
                when (val p = top) {
                    Page.Home -> HomePage(nav, explore = false)
                    Page.Explore -> HomePage(nav, explore = true)
                    Page.Library -> LibraryPage(nav)
                    Page.Search -> SearchPage(nav)
                    is Page.Album -> AlbumPage(p.url, p.title, nav)
                    is Page.AccountList -> AccountListPage(p.id, p.title, nav)
                    is Page.Artist -> ArtistPage(p.url, nav)
                    is Page.Local -> LocalPage(p.id, nav)
                    Page.Liked -> SongListPage("Liked songs", Saved.liked.collectAsState().value, nav)
                    Page.Recent -> SongListPage("Recently played", MusicHistory.list.collectAsState().value, nav)
                    Page.Settings -> MusicSettings(nav)
                    Page.Setup -> GoogleSetupScreen(Nav(video = {}, channel = {}, playlist = {}, search = {}, back = nav.back))
                }
            }
            if (current != null) MiniPlayer { nowPlaying = true }
            BottomBar(top) { page -> stack.clear(); stack.add(page) }
        }
        AnimatedVisibility(
            nowPlaying && current != null,
            enter = slideInVertically(tween(280)) { it } + fadeIn(),
            exit = slideOutVertically(tween(240)) { it } + fadeOut(),
        ) {
            NowPlaying(nav = nav, onClose = { nowPlaying = false })
        }
    }
}

@Composable
private fun BottomBar(top: Page, go: (Page) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF0B0B0B)).height(58.dp), verticalAlignment = Alignment.CenterVertically) {
        @Composable
        fun Tab(page: Page, label: String, on: ImageVector, off: ImageVector) {
            val selected = top == page
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(remember { MutableInteractionSource() }, null) { go(page) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(if (selected) on else off, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                Text(label, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
            }
        }
        Tab(Page.Home, "Home", Icons.Filled.Home, Icons.Outlined.Home)
        Tab(Page.Explore, "Explore", Icons.Filled.Explore, Icons.Outlined.Explore)
        Tab(Page.Library, "Library", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic)
    }
}

// ---------------------------------------------------------------- shared pieces

@Composable
private fun TopBar(nav: MNav, title: String? = null, back: Boolean = false) {
    val avatar by GoogleAccount.avatar.collectAsState()
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (back) {
            IconButton(onClick = nav.back) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            Text(title.orEmpty(), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(28.dp).clip(CircleShape).background(Red), contentAlignment = Alignment.Center) {
                Box(Modifier.size(18.dp).clip(CircleShape).background(Color.Transparent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(6.dp))
            Text(title ?: "Music", fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp, modifier = Modifier.weight(1f))
        }
        IconButton(onClick = { nav.open(Page.Search) }) { Icon(Icons.Outlined.Search, contentDescription = "Search", modifier = Modifier.size(26.dp)) }
        IconButton(onClick = { nav.open(Page.Settings) }) {
            if (avatar != null) Avatar(avatar, 28) else Icon(Icons.Outlined.AccountCircle, contentDescription = "Account", modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun MoodChips(selected: String?, onSelect: (String?) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        items(Music.moods) { m ->
            val on = m == selected
            Text(
                m, color = if (on) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) Color.White else Color(0x1AFFFFFF))
                    .clickable { onSelect(if (on) null else m) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

private fun songSubtitle(v: Video): String = listOfNotNull(
    artistName(v.channel).takeIf { it.isNotBlank() },
    v.views.takeIf { it > 0 }?.let { formatCount(it) + " plays" },
).joinToString(" • ")

@Composable
private fun SongRow(v: Video, list: List<Item>, nav: MNav, index: Int? = null, modifier: Modifier = Modifier) {
    val now by PlayerHolder.current.collectAsState()
    val playing = now?.url == v.url
    Row(
        modifier.fillMaxWidth().clickable { nav.item(v, list) }.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index != null) {
            Text("$index", color = Sub, fontSize = 15.sp, modifier = Modifier.width(28.dp))
        }
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(4.dp)).background(Chip)) {
            AsyncImage(v.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (playing) Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(v.title, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (playing) Red else Color.White)
            Text(songSubtitle(v), fontSize = 13.sp, color = Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        SongMenu(v, nav)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongMenu(v: Video, nav: MNav) {
    var open by remember { mutableStateOf(false) }
    var save by remember { mutableStateOf(false) }
    var download by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val like = rememberLiker()
    IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More", modifier = Modifier.size(20.dp)) }
    if (save) SaveToPlaylistDialog(v) { save = false }
    if (download) DownloadDialog(v, emptyList()) { download = false }
    if (open) ModalBottomSheet(onDismissRequest = { open = false }, containerColor = Color(0xFF212121)) {
        Row(Modifier.padding(horizontal = 20.dp).padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(v.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(v.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(artistName(v.channel), color = Sub, fontSize = 13.sp, maxLines = 1)
            }
        }
        Column(Modifier.padding(bottom = 16.dp)) {
            SheetItem(Icons.Outlined.Radio, "Start radio") { open = false; MusicQueue.startRadio(v) }
            SheetItem(Icons.Outlined.PlaylistPlay, "Play next") { open = false; MusicQueue.playNext(v) }
            SheetItem(Icons.Outlined.QueueMusic, "Add to queue") { open = false; MusicQueue.addToQueue(v) }
            SheetItem(Icons.Outlined.ThumbUp, if (Saved.liked.value.any { it.url == v.url }) "Remove from liked songs" else "Add to liked songs") {
                open = false; like(v)
            }
            SheetItem(Icons.Outlined.PlaylistAdd, "Save to playlist") { open = false; save = true }
            SheetItem(Icons.Outlined.FileDownload, "Download") { open = false; download = true }
            v.channelUrl?.let { url -> SheetItem(Icons.Outlined.AccountCircle, "Go to artist") { open = false; nav.open(Page.Artist(url)) } }
            SheetItem(Icons.Outlined.Share, "Share") {
                open = false
                context.startActivity(
                    Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, v.url), "Share")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}

/** Square card for albums, playlists, artists and songs in carousels. */
@Composable
private fun SquareCard(item: Item, all: List<Item>, nav: MNav, size: Int = 160) {
    val round = item is Channel
    Column(Modifier.width(size.dp).clickable { nav.item(item, all) }) {
        Box(
            Modifier.size(size.dp).clip(if (round) CircleShape else RoundedCornerShape(6.dp)).background(Chip)
        ) {
            AsyncImage(item.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (item is Video) Box(
                Modifier.align(Alignment.BottomEnd).padding(8.dp).size(32.dp).clip(CircleShape).background(Color(0x99000000)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
        }
        Text(
            item.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        val sub = when (item) {
            is Video -> artistName(item.channel)
            is Playlist -> listOfNotNull(artistName(item.owner).takeIf { it.isNotBlank() }, item.count.takeIf { it > 0 }?.let { "$it songs" }).joinToString(" • ")
            is Channel -> item.subscribers.takeIf { it > 0 }?.let { formatCount(it) + " subscribers" } ?: "Artist"
        }
        if (sub.isNotBlank()) Text(sub, fontSize = 13.sp, color = Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ShelfTitle(title: String, subtitle: String? = null, action: (() -> Unit)? = null, actionText: String = "Play all") {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 22.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            if (subtitle != null) Text(subtitle.uppercase(), fontSize = 12.sp, color = Sub, letterSpacing = 0.5.sp)
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        if (action != null) Text(
            actionText, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0x1AFFFFFF)).clickable(onClick = action)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun ShelfView(shelf: Shelf, nav: MNav) {
    when (shelf.style) {
        Shelf.Style.QuickPicks -> {
            val songs = shelf.items.filterIsInstance<Video>()
            ShelfTitle(shelf.title, shelf.subtitle, action = { MusicQueue.play(songs, 0) })
            BoxWithConstraints {
                val w = maxWidth * 0.88f
                LazyHorizontalGrid(
                    rows = GridCells.Fixed(4),
                    modifier = Modifier.height(272.dp),
                ) {
                    itemsIndexed(songs, key = { _, v -> v.url }) { _, v ->
                        SongRow(v, songs, nav, modifier = Modifier.width(w).height(68.dp))
                    }
                }
            }
        }
        Shelf.Style.Squares, Shelf.Style.Wide -> {
            ShelfTitle(shelf.title, shelf.subtitle)
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(shelf.items, key = { it.url }) { SquareCard(it, shelf.items, nav, if (shelf.style == Shelf.Style.Wide) 140 else 160) }
            }
        }
    }
}

@Composable
private fun LoadingBox() {
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun MErrorBox(text: String, retry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = Sub)
        Text(
            "Retry", color = Color.Black, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp).clip(RoundedCornerShape(18.dp)).background(Color.White).clickable(onClick = retry)
                .padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}

// ---------------------------------------------------------------- home / explore

private object HomeMemory {
    val shelves = HashMap<String, List<Shelf>>()
}

@Composable
private fun HomePage(nav: MNav, explore: Boolean) {
    var mood by remember(explore) { mutableStateOf(if (explore) Music.moods.first() else null) }
    val key = (if (explore) "x:" else "h:") + mood
    var shelves by remember(key) { mutableStateOf(HomeMemory.shelves[key]) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    // Signed in: use your liked music to shape Home (fetched quietly once per app start).
    val signedIn by GoogleAccount.signedIn.collectAsState()
    val tokens = rememberTokenRunner { }
    val scope = rememberCoroutineScope()
    LaunchedEffect(signedIn) {
        if (!signedIn || Music.accountLiked.isNotEmpty() || explore) return@LaunchedEffect
        tokens.run {
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { GoogleAccount.liked() } }.onSuccess {
                    if (it.isNotEmpty()) {
                        Music.accountLiked = it
                        HomeMemory.shelves.clear()
                        reload++
                    }
                }
            }
        }
    }
    LaunchedEffect(key, reload) {
        if (shelves != null && reload == 0) return@LaunchedEffect
        error = null
        try {
            val s = withContext(Dispatchers.IO) { Music.home(mood) }
            HomeMemory.shelves[key] = s
            shelves = s
        } catch (t: Throwable) {
            error = friendlyError(t)
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { TopBar(nav, if (explore) "Explore" else null) }
        item { MoodChips(mood) { mood = if (explore) (it ?: Music.moods.first()) else it } }
        val s = shelves
        when {
            s != null -> s.forEach { shelf -> item(key = shelf.title) { ShelfView(shelf, nav) } }
            error != null -> item { MErrorBox(error!!) { reload++ } }
            else -> item { LoadingBox() }
        }
    }
}

// ---------------------------------------------------------------- search

@Composable
private fun SearchPage(nav: MNav) {
    var text by remember { mutableStateOf("") }
    var query by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf(Music.SONGS) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    val results = remember { mutableStateListOf<Item>() }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(text) {
        if (query == text) return@LaunchedEffect
        delay(200)
        suggestions = if (text.isBlank()) emptyList() else withContext(Dispatchers.IO) { Yt.suggestions(text) }
    }
    LaunchedEffect(query, filter, retry) {
        val q = query ?: return@LaunchedEffect
        loading = true; error = null; results.clear()
        try {
            results.addAll(withContext(Dispatchers.IO) { Music.search(q, filter).items })
        } catch (t: Throwable) {
            error = friendlyError(t)
        } finally {
            loading = false
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = nav.back) { Icon(Icons.Filled.ArrowBack, "Back") }
            BasicTextField(
                value = text, onValueChange = { text = it; if (it.isEmpty()) query = null },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 17.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (text.isNotBlank()) query = text }),
                modifier = Modifier.weight(1f).focusRequester(focus),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Chip).padding(horizontal = 18.dp, vertical = 11.dp)) {
                        if (text.isEmpty()) Text("Search songs, albums, artists", color = Sub, fontSize = 17.sp)
                        inner()
                    }
                },
            )
            if (text.isNotEmpty()) IconButton(onClick = { text = ""; query = null }) { Icon(Icons.Filled.Close, "Clear") }
        }
        if (query == null) {
            LazyColumn {
                items(suggestions) { s ->
                    Row(Modifier.fillMaxWidth().clickable { text = s; query = s }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Search, null, tint = Sub)
                        Spacer(Modifier.width(18.dp))
                        Text(s, fontSize = 16.sp)
                    }
                }
                if (text.isBlank()) items(MusicHistory.list.value.take(6)) { v ->
                    Row(Modifier.fillMaxWidth().clickable { text = v.title; query = v.title }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.History, null, tint = Sub)
                        Spacer(Modifier.width(18.dp))
                        Text(v.title, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        } else {
            val filters = listOf(Music.SONGS to "Songs", Music.VIDEOS to "Videos", Music.ALBUMS to "Albums", Music.PLAYLISTS to "Playlists", Music.ARTISTS to "Artists")
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                items(filters) { (k, label) ->
                    val on = k == filter
                    Text(
                        label, color = if (on) Color.Black else Color.White, fontSize = 14.sp,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) Color.White else Color(0x1AFFFFFF))
                            .clickable { filter = k }.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                if (loading) item { LoadingBox() }
                error?.let { e -> item { MErrorBox(e) { retry++ } } }
                items(results, key = { it.url }) { item ->
                    when (item) {
                        is Video -> SongRow(item, results, nav)
                        else -> ListRow(item, nav)
                    }
                }
            }
        }
    }
}

/** Album / playlist / artist as one line in a list. */
@Composable
private fun ListRow(item: Item, nav: MNav) {
    Row(Modifier.fillMaxWidth().clickable { nav.item(item, emptyList()) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            item.thumbnail, null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(if (item is Channel) CircleShape else RoundedCornerShape(4.dp)).background(Chip),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when (item) {
                    is Channel -> "Artist" + (item.subscribers.takeIf { it > 0 }?.let { " • " + formatCount(it) + " subscribers" } ?: "")
                    is Playlist -> listOf(if (item.url.contains("OLAK")) "Album" else "Playlist", artistName(item.owner)).filter { it.isNotBlank() }.joinToString(" • ")
                    is Video -> artistName(item.channel)
                },
                fontSize = 13.sp, color = Sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------- album / playlist / artist

@Composable
private fun CollectionHeader(art: String?, title: String, subtitle: String, songs: List<Video>, round: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AsyncImage(
            art, null, contentScale = ContentScale.Crop,
            modifier = Modifier.padding(top = 8.dp).size(230.dp).clip(if (round) CircleShape else RoundedCornerShape(8.dp)).background(Chip),
        )
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 18.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (subtitle.isNotBlank()) Text(subtitle, color = Sub, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Row(Modifier.padding(top = 18.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundAction(Icons.Filled.Shuffle, "Shuffle", dark = true) {
                if (songs.isNotEmpty()) { MusicQueue.shuffle.value = true; MusicQueue.play(songs.shuffled(), 0) }
            }
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(Color.White).clickable { if (songs.isNotEmpty()) { MusicQueue.shuffle.value = false; MusicQueue.play(songs, 0) } },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.PlayArrow, "Play", tint = Color.Black, modifier = Modifier.size(38.dp)) }
            RoundAction(Icons.Outlined.Radio, "Radio", dark = true) { songs.firstOrNull()?.let { MusicQueue.startRadio(it) } }
        }
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, dark: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(if (dark) Color(0x26FFFFFF) else Color.White).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = if (dark) Color.White else Color.Black, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun AlbumPage(url: String, title: String?, nav: MNav) {
    var page by remember(url) { mutableStateOf<Yt.PlaylistPage?>(null) }
    val songs = remember(url) { mutableStateListOf<Video>() }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }
    LaunchedEffect(url, retry) {
        error = null
        try {
            val p = withContext(Dispatchers.IO) { Yt.playlist(url) }
            page = p
            songs.clear(); songs.addAll(p.videos.items.filterIsInstance<Video>())
            var next = p.videos.next
            while (next != null && songs.size < 300) {
                val more = withContext(Dispatchers.IO) { next!!() }
                songs.addAll(more.items.filterIsInstance<Video>().filter { s -> songs.none { it.url == s.url } })
                next = more.next
            }
        } catch (t: Throwable) {
            error = friendlyError(t)
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { TopBar(nav, "", back = true) }
        val p = page
        if (p == null) {
            item { if (error != null) MErrorBox(error!!) { retry++ } else LoadingBox() }
        } else {
            item {
                val album = url.contains("OLAK")
                CollectionHeader(
                    p.info.thumbnails.maxByOrNull { it.height }?.url ?: songs.firstOrNull()?.thumbnail,
                    p.info.name ?: title.orEmpty(),
                    listOfNotNull(if (album) "Album" else "Playlist", artistName(p.info.uploaderName.orEmpty()).takeIf { it.isNotBlank() },
                        "${songs.size} songs").joinToString(" • "),
                    songs,
                )
            }
            itemsIndexed(songs, key = { _, v -> v.url }) { i, v -> SongRow(v, songs, nav, index = if (url.contains("OLAK")) i + 1 else null) }
        }
    }
}

@Composable
private fun AccountListPage(id: String, title: String, nav: MNav) {
    val songs = remember(id) { mutableStateListOf<Video>() }
    var loaded by remember(id) { mutableStateOf(false) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    val tokens = rememberTokenRunner { error = it }
    val scope = rememberCoroutineScope()
    LaunchedEffect(id) {
        tokens.run {
            scope.launch {
                try {
                    val list = withContext(Dispatchers.IO) {
                        if (id == "LL") GoogleAccount.liked() else GoogleAccount.playlistVideos("list=$id")
                    }
                    songs.clear(); songs.addAll(list)
                } catch (t: Throwable) {
                    error = (t as? GoogleAccount.ApiException)?.message ?: friendlyError(t)
                } finally {
                    loaded = true
                }
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { TopBar(nav, "", back = true) }
        if (!loaded) item { LoadingBox() }
        error?.let { e -> item { Text(e, color = Sub, modifier = Modifier.padding(24.dp)) } }
        if (loaded && error == null) item { CollectionHeader(songs.firstOrNull()?.thumbnail, title, "Playlist • ${songs.size} songs", songs) }
        items(songs, key = { it.url }) { SongRow(it, songs, nav) }
    }
}

@Composable
private fun ArtistPage(url: String, nav: MNav) {
    var page by remember(url) { mutableStateOf<Music.ArtistPage?>(null) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }
    var allSongs by remember { mutableStateOf(false) }
    val subs by app.vidflow.core.Library.subscriptions.collectAsState()
    LaunchedEffect(url, retry) {
        error = null
        try { page = withContext(Dispatchers.IO) { Music.artist(url) } } catch (t: Throwable) { error = friendlyError(t) }
    }
    val p = page
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        if (p == null) {
            item { TopBar(nav, "", back = true) }
            item { if (error != null) MErrorBox(error!!) { retry++ } else LoadingBox() }
            return@LazyColumn
        }
        item {
            Box(Modifier.fillMaxWidth().aspectRatio(1.1f)) {
                AsyncImage(p.avatar ?: p.banner, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.3f to Color.Transparent, 1f to Bg)))
                IconButton(onClick = nav.back, modifier = Modifier.padding(4.dp)) { Icon(Icons.Filled.ArrowBack, "Back") }
                Text(
                    artistName(p.name), fontSize = 40.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        item {
            val subscribed = subs.any { it.url == url }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (subscribed) "Subscribed" else "Subscribe" + (p.subscribers.takeIf { it > 0 }?.let { " " + formatCount(it) } ?: ""),
                    fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(18.dp))
                        .background(if (subscribed) Color(0x26FFFFFF) else Color.Transparent)
                        .then(if (subscribed) Modifier else Modifier.background(Color.Transparent))
                        .clickable { app.vidflow.core.Library.toggle(url, p.name, p.avatar) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                RoundAction(Icons.Filled.Shuffle, "Shuffle", dark = true) { if (p.songs.isNotEmpty()) MusicQueue.play(p.songs.shuffled(), 0) }
                Spacer(Modifier.width(12.dp))
                RoundAction(Icons.Outlined.Radio, "Radio", dark = true) { p.songs.firstOrNull()?.let { MusicQueue.startRadio(it) } }
            }
        }
        if (p.songs.isNotEmpty()) {
            item { ShelfTitle("Songs", action = { MusicQueue.play(p.songs, 0) }) }
            val shown = if (allSongs) p.songs else p.songs.take(5)
            items(shown, key = { "s" + it.url }) { SongRow(it, p.songs, nav) }
            if (!allSongs && p.songs.size > 5) item {
                Text(
                    "Show all", fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(16.dp).clip(RoundedCornerShape(18.dp)).background(Color(0x1AFFFFFF))
                        .clickable { allSongs = true }.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        if (p.albums.isNotEmpty()) item { ShelfView(Shelf("Albums", p.albums, Shelf.Style.Squares), nav) }
        if (p.playlists.isNotEmpty()) item { ShelfView(Shelf("Featured on", p.playlists, Shelf.Style.Squares), nav) }
        if (p.videos.isNotEmpty()) item {
            ShelfTitle("Videos")
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(p.videos, key = { it.url }) { v ->
                    Column(Modifier.width(240.dp).clickable { nav.item(v, p.videos) }) {
                        AsyncImage(v.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp)).background(Chip))
                        Text(v.title, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                        Text(v.views.takeIf { it > 0 }?.let { formatCount(it) + " views" } ?: "", fontSize = 13.sp, color = Sub)
                    }
                }
            }
        }
    }
}

@Composable
private fun SongListPage(title: String, songs: List<Video>, nav: MNav) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { TopBar(nav, "", back = true) }
        item { CollectionHeader(songs.firstOrNull()?.thumbnail, title, "${songs.size} songs", songs) }
        if (songs.isEmpty()) item { Text("Nothing here yet.", color = Sub, modifier = Modifier.padding(24.dp)) }
        items(songs, key = { it.url }) { SongRow(it, songs, nav) }
    }
}

@Composable
private fun LocalPage(id: String, nav: MNav) {
    val lists by Saved.playlists.collectAsState()
    val p = lists.firstOrNull { it.id == id }
    SongListPage(p?.name ?: "Playlist", p?.videos.orEmpty(), nav)
}

// ---------------------------------------------------------------- library

@Composable
private fun LibraryPage(nav: MNav) {
    val liked by Saved.liked.collectAsState()
    val recent by MusicHistory.list.collectAsState()
    val local by Saved.playlists.collectAsState()
    val signedIn by GoogleAccount.signedIn.collectAsState()
    val artists by app.vidflow.core.Library.subscriptions.collectAsState()
    var account by remember { mutableStateOf(PlaylistCache.lists) }
    var error by remember { mutableStateOf<String?>(null) }
    val tokens = rememberTokenRunner { error = it }
    val scope = rememberCoroutineScope()
    LaunchedEffect(signedIn) {
        if (!signedIn) return@LaunchedEffect
        tokens.run {
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { GoogleAccount.myPlaylists() } }
                    .onSuccess { account = it; PlaylistCache.lists = it }
                    .onFailure { error = (it as? GoogleAccount.ApiException)?.message ?: friendlyError(it) }
                // Your liked music personalises Home.
                runCatching { withContext(Dispatchers.IO) { GoogleAccount.liked() } }.onSuccess { Music.accountLiked = it }
            }
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { TopBar(nav, "Library") }
        if (recent.isNotEmpty()) item {
            ShelfTitle("Recent activity", action = { nav.open(Page.Recent) }, actionText = "More")
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(recent.take(15), key = { it.url }) { SquareCard(it, recent, nav, 130) }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
        item { LibTile(liked.firstOrNull()?.thumbnail, "Liked songs", "Auto playlist • ${liked.size} songs", Icons.Filled.ThumbUp) { nav.open(Page.Liked) } }
        if (signedIn) item { LibTile(null, "Liked music", "From your Google account", Icons.Filled.ThumbUp) { nav.open(Page.AccountList("LL", "Liked music")) } }
        items(local.filter { it.id != Saved.SAVED_SHORTS }, key = { "l" + it.id }) { p ->
            LibTile(p.videos.firstOrNull()?.thumbnail, p.name, "Playlist • ${p.videos.size} songs • in this app", null) { nav.open(Page.Local(p.id)) }
        }
        account?.let { acc -> items(acc, key = { "a" + it.id }) { p ->
            LibTile(p.thumbnail, p.title, "Playlist • ${p.count} songs", null) { nav.open(Page.AccountList(p.id, p.title)) }
        } }
        if (!signedIn) item {
            Text(
                "Sign in with Google (tap your picture at the top) to see your YouTube playlists here.",
                color = Sub, fontSize = 13.sp, modifier = Modifier.padding(20.dp),
            )
        }
        if (artists.isNotEmpty()) {
            item { ShelfTitle("Artists") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(artists, key = { "ar" + it.url }) { a ->
                        Column(Modifier.width(110.dp).clickable { nav.open(Page.Artist(a.url)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                            AsyncImage(a.avatar, null, contentScale = ContentScale.Crop, modifier = Modifier.size(110.dp).clip(CircleShape).background(Chip))
                            Text(artistName(a.name), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
        error?.let { e -> item { Text(e, color = Color(0xFFFF8A80), fontSize = 13.sp, modifier = Modifier.padding(20.dp)) } }
    }
}

@Composable
private fun LibTile(thumb: String?, title: String, subtitle: String, icon: ImageVector?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(4.dp)).background(Brush.linearGradient(listOf(Color(0xFF6A3FD6), Color(0xFFD63F7A)))), contentAlignment = Alignment.Center) {
            if (icon != null && thumb == null) Icon(icon, null, tint = Color.White)
            if (thumb != null) AsyncImage(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 13.sp, color = Sub, maxLines = 1)
        }
    }
}

/** Account page: sign in with Google, switch or add accounts, sign out, import, settings. */
@Composable
private fun MusicSettings(nav: MNav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val signedIn by GoogleAccount.signedIn.collectAsState()
    val name by GoogleAccount.name.collectAsState()
    val avatar by GoogleAccount.avatar.collectAsState()
    val email by GoogleAccount.email.collectAsState()
    val handle by GoogleAccount.handle.collectAsState()
    val accounts by GoogleAccount.accounts.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val actions = rememberAccountActions { message = it }
    val tokens = rememberTokenRunner { message = it }
    val needsSetup = message?.contains("isn't set up yet") == true

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { TopBar(nav, "Account", back = true) }
        if (signedIn) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(avatar, 84)
                    Text(name ?: "Google account", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                    Text(listOfNotNull(handle, email).joinToString(" • "), color = Sub, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
                }
            }
        } else {
            item {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.AccountCircle, null, modifier = Modifier.size(84.dp), tint = Sub)
                    Text("Sign in with Google", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                    Text(
                        "Optional. See your YouTube playlists and liked music, save songs to your playlists, " +
                            "sync likes, and get a Home made from what you like.",
                        color = Sub, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Text(
                        "Sign in", color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                        modifier = Modifier.padding(top = 18.dp).clip(RoundedCornerShape(22.dp)).background(Color.White)
                            .clickable { message = null; actions.signIn(null) }.padding(horizontal = 36.dp, vertical = 11.dp),
                    )
                }
            }
        }
        message?.let { m ->
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x1AFFFFFF)).padding(14.dp)) {
                    Text(m, fontSize = 14.sp)
                    if (needsSetup) Text(
                        "This app is new to your Google Cloud project: add an Android sign-in for it (package app.vidflow.music). Open the guide",
                        color = Color(0xFF3EA6FF), fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp).clickable { nav.open(Page.Setup) },
                    )
                }
            }
        }

        // Accounts on this phone: switch, add, use without one.
        if (accounts.isNotEmpty() || signedIn) {
            item { SectionLabel("Accounts") }
            items(accounts, key = { "acc" + it.email }) { a ->
                val active = signedIn && a.email == email
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !active) { message = null; actions.signIn(a.email) }.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(a.avatar, 40)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.name, fontSize = 16.sp, maxLines = 1)
                        Text(listOfNotNull(a.handle, a.email).joinToString(" • "), color = Sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (active) Icon(Icons.Filled.Check, "Signed in")
                }
            }
            item { AccountLine(Icons.Outlined.PersonAdd, "Add account") { message = null; actions.addAccount() } }
            if (signedIn) item { AccountLine(Icons.Outlined.Logout, "Use without an account") { actions.signOut(); message = "Signed out. Your songs and playlists in this app stay." } }
        }

        if (signedIn) {
            item { SectionLabel("Your YouTube") }
            item { AccountLine(Icons.Filled.ThumbUp, "Liked music") { nav.open(Page.AccountList("LL", "Liked music")) } }
            item { AccountLine(Icons.Outlined.LibraryMusic, "Your playlists") { nav.open(Page.Library) } }
            item {
                AccountLine(Icons.Outlined.PersonAdd, if (busy) "Importing artists…" else "Import subscribed artists") {
                    if (busy) return@AccountLine
                    busy = true
                    tokens.run {
                        scope.launch {
                            message = try {
                                val n = withContext(Dispatchers.IO) { GoogleAccount.importSubscriptions() }
                                "Added $n channels to your artists."
                            } catch (t: Throwable) {
                                (t as? GoogleAccount.ApiException)?.message ?: friendlyError(t)
                            } finally {
                                busy = false
                            }
                        }
                    }
                }
            }
        }

        item { SectionLabel("Settings") }
        item { SettingLine("Google sign-in setup guide") { nav.open(Page.Setup) } }
        item { SettingLine("Clear recently played") { MusicHistory.clear(); message = "Recently played cleared." } }
        item {
            val version = remember {
                runCatching {
                    "v" + androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
                }.getOrDefault("?")
            }
            Text("Vidflow Music $version", color = Sub, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 13.sp, color = Sub, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 6.dp))
}

@Composable
private fun AccountLine(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Text(text, fontSize = 16.sp)
    }
}

/**
 * Like / unlike a song. Always kept in the app; when you're signed in it's also
 * liked on your YouTube account, so it shows up in YouTube Music too.
 */
@Composable
private fun rememberLiker(): (Video) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tokens = rememberTokenRunner { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show() }
    return { v ->
        val nowLiked = Saved.liked.value.none { it.url == v.url }
        Saved.toggleLike(v)
        val id = Yt.videoId(v.url)
        if (GoogleAccount.signedIn.value && id != null) tokens.run {
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { GoogleAccount.rate(id, if (nowLiked) "like" else "none") } }
                    .onFailure { android.widget.Toast.makeText(context, (it as? GoogleAccount.ApiException)?.message ?: friendlyError(it), android.widget.Toast.LENGTH_SHORT).show() }
            }
        }
    }
}

@Composable
private fun SettingLine(text: String, onClick: () -> Unit) {
    Text(text, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp))
}

// ---------------------------------------------------------------- player

@Composable
private fun rememberProgress(): Pair<Long, Long> {
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            val p = PlayerHolder.player
            pos = p.currentPosition
            dur = p.duration.takeIf { it > 0 } ?: 0
            delay(250)
        }
    }
    return pos to dur
}

@Composable
private fun isPlayingState(): Boolean {
    var playing by remember { mutableStateOf(PlayerHolder.player.isPlaying) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        val l = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        PlayerHolder.player.addListener(l)
        onDispose { PlayerHolder.player.removeListener(l) }
    }
    return playing
}

@Composable
private fun MiniPlayer(open: () -> Unit) {
    val v by PlayerHolder.current.collectAsState()
    val loading by MusicQueue.loading.collectAsState()
    val playing = isPlayingState()
    val (pos, dur) = rememberProgress()
    val song = v ?: return
    Column(Modifier.fillMaxWidth().background(Color(0xFF212121)).clickable(onClick = open)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0x33FFFFFF))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f).background(Color.White))
        }
        Row(Modifier.fillMaxWidth().height(62.dp).padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(song.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.size(42.dp).clip(RoundedCornerShape(4.dp)).background(Chip))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(artistName(song.channel), fontSize = 13.sp, color = Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (loading) CircularProgressIndicator(Modifier.padding(12.dp).size(22.dp), color = Color.White, strokeWidth = 2.dp)
            else IconButton(onClick = { if (playing) PlayerHolder.player.pause() else PlayerHolder.player.play() }) {
                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play", modifier = Modifier.size(30.dp))
            }
            IconButton(onClick = { MusicQueue.next() }) { Icon(Icons.Filled.SkipNext, "Next", modifier = Modifier.size(28.dp)) }
        }
    }
}

private fun ms(t: Long): String {
    val s = t / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun NowPlaying(nav: MNav, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val v by PlayerHolder.current.collectAsState()
    val audioOnly by PlayerHolder.audioOnly.collectAsState()
    val loading by MusicQueue.loading.collectAsState()
    val shuffle by MusicQueue.shuffle.collectAsState()
    val repeat by MusicQueue.repeat.collectAsState()
    val liked by Saved.liked.collectAsState()
    val error by PlayerHolder.error.collectAsState()
    val playing = isPlayingState()
    val (pos, dur) = rememberProgress()
    var dragging by remember { mutableStateOf<Float?>(null) }
    var sheet by remember { mutableStateOf<String?>(null) }
    val like = rememberLiker()
    val song = v ?: return
    val isLiked = liked.any { it.url == song.url }

    Box(Modifier.fillMaxSize().background(Bg).clickable(remember { MutableInteractionSource() }, null) {}) {
        // Blurred artwork behind everything, like YouTube Music's colour wash.
        AsyncImage(
            song.thumbnail, null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(90.dp).graphicsLayer { alpha = 0.55f },
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66000000), Color(0xE6000000)))))
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.KeyboardArrowDown, "Close", modifier = Modifier.size(32.dp)) }
                Spacer(Modifier.weight(1f))
                // Song | Video switch
                Row(Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0x33FFFFFF)).padding(3.dp)) {
                    listOf(true to "Song", false to "Video").forEach { (audio, label) ->
                        val on = audioOnly == audio
                        Text(
                            label, color = if (on) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(if (on) Color.White else Color.Transparent)
                                .clickable { if (!on) PlayerHolder.setVideoMode(!audio) }.padding(horizontal = 18.dp, vertical = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                SongMenu(song, nav)
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (audioOnly) {
                    AsyncImage(
                        song.thumbnail, null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(Chip),
                    )
                } else {
                    PlayerSurface(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)), controls = false, fit = true)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 20.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(song.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.basicMarquee())
                    Text(
                        artistName(song.channel), fontSize = 16.sp, color = Color(0xCCFFFFFF), maxLines = 1,
                        modifier = Modifier.clickable(enabled = song.channelUrl != null) { song.channelUrl?.let { onClose(); nav.open(Page.Artist(it)) } },
                    )
                }
                IconButton(onClick = { like(song) }) {
                    Icon(if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, "Like", modifier = Modifier.size(26.dp))
                }
            }
            error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
            Slider(
                value = dragging ?: if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                onValueChange = { dragging = it },
                onValueChangeFinished = { dragging?.let { PlayerHolder.player.seekTo((it * dur).toLong()) }; dragging = null },
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color(0x40FFFFFF)),
                modifier = Modifier.padding(top = 12.dp),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(ms(dragging?.let { (it * dur).toLong() } ?: pos), fontSize = 12.sp, color = Sub)
                Spacer(Modifier.weight(1f))
                Text(ms(dur), fontSize = 12.sp, color = Sub)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { MusicQueue.toggleShuffle() }) {
                    Icon(Icons.Filled.Shuffle, "Shuffle", tint = if (shuffle) Color.White else Color(0x80FFFFFF))
                }
                IconButton(onClick = { MusicQueue.previous() }, modifier = Modifier.size(56.dp)) { Icon(Icons.Filled.SkipPrevious, "Previous", modifier = Modifier.size(40.dp)) }
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(Color.White)
                        .clickable { if (playing) PlayerHolder.player.pause() else PlayerHolder.player.play() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (loading) CircularProgressIndicator(Modifier.size(30.dp), color = Color.Black, strokeWidth = 3.dp)
                    else Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play", tint = Color.Black, modifier = Modifier.size(42.dp))
                }
                IconButton(onClick = { MusicQueue.next() }, modifier = Modifier.size(56.dp)) { Icon(Icons.Filled.SkipNext, "Next", modifier = Modifier.size(40.dp)) }
                IconButton(onClick = { MusicQueue.cycleRepeat() }) {
                    Icon(
                        if (repeat == MusicQueue.Repeat.One) Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Repeat",
                        tint = if (repeat == MusicQueue.Repeat.Off) Color(0x80FFFFFF) else Color.White,
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.SpaceAround) {
                listOf("UP NEXT", "LYRICS", "RELATED").forEach { t ->
                    Text(t, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { sheet = t }.padding(horizontal = 12.dp, vertical = 10.dp))
                }
            }
        }
    }

    if (sheet != null) ModalBottomSheet(
        onDismissRequest = { sheet = null },
        containerColor = Color(0xFF181818),
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Box(Modifier.fillMaxHeight(0.8f)) {
            when (sheet) {
                "UP NEXT" -> QueueList(nav)
                "RELATED" -> RelatedList(song, nav)
                else -> Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text("Lyrics", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    val desc = PlayerHolder.info.collectAsState().value?.description?.content.orEmpty()
                    val lyrics = app.vidflow.core.plainText(desc)
                    Text(
                        lyrics.ifBlank { "Lyrics aren't available for this song." }, color = Color(0xDDFFFFFF), fontSize = 15.sp, lineHeight = 22.sp,
                        modifier = Modifier.padding(top = 12.dp).verticalScrollable(),
                    )
                    if (lyrics.isNotBlank()) Text("From the song's description", color = Sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun Modifier.verticalScrollable(): Modifier = this.then(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()))

@Composable
private fun QueueList(nav: MNav) {
    val q by MusicQueue.queue.collectAsState()
    val index by MusicQueue.index.collectAsState()
    val radio by MusicQueue.radio.collectAsState()
    val state = rememberLazyListState(initialFirstVisibleItemIndex = index.coerceAtLeast(0))
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Up next", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("Autoplay", color = Sub, fontSize = 13.sp)
            androidx.compose.material3.Switch(
                checked = radio, onCheckedChange = { MusicQueue.radio.value = it },
                modifier = Modifier.padding(start = 8.dp),
                colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Color.White, checkedThumbColor = Color.Black),
            )
        }
        LazyColumn(state = state) {
            itemsIndexed(q, key = { i, v -> "$i-${v.url}" }) { i, v ->
                Row(
                    Modifier.fillMaxWidth().background(if (i == index) Color(0x1AFFFFFF) else Color.Transparent)
                        .clickable { MusicQueue.jumpTo(i) }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(v.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)).background(Chip))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(v.title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (i == index) Red else Color.White)
                        Text(artistName(v.channel), fontSize = 13.sp, color = Sub, maxLines = 1)
                    }
                    if (i != index) IconButton(onClick = { MusicQueue.remove(i) }) { Icon(Icons.Filled.Close, "Remove", tint = Sub, modifier = Modifier.size(18.dp)) }
                }
            }
        }
    }
}

@Composable
private fun RelatedList(song: Video, nav: MNav) {
    var list by remember(song.url) { mutableStateOf<List<Video>?>(null) }
    LaunchedEffect(song.url) { list = withContext(Dispatchers.IO) { Music.related(song) } }
    Column {
        Text("You might also like", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        val l = list
        if (l == null) LoadingBox()
        else LazyColumn { items(l, key = { it.url }) { SongRow(it, l, nav) } }
    }
}
