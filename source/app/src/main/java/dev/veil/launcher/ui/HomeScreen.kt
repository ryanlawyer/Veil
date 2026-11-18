@file:OptIn(ExperimentalTvMaterial3Api::class)

package dev.veil.launcher.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.veil.launcher.AppSheet
import dev.veil.launcher.LauncherState
import dev.veil.launcher.Screen
import dev.veil.launcher.data.WatchItem
import kotlinx.coroutines.delay

private val EDGE = 48.dp
/** Service tiles: 16:9 like the TV banners they show. All apps is a square of the same height. */
private val TILE_W = 220.dp
private val TILE_H = 124.dp
/** How long each spotlight title stays up before the next one, while a service tile is focused. */
private const val SPOTLIGHT_MS = 15_000L

@Composable
fun HomeScreen(state: LauncherState) {
    // Continue Watching only exists with a Trakt sign-in. Without one, Home is the service tiles.
    val showRow = state.connected
    val firstFocus = remember { FocusRequester() }
    val requesters = remember { HashMap<String, FocusRequester>() }
    val focusKey: (String) -> FocusRequester = { k -> requesters.getOrPut(k) { FocusRequester() } }

    // On start and each time Home is pressed: go back to the item last used on Home (e.g. the app you
    // launched); fall back to the first tile (or the first Continue Watching pill).
    LaunchedEffect(state.homeTick, state.items.isEmpty(), state.connected) {
        delay(60)
        val last = state.lastFocusKey?.let { requesters[it] }
        val ok = last != null && runCatching { last.requestFocus() }.isSuccess
        if (!ok) runCatching { firstFocus.requestFocus() }
    }

    // The hero: the focused service's spotlight (what's popular there, one title at a time), or the
    // focused Continue Watching item. Each service keeps its place in its slideshow.
    // Before focus lands (and whenever it is up in the top bar), the first tile stands in.
    val focusedApp = state.focusedApp ?: if (showRow) null else state.pinnedApps.firstOrNull()?.pkg
    val spot = focusedApp?.let { state.spotlight[it] }?.takeIf { it.isNotEmpty() }
    val spotIndex = remember { mutableStateMapOf<String, Int>() }
    LaunchedEffect(focusedApp, spot?.size) {
        val n = spot?.size ?: return@LaunchedEffect
        if (focusedApp == null) return@LaunchedEffect
        while (n > 1) {
            delay(SPOTLIGHT_MS)
            spotIndex[focusedApp] = ((spotIndex[focusedApp] ?: 0) + 1) % n
        }
    }
    val hero = when {
        focusedApp != null -> spot?.getOrNull((spotIndex[focusedApp] ?: 0) % spot.size)
        showRow -> state.focused ?: state.items.firstOrNull()
        else -> null
    }
    val appLabel = focusedApp?.let { pkg -> state.pinnedApps.firstOrNull { it.pkg == pkg }?.label }

    // Colour and frost come from the small card image (the same picture as the 4K backdrop): both are
    // shrunk to ~100-200 px anyway, so decoding the 4K original three times per card change was wasted.
    val smallArt = hero?.thumb ?: hero?.backdrop
    ArtTintEffect(smallArt, active = !state.veilMenuOpen)

    // Check title logos ahead of time, so the hero can show them without a text-to-logo swap.
    val context = LocalContext.current
    LaunchedEffect(state.items, state.spotlight) {
        (state.items + state.spotlight.values.flatten()).mapNotNull { it.logo }
            .forEach { runCatching { LogoCheck.check(context, it) } }
    }

    // Pre-blurred copy of the current backdrop, ready before any menu opens.
    var frostImg by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(smallArt) {
        frostImg = smallArt?.let { runCatching { FrostCache.get(context, it) }.getOrNull() }
    }

    // Frosted glass: while a menu is open, the artwork behind it turns soft and the page fades back, so
    // the menu's text always sits on a calm surface. Dropdown panels (Veil menu, privacy) leave the top
    // bar alone; full menus (hold menus, pickers) fade it too.
    val dialogOpen = state.sheet != null || state.privacySheet || state.musicSheet || state.watchSheet != null ||
        state.weatherPicker || state.vizSettingsOpen
    val panelOpen = state.veilMenuOpen || (state.privacyFocused && state.dnsAvailable)
    // Read only at draw time (graphicsLayer blocks): reading these during composition rebuilt the whole of
    // Home on every frame of the 0.26 s animation whenever a menu opened or closed.
    val frosted = dialogOpen || panelOpen
    val frost = animateFloatAsState(if (frosted) 1f else 0f, tween(260), label = "frost")
    val frostTop = animateFloatAsState(if (dialogOpen) 1f else 0f, tween(260), label = "frost-top")

    Box(Modifier.fillMaxSize().background(Veil.Bg)) {
        // The drift also holds still while Veil Ambient covers Home (nobody can see it, and it cost frames).
        // (Lite mode: no drift at all; on slow TVs redrawing the full-screen artwork costs frames.)
        Backdrop(hero?.backdrop, paused = frosted || state.ambient || dev.veil.launcher.BuildConfig.LITE_MODE)
        // Frost: the pre-blurred artwork fades in over the real one (cheap; a live blur is too slow here).
        val fi = frostImg
        if (fi != null) {
            androidx.compose.foundation.Image(
                fi, contentDescription = null, contentScale = ContentScale.Crop,
                filterQuality = androidx.compose.ui.graphics.FilterQuality.Low,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = frost.value }, // invisible (skipped) at 0
            )
        }
        // Dark fades + artwork-colour glow, pre-rendered as one dithered image (see HomeShade: the GPU is
        // too slow for live full-screen gradients while the backdrop drifts).
        HomeShade(bg = ThemeState.current.bg, glow = ArtTint.target)

        Column(Modifier.fillMaxSize().padding(top = 26.dp, bottom = 22.dp)) {
            TopBar(state, modifier = Modifier.zIndex(1f).recede(frostTop).padding(horizontal = EDGE))
            Spacer(Modifier.weight(1f))
            Column(Modifier.recede(frost)) {
                Hero(hero, appLabel, state, Modifier.padding(horizontal = EDGE))
                Spacer(Modifier.height(24.dp))
                if (showRow) {
                    ContinueRow(state, firstFocus, focusKey)
                    Spacer(Modifier.height(14.dp))
                }
                ServiceRow(state, focusKey, firstFocus = if (showRow) null else firstFocus)
            }
        }
    }
}

