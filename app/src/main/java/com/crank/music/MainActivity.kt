package com.crank.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.crank.music.ui.screens.MainScreen
import com.crank.music.ui.theme.CrankTheme
import com.crank.music.ui.theme.ThemePreference
import com.crank.music.ui.theme.isLight
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialMode = themePreference.initialMode()

        setContent {
            val mode by themePreference.mode.collectAsState()

            // `AUTO` is the only mode whose answer depends on the system setting, and this is
            // the only layer that can observe it. `isSystemInDarkTheme` is a composable read,
            // so it stays inside setContent.
            val systemInDarkTheme = isSystemInDarkTheme()
            val effectiveMode = mode ?: initialMode

            CrankTheme(darkTheme = !effectiveMode.isLight(systemInDarkTheme)) {
                MainScreen()
            }
        }
    }
}
