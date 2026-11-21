package app.vidflow.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.ClosedCaptionOff
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import app.vidflow.core.formatDuration
import app.vidflow.player.PlayerHolder
import kotlinx.coroutines.delay

private val Scrim = Color(0x73000000)

/**
 * The video with Vidflow's own controls on top: tap to show/hide, double-tap a side to skip 10s,
 * swipe down to shrink into the mini player.
 */
@Composable
fun VideoPlayer(
    modifier: Modifier = Modifier,
    fullscreen: Boolean,
    onFullscreen: () -> Unit,
    onMinimize: () -> Unit,
    onNext: (() -> Unit)?,
    onPrevious: (() -> Unit)?,
    onBackground: (() -> Unit)? = null,
) {
    val player = PlayerHolder.player
    val locked by PlayerHolder.locked.collectAsState()
    var ended by remember { mutableStateOf(player.playbackState == Player.STATE_ENDED) }
    var visible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var buffering by remember { mutableStateOf(player.playbackState == Player.STATE_BUFFERING) }
    var position by remember { mutableLongStateOf(player.currentPosition) }
    var duration by remember { mutableLongStateOf(player.duration.coerceAtLeast(0)) }
    var buffered by remember { mutableLongStateOf(player.bufferedPosition) }
    var touchedAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var skipLabel by remember { mutableStateOf<String?>(null) }
    var skipLeft by remember { mutableStateOf(false) }
    var skipCount by remember { mutableIntStateOf(0) }
    var settings by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val chapters by PlayerHolder.chapters.collectAsState()
    val autoplay by PlayerHolder.autoplay.collectAsState()
    val caption by PlayerHolder.caption.collectAsState()
    val captions by PlayerHolder.captions.collectAsState()

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                ended = state == Player.STATE_ENDED
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0)
            buffered = player.bufferedPosition
            // Hide controls after 3 seconds of no touches while playing.
            if (visible && isPlaying && System.currentTimeMillis() - touchedAt > 3000) visible = false
            delay(250)
        }
    }
    LaunchedEffect(skipCount) {
        if (skipLabel != null) {
            delay(700)
            skipLabel = null
        }
    }

    fun touch() {
        touchedAt = System.currentTimeMillis()
    }

    BoxWithConstraints(modifier.background(Color.Black)) {
        val width = constraints.maxWidth
        PlayerSurface(Modifier.fillMaxSize(), controls = false, fit = true)

        // Gestures: taps, double-taps, swipe down.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { visible = !visible; touch() },
                        onDoubleTap = { o ->
                            if (PlayerHolder.locked.value) return@detectTapGestures
                            val left = o.x < width / 2f
                            val delta = if (left) -10_000L else 10_000L
                            player.seekTo((player.currentPosition + delta).coerceIn(0, player.duration.coerceAtLeast(0)))
                            skipLeft = left
                            skipLabel = if (left) "« 10 seconds" else "10 seconds »"
                            skipCount++
                        },
                    )
                }
                .pointerInput(fullscreen, locked) {
                    if (locked) return@pointerInput
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragY > 120.dp.toPx()) {
                                if (fullscreen) onFullscreen() else onMinimize()
                            } else if (dragY < -120.dp.toPx() && !fullscreen) {
                                onFullscreen()
                            }
                            dragY = 0f
                        },
                        onDragCancel = { dragY = 0f },
                    ) { _, dy -> dragY += dy }
                }
        )

        // Skip feedback on the side that was double-tapped.
        skipLabel?.let { label ->
            Box(
                Modifier
                    .align(if (skipLeft) Alignment.CenterStart else Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(140.dp)
                    .background(Color(0x26FFFFFF), RoundedCornerShape(if (skipLeft) 0 else 70, if (skipLeft) 70 else 0, if (skipLeft) 70 else 0, if (skipLeft) 0 else 70)),
                contentAlignment = Alignment.Center,
            ) { Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
        }

        if (buffering) {
            androidx.compose.material3.CircularProgressIndicator(
                color = Color.White, strokeWidth = 3.dp,
                modifier = Modifier.align(Alignment.Center).size(44.dp),
            )
        }

        // Locked: only an unlock button.
        if (locked) {
            AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(56.dp).clip(CircleShape).background(Color(0x99000000))
                            .clickable { PlayerHolder.locked.value = false; touch() },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.LockOpen, contentDescription = "Unlock", tint = Color.White) }
                    Text("Tap to unlock", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        AnimatedVisibility(visible && !locked, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Scrim)) {
                // Top row: minimise · autoplay · captions · settings.
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerIcon(if (fullscreen) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowDown, "Minimise", 30) {
                        if (fullscreen) onFullscreen() else onMinimize()
                    }
                    Spacer(Modifier.weight(1f))
                    // Autoplay switch: a pill with a round knob showing play or pause.
                    Box(
                        Modifier
                            .clip20()
                            .clickable { PlayerHolder.autoplay.value = !autoplay; touch() }
                            .background(if (autoplay) Color(0x80FFFFFF) else Color(0x40FFFFFF))
                            .padding(3.dp)
                            .width(50.dp),
                        contentAlignment = if (autoplay) Alignment.CenterEnd else Alignment.CenterStart,
                    ) {
                        Box(Modifier.size(24.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(
                                if (autoplay) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                contentDescription = "Autoplay", tint = Color.Black, modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    PlayerIcon(
                        if (caption != null) Icons.Filled.ClosedCaption else Icons.Outlined.ClosedCaptionOff,
                        "Captions", 26, enabled = captions.isNotEmpty(),
                    ) {
                        touch()
                        if (caption != null) PlayerHolder.setCaption(null) else PlayerHolder.setCaption(captions.first().first)
                    }
                    PlayerIcon(Icons.Outlined.Settings, "Settings", 26) { settings = true; touch() }
                }

                // Centre: previous · play/pause · next.
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundIcon(Icons.Filled.SkipPrevious, "Previous", 48, enabled = onPrevious != null) { onPrevious?.invoke(); touch() }
                    if (!buffering) {
                        RoundIcon(
                            when {
                                ended -> Icons.Filled.Replay
                                isPlaying -> Icons.Filled.Pause
                                else -> Icons.Filled.PlayArrow
                            },
                            "Play", 64,
                        ) {
                            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                            if (player.isPlaying) player.pause() else player.play()
                            touch()
                        }
                    } else {
                        Spacer(Modifier.size(64.dp))
                    }
                    RoundIcon(Icons.Filled.SkipNext, "Next", 48, enabled = onNext != null) { onNext?.invoke(); touch() }
                }

                // Bottom: time and chapter on the left, full screen on the right, then the seek bar.
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${formatMs(position)} / ${formatMs(duration)}",
                            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.background(Color(0x66000000), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                        PlayerHolder.chapterAt(position)?.let { ch ->
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "• $ch ›",
                                color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .background(Color(0x66000000), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        PlayerIcon(if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen, "Full screen", 28) {
                            onFullscreen(); touch()
                        }
                    }
                    SeekBar(
                        position = position, duration = duration, buffered = buffered,
                        chapters = chapters.map { it.first },
                        onSeek = { player.seekTo(it); position = it; touch() },
                        modifier = Modifier.fillMaxWidth().padding(bottom = if (fullscreen) 18.dp else 0.dp),
                    )
                }
            }
        }

        // When controls are hidden, keep a thin progress line at the bottom.
        if (!visible && !fullscreen && duration > 0) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(position.toFloat() / duration)
                    .height(2.dp)
                    .background(PlayRed)
            )
        }
    }

    if (settings) PlayerSettingsSheet(onBackground = onBackground) { settings = false }
}

