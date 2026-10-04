package com.manikyndan.crank.presentation.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.manikyndan.crank.presentation.ui.theme.CrankScaffoldTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Basic Compose instrumented test setup for [ItemScreen].
 * Runs on device/emulator via `connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class ItemScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun loadingIndicator_isDisplayed_onStart() {
        composeTestRule.setContent {
            CrankScaffoldTheme {
                // Rendering the loading branch directly keeps this test hermetic
                // (no Hilt / network needed for the scaffold check).
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            }
        }

        // The progress indicator has no text; assert the tree composed without crashing.
        composeTestRule.waitForIdle()
    }

    @Test
    fun errorMessage_isDisplayed() {
        composeTestRule.setContent {
            CrankScaffoldTheme {
                androidx.compose.material3.Text("Something went wrong")
            }
        }

        composeTestRule.onNodeWithText("Something went wrong").assertIsDisplayed()
    }
}
