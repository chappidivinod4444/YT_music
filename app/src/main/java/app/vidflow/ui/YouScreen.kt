package app.vidflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vidflow.core.Downloads
import app.vidflow.core.GoogleAccount
import app.vidflow.core.Library
import app.vidflow.core.Saved
import app.vidflow.core.SeenShorts
import app.vidflow.core.UploadsWorker
import app.vidflow.core.friendlyError
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Sign in, switch accounts or sign out, from anywhere. */
class AccountActions(val signIn: (String?) -> Unit, val addAccount: () -> Unit, val signOut: () -> Unit)

@Composable
fun rememberAccountActions(say: (String) -> Unit): AccountActions {
    val scope = rememberCoroutineScope()
    val google = rememberGoogleSignIn()
    google.onToken = { t ->
        scope.launch {
            try {
                withContext(Dispatchers.IO) { GoogleAccount.onToken(t, google.pickedEmail) }
                say("Signed in as ${GoogleAccount.name.value}")
            } catch (e: Throwable) {
                say(e.message ?: friendlyError(e))
            }
        }
    }
    google.onError = { say(it) }
    return remember {
        AccountActions(
            signIn = { mail ->
                GoogleAccount.token = null
                if (mail == null) google.pickAccount() else google.request(mail)
            },
            addAccount = { GoogleAccount.token = null; google.pickAccount() },
            signOut = { scope.launch(Dispatchers.IO) { GoogleAccount.signOut() } },
        )
    }
}