private fun Modifier.clip20() = clip(RoundedCornerShape(20.dp))

private fun Modifier.border1() = border(1.5.dp, Color.White, RoundedCornerShape(4.dp))

private fun formatMs(ms: Long): String = formatDuration(ms / 1000).ifEmpty { "0:00" }

/** YouTube-style red for the progress bar. */
val PlayRed = Color(0xFFFF0033)

@Composable
private fun PlayerIcon(icon: ImageVector, label: String, size: Int, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = if (enabled) Color.White else Color(0x66FFFFFF), modifier = Modifier.size(size.dp)) }
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String, size: Int, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color(0x4D000000))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = if (enabled) Color.White else Color(0x59FFFFFF), modifier = Modifier.size((size * 0.62).dp))
    }
}

/** Thin bar with buffered part, chapter gaps and a draggable dot. */
@Composable
private fun SeekBar(
    position: Long,
    duration: Long,
    buffered: Long,
    chapters: List<Long>,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val accent = PlayRed
    val density = LocalDensity.current
    val shown = if (dragging) dragFraction else if (duration > 0) position.toFloat() / duration else 0f
    Box(
        modifier
            .height(24.dp)
            .pointerInput(duration) {
                detectTapGestures { o -> if (duration > 0) onSeek((o.x / size.width * duration).toLong()) }
            }
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> dragging = true; dragFraction = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { dragging = false; if (duration > 0) onSeek((dragFraction * duration).toLong()) },
                    onDragCancel = { dragging = false },
                ) { change, _ -> dragFraction = (change.position.x / size.width).coerceIn(0f, 1f) }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height - with(density) { 6.dp.toPx() }
            val h = with(density) { (if (dragging) 5.dp else 3.dp).toPx() }
            drawRect(Color(0x4DFFFFFF), Offset(0f, y - h / 2), Size(size.width, h))
            if (duration > 0) {
                drawRect(Color(0x80FFFFFF), Offset(0f, y - h / 2), Size(size.width * (buffered.toFloat() / duration).coerceIn(0f, 1f), h))
            }
            drawRect(accent, Offset(0f, y - h / 2), Size(size.width * shown, h))
            // Small gaps where chapters start.
            if (duration > 0) for (c in chapters) {
                if (c <= 0) continue
                val x = size.width * c / duration
                drawRect(Color.Black, Offset(x - 1.5f, y - h / 2), Size(3f, h))
            }
            drawCircle(accent, radius = with(density) { (if (dragging) 9.dp else 6.dp).toPx() }, center = Offset(size.width * shown, y))
        }
    }
}

