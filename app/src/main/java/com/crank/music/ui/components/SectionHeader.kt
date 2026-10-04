package com.crank.music.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.crank.music.ui.theme.ChampagneGold
import com.crank.music.ui.theme.TextSecondary
import com.crank.music.ui.theme.WarmWhite

/**
 * A section title, with an optional leading icon and an optional supporting line.
 *
 * ## Why this one signature replaced six
 *
 * Five settings-style screens (Audio Quality, Equalizer, Privacy & Security, Playback Settings and
 * Offline Music) each declared their own private `SectionHeader` — all of them the same
 * `Icon + Spacer(8.dp) + Text(titleMedium, Bold)` trio, copy-pasted. They had already drifted: three
 * drew an 18 dp icon and one a 20 dp one, and the icon's `contentDescription` was `null` in every copy.
 *
 * Meanwhile this file already held the plain-title header the browsing screens use. So the app had two
 * components for one concept and five private copies of one of them, which is why a spacing or
 * typography change could never land everywhere at once.
 *
 * This now covers both shapes:
 * - **[title] only** — the browsing form (Search, Queue, Profile), rendered as a `headlineMedium`
 *   block heading; no icon, no accent, and a bottom margin suited to a carousel below it.
 * - **[title] + [icon]** — the settings form, rendered as a smaller `titleMedium` row.
 *
 * The icon is marked decorative: it sits beside the title it illustrates and is never the only carrier
 * of meaning, so announcing it separately would just make the screen read as "shield, Privacy
 * Dashboard". The nested `Column` in the icon row keeps [subtitle] aligned under the title rather than
 * under the icon.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    accentColor: androidx.compose.ui.graphics.Color = ChampagneGold,
    /** Icon size for the settings form. Ignored when [icon] is null. */
    iconSize: androidx.compose.ui.unit.Dp = 18.dp,
) {
    if (icon == null && subtitle == null) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = WarmWhite,
            modifier = modifier.padding(bottom = 12.dp)
        )
        return
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                // Decorative: the adjacent title carries the meaning.
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(iconSize)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        if (subtitle == null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = WarmWhite
            )
        } else {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = WarmWhite
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}
