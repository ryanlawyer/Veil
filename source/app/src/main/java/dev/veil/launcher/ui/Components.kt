@file:OptIn(ExperimentalTvMaterial3Api::class)

package dev.veil.launcher.ui

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.style.TextAlign
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import dev.veil.launcher.LauncherState
import dev.veil.launcher.R
import dev.veil.launcher.data.AppEntry
import dev.veil.launcher.data.NowPlaying
import dev.veil.launcher.data.Weather
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Date

// ---- Icons (stroke icons drawn from SVG path data, so they stay crisp at any scale) -------------

private fun icon(name: String, d: String, filled: Boolean = false): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = addPathNodes(d),
        fill = if (filled) SolidColor(Color.White) else null,
        stroke = if (filled) null else SolidColor(Color.White),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()

object VeilIcons {
    val Lock = icon("lock", "M8 11h8a3 3 0 0 1 3 3v4a3 3 0 0 1-3 3H8a3 3 0 0 1-3-3v-4a3 3 0 0 1 3-3z M8 11V8a4 4 0 0 1 8 0v3")
    val Gear = icon(
        "gear",
        "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z " +
            "M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1" +
            "a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3" +
            "a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9" +
            "a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1" +
            "a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z",
    )
    val Pause = icon("pause", "M9 5v14 M15 5v14")
    val Note = icon("note", "M9 18V5l12-2v13 M9 18a3 3 0 1 1-6 0a3 3 0 1 1 6 0z M21 16a3 3 0 1 1-6 0a3 3 0 1 1 6 0z")
    val Lyrics = icon("lyrics", "M4 6h16 M4 11h11 M4 16h14 M4 21h8")
    val ShieldOn = icon("shield-on", "M12 3l7 3v6c0 4.5-3 7.5-7 9-4-1.5-7-4.5-7-9V6z M9 12l2 2 4-4")
    val ShieldOff = icon("shield-off", "M12 3l7 3v6c0 4.5-3 7.5-7 9-4-1.5-7-4.5-7-9V6z M4 4l16 16")
    // Theme glyphs
    val Wave = icon("wave", "M3 9c3-3 6-3 9 0s6 3 9 0 M3 15c3-3 6-3 9 0s6 3 9 0")
    val Flame = icon("flame", "M12 3c1 4 5 5.5 5 10.5a5 5 0 0 1-10 0c0-3 2-4 2-6.5 1 1.2 2 2 3 2.2 0-2.2-1-4.2 0-6.2z")
    val Snowflake = icon("snowflake", "M12 2v20 M3.3 7l17.4 10 M3.3 17L20.7 7 M9 3.5l3 2 3-2 M9 20.5l3-2 3 2")
    val Heart = icon("heart", "M12 20s-7.5-4.6-7.5-10.2A4.3 4.3 0 0 1 12 7.2a4.3 4.3 0 0 1 7.5 2.6C19.5 15.4 12 20 12 20z")
    val HalfCircle = icon("half", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M12 3v18")
    val Star = icon("star", "M12 3l2.6 5.6 6.1.7-4.5 4.2 1.2 6L12 16.6 6.6 19.5l1.2-6L3.3 9.3l6.1-.7z")
    val Pin2 = icon("pin2", "M12 21s-7-6.2-7-11.5a7 7 0 0 1 14 0C19 14.8 12 21 12 21z M12 7a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5z")
    val Check = icon("check", "M5 12.5l4.5 4.5L19 7.5")
    val ChevronDown = icon("chevron-down", "M6 9l6 6 6-6")
    val Search = icon("search", "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14z M20 20l-4-4")

    val Timer = icon("timer", "M12 4a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M12 9v4l2.5 2 M9 2h6")
    val Sparkle = icon("sparkle", "M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z M19 17l.7 1.8 1.8.7-1.8.7L19 22l-.7-1.8-1.8-.7 1.8-.7z")

    // Weather
    val Sun = icon(
        "sun",
        "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8z M12 2v2 M12 20v2 M4.9 4.9l1.4 1.4 M17.7 17.7l1.4 1.4 M2 12h2 M20 12h2 M4.9 19.1l1.4-1.4 M17.7 6.3l1.4-1.4",
    )
    val Moon = icon("moon", "M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z")
    val Partly = icon(
        "partly",
        "M12 2v2 M4.9 4.9l1.4 1.4 M2 12h2 M19.1 4.9l-1.4 1.4 M15.9 10.2A4 4 0 0 0 8.1 11 M17.5 21H9a5 5 0 1 1 4.6-7h1.4a3.5 3.5 0 0 1 2.5 7z",
    )
    val Cloud = icon("cloud", "M17.5 19H9a7 7 0 1 1 6.7-9h1.8a4.5 4.5 0 0 1 0 9z")
    val Fog = icon("fog", "M3 10h18 M3 14h18 M5 18h14 M7 6h10")
    val Drizzle = icon("drizzle", "M8 19v2 M8 13v2 M16 19v2 M16 13v2 M12 21v2 M12 15v2 M20 16.6A5 5 0 0 0 18 7h-1.3A8 8 0 1 0 4 15.3")
    val Rain = icon("rain", "M16 13v8 M8 13v8 M12 15v8 M20 16.6A5 5 0 0 0 18 7h-1.3A8 8 0 1 0 4 15.3")
    val Snow = icon(
        "snow",
        "M20 17.6A5 5 0 0 0 18 8h-1.3A8 8 0 1 0 4 16.3 M8 16h.01 M8 20h.01 M12 18h.01 M12 22h.01 M16 16h.01 M16 20h.01",
    )
    val Thunder = icon("thunder", "M19 16.9A5 5 0 0 0 18 7h-1.3a8 8 0 1 0-11.7 9 M13 11l-4 6h6l-4 6")
    val Pin = icon("pin", "M12 17v5 M8 3h8l-1 7 3 3H6l3-3z")
    val Unpin = icon("unpin", "M4 4l16 16 M12 17v5 M8 3h8l-1 7 3 3H6l3-3z")
    val Grid = icon(
        "grid",
        "M6 3.8a2.2 2.2 0 1 0 0 4.4a2.2 2.2 0 1 0 0-4.4z M18 3.8a2.2 2.2 0 1 0 0 4.4a2.2 2.2 0 1 0 0-4.4z " +
            "M6 15.8a2.2 2.2 0 1 0 0 4.4a2.2 2.2 0 1 0 0-4.4z M18 15.8a2.2 2.2 0 1 0 0 4.4a2.2 2.2 0 1 0 0-4.4z",
        filled = true,
    )
    val Play = icon("play", "M7 4l12 8-12 8z")
    val Info = icon("info", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z M12 11v6 M12 7.5v.5")
    val Left = icon("left", "M15 6l-6 6 6 6")
    val Right = icon("right", "M9 6l6 6-6 6")
}

// ---- Top bar (from concept B "Halo") -----------------------------------------------------------

@Composable
fun Clock(fontSize: TextUnit = 17.sp) {
    val context = LocalContext.current
    val time by produceState(initialValue = "") {
        while (true) {
            value = DateFormat.getTimeFormat(context).format(Date())
            delay(5_000)
        }
    }
    Text(time, fontSize = fontSize, fontWeight = FontWeight.Normal, letterSpacing = 0.3.sp)
}

@Composable
fun TopBar(state: LauncherState, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(34.dp)) {
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            VeilMenuAnchor(state)
            state.nowPlaying.track?.let { track ->
                Spacer(Modifier.width(14.dp))
                NowPlayingPill(track, onToggle = state.nowPlaying::togglePlayPause, onHold = { state.musicSheet = true })
            }
        }
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
            Clock()
            state.weather?.let { w ->
                Spacer(Modifier.width(12.dp))
                Icon(weatherIcon(w), contentDescription = null, modifier = Modifier.size(14.dp), tint = Veil.Text.copy(alpha = 0.9f))
                Spacer(Modifier.width(4.dp))
                Text("${w.tempC}°", fontSize = 14.sp, color = Veil.Text.copy(alpha = 0.9f))
            }
        }
        Row(
            Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            var privacyFocused by remember { mutableStateOf(false) }
            // Same short delay as the Veil menu, so passing over the pill doesn't flash the panel.
            LaunchedEffect(privacyFocused) {
                if (privacyFocused) { delay(200); state.onPrivacyFocus(true) } else state.onPrivacyFocus(false)
            }
            PrivacyPill(state, Modifier.onFocusChanged { privacyFocused = it.isFocused })
            Surface(
                onClick = { Sfx.select(); state.openSettings() },
                modifier = Modifier.size(30.dp).focusSound(1.12f),
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Veil.GlassStrong, contentColor = Veil.Text,
                    focusedContainerColor = Veil.Text, focusedContentColor = Veil.Ink,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            ) {
                Icon(VeilIcons.Gear, contentDescription = "Settings", modifier = Modifier.size(13.dp).align(Alignment.Center))
            }
        }
        // Details panel that drops down under the privacy pill while it is focused. It overflows the
        // bar's bounds on purpose; callers give the top bar a zIndex so it draws above the page.
        Box(
            Modifier.align(Alignment.TopEnd).offset(y = 42.dp)
                .wrapContentSize(Alignment.TopEnd, unbounded = true),
        ) {
            // Built once and kept (invisible) while on Home, so focusing the pill only fades it in; it used
            // to be built from scratch on every open, which froze a few frames. It has nothing focusable.
            if (state.dnsAvailable) {
                val open = state.privacyFocused
                val shown by animateFloatAsState(if (open) 1f else 0f, tween(if (open) 200 else 120), label = "privacy-panel")
                Box(
                    Modifier.graphicsLayer {
                        alpha = shown
                        translationY = -12.dp.toPx() * (1f - shown)
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    },
                ) { PrivacyPanel(state) }
            }
        }
    }
}