@Composable
fun YouScreen(
    nav: Nav,
    say: (String) -> Unit,
    openSettings: () -> Unit,
    openNotifications: () -> Unit,
    openHistory: () -> Unit,
    openLocalLiked: () -> Unit,
    openLocalPlaylist: (String) -> Unit,
    openGoogleLiked: () -> Unit,
    openGooglePlaylists: () -> Unit,
) {
    val signedIn by GoogleAccount.signedIn.collectAsState()
    val name by GoogleAccount.name.collectAsState()
    val avatar by GoogleAccount.avatar.collectAsState()
    val handle by GoogleAccount.handle.collectAsState()
    val channelId by GoogleAccount.channelId.collectAsState()
    val email by GoogleAccount.email.collectAsState()
    val accounts by GoogleAccount.accounts.collectAsState()
    val history by Library.history.collectAsState()
    val liked by Saved.liked.collectAsState()
    val playlists by Saved.playlists.collectAsState()
    val jobs by Downloads.jobs.collectAsState()
    val actions = rememberAccountActions(say)
    var menu by remember { mutableStateOf(false) }
    var shelf by rememberSaveable { mutableStateOf("Recent") }

    LazyColumn(contentPadding = PaddingValues(bottom = 90.dp), modifier = Modifier.fillMaxSize()) {
        // Top row: accounts, bell, search, settings.
        item {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                            .clickable { menu = true }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Accounts", fontWeight = FontWeight.Medium)
                        Text("  ▾")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        accounts.forEach { a ->
                            DropdownMenuItem(
                                leadingIcon = { Avatar(a.avatar, 28) },
                                text = {
                                    Column {
                                        Text(a.name + if (a.email == email && signedIn) "  ✓" else "", fontWeight = FontWeight.SemiBold)
                                        Text(a.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = { menu = false; if (a.email != email || !signedIn) actions.signIn(a.email) },
                            )
                        }
                        if (accounts.isNotEmpty()) HorizontalDivider()
                        DropdownMenuItem(text = { Text("➕  Add account") }, onClick = { menu = false; actions.addAccount() })
                        if (signedIn) DropdownMenuItem(text = { Text("Use without an account") }, onClick = { menu = false; actions.signOut() })
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = openNotifications) { Icon(Icons.Filled.Notifications, contentDescription = "New uploads") }
                IconButton(onClick = nav.search) { Icon(Icons.Filled.Search, contentDescription = "Search") }
                IconButton(onClick = openSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
            }
        }

        // Profile.
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (signedIn && avatar != null) Avatar(avatar, 84) else Box(
                    Modifier.size(84.dp).clip(RoundedCornerShape(42.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Text((name ?: "V").take(1).uppercase(), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer) }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (signedIn) name ?: "Google account" else "Vidflow", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (signedIn) handle ?: email ?: "" else "Not signed in · everything stays on this phone",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (signedIn && channelId != null) {
                    Pill2("View channel", filled = true, modifier = Modifier.weight(1f)) {
                        nav.channel("https://www.youtube.com/channel/$channelId")
                    }
                    Pill2("Switch account", filled = false, modifier = Modifier.weight(1f)) { menu = true }
                } else if (!signedIn) {
                    Pill2("Sign in with Google", filled = true, modifier = Modifier.weight(1f)) { actions.signIn(null) }
                    Pill2("Settings", filled = false, modifier = Modifier.weight(1f)) { openSettings() }
                } else {
                    Pill2("Switch account", filled = true, modifier = Modifier.weight(1f)) { menu = true }
                }
            }
        }

        // History row.
        item {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = openHistory).padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("History", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("  ›", style = MaterialTheme.typography.titleLarge)
            }
        }
        item {
            if (history.isEmpty()) {
                Text("Videos you watch appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            } else {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(history.take(20), key = { "hr-" + it.video.url }) { w ->
                        Column(Modifier.width(180.dp).clickable { nav.video(w.video) }) {
                            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                                AsyncImage(model = w.video.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                val total = w.video.durationSec * 1000
                                if (total > 0 && w.positionMs > 0) {
                                    Box(
                                        Modifier.align(Alignment.BottomStart).fillMaxWidth((w.positionMs.toFloat() / total).coerceIn(0.03f, 1f))
                                            .height(3.dp).background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                            Row(Modifier.padding(top = 6.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(w.video.title, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                    Text(w.video.channel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                                VideoMenu(w.video, nav)
                            }
                        }
                    }
                }
            }
        }

        // Library with chips.
        item {
            Text("Library", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 28.dp, bottom = 12.dp))
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("Recent", "Playlists", "Liked", "Downloaded")) { c -> TopicChip(c, shelf == c) { shelf = c } }
            }
        }
        when (shelf) {
            "Playlists" -> {
                item { LibRow("Liked videos", "${liked.size} videos · on this phone", liked.firstOrNull()?.thumbnail, openLocalLiked) }
                items(playlists, key = { "pl-" + it.id }) { p ->
                    LibRow(p.name, "Playlist · ${p.videos.size} videos", p.videos.firstOrNull()?.thumbnail) { openLocalPlaylist(p.id) }
                }
                if (signedIn) item { LibRow("Your Google playlists", "From your account", null, openGooglePlaylists) }
                if (playlists.isEmpty()) item {
                    Text("Tap ➕ Save under a video to make a playlist.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
            }
            "Liked" -> {
                if (signedIn) item { LibRow("Liked on your Google account", "From your account", null, openGoogleLiked) }
                if (liked.isEmpty()) item {
                    Text("Tap 👍 on a video to keep it here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
                items(liked, key = { "lk-" + it.url }) { VideoRow(it, nav) }
            }
            "Downloaded" -> {
                if (jobs.isEmpty()) item {
                    Text(
                        "Use ⬇ Download under a video. Files are saved in your phone's Downloads/Vidflow folder.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp),
                    )
                }
                items(jobs.reversed(), key = { "dj-" + it.id }) { j ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(j.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        if (!j.done && !j.failed) LinearProgressIndicator(progress = { j.progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                        Text(j.status, style = MaterialTheme.typography.bodySmall, color = if (j.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            else -> items(history.take(30), key = { "rc-" + it.video.url }) { VideoRow(it.video, nav) }
        }
    }
}

@Composable
private fun Pill2(text: String, filled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text,
        fontWeight = FontWeight.SemiBold,
        color = if (filled) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(if (filled) MaterialTheme.colorScheme.onSurface else Color.Transparent)
            .border(1.dp, if (filled) Color.Transparent else MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
    )
}

@Composable
private fun LibRow(title: String, subtitle: String, thumb: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(150.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (thumb != null) AsyncImage(model = thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Text("☰", color = Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color(0x99000000), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Everything that used to be on the You tab: account tools, playback, alerts, clean-up. */
@Composable
fun SettingsScreen(nav: Nav, say: (String) -> Unit, openSetup: () -> Unit, openLiked: () -> Unit, openPlaylists: () -> Unit) {
    val context = LocalContext.current
    var alerts by remember {
        mutableStateOf(context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).getBoolean("uploadAlerts", true))
    }
    var confirm by remember { mutableStateOf<String?>(null) }
    LazyColumn(contentPadding = PaddingValues(bottom = 60.dp), modifier = Modifier.fillMaxSize()) {
        item { AppBar("Settings", nav, showBack = true) }
        item { AccountSection(nav, openLiked, openPlaylists, say) }
        item { PlaybackSettings() }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("New upload alerts", fontWeight = FontWeight.SemiBold)
                    Text("A notification when your channels post (checked every few hours)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = alerts, onCheckedChange = {
                    alerts = it
                    context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE).edit().putBoolean("uploadAlerts", it).apply()
                    UploadsWorker.schedule(context, it)
                })
            }
        }
        item { SettingsLink("Google sign-in setup (step by step)", openSetup) }
        item { SettingsLink("Clear watch history") { confirm = "history" } }
        item { SettingsLink("Show Shorts I've already seen again") { SeenShorts.clear(); say("Done: seen Shorts can show up again") } }
        item {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val version = remember {
                runCatching {
                    val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
                    "v" + androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info)
                }.getOrDefault("?")
            }
            Text(
                "Vidflow $version",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        item {
            Text(
                "Vidflow · no ads · no tracking. Your history, likes and playlists are saved only on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
    if (confirm == "history") {
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Clear watch history?") },
            confirmButton = { TextButton(onClick = { Library.clearHistory(); confirm = null; say("History cleared") }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingsLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp),
    )
}

/** Full watch history. */
@Composable
fun HistoryScreen(nav: Nav) {
    val history by Library.history.collectAsState()
    LazyColumn(contentPadding = PaddingValues(bottom = 90.dp), modifier = Modifier.fillMaxSize()) {
        item { AppBar("History", nav, showBack = true) }
        if (history.isEmpty()) item { Text("Nothing watched yet.", modifier = Modifier.padding(32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(history, key = { "h-" + it.video.url }) { w ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { VideoRow(w.video, nav) }
                TextButton(onClick = { Library.removeFromHistory(w.video.url) }) { Text("✕") }
            }
        }
    }
}
