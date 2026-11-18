package dev.veil.launcher

import android.service.dreams.DreamService
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.veil.launcher.data.ContinueWatching
import dev.veil.launcher.data.NextDnsStats
import dev.veil.launcher.data.NowPlaying
import dev.veil.launcher.data.PrivateDns
import dev.veil.launcher.data.Tmdb
import dev.veil.launcher.data.Trakt
import dev.veil.launcher.data.Spotlight
import dev.veil.launcher.data.VeilCache
import dev.veil.launcher.data.Weather
import dev.veil.launcher.ui.AmbientScreen
import dev.veil.launcher.ui.ThemeState
import dev.veil.launcher.ui.VeilTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Veil Ambient as the system screensaver, replacing Google's ambient mode when the TV is idle in any app.
 * Shows the same screen as the in-launcher ambient. Any button press wakes the TV as usual.
 */
class VeilDream : DreamService(), LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    private lateinit var nowPlaying: NowPlaying

    override fun onCreate() {
        super.onCreate()
        savedState.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        ThemeState.load(applicationContext)
        dev.veil.launcher.viz.VizSettings.load(applicationContext)
        nowPlaying = NowPlaying(applicationContext)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@VeilDream)
            setViewTreeSavedStateRegistryOwner(this@VeilDream)
            // No accessibility tree (see MainActivity): it was rebuilt on every animated frame.
            setContent {
                VeilTheme {
                    androidx.compose.foundation.layout.Box(
                        androidx.compose.ui.Modifier.clearAndSetSemantics { },
                    ) { DreamContent() }
                }
            }
        }
        window?.decorView?.let {
            it.setViewTreeLifecycleOwner(this)
            it.setViewTreeSavedStateRegistryOwner(this)
        }
        setContentView(view)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        nowPlaying.start()
    }

    override fun onDreamingStopped() {
        nowPlaying.stop()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        super.onDreamingStopped()
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    @androidx.compose.runtime.Composable
    private fun DreamContent() {
        val ctx = applicationContext
        val dns = remember { PrivateDns(ctx) }
        var items by remember { mutableStateOf(VeilCache.items) }
        var weather by remember { mutableStateOf(VeilCache.weather) }
        var blocked by remember { mutableStateOf(VeilCache.blocked) }
        // Fill anything the home screen hasn't loaded yet (e.g. after a reboot).
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                if (items.isEmpty()) {
                    val trakt = Trakt(ctx)
                    if (trakt.isConnected) runCatching { ContinueWatching.load(trakt, Tmdb(BuildConfig.TMDB_KEY)) }
                        .getOrNull()?.let { items = it; VeilCache.items = it }
                    // No Trakt (or nothing in progress): what's popular on the Home services instead.
                    if (items.isEmpty()) runCatching { Spotlight.load(Tmdb(BuildConfig.TMDB_KEY)) }.getOrNull()
                        ?.values?.flatten()?.takeIf { it.isNotEmpty() }?.let { items = it; VeilCache.items = it }
                }
                if (weather == null) Weather(ctx).current()?.let { weather = it; VeilCache.weather = it }
                if (blocked == null && dns.isOn) NextDnsStats.blockedLast24h()?.let { blocked = it; VeilCache.blocked = it }
            }
        }
        AmbientScreen(
            track = nowPlaying.track,
            items = items,
            weather = weather,
            blocked = blocked,
            dnsOn = dns.isOn,
            weeklyBlocked = VeilCache.weeklyBlocked,
            seasonal = remember { ctx.getSharedPreferences("veil", MODE_PRIVATE).getBoolean("seasonal", true) },
            lyrics = remember { ctx.getSharedPreferences("veil", MODE_PRIVATE).getBoolean("lyrics_music", true) },
            visualizer = remember {
                ctx.getSharedPreferences("veil", MODE_PRIVATE).getString("music_style", "veil") == "viz" &&
                    dev.veil.launcher.viz.VizSettings.supported(ctx) &&
                    ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
            },
        )
    }
}
