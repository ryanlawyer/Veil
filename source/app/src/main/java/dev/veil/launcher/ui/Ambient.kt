package dev.veil.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.BiasAlignment
import dev.veil.launcher.data.Lyrics
import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import dev.veil.launcher.data.AudioHub
import kotlin.math.exp
import kotlin.math.sin
import dev.veil.launcher.viz.VizSettings
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.produceState
import androidx.compose.ui.text.TextStyle
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.veil.launcher.data.NowPlaying
import dev.veil.launcher.data.WatchItem
import dev.veil.launcher.data.Weather
import kotlinx.coroutines.delay
import java.text.NumberFormat

/**
 * Veil Ambient: one calm full-screen mode with two faces.
 *  - Music playing → blurred, slowly breathing album art with the track in large type.
 *  - Otherwise → a slow, drifting slideshow of your Continue Watching artwork.
 * Both share the same frame: clock + weather top right, Veil Shield line bottom right.
 */
@Composable
fun AmbientScreen(
    track: NowPlaying.Track?,
    items: List<WatchItem>,
    weather: Weather.Now?,
    blocked: Int?,
    dnsOn: Boolean,
    visualizer: Boolean = false,
    weeklyBlocked: Int? = null,
    seasonal: Boolean = true,
    /** Show synced lyrics in Veil music mode (when the song has them). */
    lyrics: Boolean = true,
) {
    val season = if (seasonal) currentSeason() else Season.NONE
    val sunday = java.time.LocalDate.now().dayOfWeek == java.time.DayOfWeek.SUNDAY
    // "Clean screen" (visualizer only): hide all text and icons, but reveal them for 5 s when a song starts
    // (and when the visualizer first appears).
    val clean = visualizer && track != null && VizSettings.clean
    val songKey = track?.let { "${it.pkg}|${it.title}|${it.artist}" }
    var reveal by remember { mutableStateOf(true) }
    LaunchedEffect(songKey, clean) {
        reveal = true
        if (clean) { delay(5_000); reveal = false }
    }
    val chrome by animateFloatAsState(if (!clean || reveal) 1f else 0f, tween(900), label = "chrome")

    Box(Modifier.fillMaxSize().background(Veil.Bg)) {
        // Switch smoothly between music and artwork faces.
        AnimatedContent(
            targetState = (track != null) to visualizer,
            transitionSpec = { fadeIn(tween(1200)) togetherWith fadeOut(tween(1200)) },
            label = "ambient-face",
        ) { (music, viz) ->
            when {
                music && track != null && viz -> VisualizerFace(track, chromeAlpha = chrome)
                music && track != null -> MusicFace(track, lyrics)
                else -> ArtworkFace(items)
            }
        }

        // Seasonal touch (never over the visualizer)
        val showingViz = visualizer && track != null
        if (!showingViz) {
            SeasonalLayer(season)
        }

        // Shared frame
        Row(
            Modifier.align(Alignment.TopStart).padding(start = 48.dp, top = 30.dp).alpha(chrome),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(VeilIcons.Lock, contentDescription = null, modifier = Modifier.size(12.dp), tint = Veil.Text.copy(alpha = 0.7f))
            Spacer(Modifier.width(6.dp))
            Text("veil", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Veil.Text.copy(alpha = 0.7f))
        }
        Row(
            Modifier.align(Alignment.TopEnd).padding(end = 48.dp, top = 24.dp).alpha(chrome),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Clock(fontSize = 30.sp)
            weather?.let { w ->
                Spacer(Modifier.width(14.dp))
                Icon(weatherIcon(w), contentDescription = null, modifier = Modifier.size(22.dp), tint = Veil.Text.copy(alpha = 0.9f))
                Spacer(Modifier.width(6.dp))
                Text("${w.tempC}°", fontSize = 24.sp, color = Veil.Text.copy(alpha = 0.9f))
            }
        }
        Row(
            Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 30.dp).alpha(chrome),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (dnsOn) VeilIcons.ShieldOn else VeilIcons.ShieldOff, contentDescription = null,
                modifier = Modifier.size(12.dp), tint = if (dnsOn) Veil.Accent else Veil.Text.copy(alpha = 0.5f),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                when {
                    !dnsOn -> "Veil Shield is off"
                    // Sundays: the weekly report, in the same quiet line.
                    sunday && weeklyBlocked != null ->
                        "Veil Shield · ${NumberFormat.getIntegerInstance().format(weeklyBlocked)} trackers blocked this week"
                    blocked == null -> "Veil Shield is up"
                    else -> "Veil Shield · ${NumberFormat.getIntegerInstance().format(blocked)} trackers blocked today"
                },
                fontSize = 10.sp, color = Veil.Text.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun MusicFace(track: NowPlaying.Track, lyricsOn: Boolean) {
    val breathe = rememberInfiniteTransition(label = "breathe")
    // Read only at draw time (in graphicsLayer blocks): reading these during composition rebuilt the whole
    // music screen, lyrics included, on every frame.
    val scale = breathe.animateFloat(
        1f, 1.03f, infiniteRepeatable(tween(7000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "scale",
    )
    val drift = breathe.animateFloat(
        1.15f, 1.3f, infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Reverse), label = "drift",
    )
    val lyr = if (lyricsOn) rememberLyrics(track) else Lyrics.State.None
    // Lyrics layout once lyrics are found. While the next song's lyrics load it stays put (up to 4 s), so
    // back-to-back songs with lyrics don't bounce between layouts; no lyrics / offline → the classic layout.
    var lyricLayout by remember { mutableStateOf(false) }
    LaunchedEffect(lyr) {
        when {
            lyr.hasLines() -> lyricLayout = true
            lyr is Lyrics.State.Loading -> { delay(4_000); lyricLayout = false }
            else -> lyricLayout = false
        }
    }
    Box(Modifier.fillMaxSize()) {
        // Blurred album art wash: blurred once into a tiny image and scaled up (a live full-screen blur
        // was the most expensive thing in music mode, and two of them overlapped on every song change).
        val soft = rememberSoftArt(track.artLarge)
        Crossfade(targetState = soft, animationSpec = tween(1500), label = "wash") { art ->
            if (art != null) {
                Image(
                    art, contentDescription = null, contentScale = ContentScale.Crop,
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.Low,
                    modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = drift.value; scaleY = drift.value },
                )
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFF1E2430)))
            }
        }
        // Darkening + left-side fade, as one tiny pre-rendered strip stretched over the screen.
        val shade = remember(Veil.Bg) { SoftShapes.sideShade(Veil.Bg) }
        Box(
            Modifier.fillMaxSize().drawBehind {
                drawRect(Color.Black.copy(alpha = 0.42f))
                drawImage(
                    shade, dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.Low,
                )
            },
        )
        EdgeGlow(track.artLarge)

        AnimatedContent(
            targetState = lyricLayout,
            transitionSpec = { fadeIn(tween(900, delayMillis = 150)) togetherWith fadeOut(tween(500)) },
            label = "music-layout",
        ) { withLyrics ->
            if (withLyrics) MusicWithLyrics(track, lyr, scale) else MusicClassic(track, scale)
        }
    }
}

