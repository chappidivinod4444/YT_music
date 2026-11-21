package app.vidflow.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Reads the subscriptions.csv file from Google Takeout and adds those channels. Returns how many were new. */
object TakeoutImport {
    fun import(csv: String): Int {
        var added = 0
        val lines = csv.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        for (line in lines.drop(1)) { // first line is the header
            val cols = splitCsv(line)
            if (cols.size < 3) continue
            val id = cols[0].trim()
            if (!id.startsWith("UC")) continue
            val url = "https://www.youtube.com/channel/$id"
            if (!Library.isSubscribed(url)) {
                Library.toggle(url, cols[2].trim(), null)
                added++
            }
        }
        return added
    }

    private fun splitCsv(line: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { out.add(cur.toString()); cur.clear() }
                else -> cur.append(c)
            }
            i++
        }
        out.add(cur.toString())
        return out
    }
}

/**
 * Optional Google account features through the official YouTube Data API.
 * Only reads (subscriptions, playlists, likes). Playback never uses the account.
 * The access token comes from Google Play services and is never stored.
 */
object GoogleAccount {
    /** Read your subscriptions/playlists and post comments. */
    const val SCOPE = "https://www.googleapis.com/auth/youtube.force-ssl"

    val signedIn = MutableStateFlow(false)
    val name = MutableStateFlow<String?>(null)
    val avatar = MutableStateFlow<String?>(null)

    @Volatile
    var token: String? = null

    private lateinit var app: Context
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()

    /** Accounts you've signed in with, so you can switch between them. */
    data class Saved(val email: String, val name: String, val avatar: String?, val handle: String?, val channelId: String?)

    val accounts = MutableStateFlow<List<Saved>>(emptyList())
    val email = MutableStateFlow<String?>(null)
    val handle = MutableStateFlow<String?>(null)
    val channelId = MutableStateFlow<String?>(null)

