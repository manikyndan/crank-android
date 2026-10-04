package com.manikyndan.crank

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.manikyndan.crank.presentation.ui.screen.ItemScreen
import com.manikyndan.crank.presentation.ui.theme.CrankScaffoldTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Scaffold entry point for the `com.manikyndan.crank` sample graph.
 *
 * NOTE: the production launcher remains `com.crank.music.MainActivity` (declared in the
 * manifest). Register this activity in `AndroidManifest.xml` only if you want to launch
 * the scaffold screen directly.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CrankScaffoldTheme {
                ItemScreen()
            }
        }
    }
}