/** The classic music layout: big cover, big title. */
@Composable
private fun MusicClassic(track: NowPlaying.Track, scale: androidx.compose.runtime.State<Float>) {
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier.align(Alignment.CenterStart).padding(start = 96.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(track, 230.dp, scale)
            Spacer(Modifier.width(40.dp))
            Column(Modifier.widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayingLabel(track)
                AnimatedContent(
                    targetState = track.title to track.artist,
                    transitionSpec = { fadeIn(tween(700)) togetherWith fadeOut(tween(500)) },
                    label = "track-text",
                ) { (title, artist) ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            title, fontSize = 44.sp, lineHeight = 46.sp, fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-1.4).sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        artist?.let { Text(it, fontSize = 20.sp, color = Veil.Text.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
                SongProgress(track)
            }
        }
    }
}

/** With lyrics: cover and song details stacked on the left, the lyrics flowing on the right. */
@Composable
private fun MusicWithLyrics(track: NowPlaying.Track, lyr: Lyrics.State, scale: androidx.compose.runtime.State<Float>) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.CenterStart).padding(start = 80.dp).width(250.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Cover(track, 190.dp, scale)
            Spacer(Modifier.height(10.dp))
            PlayingLabel(track)
            AnimatedContent(
                targetState = track.title to track.artist,
                transitionSpec = { fadeIn(tween(700)) togetherWith fadeOut(tween(500)) },
                label = "track-text-small",
            ) { (title, artist) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        title, fontSize = 24.sp, lineHeight = 27.sp, fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.6).sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    artist?.let { Text(it, fontSize = 14.sp, color = Veil.Text.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
            SongProgress(track, width = 250.dp, top = 10.dp)
        }
        Box(
            Modifier.align(Alignment.CenterEnd).padding(end = 60.dp, top = 84.dp, bottom = 76.dp)
                .fillMaxHeight().width(480.dp),
        ) {
            val song = (lyr as? Lyrics.State.Ready)?.song
            Crossfade(targetState = song, animationSpec = tween(700), label = "lyrics") { s ->
                if (s != null) {
                    val pos = rememberPlaybackPosition(track)
                    LyricsScroller(s, pos, LyricsLook.PANEL, Modifier.fillMaxSize())
                } else {
                    LyricsWaiting(Modifier.fillMaxSize())
                }
            }
        }
    }
}

