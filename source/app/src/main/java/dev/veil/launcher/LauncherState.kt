package dev.veil.launcher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.veil.launcher.data.AppEntry
import dev.veil.launcher.data.Apps
import dev.veil.launcher.data.ContinueWatching
import dev.veil.launcher.data.NextDnsStats
import dev.veil.launcher.data.NowPlaying
import dev.veil.launcher.data.PrivateDns
import dev.veil.launcher.data.Spotlight
import dev.veil.launcher.data.Weather
import dev.veil.launcher.data.Tmdb
import dev.veil.launcher.data.Trakt
import dev.veil.launcher.data.VeilCache
import dev.veil.launcher.data.WatchItem
import dev.veil.launcher.ui.ThemeState
import dev.veil.launcher.ui.VeilThemeSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { HOME, DRAWER, CONNECT }

/** The hold-OK menu: which app it is for, and whether it was opened from the Home row. */
data class AppSheet(val app: AppEntry, val fromHome: Boolean)

/**
 * @Stable: everything the UI reads from here is Compose state (or never changes, like permissions), so
 * screens that receive this object can be skipped when nothing they show has changed.
 */
@androidx.compose.runtime.Stable
class LauncherState(private val context: Context, private val scope: CoroutineScope) {
    val trakt = Trakt(context)
    private val tmdb = Tmdb(BuildConfig.TMDB_KEY)
    private val prefs = context.getSharedPreferences("veil", Context.MODE_PRIVATE)

    var screen by mutableStateOf(Screen.HOME)
    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set
    var items by mutableStateOf<List<WatchItem>>(emptyList())
        private set
    var focused by mutableStateOf<WatchItem?>(null)
    /** Index of the last focused Continue Watching item, for the "x of N" counter. */
    var focusedIndex by mutableStateOf(0)
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var connected by mutableStateOf(trakt.isConnected)
        private set
    var deviceCode by mutableStateOf<Trakt.DeviceCode?>(null)
        private set
    var connectStatus by mutableStateOf("")
        private set
    /** Connect screen shows the "your own Trakt app" client ID field instead of a sign-in code. */
    var editingClientId by mutableStateOf(false)
        private set
    var sheet by mutableStateOf<AppSheet?>(null)

    /** Popular/trending artwork per service tile (package → items), for the Home backdrop and Ambient. */
    var spotlight by mutableStateOf<Map<String, List<WatchItem>>>(emptyMap())
        private set
    /** Package of the Home tile that has focus; null while focus is on Continue Watching. */
    var focusedApp by mutableStateOf<String?>(null)
    private var lastSpotlight = 0L
    private var spotlightLoading = false

    /** Ordered package names pinned to the Home row. */
    var pins by mutableStateOf(loadPins())
        private set

    // ---- Top-bar extras: ad blocking, blocked counter, weather, now playing ----
    private val dns = PrivateDns(context)
    private val weatherSource = Weather(context)
    val nowPlaying = NowPlaying(context)
    val dnsAvailable: Boolean get() = dns.canControl
    var dnsOn by mutableStateOf(dns.isOn)
        private set
    /** When a pause ends (epoch ms), 0 when not paused. */
    var dnsResumeAt by mutableStateOf(dns.resumeAt)
        private set
    val dnsPaused: Boolean get() = dnsResumeAt > 0L && !dnsOn
    /** Updated every minute so "paused · 14 min" counts down. */
    var minuteTick by mutableStateOf(System.currentTimeMillis())
        private set
    var dnsBusy by mutableStateOf(false)
        private set
    /** Short-lived message on the privacy pill, e.g. when ad blocking had to be turned back off. */
    var dnsMessage by mutableStateOf<String?>(null)
        private set
    var blockedToday by mutableStateOf<Int?>(null)
        private set
    var weather by mutableStateOf<Weather.Now?>(null)
        private set
    /** True while the privacy pill is focused, which shows the details panel under it. */
    var privacyFocused by mutableStateOf(false)
        private set
    var dnsDetails by mutableStateOf<NextDnsStats.Details?>(null)
        private set
    val dnsProvider: String get() = if (NextDnsStats.enabled) "NextDNS" else "AdGuard DNS"
    val usesNextDns: Boolean get() = NextDnsStats.enabled
    private var lastDetails = 0L