private fun minutesLeft(state: LauncherState): Int =
    (((state.dnsResumeAt - state.minuteTick).coerceAtLeast(0) + 59_999) / 60_000).toInt()

@Composable
private fun PrivacyPanel(state: LauncherState) {
    val nf = remember { NumberFormat.getIntegerInstance() }
    val d = state.dnsDetails
    val on = state.dnsOn
    val paused = state.dnsPaused
    val context = LocalContext.current
    val resumeTime = remember(state.dnsResumeAt) { DateFormat.getTimeFormat(context).format(Date(state.dnsResumeAt)) }
    Column(
        Modifier.width(330.dp).clip(RoundedCornerShape(20.dp)).background(Veil.Sheet)
            .border(1.dp, Veil.Line, RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header: provider logo, name, status chip
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.usesNextDns) {
                Image(
                    painterResource(R.drawable.nextdns_logo), contentDescription = "NextDNS",
                    modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)),
                )
            } else {
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Veil.GlassStrong), contentAlignment = Alignment.Center) {
                    Icon(VeilIcons.ShieldOn, contentDescription = null, modifier = Modifier.size(15.dp), tint = Veil.Text)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(state.dnsProvider, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                Text("Encrypted DNS · DNS-over-TLS", fontSize = 8.5.sp, color = Veil.Text.copy(alpha = 0.6f))
            }
            Text(
                if (on) "ACTIVE" else if (paused) "PAUSED" else "OFF",
                fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                color = if (on) Veil.Ink else Veil.Text.copy(alpha = 0.75f),
                modifier = Modifier.clip(RoundedCornerShape(50))
                    .background(if (on) Veil.Accent else Color.White.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }

        // Headline
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                when {
                    paused -> "Taking a breather."
                    !on -> "The veil is down."
                    d == null -> "Veil Shield is up."
                    d.blocked == 0 -> "All quiet. Nothing's slipped through."
                    d.blocked == 1 -> "1 tracker stopped at the door."
                    else -> "${nf.format(d.blocked)} trackers stopped at the door."
                },
                fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp, lineHeight = 18.sp,
            )
            Text(
                when {
                    on -> "Every lookup your TV makes is encrypted and filtered before it leaves the house. Ads and trackers never get an answer."
                    paused -> "Ad blocking is paused and raises itself again at $resumeTime (${minutesLeft(state)} min). Press OK to bring it back now."
                    else -> "Lookups go through your network's default DNS, so ads and trackers get through. Press OK to raise the veil."
                },
                fontSize = 9.5.sp, lineHeight = 13.sp, color = Veil.Text.copy(alpha = 0.72f),
            )
        }

        // Stats (NextDNS only)
        if (on && d != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val pct = if (d.total > 0) d.blocked * 100f / d.total else 0f
                StatTile("Blocked", nf.format(d.blocked), Modifier.weight(1f), accent = true)
                StatTile("Lookups", nf.format(d.total), Modifier.weight(1f))
                StatTile("Blocked %", if (pct in 0.01f..0.99f) "<1%" else "${pct.toInt()}%", Modifier.weight(1f))
            }
            if (d.topBlocked.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("MOST BLOCKED · 24 H", fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Veil.Text.copy(alpha = 0.55f))
                    d.topBlocked.forEach { (label, count) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(5.dp).clip(CircleShape).background(Veil.Accent))
                            Spacer(Modifier.width(8.dp))
                            Text(label, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(nf.format(count), fontSize = 10.sp, color = Veil.Text.copy(alpha = 0.6f))
                        }
                    }
                }
            }
            d.encryptedPct?.let {
                Text("$it% of lookups encrypted in the last 24 hours", fontSize = 8.5.sp, color = Veil.Text.copy(alpha = 0.55f))
            }
        }

        // This week (NextDNS): total, a 7-day bar chart, and the most-blocked tracker
        state.dnsWeekly?.takeIf { it.days.isNotEmpty() }?.let { w ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(Veil.Line))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("THIS WEEK", fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Veil.Text.copy(alpha = 0.55f))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(nf.format(w.blocked), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Veil.Accent)
                        Spacer(Modifier.width(5.dp))
                        Text("blocked", fontSize = 9.sp, color = Veil.Text.copy(alpha = 0.6f), modifier = Modifier.padding(bottom = 3.dp))
                    }
                    w.topTracker?.let {
                        Text("Top: $it", fontSize = 9.sp, color = Veil.Text.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                // Mini bar chart, today on the right
                val max = (w.days.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                    w.days.forEachIndexed { i, (label, n) ->
                        val today = i == w.days.lastIndex
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.width(10.dp).height((4f + 30f * n / max).dp).clip(RoundedCornerShape(3.dp))
                                    .background(if (today) Veil.Accent else Veil.Accent.copy(alpha = 0.4f)),
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(label.take(1), fontSize = 7.sp, color = Veil.Text.copy(alpha = if (today) 0.9f else 0.5f))
                        }
                    }
                }
            }
        }

        // Footer hint
        Box(Modifier.fillMaxWidth().height(1.dp).background(Veil.Line))
        Text(
            when {
                on -> "OK to turn off  ·  Hold OK to pause"
                paused -> "OK to resume now  ·  Hold OK for options"
                else -> "OK to turn on  ·  Hold OK for options"
            },
            fontSize = 9.sp, color = Veil.Text.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier, accent: Boolean = false) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = if (accent) Veil.Accent else Veil.Text, maxLines = 1)
        Text(label, fontSize = 8.sp, color = Veil.Text.copy(alpha = 0.6f), maxLines = 1)
    }
}