/** While the next song's lyrics load: three softly breathing dots where the lyrics will appear. */
@Composable
internal fun LyricsWaiting(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "wait")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "phase")
    Box(modifier) {
        Row(Modifier.align(BiasAlignment(-1f, -0.28f)), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (k in 0..2) {
                val a = 0.25f + 0.5f * (0.5f + 0.5f * kotlin.math.sin((phase - k * 0.18f) * 6.2832f))
                Box(Modifier.size(11.dp).graphicsLayer { alpha = a }.clip(CircleShape).background(Veil.Text))
            }
        }
    }
}

@Composable
private fun Cover(track: NowPlaying.Track, size: androidx.compose.ui.unit.Dp, scale: androidx.compose.runtime.State<Float>) {
    Crossfade(targetState = track.artLarge, animationSpec = tween(1200), label = "cover") { art ->
        // The shadow stays still and only the cover breathes (a resizing shadow is recalculated every frame).
        Box(Modifier.size(size).shadow(40.dp, RoundedCornerShape(26.dp)).clip(RoundedCornerShape(26.dp))) {
            Box(
                Modifier.fillMaxSize().graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                    .clip(RoundedCornerShape(26.dp)).background(Veil.GlassStrong),
                contentAlignment = Alignment.Center,
            ) {
                if (art != null) Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Icon(VeilIcons.Note, contentDescription = null, modifier = Modifier.size(48.dp), tint = Veil.Text)
            }
        }
    }
}

@Composable
private fun PlayingLabel(track: NowPlaying.Track) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(if (track.playing) Veil.Accent else Veil.Text.copy(alpha = 0.4f)))
        Spacer(Modifier.width(8.dp))
        Text(
            if (track.playing) "NOW PLAYING" else "PAUSED",
            fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, color = Veil.Accent,
        )
    }
}

