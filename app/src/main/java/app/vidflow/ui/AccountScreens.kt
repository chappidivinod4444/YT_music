package app.vidflow.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.vidflow.core.GoogleAccount
import app.vidflow.core.Item
import app.vidflow.core.Playlist
import app.vidflow.core.TakeoutImport
import app.vidflow.core.friendlyError
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Asks Google for a read-only token. Shows Google's own screen the first time. */
class GoogleSignIn(
    private val activity: Activity,
    private val launchUi: (IntentSenderRequest) -> Unit,
    private val launchPicker: (android.content.Intent) -> Unit,
) {
    var onToken: (String) -> Unit = {}
    var onError: (String) -> Unit = {}

    /** The account being signed in, if one was picked. */
    var pickedEmail: String? = null

    /** Shows the phone's own list of Google accounts to choose from. */
    fun pickAccount() {
        launchPicker(
            android.accounts.AccountManager.newChooseAccountIntent(
                null, null, arrayOf("com.google"), null, null, null, null,
            )
        )
    }

    fun request(email: String? = null) {
        if (email != null) pickedEmail = email
        val builder = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(GoogleAccount.SCOPE)))
        pickedEmail?.let { builder.setAccount(android.accounts.Account(it, "com.google")) }
        val req = builder.build()
        Identity.getAuthorizationClient(activity).authorize(req)
            .addOnSuccessListener { r ->
                val pi = r.pendingIntent
                if (r.hasResolution() && pi != null) launchUi(IntentSenderRequest.Builder(pi.intentSender).build())
                else r.accessToken?.let(onToken) ?: onError("Google didn't return access.")
            }
            .addOnFailureListener { e ->
                onError(
                    if (e.message?.contains("10:") == true || e.message?.contains("DEVELOPER_ERROR") == true)
                        "Google sign-in isn't set up yet. Follow the setup steps (package name + SHA-1) in your Google Cloud project."
                    else e.message ?: "Sign-in failed"
                )
            }
    }

    fun handleResult(data: android.content.Intent?) {
        try {
            val r = Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data)
            r.accessToken?.let(onToken) ?: onError("Sign-in was cancelled.")
        } catch (e: Exception) {
            onError("Sign-in was cancelled.")
        }
    }
}

@Composable
fun rememberGoogleSignIn(): GoogleSignIn {
    val activity = LocalContext.current as Activity
    var holder: GoogleSignIn? = null
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        holder?.handleResult(res.data)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val mail = res.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME)
        if (mail != null) holder?.request(mail) else holder?.onError?.invoke("No account chosen.")
    }
    val g = remember { GoogleSignIn(activity, { launcher.launch(it) }, { picker.launch(it) }) }
    holder = g
    return g
}

