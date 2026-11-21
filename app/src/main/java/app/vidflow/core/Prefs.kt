package app.vidflow.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

/** What happens when you leave the app while a video plays. */
enum class LeaveMode(val label: String) {
    PIP("Picture-in-picture"),
    SOUND("Keep playing sound"),
    PAUSE("Pause"),
}

object Prefs {
    private lateinit var app: Context
    val leaveMode = MutableStateFlow(LeaveMode.PIP)

    fun init(context: Context) {
        app = context.applicationContext
        leaveMode.value = runCatching { LeaveMode.valueOf(sp().getString("leave", "PIP")!!) }.getOrDefault(LeaveMode.PIP)
    }

    private fun sp() = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun setLeaveMode(m: LeaveMode) {
        leaveMode.value = m
        sp().edit().putString("leave", m.name).apply()
    }
}

/** Shorts you've already seen, so the feed keeps showing new ones. */
object SeenShorts {
    private lateinit var app: Context
    private val seen = LinkedHashSet<String>()

    fun init(context: Context) {
        app = context.applicationContext
        app.getSharedPreferences("shorts", Context.MODE_PRIVATE).getString("seen", "")!!
            .split('\n').filter { it.isNotBlank() }.forEach { seen.add(it) }
    }

    @Synchronized
    fun has(url: String) = id(url) in seen

    @Synchronized
    fun mark(url: String) {
        val i = id(url)
        if (!seen.add(i)) return
        while (seen.size > 3000) seen.remove(seen.first())
        app.getSharedPreferences("shorts", Context.MODE_PRIVATE).edit().putString("seen", seen.joinToString("\n")).apply()
    }

    @Synchronized
    fun clear() {
        seen.clear()
        app.getSharedPreferences("shorts", Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun id(url: String) = url.substringAfter("v=").substringAfter("/shorts/").substringBefore('&').substringBefore('?')
}