/** Slim lime progress bar with elapsed / remaining time. Hidden when the player doesn't report a length. */
@Composable
internal fun SongProgress(track: NowPlaying.Track, width: androidx.compose.ui.unit.Dp = 400.dp, top: androidx.compose.ui.unit.Dp = 22.dp) {
    if (track.durationMs <= 0) return
    // Times: a few updates a second is plenty.
    val pos by produceState(track.positionNow(), track) {
        while (true) {
            value = track.positionNow()
            delay(250)
        }
    }
    // The bar: drawn straight from the playback position every frame (smooth, and no re-layout).
    val frame = remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    LaunchedEffect(track) { while (true) androidx.compose.runtime.withFrameMillis { frame.longValue = it } }
    val accent = Veil.Accent
    Column(Modifier.width(width).padding(top = top)) {
        Box(
            // Own layer: redrawing the bar every frame must not re-record the rest of the screen.
            Modifier.fillMaxWidth().height(4.dp).graphicsLayer().drawBehind {
                frame.longValue // redraw each frame
                val f = (track.positionNow().toFloat() / track.durationMs).coerceIn(0f, 1f)
                val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2, size.height / 2)
                drawRoundRect(Color.White.copy(alpha = 0.18f), cornerRadius = r)
                if (f > 0f) drawRoundRect(accent, size = androidx.compose.ui.geometry.Size(maxOf(size.width * f, size.height), size.height), cornerRadius = r)
            },
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val style = TextStyle(fontFeatureSettings = "tnum")
            Text(clock(pos), fontSize = 11.sp, color = Veil.Text.copy(alpha = 0.65f), style = style)
            Text("-" + clock(track.durationMs - pos), fontSize = 11.sp, color = Veil.Text.copy(alpha = 0.65f), style = style)
        }
    }
}