internal fun weatherIcon(w: Weather.Now): ImageVector = when (w.kind) {
    Weather.Kind.CLEAR -> if (w.isDay) VeilIcons.Sun else VeilIcons.Moon
    Weather.Kind.PARTLY -> if (w.isDay) VeilIcons.Partly else VeilIcons.Moon
    Weather.Kind.CLOUDY -> VeilIcons.Cloud
    Weather.Kind.FOG -> VeilIcons.Fog
    Weather.Kind.DRIZZLE -> VeilIcons.Drizzle
    Weather.Kind.RAIN -> VeilIcons.Rain
    Weather.Kind.SNOW -> VeilIcons.Snow
    Weather.Kind.THUNDER -> VeilIcons.Thunder
}

/**
 * "Now playing": OK toggles play/pause; holding OK opens a menu (open the app, or Veil Ambient).
 * The menu ignores the release of the press that opened it, so nothing is triggered by accident.
 */
@Composable
private fun NowPlayingPill(track: NowPlaying.Track, onToggle: () -> Unit, onHold: () -> Unit) {
    Pill(
        onClick = onToggle, onLongClick = onHold,
        padding = PaddingValues(start = 3.dp, end = 11.dp, top = 3.dp, bottom = 3.dp), soundPitch = 1.12f,
    ) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(Veil.GlassStrong), contentAlignment = Alignment.Center) {
            val art = track.art
            if (art != null) Image(art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(VeilIcons.Note, contentDescription = null, modifier = Modifier.size(12.dp), tint = LocalContentColor.current)
        }
        Column(Modifier.widthIn(max = 170.dp)) {
            Text(track.title, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            track.artist?.let { Text(it, fontSize = 8.sp, color = dimColor(), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Icon(
            if (track.playing) VeilIcons.Pause else VeilIcons.Play,
            contentDescription = if (track.playing) "Pause" else "Play",
            modifier = Modifier.size(11.dp), tint = LocalContentColor.current,
        )
    }
}

/** The "No ads · No tracking" pill. When Veil may control Private DNS, OK toggles ad blocking on/off. */
@Composable
private fun PrivacyPill(state: LauncherState, modifier: Modifier = Modifier) {
    if (!state.dnsAvailable) {
        Text(
            "No ads · No tracking",
            fontSize = 9.sp, color = Veil.Text.copy(alpha = 0.85f),
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.10f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
        return
    }
    val label = when {
        state.dnsBusy -> "Checking…"
        state.dnsMessage != null -> state.dnsMessage!!
        state.dnsOn -> state.blockedToday?.let { "No ads · No tracking · ${NumberFormat.getIntegerInstance().format(it)} blocked" }
            ?: "No ads · No tracking"
        state.dnsPaused -> "Ad blocking paused · ${minutesLeft(state)} min"
        else -> "Ad blocking off"
    }
    Pill(
        onClick = state::toggleDns, onLongClick = { state.privacySheet = true },
        modifier = modifier, padding = PaddingValues(start = 8.dp, end = 11.dp, top = 5.dp, bottom = 5.dp), soundPitch = 1.12f,
    ) {
        val focusedInk = LocalContentColor.current == Veil.Ink
        Icon(
            if (state.dnsOn) VeilIcons.ShieldOn else VeilIcons.ShieldOff,
            contentDescription = null,
            modifier = Modifier.size(11.dp),
            tint = when {
                focusedInk -> Veil.Ink
                state.dnsOn -> Veil.Accent
                else -> Veil.Text.copy(alpha = 0.6f)
            },
        )
        Text(
            label, fontSize = 9.sp, maxLines = 1,
            color = if (state.dnsOn || focusedInk) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.7f),
        )
    }
}

// ---- Pills (from concept D "Ink") --------------------------------------------------------------

/** Rounded pill: translucent with a thin outline, turning solid white with dark text when focused. */
@Composable
fun Pill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
    /** Pitch of the "move" tick: cards lower, top bar higher, so you can hear where you are. */
    soundPitch: Float = 1f,
    content: @Composable RowScope.() -> Unit,
) {
    val glass = rememberGlassMotion()
    Surface(
        onClick = { Sfx.select(); onClick() },
        onLongClick = onLongClick, // hold menus play their own "open" chime
        modifier = modifier.focusSound(soundPitch).glassTilt(glass),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Veil.Glass, contentColor = Veil.Text,
            focusedContainerColor = Veil.Text, focusedContentColor = Veil.Ink,
            pressedContainerColor = Veil.Text.copy(alpha = 0.85f), pressedContentColor = Veil.Ink,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Veil.Line), shape = RoundedCornerShape(50)),
            focusedBorder = Border.None,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(elevationColor = Color.Black.copy(alpha = 0.45f), elevation = 12.dp),
        ),
    ) {
        Row(
            Modifier.glassGlint(glass).padding(padding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/** Secondary text that follows the pill's focus state (grey on dark, dark grey on white). */
@Composable
fun dimColor() = LocalContentColor.current.copy(alpha = 0.72f)

@Composable
fun AppPill(app: AppEntry, onClick: () -> Unit, onLongClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    Pill(onClick = onClick, onLongClick = onLongClick, modifier = modifier) {
        val icon = app.icon
        if (icon != null) {
            Image(icon, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(30.dp).clip(CircleShape))
        } else {
            Box(Modifier.size(30.dp).clip(CircleShape).background(Veil.GlassStrong))
        }
        Text(app.label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun AllAppsPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Pill(onClick = onClick, modifier = modifier) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(VeilIcons.Grid, contentDescription = null, modifier = Modifier.size(13.dp), tint = LocalContentColor.current)
        }
        Text("All apps", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

private val TileShape = RoundedCornerShape(18.dp)

/**
 * A big branded tile: the app's TV banner, or its icon and name when it has none. The service tiles on
 * Home and the grid in All apps. [pinned] adds the small pin badge (All apps only).
 */
@Composable
fun BannerTile(
    app: AppEntry,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    pinned: Boolean = false,
) {
    TileCard(onClick, onLongClick, modifier) {
        val banner = app.banner
        if (banner != null) {
            Image(banner, contentDescription = app.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                app.icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(34.dp).clip(CircleShape)) }
                Spacer(Modifier.height(6.dp))
                Text(
                    app.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
        if (pinned) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(7.dp).size(20.dp).clip(CircleShape).background(Veil.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(VeilIcons.Pin, contentDescription = "Pinned", modifier = Modifier.size(11.dp), tint = Veil.Ink)
            }
        }
    }
}

/** The tile at the end of the Home row that opens All apps. */
@Composable
fun AllAppsTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TileCard(onClick, null, modifier) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(VeilIcons.Grid, contentDescription = null, modifier = Modifier.size(15.dp), tint = Veil.Text)
            }
            Spacer(Modifier.height(6.dp))
            Text("All apps", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Rounded card with the glass tilt and glint, a white outline when focused, and the "move" tick. */
@Composable
private fun TileCard(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val glass = rememberGlassMotion()
    Card(
        onClick = { Sfx.select(); onClick() },
        onLongClick = onLongClick,
        modifier = modifier.focusSound().glassTilt(glass),
        shape = CardDefaults.shape(TileShape),
        scale = CardDefaults.scale(focusedScale = 1.05f),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.5.dp, Veil.Text), shape = TileShape)),
        colors = CardDefaults.colors(containerColor = Color(0xFF22252C), focusedContainerColor = Color(0xFF2C3038)),
    ) {
        Box(Modifier.fillMaxSize().glassGlint(glass), content = content)
    }
}

@Composable
fun ProgressLine(progress: Float, color: Color, track: Color, modifier: Modifier = Modifier) {
    Box(modifier.height(2.5.dp).clip(RoundedCornerShape(50)).background(track)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0.02f, 1f)).height(2.5.dp).clip(RoundedCornerShape(50)).background(color))
    }
}

