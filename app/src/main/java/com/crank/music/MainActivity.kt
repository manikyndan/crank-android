package com.crank.music

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.crank.music.ui.screens.MainScreen
import com.crank.music.ui.theme.CrankTheme
import com.crank.music.ui.theme.ThemePreference
import com.crank.music.ui.theme.isLight
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Media notifications (the lock-screen / shade player) are suppressed on
     * API 33+ until this is granted. Asked once at launch — it guards a system
     * surface, not an app screen, so no in-app UI is involved.
     */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    /**
     * Injected rather than held in a screen's ViewModel.
     *
     * The theme has to be chosen above `MainScreen`, so the preference cannot live inside a
     * composable destination. [ThemePreference.initialMode] is read once here to seed the flow
     * before the first frame, which is what prevents a light-mode user from seeing a black
     * flash on cold start.
     */
    @Inject
    lateinit var themePreference: ThemePreference

    /**
     * Route requested by a tapped notification (the update notification sets a `navigate_to`
     * extra). Held as state so [onNewIntent] can update it while the app is already open, and
     * cleared once `MainScreen` has navigated so a recomposition cannot navigate again.
     */
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // A cold start from a tapped notification carries the requested route here.
        pendingRoute = intent?.getStringExtra(EXTRA_NAVIGATE_TO)

        val initialMode = themePreference.initialMode()
        val initialAppearance = themePreference.initialAppearance()

        setContent {
            val mode by themePreference.mode.collectAsStateWithLifecycle()
            val appearance by themePreference.appearance.collectAsStateWithLifecycle()

            // `AUTO` is the only mode whose answer depends on the system setting, and this is
            // the only layer that can observe it. `isSystemInDarkTheme` is a composable read,
            // so it stays inside setContent.
            val systemInDarkTheme = isSystemInDarkTheme()
            val effectiveMode = mode ?: initialMode

            // Falls back to the synchronously-read value for the first frame, so the accent and text
            // scale are already correct on the very first composition rather than one frame later.
            val effectiveAppearance =
                appearance.takeIf { it.isLoaded } ?: initialAppearance

            CrankTheme(
                darkTheme = !effectiveMode.isLight(systemInDarkTheme),
                accentArgb = effectiveAppearance.accentArgb,
                typographyScale = effectiveAppearance.typographyScale,
            ) {
                MainScreen(
                    startRoute = pendingRoute,
                    onRouteConsumed = { pendingRoute = null },
                )
            }
        }
    }

    /**
     * Handles an intent delivered while the activity is already running.
     *
     * The update notification's PendingIntent uses SINGLE_TOP | CLEAR_TOP, so tapping it while
     * Crank is open arrives here rather than recreating the activity.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute = intent.getStringExtra(EXTRA_NAVIGATE_TO)
    }

    companion object {
        /** Extra the update notification sets to open a specific screen. */
        const val EXTRA_NAVIGATE_TO = "navigate_to"
    }
}