private fun clock(ms: Long): String {
    val s = (ms.coerceAtLeast(0) / 1000).toInt()
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun ArtworkFace(items: List<WatchItem>) {
    val withArt = remember(items) { items.filter { it.backdrop != null } }
    var index by remember(withArt) { mutableIntStateOf(0) }
    LaunchedEffect(withArt) {
        while (withArt.size > 1) {
            delay(14_000)
            index = (index + 1) % withArt.size
        }
    }
    val item = withArt.getOrNull(index)
    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = item, animationSpec = tween(2500), label = "slides") { it ->
            if (it?.backdrop != null) {
                // Slow "Ken Burns" drift for each slide.
                val zoom = remember { Animatable(1f) }
                LaunchedEffect(Unit) { zoom.animateTo(1.09f, tween(17_000, easing = LinearEasing)) }
                AsyncImage(
                    model = it.backdrop, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer(
                        scaleX = zoom.value, scaleY = zoom.value,
                        translationX = (zoom.value - 1f) * -260f,
                    ),
                )
            } else {
                Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF232A36), Veil.Bg), radius = 1600f)))
            }
        }
        SlideshowShade(Veil.Bg)

        AnimatedContent(
            targetState = item,
            transitionSpec = { fadeIn(tween(1500)) togetherWith fadeOut(tween(800)) },
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 56.dp),
            label = "slide-text",
        ) { it ->
            if (it != null) {
                Column(Modifier.widthIn(max = 620.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        it.spotlight?.uppercase() ?: if (it.upNext) "UP NEXT" else "CONTINUE",
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, color = Veil.Accent,
                    )
                    TitleOrLogo(it, fontSize = 40.sp, maxLogoHeight = 120.dp)
                    Text(it.heroLine, fontSize = 14.sp, color = Veil.Text.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Text("Private by design.", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
            }
        }
    }
}

/**
 * Ambilight-style glow around the screen edges in the album art's two strongest colours, pulsing softly
 * with the music (beats from the shared [AudioHub]). Without audio access it just breathes slowly.
 */
@Composable
private fun EdgeGlow(art: ImageBitmap?) {
    val context = LocalContext.current
    val fallback = Veil.Accent
    val colors by androidx.compose.runtime.produceState(emptyList<Color>(), art) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            art?.let { ArtColor.extract(it).map(ArtColor::asGlow) } ?: emptyList()
        }
    }
    val a by animateColorAsState(colors.getOrNull(0) ?: fallback, tween(1800), label = "glow-a")
    val b by animateColorAsState(colors.getOrNull(1) ?: colors.getOrNull(0) ?: fallback, tween(1800), label = "glow-b")

    val audio = remember { AudioHub.acquire(context) }
    DisposableEffect(Unit) { onDispose { AudioHub.release() } }
    val energy = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(audio) {
        var e = 0f
        var lastCheck = 0L
        while (true) {
            withFrameMillis { }
            val now = SystemClock.elapsedRealtime()
            val beat = exp(-(now - audio.lastBeatAt) / 280f)
            val bass = (audio.bassRel - 1f).coerceIn(0f, 1.2f) / 1.2f
            val breathe = 0.08f * (1f + sin(now / 1900f)) // so it's never completely still
            val target = (0.5f * beat + 0.3f * bass + 0.25f * audio.level + breathe).coerceIn(0f, 1f)
            // Fast attack, slow release: a pulse, not a flicker.
            e += (target - e) * if (target > e) 0.45f else 0.06f
            energy.floatValue = e
            if (now - lastCheck > 2_000) { lastCheck = now; audio.restartIfSilent() }
        }
    }

    val tintA = remember(a) { androidx.compose.ui.graphics.ColorFilter.tint(a, BlendMode.Modulate) }
    val tintB = remember(b) { androidx.compose.ui.graphics.ColorFilter.tint(b, BlendMode.Modulate) }
    val tintAB = remember(a, b) { androidx.compose.ui.graphics.ColorFilter.tint(lerp(a, b, 0.5f), BlendMode.Modulate) }
    Box(
        // Own layer: the glow changes every frame; without this the whole music screen was re-recorded.
        Modifier.fillMaxSize().graphicsLayer().drawBehind {
            val e = energy.floatValue
            val alpha = 0.22f + 0.38f * e
            // Light spilling in from just off-screen at each side, like a bias light on the wall behind.
            // Each spill is one small pre-rendered soft spot, tinted and stretched (live gradients this big
            // cost 15+ ms a frame on this GPU; a stretched image costs almost nothing).
            fun spill(tint: androidx.compose.ui.graphics.ColorFilter, cx: Float, cy: Float, rx: Float, ry: Float, k: Float) {
                drawImage(
                    SoftShapes.spot,
                    srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                    srcSize = androidx.compose.ui.unit.IntSize(SoftShapes.SPOT, SoftShapes.SPOT),
                    dstOffset = androidx.compose.ui.unit.IntOffset((cx - rx).toInt(), (cy - ry).toInt()),
                    dstSize = androidx.compose.ui.unit.IntSize((2 * rx).toInt(), (2 * ry).toInt()),
                    alpha = (alpha * k).coerceIn(0f, 1f),
                    colorFilter = tint,
                    blendMode = BlendMode.Plus,
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.Low,
                )
            }
            val w = size.width; val h = size.height
            val reach = 1f + 0.22f * e
            spill(tintA, -w * 0.04f, h * 0.5f, w * 0.2f * reach, h * 0.62f, 1f)
            spill(tintB, w * 1.04f, h * 0.5f, w * 0.2f * reach, h * 0.62f, 1f)
            // A faint wash low in the middle, blending the two colours together.
            spill(tintAB, w * 0.5f, h * 1.12f, w * 0.42f * reach, h * 0.3f, 0.45f)
        },
    )
}

/** Small pre-rendered soft shapes, stretched at draw time (cheap on this GPU, and dithered: no banding). */
private object SoftShapes {
    const val SPOT = 128