@Composable
private fun Backdrop(url: String?, paused: Boolean = false) {
    val context = LocalContext.current
    Crossfade(targetState = url, animationSpec = tween(600), label = "backdrop") { u ->
        if (u != null) {
            // Rest on a show for a few seconds and its artwork starts a slow pan and zoom (Apple TV style),
            // drifting back and forth until you move on.
            // (Holds still while a menu is open, so the blurred page behind it isn't redrawn every frame.)
            // Updated at 30 fps (every other screen refresh): it moves far less than a pixel per frame, so
            // this looks the same and halves the work while Home sits idle.
            val drift = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
            val rising = remember { booleanArrayOf(true) }
            LaunchedEffect(paused) {
                if (paused) return@LaunchedEffect
                if (drift.floatValue == 0f) delay(3_000)
                while (true) {
                    val goal = if (rising[0]) 1f else 0f
                    val from = drift.floatValue
                    val dur = (24_000 * kotlin.math.abs(goal - from)).coerceAtLeast(1f)
                    val start = withFrameMillis { it }
                    while (true) {
                        delay(30)
                        val now = withFrameMillis { it }
                        val f = ((now - start) / dur).coerceIn(0f, 1f)
                        drift.floatValue = from + (goal - from) * FastOutSlowInEasing.transform(f)
                        if (f >= 1f) break
                    }
                    rising[0] = !rising[0]
                }
            }
            AsyncImage(
                model = ImageRequest.Builder(context).data(u).crossfade(true).size(1920, 1080).build(), // screen size, not 4K
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    val d = drift.floatValue
                    val z = 1f + 0.08f * d
                    scaleX = z; scaleY = z
                    translationX = -size.width * 0.035f * d
                    translationY = -size.height * 0.012f * d
                },
            )
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xFF1E2430)))
        }
    }
}

/**
 * The hero text glides in with each new title: the old text fades out quickly, the new text fades in and
 * rises slightly, timed to land with the backdrop crossfade.
 */
@Composable
private fun Hero(hero: WatchItem?, appLabel: String?, state: LauncherState, modifier: Modifier) {
    val rise = with(LocalDensity.current) { 12.dp.roundToPx() }
    AnimatedContent(
        targetState = hero to appLabel,
        contentKey = { (h, label) -> h?.key ?: label },
        transitionSpec = {
            (fadeIn(tween(420, delayMillis = 120)) +
                slideInVertically(tween(560, delayMillis = 120, easing = FastOutSlowInEasing)) { rise }) togetherWith
                fadeOut(tween(160)) using SizeTransform(clip = false)
        },
        contentAlignment = Alignment.BottomStart,
        modifier = modifier,
        label = "hero",
    ) { (h, label) -> HeroBody(h, label, state) }
}

