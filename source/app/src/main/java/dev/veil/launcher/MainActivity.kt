package dev.veil.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.veil.launcher.ui.ThemeState
import dev.veil.launcher.ui.VeilApp
import dev.veil.launcher.ui.VeilTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var state: LauncherState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeState.load(applicationContext)
        dev.veil.launcher.ui.ArtTint.load(applicationContext)
        dev.veil.launcher.ui.Sfx.init(applicationContext)
        dev.veil.launcher.viz.VizSettings.load(applicationContext)
        state = LauncherState(applicationContext, lifecycleScope)
        // No accessibility tree: Veil's own Home-button helper is an accessibility service, and while any
        // service is on, Compose rebuilds and re-sorts a screen-reader description of every element on
        // every animated frame (measured: ~70% of the main thread in music mode). Focus, navigation and
        // clicks don't use it.
        setContent {
            VeilTheme {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.clearAndSetSemantics { }) { VeilApp(state) }
            }
        }
        // Fresh start right after the TV boots: play the intro.
        if (android.os.SystemClock.elapsedRealtime() < 10 * 60_000L) state.playIntro()
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
        })
        // While Veil is on screen, keep the weather and blocked counter fresh.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    state.refreshExtras()
                    delay(60_000)
                }
            }
        }
        // Idle check for Veil Ambient.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(5_000)
                    state.checkIdle()
                }
            }
        }
    }

    /** Screen off arms the wake-up intro; screen on plays it if Veil is what's showing. */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> state.onScreenOff()
                Intent.ACTION_SCREEN_ON -> state.onScreenOn(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        if (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP) {
            if (state.onKey(event.keyCode, down)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStart() {
        super.onStart()
        state.nowPlaying.start()
    }

    override fun onStop() {
        state.nowPlaying.stop()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        HomeServiceGuard.ensureEnabled(this)
        state.resetIdle()
        state.onResumedForIntro()
        state.refreshExtras()
        state.refreshApps()
        state.refreshWatching()
        state.refreshSpotlight()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        state.onHomePressed()
        checkFocusOnWindowFocus = true
    }

    /**
     * When Veil comes back to the front, Android gives focus to the first focusable item (the wordmark,
     * top-left) once the window is focused — after our own focus request has already run. So check again
     * at that moment and move focus to Continue Watching if it landed on the wordmark by default.
     */
    private var checkFocusOnWindowFocus = true

    override fun onPause() {
        super.onPause()
        checkFocusOnWindowFocus = true
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || !checkFocusOnWindowFocus) return
        checkFocusOnWindowFocus = false
        lifecycleScope.launch {
            delay(80)
            if (state.wordmarkFocused) state.focusHome()
        }
    }
}
