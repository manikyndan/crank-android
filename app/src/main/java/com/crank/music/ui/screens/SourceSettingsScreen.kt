package com.crank.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crank.music.ui.components.SectionHeader
import com.crank.music.ui.theme.ChampagneGold
import com.crank.music.ui.theme.CharcoalSurface
import com.crank.music.ui.theme.ObsidianBlack
import com.crank.music.ui.theme.TextSecondary
import com.crank.music.ui.theme.WarmWhite
import com.crank.music.ui.viewmodel.ConnectionTest
import com.crank.music.ui.viewmodel.SourceSettingsState
import com.crank.music.ui.viewmodel.SourceSettingsViewModel

/** Semantic, not brand: these describe probe outcomes rather than belonging to the palette. */
private val SuccessGreen = Color(0xFF3FB950)
private val FailureRed = Color(0xFFF44336)

/**
 * Configures the optional self-hosted Gaana source.
 *
 * The screen states *what happens* when nothing is set — "YouTube only" — rather than presenting an
 * empty field as an error, because an unconfigured server is the normal state for anyone who has
 * not stood one up.
 *
 * It also says plainly that a `http://` LAN address must be allow-listed in
 * `res/xml/network_security_config.xml`: Android rejects cleartext by default, and without this
 * note the failure reads as a broken server rather than a blocked request.
 */
@Composable
fun SourceSettingsScreen(
    viewModel: SourceSettingsViewModel = hiltViewModel(),
    onBackClick: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = WarmWhite,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Music Sources",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = WarmWhite
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "YouTube Music is always available. Gaana is an additional source, added to " +
                    "results when a server is configured — it never replaces YouTube.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )

            Spacer(modifier = Modifier.height(16.dp))

            SourceCard(state = state, viewModel = viewModel)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "A plain http:// address on your local network must be listed in " +
                    "app/src/main/res/xml/network_security_config.xml — Android blocks cleartext " +
                    "traffic by default, so an unlisted host is refused before it is ever reached. " +
                    "A tunnel such as ngrok or Cloudflare Tunnel is served over https and needs no " +
                    "change.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SourceCard(
    state: SourceSettingsState,
    viewModel: SourceSettingsViewModel,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = CharcoalSurface,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(title = "Gaana")

            Spacer(modifier = Modifier.height(4.dp))

            StatusLine(savedUrl = state.savedUrl)

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = state.input,
                onValueChange = viewModel::onInputChanged,
                label = { Text("Server base URL", color = TextSecondary) },
                placeholder = { Text("http://192.168.1.42:8000", color = TextSecondary) },
                isError = state.inputError != null,
                supportingText = {
                    val message = state.inputError
                    Text(
                        text = message ?: "Leave blank to disable this source.",
                        color = if (message != null) FailureRed else TextSecondary,
                    )
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = ObsidianBlack,
                    unfocusedContainerColor = ObsidianBlack,
                    disabledContainerColor = ObsidianBlack,
                    focusedBorderColor = ChampagneGold,
                    unfocusedBorderColor = TextSecondary,
                    focusedTextColor = WarmWhite,
                    unfocusedTextColor = WarmWhite,
                    cursorColor = ChampagneGold,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                TestResult(state.test)
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = viewModel::testConnection) {
                    Text(
                        text = "Test connection",
                        color = ChampagneGold,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = viewModel::save,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ChampagneGold,
                        contentColor = ObsidianBlack,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = "Save",
                        style = MaterialTheme.typography.labelLarge,
                        color = ObsidianBlack,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusLine(savedUrl: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (savedUrl.isBlank()) "Not configured — searching YouTube only" else "Configured",
            style = MaterialTheme.typography.labelLarge,
            color = if (savedUrl.isBlank()) TextSecondary else SuccessGreen,
        )
        if (savedUrl.isNotBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = savedUrl,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TestResult(test: ConnectionTest) {
    when (test) {
        ConnectionTest.IDLE -> Unit
        ConnectionTest.RUNNING -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = ChampagneGold,
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Testing…", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        ConnectionTest.OK -> Text(
            text = "Server reachable",
            color = SuccessGreen,
            style = MaterialTheme.typography.bodySmall,
        )
        ConnectionTest.FAILED -> Text(
            text = "No response — check the address and that the server is running",
            color = FailureRed,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