/** The "Your account" box on the Library screen. */
@Composable
fun AccountSection(nav: Nav, openLiked: () -> Unit, openPlaylists: () -> Unit, say: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val signedIn by GoogleAccount.signedIn.collectAsState()
    val name by GoogleAccount.name.collectAsState()
    val avatar by GoogleAccount.avatar.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    var after: (() -> Unit)? by remember { mutableStateOf(null) }
    val google = rememberGoogleSignIn()

    google.onToken = { t ->
        scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) { GoogleAccount.onToken(t, google.pickedEmail) }
                after?.invoke()
            } catch (e: Throwable) {
                say(e.message ?: friendlyError(e))
            } finally {
                busy = false
                after = null
            }
        }
    }
    google.onError = { say(it); busy = false }

    /** Runs [action] with a fresh token (silently if already allowed). */
    fun withToken(action: () -> Unit) {
        if (GoogleAccount.token != null) action() else {
            after = action
            busy = true
            google.request()
        }
    }

    val takeout = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
                    TakeoutImport.import(text)
                }
            }
            result.onSuccess { say(if (it > 0) "Added $it channels" else "No new channels found in that file") }
                .onFailure { say("Couldn't read that file") }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Your account (optional)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (signedIn) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(avatar, 40)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(name ?: "Google account", fontWeight = FontWeight.SemiBold)
                    Text("Read-only access", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { scope.launch(Dispatchers.IO) { GoogleAccount.signOut() } }) { Text("Sign out") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { withToken(openLiked) }, modifier = Modifier.weight(1f)) { Text("Liked") }
                OutlinedButton(onClick = { withToken(openPlaylists) }, modifier = Modifier.weight(1f)) { Text("Playlists") }
            }
            Button(enabled = !busy, onClick = {
                withToken {
                    scope.launch {
                        busy = true
                        try {
                            val n = withContext(Dispatchers.IO) { GoogleAccount.importSubscriptions() }
                            say(if (n > 0) "Added $n channels from your account" else "Your subscriptions are already here")
                        } catch (e: Throwable) {
                            say(e.message ?: "Couldn't import")
                        } finally {
                            busy = false
                        }
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Working…" else "Import my subscriptions") }
        } else {
            Text(
                "Vidflow works without an account. Sign in only if you want your subscriptions, playlists and liked videos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(enabled = !busy, onClick = { google.pickAccount() }, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "Opening Google…" else "Sign in with Google")
            }
            TextButton(onClick = { help = true }) { Text("First time? Setup steps") }
        }
        OutlinedButton(onClick = { takeout.launch(arrayOf("text/*", "text/csv", "application/octet-stream", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
            Text("Import subscriptions from Google Takeout")
        }
    }

    if (help) {
        AlertDialog(
            onDismissRequest = { help = false },
            title = { Text("Set up Google sign-in") },
            text = {
                Text(
                    "Google requires every app to be registered once:\n\n" +
                        "1. Open console.cloud.google.com and create a project.\n" +
                        "2. APIs & Services > Library: enable \"YouTube Data API v3\".\n" +
                        "3. OAuth consent screen: External, add your Gmail as a test user.\n" +
                        "4. Credentials > Create OAuth client ID > Android:\n" +
                        "   Package: app.vidflow\n" +
                        "   SHA-1: F6:DD:C7:FE:6F:92:9B:F8:41:4E:06:85:D2:58:CD:12:61:4C:DC:B0\n\n" +
                        "Then come back and tap Sign in. Google will warn the app isn't verified: that's expected for your own app.",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = { TextButton(onClick = { help = false }) { Text("OK") } },
        )
    }
}

/** A plain list of items fetched from the account (liked videos, a playlist, your playlists). */
@Composable
fun AccountListScreen(title: String, nav: Nav, load: () -> List<Item>, openPlaylist: (Playlist) -> Unit) {
    var items by remember(title) { mutableStateOf<List<Item>?>(null) }
    var error by remember(title) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun reload() {
        error = null
        scope.launch {
            try {
                items = withContext(Dispatchers.IO) { load() }
            } catch (e: Throwable) {
                error = e.message ?: friendlyError(e)
            }
        }
    }
    LaunchedEffect(title) { reload() }
    Column(Modifier.fillMaxSize()) {
        AppBar(title, nav, showBack = true)
        val list = items
        val err = error
        when {
            err != null -> ErrorBox(err) { reload() }
            list == null -> Loading()
            list.isEmpty() -> Text("Nothing here.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp))
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 90.dp)) {
                items(list, key = { it.url }) { item ->
                    if (item is Playlist) {
                        PlaylistRow(item, Nav(nav.video, nav.channel, { openPlaylist(item) }, nav.search, nav.back))
                    } else {
                        ItemView(item, nav, bigVideos = false)
                    }
                }
            }
        }
    }
}

/**
 * Runs Google-account work with a fresh token, asking Google first when needed
 * (the token is never stored, so after the app restarts it's fetched again quietly).
 */
class TokenRunner(private val google: GoogleSignIn) {
    internal var pending: (() -> Unit)? = null

    fun run(action: () -> Unit) {
        if (GoogleAccount.token != null) return action()
        pending = action
        val mail = GoogleAccount.email.value
        if (mail != null && GoogleAccount.signedIn.value) google.request(mail) else google.pickAccount()
    }
}

@Composable
fun rememberTokenRunner(onError: (String) -> Unit): TokenRunner {
    val scope = rememberCoroutineScope()
    val google = rememberGoogleSignIn()
    val runner = remember { TokenRunner(google) }
    google.onToken = { t ->
        scope.launch {
            try {
                withContext(Dispatchers.IO) { GoogleAccount.onToken(t, google.pickedEmail) }
                runner.pending?.invoke()
            } catch (e: Throwable) {
                onError(e.message ?: friendlyError(e))
            }
            runner.pending = null
        }
    }
    google.onError = { onError(it); runner.pending = null }
    return runner
}
