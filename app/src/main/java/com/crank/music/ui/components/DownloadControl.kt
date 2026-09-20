package com.crank.music.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.crank.music.domain.model.DownloadState
import kotlin.math.roundToInt

/**
 * The one place that decides what a download control looks like.
 *
 * The player control and the three-dot menu show the same fact about the same song, so they
 * have to agree on all three of its visible properties — glyph, wording, and whether the row
 * is tappable. When each surface spelled this out itself the two could disagree after a change
 * to either one, which is exactly the inconsistency that prompted this. Both now read from
 * here, so a change lands on both or neither.
 *
 * The three states the user sees:
 * - **not downloaded** → Download arrow, tappable.
 * - **downloading** → Downloading glyph, not tappable (there is nothing useful to tap).
 * - **downloaded** → a plain checkmark (✓), not tappable. Deliberately *not* a circled check:
 *   a ring reads as a status badge, while a bare tick reads as "done" at a glance and matches
 *   the tick used elsewhere in the app for a completed state.
 */
internal object DownloadControlVisuals {

    /**
     * Glyph for [state].
     *
     * `FAILED` maps to the Download arrow rather than an error glyph, because a failed transfer
     * is retried by pressing Download again — the arrow is the affordance that performs the
     * retry, so showing an error icon would advertise a dead end.
     */
    fun icon(state: DownloadState): ImageVector = when (state) {
        DownloadState.COMPLETED -> Icons.Default.Check
        DownloadState.DOWNLOADING -> Icons.Default.Downloading
        DownloadState.IDLE, DownloadState.FAILED -> Icons.Default.Download
    }

    /** Row wording for [state]. The ellipsis marks an operation still in progress. */
    fun label(state: DownloadState): String = when (state) {
        DownloadState.COMPLETED -> "Downloaded"
        DownloadState.DOWNLOADING -> "Downloading…"
        DownloadState.IDLE, DownloadState.FAILED -> "Download"
    }

    /**
     * Whether the control should accept a tap.
     *
     * False once the transfer is running or finished. Leaving a completed row tappable would
     * invite a second download of audio that is already on disk, and leaving a running one
     * tappable would queue a duplicate transfer.
     */
    fun isActionable(state: DownloadState): Boolean = when (state) {
        DownloadState.COMPLETED, DownloadState.DOWNLOADING -> false
        DownloadState.IDLE, DownloadState.FAILED -> true
    }

    /**
     * "45%" for a 0f..1f fraction.
     *
     * Clamped before rounding, so a float that overshot to 1.0001 cannot render "101%".
     */
    fun percentLabel(fraction: Float): String =
        "${(fraction.coerceIn(0f, 1f) * 100f).roundToInt()}%"
}

/**
 * How long the arc takes to travel to a newly reported position.
 *
 * Media3 reports progress in steps, not continuously, so the raw value would advance in visible
 * jumps. Interpolating over roughly one reporting interval turns those steps into one continuous
 * sweep. Linear easing, not an ease curve: this is a measurement catching up to reality, and an
 * ease would make it surge and stall on every tick instead of moving at a steady rate.
 */
private const val PROGRESS_TWEEN_MS = 900

/**
 * A download glyph that also shows how far along the transfer is.
 *
 * While downloading, a determinate arc sweeps clockwise from twelve o'clock around the glyph.
 * A null [fraction] means the total size is not yet known — Media3 reports `contentLength = -1`
 * until the server answers, which is the first moment of every transfer — and the arc sweeps
 * indeterminately instead, because a ring frozen at 0% is indistinguishable from a stalled one.
 *
 * [fraction] is expected to be the *interpolated* value from [animatedDownloadFraction], not the
 * raw engine reading. That keeps the arc and any percentage label beside it driven by one shared
 * value; if each animated its own copy they would drift apart on every tick and the number would
 * visibly disagree with the ring.
 *
 * The ring is a separate, larger element rather than a tint change on the glyph, so progress is
 * readable without reading the percentage. The glyph scales down as the ring appears, which is
 * what makes the transition read as one control changing state rather than two icons swapping.
 */
@Composable
internal fun DownloadProgressIcon(
    state: DownloadState,
    fraction: Float?,
    tint: Color,
    modifier: Modifier = Modifier,
    ringSize: Dp = 28.dp,
    /**
     * Announced state. Left null in the three-dot sheet, where the row's own text already says
     * it and a second announcement would only repeat it; set on the player control, which has no
     * adjacent label.
     */
    contentDescription: String? = null,
) {
    val downloading = state == DownloadState.DOWNLOADING

    // Non-finite is treated as unknown, so a bad value degrades to the indeterminate sweep
    // rather than drawing an arc from a NaN angle. `coerceIn` alone would not catch it.
    val safeFraction = fraction?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)

    // The ring fades rather than appearing, and the glyph gives it room by shrinking. Both are
    // driven off the state, so the two move together and the control never looks like it
    // changed twice.
    val ringAlpha by animateFloatAsState(
        targetValue = if (downloading) 1f else 0f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "download_ring_alpha",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (downloading) 0.74f else 1f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "download_icon_scale",
    )

    val spin = rememberInfiniteTransition(label = "download_spin")
    val rotation by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
        ),
        label = "download_spin_rotation",
    )

    Box(modifier.size(ringSize), contentAlignment = Alignment.Center) {
        if (ringAlpha > 0.01f) {
            Canvas(Modifier.fillMaxSize()) {
                val strokePx = 2.dp.toPx()
                val inset = strokePx / 2f
                val arcSize = Size(size.width - strokePx, size.height - strokePx)
                val topLeft = Offset(inset, inset)

                drawArc(
                    color = tint.copy(alpha = 0.22f * ringAlpha),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )

                if (safeFraction == null) {
                    // Unknown total: a short arc orbiting the track.
                    rotate(rotation) {
                        drawArc(
                            color = tint.copy(alpha = ringAlpha),
                            startAngle = -90f,
                            sweepAngle = 80f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokePx, cap = StrokeCap.Round),
                        )
                    }
                } else {
                    drawArc(
                        color = tint.copy(alpha = ringAlpha),
                        startAngle = -90f,
                        sweepAngle = safeFraction * 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    )
                }
            }
        }

        Icon(
            imageVector = DownloadControlVisuals.icon(state),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                },
        )
    }
}

/**
 * The fraction the UI should draw, interpolated between engine reports.
 *
 * Media3 reports progress in steps, not continuously, so the raw value would advance in visible
 * jumps. Interpolating over roughly one reporting interval turns those steps into one continuous
 * sweep. Linear easing, not an ease curve: this is a measurement catching up to reality, and an
 * ease would make it surge and stall on every tick instead of moving at a steady rate.
 *
 * Null in, null out — an unknown total stays unknown so the caller can still choose an
 * indeterminate treatment rather than being handed a 0 that looks like a stall.
 */
@Composable
internal fun animatedDownloadFraction(fraction: Float?): Float? {
    // `isFinite` also rejects NaN, and that check is not redundant with the clamp.
    // `coerceIn` does not filter NaN: NaN compares false against both bounds, so it passes
    // through untouched and then crashes the animation with "AnimationVector cannot contain a
    // NaN". A non-finite fraction is treated as unknown, which is both the safe answer and the
    // honest one — an unmeasurable transfer is not a 0% transfer.
    val target = fraction?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target ?: 0f,
        animationSpec = tween(durationMillis = PROGRESS_TWEEN_MS, easing = LinearEasing),
        label = "download_fraction",
    )
    return if (target == null) null else animated
}