    fun init(context: Context) {
        app = context.applicationContext
        val p = prefs()
        signedIn.value = p.getBoolean("signedIn", false)
        name.value = p.getString("name", null)
        avatar.value = p.getString("avatar", null)
        email.value = p.getString("email", null)
        handle.value = p.getString("handle", null)
        channelId.value = p.getString("channelId", null)
        accounts.value = try {
            val arr = org.json.JSONArray(app.getSharedPreferences("accounts", Context.MODE_PRIVATE).getString("list", "[]"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Saved(
                    o.getString("email"), o.optString("name"), o.optString("avatar").ifEmpty { null },
                    o.optString("handle").ifEmpty { null }, o.optString("channelId").ifEmpty { null },
                )
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun saveAccounts() {
        val arr = org.json.JSONArray()
        accounts.value.forEach {
            arr.put(JSONObject().put("email", it.email).put("name", it.name).put("avatar", it.avatar ?: "")
                .put("handle", it.handle ?: "").put("channelId", it.channelId ?: ""))
        }
        app.getSharedPreferences("accounts", Context.MODE_PRIVATE).edit().putString("list", arr.toString()).apply()
    }

    fun forget(mail: String) {
        accounts.value = accounts.value.filterNot { it.email == mail }
        saveAccounts()
    }

    private fun prefs() = app.getSharedPreferences("account", Context.MODE_PRIVATE)

    /** Called after Google gives a token. Blocking: run on Dispatchers.IO. */
    fun onToken(t: String, accountEmail: String? = null) {
        token = t
        val item = get("channels?part=snippet&mine=true").optJSONArray("items")?.optJSONObject(0)
        val me = item?.optJSONObject("snippet")
        val n = me?.optString("title")?.takeIf { it.isNotBlank() } ?: "Google account"
        val a = me?.optJSONObject("thumbnails")?.let { thumb(it) }
        val h = me?.optString("customUrl")?.takeIf { it.isNotBlank() }?.let { if (it.startsWith("@")) it else "@$it" }
        val id = item?.optString("id")?.takeIf { it.isNotBlank() }
        val mail = accountEmail ?: email.value ?: n
        name.value = n
        avatar.value = a
        handle.value = h
        channelId.value = id
        email.value = mail
        signedIn.value = true
        prefs().edit().putBoolean("signedIn", true).putString("name", n).putString("avatar", a)
            .putString("email", mail).putString("handle", h).putString("channelId", id).apply()
        accounts.value = listOf(Saved(mail, n, a, h, id)) + accounts.value.filterNot { it.email == mail }
        saveAccounts()
    }

    /** Blocking. */
    fun signOut() {
        val t = token
        token = null
        signedIn.value = false
        name.value = null
        avatar.value = null
        handle.value = null
        channelId.value = null
        email.value = null
        prefs().edit().clear().apply()
        if (t != null) {
            try {
                http.newCall(
                    okhttp3.Request.Builder().url("https://oauth2.googleapis.com/revoke")
                        .post(FormBody.Builder().add("token", t).build()).build()
                ).execute().close()
            } catch (_: Throwable) {
            }
        }
    }

    class ApiException(message: String, val needsSignIn: Boolean = false) : IOException(message)

    private fun get(path: String): JSONObject {
        val t = token ?: throw ApiException("Please sign in again.", needsSignIn = true)
        val req = okhttp3.Request.Builder()
            .url("https://www.googleapis.com/youtube/v3/$path")
            .header("Authorization", "Bearer $t")
            .build()
        http.newCall(req).execute().use { r ->
            val body = r.body?.string().orEmpty()
            if (r.code == 401) {
                token = null
                throw ApiException("Your Google sign-in expired. Tap it again.", needsSignIn = true)
            }
            if (!r.isSuccessful) {
                val reason = runCatching {
                    JSONObject(body).getJSONObject("error").getJSONArray("errors").getJSONObject(0).optString("reason")
                }.getOrDefault("")
                throw ApiException(
                    when (reason) {
                        "accessNotConfigured" -> "Turn on \"YouTube Data API v3\" in your Google Cloud project, then try again."
                        "quotaExceeded" -> "Daily limit for your Google project reached. Try again tomorrow."
                        else -> "Google said: HTTP ${r.code} $reason"
                    }
                )
            }
            return JSONObject(body)
        }
    }

    private fun send(path: String, body: JSONObject?, method: String = "POST"): JSONObject {
        val t = token ?: throw ApiException("Please sign in again.", needsSignIn = true)
        val req = okhttp3.Request.Builder()
            .url("https://www.googleapis.com/youtube/v3/$path")
            .header("Authorization", "Bearer $t")
            .method(method, body?.toString()?.toRequestBody("application/json".toMediaTypeOrNull()))
            .build()
        http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (r.code == 401) {
                token = null
                throw ApiException("Your Google sign-in expired. Try again.", needsSignIn = true)
            }
            if (!r.isSuccessful) {
                val reason = runCatching {
                    JSONObject(text).getJSONObject("error").getJSONArray("errors").getJSONObject(0).optString("reason")
                }.getOrDefault("")
                throw ApiException(
                    when (reason) {
                        "insufficientPermissions", "ACCESS_TOKEN_SCOPE_INSUFFICIENT" -> {
                            token = null
                            "Google needs your OK to post comments. Tap send again."
                        }
                        "commentsDisabled" -> "Comments are turned off for this video."
                        "forbidden" -> "YouTube didn't allow this comment. Your account may need a YouTube channel first."
                        "accessNotConfigured" -> "Turn on \"YouTube Data API v3\" in your Google Cloud project, then try again."
                        "quotaExceeded" -> "Daily limit for your Google project reached. Try again tomorrow."
                        else -> "Google said: HTTP ${r.code} $reason"
                    },
                    needsSignIn = reason == "insufficientPermissions" || reason == "ACCESS_TOKEN_SCOPE_INSUFFICIENT",
                )
            }
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        }
    }

    /** One of your playlists on YouTube. */
    data class AccountList(val id: String, val title: String, val thumbnail: String?, val count: Long, val privacy: String)

    /** Your YouTube playlists, newest first. Blocking. */
    fun myPlaylists(): List<AccountList> = all("playlists?part=snippet,contentDetails,status&mine=true", max = 200).map { p ->
        val sn = p.optJSONObject("snippet")
        AccountList(
            id = p.optString("id"),
            title = sn?.optString("title").orEmpty(),
            thumbnail = sn?.optJSONObject("thumbnails")?.let { thumb(it) },
            count = p.optJSONObject("contentDetails")?.optLong("itemCount") ?: 0,
            privacy = p.optJSONObject("status")?.optString("privacyStatus").orEmpty(),
        )
    }

    /** The entry for [videoId] in a playlist, or null if it isn't there. Blocking. */
    fun playlistItemId(playlistId: String, videoId: String): String? =
        get("playlistItems?part=id&maxResults=1&playlistId=$playlistId&videoId=$videoId")
            .optJSONArray("items")?.optJSONObject(0)?.optString("id")?.takeIf { it.isNotEmpty() }

    /** Adds a video to one of your playlists; returns the new entry's id. Blocking. */
    fun addToPlaylist(playlistId: String, videoId: String): String {
        val body = JSONObject().put(
            "snippet", JSONObject().put("playlistId", playlistId)
                .put("resourceId", JSONObject().put("kind", "youtube#video").put("videoId", videoId))
        )
        return send("playlistItems?part=snippet", body).optString("id")
    }

    fun removeFromPlaylist(itemId: String) {
        send("playlistItems?id=$itemId", null, method = "DELETE")
    }

    /** Makes a new playlist in your account. Blocking. */
    fun createPlaylist(title: String, privacy: String): AccountList {
        val body = JSONObject()
            .put("snippet", JSONObject().put("title", title))
            .put("status", JSONObject().put("privacyStatus", privacy))
        val r = send("playlists?part=snippet,status", body)
        return AccountList(r.optString("id"), title, null, 0, privacy)
    }

    private fun commentFrom(s: JSONObject, id: String?, replies: Int = 0) = Yt.Comment(
        id = id,
        author = s.optString("authorDisplayName"),
        authorUrl = s.optString("authorChannelUrl").ifEmpty { null },
        avatar = s.optString("authorProfileImageUrl").ifEmpty { null },
        text = plainText(s.optString("textDisplay")),
        likes = s.optInt("likeCount"),
        date = s.optString("publishedAt").takeIf { it.isNotEmpty() }?.let {
            runCatching { timeAgo(java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli()) }.getOrNull()
        },
        replyCount = replies,
        replies = null,
        pinned = false,
        hearted = false,
    )

    /** Posts a new comment on a video. Blocking. */
    fun postComment(videoId: String, text: String): Yt.Comment {
        val body = JSONObject().put(
            "snippet", JSONObject().put("videoId", videoId)
                .put("topLevelComment", JSONObject().put("snippet", JSONObject().put("textOriginal", text)))
        )
        val r = send("commentThreads?part=snippet", body)
        val top = r.getJSONObject("snippet").getJSONObject("topLevelComment")
        return commentFrom(top.getJSONObject("snippet"), top.optString("id"))
    }

    /** Replies to a comment. Blocking. */
    fun reply(parentId: String, text: String): Yt.Comment {
        val body = JSONObject().put("snippet", JSONObject().put("parentId", parentId).put("textOriginal", text))
        val r = send("comments?part=snippet", body)
        return commentFrom(r.getJSONObject("snippet"), r.optString("id"))
    }

    /** Newest comments first (needs sign-in). Blocking. */
    fun newestComments(videoId: String, pageToken: String? = null): Pair<List<Yt.Comment>, String?> {
        val o = get("commentThreads?part=snippet&order=time&maxResults=30&textFormat=html&videoId=$videoId" + (pageToken?.let { "&pageToken=$it" } ?: ""))
        val items = o.optJSONArray("items") ?: org.json.JSONArray()
        val list = (0 until items.length()).map { i ->
            val sn = items.getJSONObject(i).getJSONObject("snippet")
            val top = sn.getJSONObject("topLevelComment")
            commentFrom(top.getJSONObject("snippet"), top.optString("id"), sn.optInt("totalReplyCount"))
        }
        return list to o.optString("nextPageToken").ifEmpty { null }
    }

    private fun thumb(t: JSONObject): String? =
        listOf("high", "medium", "default").firstNotNullOfOrNull { t.optJSONObject(it)?.optString("url")?.takeIf { u -> u.isNotEmpty() } }

    /** All pages of a list call. */
    private fun all(path: String, max: Int = 1000): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        var pageToken: String? = null
        do {
            val o = get(path + "&maxResults=50" + (pageToken?.let { "&pageToken=$it" } ?: ""))
            val items = o.optJSONArray("items")
            if (items != null) for (i in 0 until items.length()) out.add(items.getJSONObject(i))
            pageToken = o.optString("nextPageToken").takeIf { it.isNotEmpty() }
        } while (pageToken != null && out.size < max)
        return out
    }

    /** Adds your account's subscriptions to Vidflow. Returns how many were new. Blocking. */
    fun importSubscriptions(): Int {
        var added = 0
        for (s in all("subscriptions?part=snippet&mine=true")) {
            val sn = s.optJSONObject("snippet") ?: continue
            val id = sn.optJSONObject("resourceId")?.optString("channelId") ?: continue
            val url = "https://www.youtube.com/channel/$id"
            if (!Library.isSubscribed(url)) {
                Library.toggle(url, sn.optString("title"), sn.optJSONObject("thumbnails")?.let { thumb(it) })
                added++
            }
        }
        return added
    }

    fun playlists(): List<Playlist> = all("playlists?part=snippet,contentDetails&mine=true").map { p ->
        val sn = p.optJSONObject("snippet")
        Playlist(
            url = "https://www.youtube.com/playlist?list=" + p.optString("id"),
            title = sn?.optString("title").orEmpty(),
            thumbnail = sn?.optJSONObject("thumbnails")?.let { thumb(it) },
            owner = name.value ?: "",
            count = p.optJSONObject("contentDetails")?.optLong("itemCount") ?: -1,
        )
    }

    fun liked(): List<Video> = all("videos?part=snippet,contentDetails,statistics&myRating=like", max = 200).map { toVideo(it.optString("id"), it) }

    fun playlistVideos(playlistUrl: String): List<Video> {
        val id = playlistUrl.substringAfter("list=").substringBefore('&')
        return all("playlistItems?part=snippet&playlistId=$id", max = 500).mapNotNull { item ->
            val sn = item.optJSONObject("snippet") ?: return@mapNotNull null
            val vid = sn.optJSONObject("resourceId")?.optString("videoId") ?: return@mapNotNull null
            Video(
                url = "https://www.youtube.com/watch?v=$vid",
                title = sn.optString("title"),
                thumbnail = sn.optJSONObject("thumbnails")?.let { thumb(it) },
                channel = sn.optString("videoOwnerChannelTitle"),
                channelUrl = sn.optString("videoOwnerChannelId").takeIf { it.isNotEmpty() }?.let { "https://www.youtube.com/channel/$it" },
                channelAvatar = null, durationSec = 0, views = -1, uploaded = null, uploadedAtMillis = null,
                live = false, short = false,
            )
        }
    }

    private fun toVideo(id: String, o: JSONObject): Video {
        val sn = o.optJSONObject("snippet")
        return Video(
            url = "https://www.youtube.com/watch?v=$id",
            title = sn?.optString("title").orEmpty(),
            thumbnail = sn?.optJSONObject("thumbnails")?.let { thumb(it) },
            channel = sn?.optString("channelTitle").orEmpty(),
            channelUrl = sn?.optString("channelId")?.takeIf { it.isNotEmpty() }?.let { "https://www.youtube.com/channel/$it" },
            channelAvatar = null,
            durationSec = isoDuration(o.optJSONObject("contentDetails")?.optString("duration").orEmpty()),
            views = o.optJSONObject("statistics")?.optString("viewCount")?.toLongOrNull() ?: -1,
            uploaded = null, uploadedAtMillis = null, live = false, short = false,
        )
    }

    /** "PT1H2M3S" -> seconds. */
    private fun isoDuration(s: String): Long {
        val m = Regex("""PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?""").find(s) ?: return 0
        val (h, mi, se) = m.destructured
        return (h.toLongOrNull() ?: 0) * 3600 + (mi.toLongOrNull() ?: 0) * 60 + (se.toLongOrNull() ?: 0)
    }
}