    /** A white soft spot: the same falloff as the old live gradient (1 → 0.55 → 0.15 → 0). */
    val spot: ImageBitmap by lazy {
        val rnd = kotlin.random.Random(3)
        val c = (SPOT - 1) / 2f
        val px = IntArray(SPOT * SPOT) { i ->
            val x = (i % SPOT - c) / c; val y = (i / SPOT - c) / c
            val r = kotlin.math.sqrt(x * x + y * y)
            val a = when {
                r < 0.35f -> 1f - (1f - 0.55f) * (r / 0.35f)
                r < 0.7f -> 0.55f - (0.55f - 0.15f) * ((r - 0.35f) / 0.35f)
                r < 1f -> 0.15f * (1f - (r - 0.7f) / 0.3f)
                else -> 0f
            }
            val A = (a * 255f + rnd.nextFloat() - 0.5f).toInt().coerceIn(0, 255)
            (A shl 24) or 0xFFFFFF
        }
        android.graphics.Bitmap.createBitmap(px, SPOT, SPOT, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    /** The left-side fade in the theme's background colour (55% at the left edge → clear at 60%). */
    fun sideShade(bg: Color): ImageBitmap {
        val w = 256
        val rnd = kotlin.random.Random(5)
        val px = IntArray(w * 2) { i ->
            val x = (i % w) / (w - 1f)
            val a = if (x < 0.6f) 0.55f * (1f - x / 0.6f) else 0f
            val A = (a * 255f + rnd.nextFloat() - 0.5f).toInt().coerceIn(0, 255)
            val r = (bg.red * 255).toInt(); val g = (bg.green * 255).toInt(); val b = (bg.blue * 255).toInt()
            (A shl 24) or (r shl 16) or (g shl 8) or b
        }
        return android.graphics.Bitmap.createBitmap(px, w, 2, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}

/**
 * The slideshow's darkening (top and bottom fades + left-side fade), pre-rendered once into a dithered image.
 * As two live full-screen gradients it was redrawn every frame of the slow zoom, which is expensive on this
 * GPU. Until the image is ready (a few ms after Ambient opens) the live gradients are drawn, so the look
 * never changes.
 */
@Composable
private fun SlideshowShade(bg: Color) {
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val img by androidx.compose.runtime.produceState<ImageBitmap?>(null, size, bg) {
        if (size.width > 0) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            bakeSlideshowShade(size.width / 2, size.height / 2, bg)
        }
    }
    Box(
        Modifier.fillMaxSize().onSizeChanged { size = it }.drawBehind {
            val i = img
            if (i != null) {
                drawImage(
                    i, dstSize = androidx.compose.ui.unit.IntSize(this.size.width.toInt(), this.size.height.toInt()),
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.Low,
                )
            } else {
                drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.45f), 0.25f to Color.Transparent, 0.65f to Color.Transparent, 1f to bg.copy(alpha = 0.92f)))
                drawRect(Brush.horizontalGradient(0f to bg.copy(alpha = 0.6f), 0.5f to Color.Transparent))
            }
        },
    )
}

private val slideshowShadeCache = HashMap<String, ImageBitmap>()

/**
 * Painted by Android's own 2D engine with the exact gradients it replaces (same colours, same stops), so it
 * matches the live version; dithering is switched on to keep the fades smooth.
 */
private fun bakeSlideshowShade(w: Int, h: Int, bg: Color): ImageBitmap {
    val key = "$w x $h ${bg.value}"
    synchronized(slideshowShadeCache) { slideshowShadeCache[key]?.let { return it } }
    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val paint = android.graphics.Paint().apply { isDither = true }
    fun argb(c: Color) = c.toArgb()
    paint.shader = android.graphics.LinearGradient(
        0f, 0f, 0f, h.toFloat(),
        intArrayOf(argb(Color.Black.copy(alpha = 0.45f)), argb(Color.Transparent), argb(Color.Transparent), argb(bg.copy(alpha = 0.92f))),
        floatArrayOf(0f, 0.25f, 0.65f, 1f), android.graphics.Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    paint.shader = android.graphics.LinearGradient(
        0f, 0f, w.toFloat(), 0f,
        intArrayOf(argb(bg.copy(alpha = 0.6f)), argb(Color.Transparent)),
        floatArrayOf(0f, 0.5f), android.graphics.Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    val img = bmp.asImageBitmap()
    synchronized(slideshowShadeCache) { slideshowShadeCache[key] = img }
    return img
}