    fun onPrivacyFocus(focused: Boolean) {
        privacyFocused = focused
        if (!focused || !NextDnsStats.enabled) return
        val now = System.currentTimeMillis()
        if (now - lastDetails < 60_000) return
        lastDetails = now
        scope.launch {
            withContext(Dispatchers.IO) { NextDnsStats.details() }?.let {
                dnsDetails = it
                blockedToday = it.blocked
            }
            withContext(Dispatchers.IO) { NextDnsStats.weekly() }?.let { setWeekly(it) }
        }
    }

    /** Last 7 days of blocking, for the privacy panel and Sunday's Ambient line. */
    var dnsWeekly by mutableStateOf<NextDnsStats.Weekly?>(null)
        private set
    private var lastWeekly = 0L
    private fun setWeekly(w: NextDnsStats.Weekly) {
        dnsWeekly = w
        VeilCache.weeklyBlocked = w.blocked
        lastWeekly = System.currentTimeMillis()
    }

    /** Snow in December, fireworks at New Year… in Veil Ambient only. */
    var seasonal by mutableStateOf(prefs.getBoolean("seasonal", true))
        private set
    fun toggleSeasonal() {
        seasonal = !seasonal
        prefs.edit().putBoolean("seasonal", seasonal).apply()
    }
    private var lastWeather = 0L
    private var lastStats = 0L

    /** Bumped every time Home is pressed, so the UI can move focus back to the start. */
    var homeTick by mutableStateOf(0)
        private set

    private var lastLoad = 0L
    private var connectJob: Job? = null

    val pinnedApps: List<AppEntry>
        get() = pins.mapNotNull { p -> apps.firstOrNull { it.pkg == p } }

    fun isPinned(app: AppEntry) = app.pkg in pins

    // ---- Pins ---------------------------------------------------------------------------------

    private fun loadPins(): List<String> {
        val saved = prefs.getString("pins", null) ?: return DEFAULT_PINS
        return saved.split('|').filter { it.isNotBlank() }
    }

    private fun savePins(list: List<String>) {
        pins = list
        prefs.edit().putString("pins", list.joinToString("|")).apply()
    }

    fun pin(app: AppEntry) { if (app.pkg !in pins) savePins(pins + app.pkg) }
    fun unpin(app: AppEntry) = savePins(pins - app.pkg)

    /** Moves a pinned app one step left (-1) or right (+1) among the apps that are installed. */
    fun move(app: AppEntry, delta: Int) {
        val visible = pinnedApps.map { it.pkg }.toMutableList()
        val i = visible.indexOf(app.pkg)
        val j = i + delta
        if (i < 0 || j !in visible.indices) return
        visible[i] = visible[j].also { visible[j] = visible[i] }
        // Keep pins for apps that are currently uninstalled at the end, so they return if reinstalled.
        savePins(visible + pins.filter { it !in visible })
    }

    // ---- Navigation ---------------------------------------------------------------------------

    fun onHomePressed() {
        if (screen == Screen.CONNECT) cancelConnect()
        sheet = null
        privacySheet = false
        musicSheet = false
        resetIdle()
        screen = Screen.HOME
        homeTick++
    }

    fun refreshApps() {
        scope.launch {
            apps = withContext(Dispatchers.IO) { runCatching { Apps.load(context) }.getOrDefault(apps) }
        }
    }

    fun refreshWatching(force: Boolean = false) {
        connected = trakt.isConnected
        if (!connected || loading) return
        val now = System.currentTimeMillis()
        if (!force && now - lastLoad < 30_000) return
        lastLoad = now
        scope.launch {
            loading = true
            try {
                val result = withContext(Dispatchers.IO) { ContinueWatching.load(trakt, tmdb) }
                items = result
                VeilCache.items = result.ifEmpty { spotlight.values.flatten() }
                val keep = result.firstOrNull { it.key == focused?.key }
                focused = keep ?: result.firstOrNull()
                focusedIndex = if (keep != null) result.indexOf(keep) else 0
                error = null
            } catch (e: Exception) {
                error = "Couldn't reach Trakt"
            } finally {
                loading = false
                connected = trakt.isConnected
            }
        }
    }