@Composable
private fun HeroBody(hero: WatchItem?, appLabel: String?, state: LauncherState) {
    Column(Modifier.widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (hero == null) {
            // A service tile without artwork (no TMDB key yet, or offline): just name it.
            if (appLabel != null) {
                Text("YOUR APPS", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, color = Veil.Accent)
                Text(appLabel, fontSize = 48.sp, lineHeight = 48.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.6).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("Press OK to open", fontSize = 14.sp, color = Veil.Text.copy(alpha = 0.85f))
                return@Column
            }
            Text("PRIVATE BY DESIGN", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, color = Veil.Accent)
            Text(
                if (state.connected) "Welcome back" else "Welcome to Veil",
                fontSize = 48.sp, lineHeight = 48.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.6).sp,
            )
            Text(
                if (state.connected) "Your shows will appear here." else "No ads. No tracking. Just your stuff.",
                fontSize = 14.sp, color = Veil.Text.copy(alpha = 0.85f),
            )
            return@Column
        }
        Text(
            hero.spotlight?.uppercase() ?: when {
                hero.isNew -> "NEW EPISODE"
                hero.upNext -> "UP NEXT"
                else -> "CONTINUE"
            },
            fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, color = Veil.Accent,
        )
        TitleOrLogo(hero, fontSize = 48.sp, maxLogoHeight = 104.dp)
        Text(hero.heroLine, fontSize = 14.sp, color = Veil.Text.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        // "done by": when you'd finish if you started now (refreshes every minute).
        val doneBy = hero.minutesLeft?.let { doneByText(it, state.minuteTick) }
        if (hero.progress > 0f) {
            Row(
                Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                ProgressLine(hero.progress, color = Veil.Accent, track = Color.White.copy(alpha = 0.25f), modifier = Modifier.width(120.dp))
                Text(
                    hero.remainingLine + (doneBy?.let { " · done by $it" } ?: ""),
                    fontSize = 11.sp, color = Veil.Text.copy(alpha = 0.8f),
                )
            }
        } else if (doneBy != null) {
            Text(
                "${durationText(hero.minutesLeft!!)} · done by $doneBy",
                fontSize = 11.sp, color = Veil.Text.copy(alpha = 0.7f), modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun ContinueRow(state: LauncherState, firstFocus: FocusRequester, focusKey: (String) -> FocusRequester) {
    val listState = rememberLazyListState()
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.padding(horizontal = EDGE), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Continue watching", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            if (state.items.size > 1) {
                Text(
                    "${state.focusedIndex + 1} of ${state.items.size}",
                    fontSize = 9.sp, color = Veil.Text.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 1.dp),
                )
            }
            // Evenings only: a quiet summary of what's queued up.
            val hour = java.util.Calendar.getInstance().apply { timeInMillis = state.minuteTick }.get(java.util.Calendar.HOUR_OF_DAY)
            val queued = state.items.mapNotNull { it.minutesLeft }.sum()
            if ((hour >= 17 || hour < 3) && queued >= 10) {
                Text(
                    "·  Tonight · about ${durationText(queued)} to catch up",
                    fontSize = 9.sp, color = Veil.Text.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 1.dp),
                )
            }
        }
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            contentPadding = PaddingValues(start = EDGE, end = EDGE, top = 6.dp, bottom = 8.dp),
            modifier = Modifier.fillMaxWidth().edgeFade(left = listState.canScrollBackward, right = listState.canScrollForward),
        ) {
            if (state.items.isEmpty()) {
                item {
                    MessagePill(
                        if (state.loading) "Loading…" else (state.error ?: "Nothing in progress"),
                        if (state.loading) "Fetching from Trakt" else "Start something in Nuvio",
                        { state.refreshWatching(force = true) },
                        Modifier.focusRequester(firstFocus).onFocusChanged { if (it.isFocused) state.focusedApp = null },
                    )
                }
            } else {
                itemsIndexed(state.items, key = { _, it -> it.key }) { index, item ->
                    WatchPill(
                        item = item,
                        onClick = { state.open(item) },
                        onLongClick = { state.watchSheet = item },
                        modifier = Modifier
                            .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                            .remembered(state, "cw:${item.key}", focusKey)
                            .onFocusChanged {
                                if (it.isFocused) { state.focused = item; state.focusedIndex = index; state.focusedApp = null }
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchPill(item: WatchItem, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier) {
    Pill(
        onClick = onClick, onLongClick = onLongClick, modifier = modifier,
        padding = PaddingValues(start = 6.dp, end = 17.dp, top = 6.dp, bottom = 6.dp), soundPitch = 0.9f,
    ) {
        Box(Modifier.size(74.dp, 42.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f))) {
            item.thumb?.let {
                AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (item.isNew) {
                Text(
                    "NEW", fontSize = 7.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp, color = Veil.Ink,
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 9.dp, top = 5.dp)
                        .clip(RoundedCornerShape(50)).background(Veil.Accent).padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            // Watch progress sits on the artwork (Apple TV style), so the text lines stay free for the episode.
            if (item.progress > 0f) {
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(14.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))),
                )
                ProgressLine(
                    item.progress, color = Veil.Accent, track = Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(start = 13.dp, end = 13.dp, bottom = 5.dp).fillMaxWidth(),
                )
            }
        }
        Column(Modifier.widthIn(max = 220.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (item.isMovie && item.progress > 0f) item.remainingLine else item.episodeLine,
                fontSize = 9.5.sp, color = dimColor(), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MessagePill(title: String, body: String, onClick: () -> Unit, modifier: Modifier) {
    Pill(onClick = onClick, modifier = modifier, padding = PaddingValues(start = 6.dp, end = 20.dp, top = 6.dp, bottom = 6.dp), soundPitch = 0.9f) {
        Box(Modifier.size(74.dp, 42.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            androidx.tv.material3.Icon(VeilIcons.Play, contentDescription = null, modifier = Modifier.size(14.dp), tint = LocalContentColor.current)
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(body, fontSize = 9.5.sp, color = dimColor())
        }
    }
}

/**
 * The pinned services as big branded tiles (their TV banners), then All apps. Focusing a tile puts
 * that service's spotlight in the hero; All apps leaves the hero as it is.
 */
@Composable
private fun ServiceRow(state: LauncherState, focusKey: (String) -> FocusRequester, firstFocus: FocusRequester?) {
    val listState = rememberLazyListState()
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = EDGE, end = EDGE, top = 8.dp, bottom = 10.dp),
        modifier = Modifier.fillMaxWidth()
            .edgeFade(left = listState.canScrollBackward, right = listState.canScrollForward),
    ) {
        itemsIndexed(state.pinnedApps, key = { _, a -> a.pkg }) { index, app ->
            BannerTile(
                app = app,
                onClick = { state.launch(app) },
                onLongClick = { state.sheet = AppSheet(app, fromHome = true) },
                modifier = Modifier.size(TILE_W, TILE_H)
                    .then(if (index == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier)
                    .remembered(state, "app:${app.pkg}", focusKey)
                    .onFocusChanged { if (it.isFocused) state.focusedApp = app.pkg },
            )
        }
        item(key = "all-apps") {
            AllAppsTile(
                onClick = { state.screen = Screen.DRAWER },
                modifier = Modifier.size(TILE_H, TILE_H)
                    .then(if (state.pinnedApps.isEmpty() && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier)
                    .remembered(state, "all-apps", focusKey),
            )
        }
    }
}

/**
 * The show's official title logo (TMDB) when it's light enough to read on Veil's dark hero; otherwise the
 * title in Veil's type. Swaps with a soft crossfade if the logo check finishes after the hero appears.
 */
@Composable
internal fun TitleOrLogo(item: WatchItem, fontSize: androidx.compose.ui.unit.TextUnit, maxLogoHeight: androidx.compose.ui.unit.Dp) {
    val logo = item.logo?.takeIf { LogoCheck.readable[it] == true }
    Crossfade(targetState = logo, animationSpec = tween(350), label = "logo") { url ->
        if (url != null) {
            AsyncImage(
                model = url, contentDescription = item.title, contentScale = ContentScale.Fit,
                alignment = Alignment.BottomStart,
                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp).sizeIn(maxWidth = 440.dp, maxHeight = maxLogoHeight),
            )
        } else {
            Text(
                item.title, fontSize = fontSize, lineHeight = fontSize, fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1.6).sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Behind an open menu: content fades back (alpha only, which this GPU handles easily). */
private fun Modifier.recede(amount: androidx.compose.runtime.State<Float>): Modifier =
    this.graphicsLayer {
        alpha = 1f - 0.85f * amount.value
        // Fade each element directly instead of through an offscreen copy of the whole page
        // (that copy was redrawn every frame of the fade and halved the frame rate).
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }

/** "45 min", "1 h 5 min" */
internal fun durationText(minutes: Int): String =
    if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"

/** The clock time [minutes] from [now], in the TV's 12/24-hour format. */
@Composable
private fun doneByText(minutes: Int, now: Long): String {
    val context = LocalContext.current
    return remember(minutes, now / 60_000) {
        android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(now + minutes * 60_000L))
    }
}

/** Lets Home return focus to this item later, and records it as the last one used. */
private fun Modifier.remembered(state: LauncherState, key: String, focusKey: (String) -> FocusRequester): Modifier =
    this.focusRequester(focusKey(key)).onFocusChanged { if (it.isFocused) state.lastFocusKey = key }