/**
 * Places content [y] below this point without taking up any layout space, so a drop-down panel never
 * pushes its neighbours around. [alignEnd] anchors the panel's right edge instead of its left.
 */
fun Modifier.floatingBelow(y: Dp, alignEnd: Boolean = false): Modifier = this.layout { measurable, _ ->
    val p = measurable.measure(Constraints())
    layout(0, 0) { p.place(if (alignEnd) -p.width else 0, y.roundToPx()) }
}

/** Fades a scrolling row's content out at the edges that have more content beyond them. */
fun Modifier.edgeFade(left: Boolean, right: Boolean, width: Dp = 130.dp): Modifier =
    this.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
        drawContent()
        val w = width.toPx()
        if (right) drawRect(
            Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = size.width - w, endX = size.width),
            blendMode = BlendMode.DstOut,
        )
        if (left) drawRect(
            Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = 0f, endX = w * 0.7f),
            blendMode = BlendMode.DstOut,
        )
    }

// ---- Hold-OK action sheet ----------------------------------------------------------------------

data class SheetAction(val label: String, val icon: ImageVector, val run: () -> Unit)

@Composable
fun ActionSheet(title: String, actions: List<SheetAction>, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    // The sheet opens while OK is still held (long press). Swallow OK events until that press ends,
    // otherwise releasing the button "clicks" the first option.
    var armed by remember { mutableStateOf(false) }
    // No tick for the automatic first focus; only for moves you make.
    var ready by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = { Sfx.back(); onDismiss() }) {
        Column(
            Modifier
                .onPreviewKeyEvent { e ->
                    if (armed || !e.isSelectKey()) return@onPreviewKeyEvent false
                    if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) {
                        // A fresh press: the long press already ended elsewhere, so accept this one.
                        armed = true
                        false
                    } else {
                        if (e.type == KeyEventType.KeyUp) armed = true
                        true
                    }
                }
                .width(220.dp).clip(RoundedCornerShape(18.dp)).background(Veil.Sheet).padding(9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                title, fontSize = 10.sp, color = Veil.Text.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 6.dp),
            )
            actions.forEachIndexed { i, a ->
                Surface(
                    onClick = { Sfx.select(); onDismiss(); a.run() },
                    modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier)
                        .onFocusChanged { if (it.isFocused && ready) Sfx.move(1.12f) },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent, contentColor = Veil.Text,
                        focusedContainerColor = Veil.Text, focusedContentColor = Veil.Ink,
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                ) {
                    Row(
                        Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(a.icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = LocalContentColor.current)
                        Text(a.label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        Sfx.open()
        delay(50)
        runCatching { first.requestFocus() }
        delay(100)
        ready = true
    }
}

private fun KeyEvent.isSelectKey() =
    key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter || key == Key.ButtonA