    /** What's popular on each service, refreshed every 6 h. Needs the TMDB key; otherwise a no-op. */
    fun refreshSpotlight(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (spotlightLoading || (!force && now - lastSpotlight < 6 * 3600_000L)) return
        spotlightLoading = true
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { Spotlight.load(tmdb) }
                if (result.values.any { it.isNotEmpty() }) {
                    spotlight = result
                    lastSpotlight = now
                    // Ambient slideshow: Continue Watching when there is one, otherwise these.
                    if (!connected || VeilCache.items.isEmpty()) VeilCache.items = result.values.flatten()
                }
            } finally {
                spotlightLoading = false
            }
        }
    }

    // ---- Ad blocking, counter, weather ----------------------------------------------------------

    /** One press toggles ad blocking. Turning on is verified and automatically undone if the network breaks. */
    /** OK on the pill: on â†’ off; off or paused â†’ on. */
    fun toggleDns() = changeDns(if (dns.isOn) DnsAction.OFF else DnsAction.ON)

    fun pauseDns(minutes: Int) = changeDns(DnsAction.PAUSE, minutes)
    fun turnDnsOn() = changeDns(DnsAction.ON)
    fun turnDnsOff() = changeDns(DnsAction.OFF)

    private enum class DnsAction { ON, OFF, PAUSE }

    private fun changeDns(action: DnsAction, minutes: Int = 0) {
        if (dnsBusy || !dns.canControl) return
        scope.launch {
            dnsBusy = true
            dnsMessage = null
            try {
                when (action) {
                    DnsAction.OFF -> { withContext(Dispatchers.IO) { dns.clearPause(); dns.turnOff() }; dev.veil.launcher.ui.Sfx.off() }
                    DnsAction.PAUSE -> { withContext(Dispatchers.IO) { dns.pause(minutes) }; dev.veil.launcher.ui.Sfx.off() }
                    DnsAction.ON -> {
                        withContext(Dispatchers.IO) { dns.clearPause() }
                        val ok = withContext(Dispatchers.IO) { dns.turnOnSafely() }
                        if (!ok) { dev.veil.launcher.ui.Sfx.error(); flash("Ad blocking failed, turned off") }
                        else { dev.veil.launcher.ui.Sfx.on(); lastStats = 0; refreshExtras() }
                    }
                }
            } catch (e: Exception) {
                runCatching { dns.turnOff() }
                flash("Ad blocking failed, turned off")
            } finally {
                dnsOn = dns.isOn
                dnsResumeAt = dns.resumeAt
                dnsBusy = false
                lastDetails = 0
                if (privacyFocused) onPrivacyFocus(true)
            }
        }
    }

    private fun flash(msg: String) {
        dnsMessage = msg
        scope.launch { delay(6_000); if (dnsMessage == msg) dnsMessage = null }
    }

    /** Weather every 30 min, blocked counter every 5 min. Cheap to call often. */
    fun refreshExtras() {
        dnsOn = dns.isOn
        dnsResumeAt = dns.resumeAt
        val now = System.currentTimeMillis()
        minuteTick = now
        // A pause that ended while the alarm was delayed: resume now.
        if (dnsResumeAt in 1..now && !dnsOn && !dnsBusy) turnDnsOn()
        if (weatherSource.enabled && now - lastWeather > 30 * 60_000) {
            lastWeather = now
            scope.launch { withContext(Dispatchers.IO) { weatherSource.current() }?.let { weather = it; VeilCache.weather = it } }
        }
        if (NextDnsStats.enabled && now - lastWeekly > 30 * 60_000) {
            lastWeekly = now
            scope.launch { withContext(Dispatchers.IO) { NextDnsStats.weekly() }?.let { setWeekly(it) } }
        }
        if (NextDnsStats.enabled && dnsOn && now - lastStats > 5 * 60_000) {
            lastStats = now
            scope.launch {
                blockedToday = withContext(Dispatchers.IO) { NextDnsStats.blockedLast24h() } ?: blockedToday
                VeilCache.blocked = blockedToday
            }
        }
    }

    // ---- Hold-OK menus on the top-bar pills ------------------------------------------------------

    var privacySheet by mutableStateOf(false)
    var musicSheet by mutableStateOf(false)
    /** Hold menu for a Continue Watching card. */
    var watchSheet by mutableStateOf<WatchItem?>(null)

    // ---- Short confirmation message at the bottom of the screen ----
    var toast by mutableStateOf<String?>(null)
        private set
    private var toastJob: Job? = null
    fun showToast(msg: String) {
        toast = msg
        toastJob?.cancel()
        toastJob = scope.launch { delay(3_500); toast = null }
    }

    // ---- Continue Watching actions (written to Trakt, not just hidden here) ----

    private fun label(item: WatchItem) = if (item.isMovie) item.title else "${item.title} S${item.season} E${item.episode}"

    /** Adds it to your Trakt history; the row then moves on to the next episode. */
    fun markWatched(item: WatchItem) = traktAction(item, "Marked ${label(item)} as watched") { trakt.markWatched(item) }

    /** Clears progress on Trakt and hides the show from Trakt's progress (Up Next). */
    fun removeFromRow(item: WatchItem) = traktAction(item, "Removed ${item.title} from Continue Watching") { trakt.removeFromProgress(item) }

    private fun traktAction(item: WatchItem, done: String, call: () -> Boolean) {
        items = items.filterNot { it.key == item.key }          // instant feedback
        if (focused?.key == item.key) focused = items.firstOrNull()
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching(call).getOrDefault(false) }
            if (ok) { dev.veil.launcher.ui.Sfx.success(); showToast(done) }
            else { dev.veil.launcher.ui.Sfx.error(); showToast("Couldn't reach Trakt. Nothing was changed") }
            lastLoad = 0
            refreshWatching(force = true)                          // pick up the next episode / restore on failure
        }
    }

    // ---- Interface sounds ----
    var sounds by mutableStateOf(dev.veil.launcher.ui.Sfx.enabled)
        private set
    fun toggleSounds() {
        sounds = !sounds
        dev.veil.launcher.ui.Sfx.setEnabled(context, sounds)
        if (sounds) dev.veil.launcher.ui.Sfx.on()
    }

    // ---- Lyrics ----
    /** Synced lyrics next to the album art in Veil music mode. */
    var lyricsMusic by mutableStateOf(context.getSharedPreferences("veil", Context.MODE_PRIVATE).getBoolean("lyrics_music", true))
        private set
    fun toggleLyricsMusic() {
        lyricsMusic = !lyricsMusic
        context.getSharedPreferences("veil", Context.MODE_PRIVATE).edit().putBoolean("lyrics_music", lyricsMusic).apply()
        if (lyricsMusic) dev.veil.launcher.ui.Sfx.on() else dev.veil.launcher.ui.Sfx.off()
    }

    // ---- Colours from artwork ----
    val artColors: Boolean get() = dev.veil.launcher.ui.ArtTint.enabled
    fun toggleArtColors() {
        val on = !artColors
        dev.veil.launcher.ui.ArtTint.setEnabled(context, on)
        if (on) dev.veil.launcher.ui.Sfx.on() else dev.veil.launcher.ui.Sfx.off()
    }

    // ---- Veil menu (drops down from the wordmark) -------------------------------------------------

    var veilMenuOpen by mutableStateOf(false)
    var vizSettingsOpen by mutableStateOf(false)
    /** Which Veil-menu button should get focus back after its window closes ("viz", "weather"). */
    var menuReturn by mutableStateOf<String?>(null)
    /** True while the wordmark (or its menu) has focus; used to undo Android's default focus landing there. */
    var wordmarkFocused = false
    /** The last item used on Home ("app:<pkg>", "cw:<key>", "all-apps"); Home returns focus to it. */
    var lastFocusKey: String? = null
    var weatherPicker by mutableStateOf(false)
    var weatherPlace by mutableStateOf(weatherSource.placeName)
        private set
    val weatherHasOverride: Boolean get() = weatherSource.hasOverride
    val weatherDefault: String get() = weatherSource.defaultName

    fun setWeatherPlace(place: Weather.Place) {
        weatherSource.setOverride(place)
        afterWeatherChange()
    }

    fun resetWeatherPlace() {
        weatherSource.clearOverride()
        afterWeatherChange()
    }

    private fun afterWeatherChange() {
        weatherPlace = weatherSource.placeName
        weather = null
        VeilCache.weather = null
        lastWeather = 0
        refreshExtras()
    }

    fun setTheme(theme: VeilThemeSpec) = ThemeState.set(context, theme)

    /** Music mode in Veil Ambient: "veil" (album art) or "viz" (visualizer). */
    var musicStyle by mutableStateOf(prefs.getString("music_style", "veil") ?: "veil")
        private set
    val visualizerAllowed: Boolean
        get() = visualizerSupported &&
            context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /** False on TVs without OpenGL ES 3.0 (the visualizer can't run there). */
    val visualizerSupported: Boolean by lazy { dev.veil.launcher.viz.VizSettings.supported(context) }

    fun chooseMusicStyle(style: String) {
        musicStyle = style
        prefs.edit().putString("music_style", style).apply()
    }

    /** Moves focus back to the first Continue Watching pill (e.g. after closing a menu). */
    fun focusHome() { homeTick++ }

    // ---- Veil Ambient (idle mode / music mode) ---------------------------------------------------

    var ambient by mutableStateOf(false)
        private set
    private var lastInput = System.currentTimeMillis()
    private var swallowKey: Int? = null

    fun startAmbient() { ambient = true }

    // ---- Wake-up intro -----------------------------------------------------------------------------

    enum class Intro { NONE, ARMED, PLAYING }
    /** ARMED = screen went off; shows a plain background so waking never flashes the old Home first. */
    var intro by mutableStateOf(Intro.NONE)
    private var screenOffAt = 0L
    private var screenOnAt = 0L

    fun playIntro() { ambient = false; intro = Intro.PLAYING }

    fun onScreenOff() {
        screenOffAt = android.os.SystemClock.elapsedRealtime()
        intro = Intro.ARMED
    }

    fun onScreenOn(veilResumed: Boolean) {
        screenOnAt = android.os.SystemClock.elapsedRealtime()
        if (intro == Intro.ARMED && veilResumed) playIntro()
    }

    /** Veil came to the front: play the intro if this is a wake-up, otherwise drop the armed state. */
    fun onResumedForIntro() {
        if (intro != Intro.ARMED) return
        val now = android.os.SystemClock.elapsedRealtime()
        when {
            screenOnAt < screenOffAt -> scope.launch {     // screen-on broadcast still on its way
                delay(1_500)
                if (intro == Intro.ARMED) playIntro()
            }
            now - screenOnAt < 15_000 -> playIntro()       // woke up and landed (or came back) Home
            else -> intro = Intro.NONE                     // TV woke into another app a while ago
        }
    }

    /** Called for every key the activity receives. Returns true if the key should be swallowed. */
    fun onKey(keyCode: Int, down: Boolean): Boolean {
        lastInput = System.currentTimeMillis()
        if (intro == Intro.ARMED) intro = Intro.NONE         // never leave a blank screen up
        if (down && intro == Intro.PLAYING) {               // any button skips the intro
            intro = Intro.NONE
            swallowKey = keyCode
            return true
        }
        if (down && ambient) {
            ambient = false
            swallowKey = keyCode
            return true
        }
        if (!down && swallowKey == keyCode) {
            swallowKey = null
            return true
        }
        return false
    }

    fun resetIdle() { lastInput = System.currentTimeMillis(); ambient = false }

    /** Music playing: ambient after 30 s idle on Home. Otherwise after 5 min. */
    fun checkIdle() {
        if (ambient || screen != Screen.HOME || sheet != null || privacySheet || musicSheet || weatherPicker ||
            vizSettingsOpen || watchSheet != null
        ) return
        val idle = System.currentTimeMillis() - lastInput
        val playing = nowPlaying.track?.playing == true
        if ((playing && idle >= 30_000) || idle >= 5 * 60_000) ambient = true
    }

    fun openNowPlayingApp() {
        val pkg = nowPlaying.track?.pkg ?: return
        val pm = context.packageManager
        (pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg))
            ?.let { start(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    // ---- Trakt sign-in ------------------------------------------------------------------------

    fun startConnect() {
        sheet = null
        if (!trakt.hasKeys) {
            connectStatus = ""
            editClientId()
            screen = Screen.CONNECT
            return
        }
        editingClientId = false
        screen = Screen.CONNECT
        connectStatus = "Getting a code…"
        deviceCode = null
        connectJob?.cancel()
        connectJob = scope.launch {
            try {
                val dc = withContext(Dispatchers.IO) { trakt.requestDeviceCode() }
                deviceCode = dc
                connectStatus = "Waiting for you to approve…"
                var interval = dc.interval.coerceAtLeast(1)
                val end = System.currentTimeMillis() + dc.expiresIn * 1000L
                while (System.currentTimeMillis() < end) {
                    delay(interval * 1000L)
                    when (withContext(Dispatchers.IO) { runCatching { trakt.pollDeviceToken(dc) }.getOrDefault(Trakt.Poll.PENDING) }) {
                        Trakt.Poll.AUTHORIZED -> {
                            connected = true
                            deviceCode = null
                            screen = Screen.HOME
                            refreshWatching(force = true)
                            return@launch
                        }
                        Trakt.Poll.SLOW_DOWN -> interval++
                        Trakt.Poll.FAILED -> { connectStatus = "Sign-in was denied or the code expired. Press Back and try again."; return@launch }
                        Trakt.Poll.PENDING -> Unit
                    }
                }
                connectStatus = "The code expired. Press Back and try again."
            } catch (e: Trakt.BadClientId) {
                connectStatus = "Trakt didn't accept this client ID. Check it and enter it again."
                editClientId()
            } catch (e: Exception) {
                connectStatus = "Couldn't reach Trakt. Check the network and try again."
            }
        }
    }

    /** Shows the client ID field (stops any sign-in in progress). */
    fun editClientId() {
        connectJob?.cancel()
        deviceCode = null
        editingClientId = true
    }

    /** Switches Trakt app (null: back to Veil's shared one), then starts sign-in with it. */
    fun saveClientId(id: String?) {
        trakt.setClientId(id)
        connected = false
        items = emptyList()
        focused = null
        focusedIndex = 0
        startConnect()
    }

    fun cancelConnect() {
        connectJob?.cancel()
        deviceCode = null
        editingClientId = false
        screen = Screen.HOME
    }

    fun disconnect() {
        trakt.disconnect()
        connected = false
        items = emptyList()
        focused = null
        focusedIndex = 0
    }

    // ---- Launching ----------------------------------------------------------------------------

    fun launch(app: AppEntry) = start(Intent(app.launch).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    fun openSettings() = start(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    fun openAppInfo(app: AppEntry) = start(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    /** Opens the item straight into Nuvio's stream picker, the same way Nuvio's own Watch Next cards do. */
    fun open(item: WatchItem) {
        val i = Intent(Intent.ACTION_MAIN)
            .setClassName(NUVIO, "$NUVIO.MainActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val imdb = item.imdb
        if (imdb != null) {
            i.putExtra("contentId", imdb)
            i.putExtra("contentType", if (item.isMovie) "movie" else "series")
            i.putExtra("launchMode", "stream")
            i.putExtra("videoId", if (item.isMovie) imdb else "$imdb:${item.season}:${item.episode}")
            i.putExtra("name", item.title)
            if (!item.isMovie) {
                i.putExtra("season", item.season)
                i.putExtra("episode", item.episode)
                item.episodeTitle?.let { i.putExtra("episodeTitle", it) }
            }
            item.poster?.let { i.putExtra("poster", it) }
            item.thumb?.let { i.putExtra("backdrop", it) }
            item.logo?.let { i.putExtra("logo", it) }
        }
        if (!start(i)) {
            context.packageManager.getLeanbackLaunchIntentForPackage(NUVIO)?.let {
                start(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent); true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    companion object {
        const val NUVIO = "com.nuvio.tv"
        val DEFAULT_PINS = listOf(Spotlight.NETFLIX, Spotlight.PRIME, Spotlight.STREMIO)
    }
}
