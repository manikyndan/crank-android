package com.crank.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.crank.music.R
import com.crank.music.ui.theme.ErrorMuted
import com.crank.music.ui.theme.GoldMuted
import com.crank.music.ui.theme.ObsidianBlack
import com.crank.music.ui.theme.TextSecondary
import com.crank.music.ui.theme.WarmWhite

/**
 * The shared layout behind every full-screen state: an icon, a title, an explanatory line and an
 * optional action.
 *
 * [EmptyState] and [ErrorState] are the two things this app ever has to say at full-screen size —
 * "there is nothing here" and "this did not load". They differ only in the glyph, its colour, and the
 * label on the action, so they share one layout rather than two that drift apart.
 */
@Composable
private fun StateSurface(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    val hasAction = actionText != null && onAction != null

    Column(
        modifier = modifier
            .fillMaxWidth()
            // The library variant wanted more breathing room than the browsing one; one value covers
            // both without either looking cramped.
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = iconTint,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = WarmWhite,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        if (hasAction) {
            Spacer(modifier = Modifier.height(20.dp))
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = onAction),
                color = WarmWhite,
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    // Contrast against the button fill, which is the theme's primary text colour.
                    color = ObsidianBlack,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                )
            }
        }
    }
}

/**
 * The app's single empty state.
 *
 * ## Why this replaced two
 *
 * `LibraryScreen` declared its own private `YtEmptyState` — the same icon/title/message stack, plus an
 * optional call-to-action button — while this shared component already existed and was used by Search,
 * Queue and Profile. So two screens that both meant "there is nothing here yet" rendered it
 * differently: 72 dp circular icon badge versus a 64 dp bare icon, `titleMedium` versus
 * `headlineMedium`, and `MaterialTheme.colorScheme` versus the app's own palette tokens.
 *
 * They are now one component. [ctaText] and [onCtaClick] are both optional, which is what let the
 * library variant fold in: pass neither and the empty state is purely informational, as Search and
 * Queue use it; pass both and it offers the action that fills the screen, as the Library tabs do.
 *
 * Colours come from the palette tokens rather than `colorScheme`, so this follows the Light/Dark/OLED
 * choice made in Appearance settings like the rest of the app.
 *
 * @param icon illustrative glyph; announced as [title] because it conveys the same meaning.
 * @param ctaText label for the optional action button. Ignored when [onCtaClick] is null.
 * @param onCtaClick invoked when the action button is tapped. When null, no button is drawn.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    ctaText: String? = null,
    onCtaClick: (() -> Unit)? = null
) {
    StateSurface(
        icon = icon,
        iconTint = GoldMuted,
        title = title,
        message = message,
        modifier = modifier,
        actionText = ctaText,
        onAction = onCtaClick
    )
}

/**
 * The app's single error state, with retry.
 *
 * ## Why this replaced five
 *
 * Every screen that could fail invented its own way of saying so. `HomeScreen` hand-rolled a message
 * plus a filled `Button` labelled "Retry"; `UpdateCheckerScreen` had a private `CannotCheckView` with
 * a 64 dp `Info` glyph and a `TextButton` labelled "Try again"; `OfflineMusicScreen` grew a third
 * retry row. Three screens, three button styles, two different words for the same action — and the
 * whole point of a retry affordance is that it is recognisable without reading it.
 *
 * [onRetry] is nullable because not every failure is retryable: a missing API token or an
 * unsupported device will fail identically however many times it is tapped. When it is null no
 * button is drawn, which is more honest than a button that cannot help.
 *
 * @param icon defaults to the outline error glyph; pass something more specific where one exists
 *   (an offline state, for example) so the screen reads at a glance.
 * @param onRetry invoked on tap. When null, the state is presented as informational only.
 */
@Composable
fun ErrorState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.ErrorOutline,
    retryText: String = stringResource(R.string.action_retry),
    onRetry: (() -> Unit)? = null
) {
    StateSurface(
        icon = icon,
        iconTint = ErrorMuted,
        title = title,
        message = message,
        modifier = modifier,
        actionText = if (onRetry != null) retryText else null,
        onAction = onRetry
    )
}