/** Settings sheet, laid out like YouTube's: quality, speed, captions, lock, background, more. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsSheet(onBackground: (() -> Unit)? = null, onClose: () -> Unit) {
    var page by remember { mutableStateOf("main") }
    val qualities by PlayerHolder.qualities.collectAsState()
    val quality by PlayerHolder.quality.collectAsState()
    val captions by PlayerHolder.captions.collectAsState()
    val caption by PlayerHolder.caption.collectAsState()
    val looping by PlayerHolder.looping.collectAsState()
    val autoplay by PlayerHolder.autoplay.collectAsState()
    val ambient by PlayerHolder.ambient.collectAsState()
    val sleep by PlayerHolder.sleep.collectAsState()
    var speed by remember { mutableFloatStateOf(PlayerHolder.player.playbackParameters.speed) }
    fun speedText(s: Float) = if (s == 1f) "1x" else "${s.toString().removeSuffix(".0")}x"

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = Color(0xFF212121),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp).verticalScroll(rememberScrollState())) {
            when (page) {
                "quality" -> {
                    SheetTitle("Quality for current video") { page = "main" }
                    qualities.forEach { h ->
                        CheckRow("${h}p" + if (h >= 1080) " HD" else "", h == quality) { PlayerHolder.changeQuality(h); onClose() }
                    }
                }
                "speed" -> {
                    SheetTitle("Playback speed") { page = "main" }
                    listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).forEach { s ->
                        CheckRow(if (s == 1f) "Normal" else speedText(s), s == speed) { speed = s; PlayerHolder.setSpeed(s); onClose() }
                    }
                }
                "captions" -> {
                    SheetTitle("Captions") { page = "main" }
                    CheckRow("Off", caption == null) { PlayerHolder.setCaption(null); onClose() }
                    captions.forEach { (tag, name) ->
                        CheckRow(name.ifBlank { tag }, caption == tag) { PlayerHolder.setCaption(tag); onClose() }
                    }
                }
                "sleep" -> {
                    SheetTitle("Sleep timer") { page = "more" }
                    listOf(0 to "Off", 10 to "10 minutes", 15 to "15 minutes", 20 to "20 minutes", 30 to "30 minutes", 45 to "45 minutes", 60 to "1 hour", -1 to "End of video")
                        .forEach { (m, label) -> CheckRow(label, sleep == m) { PlayerHolder.setSleep(m); onClose() } }
                }
                "more" -> {
                    SheetTitle("More") { page = "main" }
                    SwitchRow(Icons.Outlined.RepeatOne, "Loop video", looping) { PlayerHolder.setLoop(it) }
                    SwitchRow(Icons.Outlined.PlayCircleOutline, "Autoplay next video", autoplay) { PlayerHolder.autoplay.value = it }
                    SwitchRow(Icons.Outlined.AutoAwesome, "Ambient mode", ambient) { PlayerHolder.setAmbient(it) }
                    SheetRow(
                        Icons.Outlined.Bedtime, "Sleep timer",
                        when (sleep) { 0 -> "Off"; -1 -> "End of video"; else -> "$sleep min" },
                    ) { page = "sleep" }
                }
                else -> {
                    SheetRow(Icons.Outlined.Tune, "Quality", if (quality > 0) "${quality}p" else "Auto") { page = "quality" }
                    SheetRow(Icons.Outlined.SlowMotionVideo, "Playback speed", speedText(speed)) { page = "speed" }
                    SheetRow(
                        Icons.Outlined.ClosedCaption, "Captions",
                        if (captions.isEmpty()) "Unavailable" else captions.firstOrNull { it.first == caption }?.second ?: "Off",
                        enabled = captions.isNotEmpty(),
                    ) { page = "captions" }
                    SheetRow(Icons.Outlined.Lock, "Lock screen", null, arrow = false) { PlayerHolder.locked.value = true; onClose() }
                    if (onBackground != null) {
                        SheetRow(Icons.Outlined.Headphones, "Play in background", null, arrow = false) { onClose(); onBackground() }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), color = Color(0x33FFFFFF))
                    SheetRow(Icons.Outlined.Settings, "More", null) { page = "more" }
                }
            }
        }
    }
}

@Composable
private fun SheetTitle(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onBack).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        Spacer(Modifier.width(16.dp))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SheetRow(icon: ImageVector, label: String, value: String?, enabled: Boolean = true, arrow: Boolean = true, onClick: () -> Unit) {
    val color = if (enabled) Color.White else Color(0x80FFFFFF)
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 22.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(20.dp))
        Text(label, color = color, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (value != null) Text(value, color = Color(0xB3FFFFFF), fontSize = 15.sp)
        if (arrow) Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xB3FFFFFF))
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(46.dp)) {
            if (checked) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
        }
        Text(label, fontSize = 16.sp)
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 22.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(20.dp))
        Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF212121), checkedTrackColor = Color.White,
                uncheckedThumbColor = Color(0xFFAAAAAA), uncheckedTrackColor = Color(0xFF3A3A3A),
            ),
        )
    }
}
