package com.crank.music.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crank.music.R
import com.crank.music.ui.theme.CrankTheme
import com.crank.music.ui.theme.ObsidianBlack
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CrankMusicBoxCard(
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val crankTitle = stringResource(R.string.crank_app_name)
    val crankSubtitle = stringResource(R.string.crank_tagline)

    val goldBright = Color(0xFFF3E298)
    val goldMid = Color(0xFFD4AF37)
    val goldDark = Color(0xFF8B6914)
    val woodDark = Color(0xFF1E130B)
    val woodMedium = Color(0xFF2E1C11)
    val woodHighlight = Color(0xFF5E3F2B)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(32.dp))
            .border(
                width = 2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        goldBright,
                        goldMid,
                        goldDark,
                        goldBright,
                        goldMid
                    )
                ),
                shape = RoundedCornerShape(32.dp)
            ),
        color = ObsidianBlack
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF4A3410),
                            Color(0xFF221606),
                            Color(0xFF0D0904),
                            Color(0xFF050505)
                        ),
                        center = Offset(w * 0.5f, h * 0.45f),
                        radius = w * 0.72f
                    )
                )

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x33F3E298), Color(0x11D4AF37), Color.Transparent),
                        center = Offset(w * 0.22f, h * 0.28f),
                        radius = w * 0.35f
                    )
                )

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x28F3E298), Color(0x0DD4AF37), Color.Transparent),
                        center = Offset(w * 0.82f, h * 0.42f),
                        radius = w * 0.32f
                    )
                )

                val trailPath1 = Path().apply {
                    moveTo(w * 0.08f, h * 0.45f)
                    cubicTo(
                        w * 0.15f, h * 0.25f,
                        w * 0.35f, h * 0.22f,
                        w * 0.28f, h * 0.42f
                    )
                    cubicTo(
                        w * 0.22f, h * 0.58f,
                        w * 0.05f, h * 0.38f,
                        w * 0.20f, h * 0.26f
                    )
                }
                drawPath(
                    path = trailPath1,
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0x00D4AF37), Color(0xAAFFE89C), Color(0x22D4AF37))
                    ),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )

                val trailPath2 = Path().apply {
                    moveTo(w * 0.75f, h * 0.52f)
                    cubicTo(
                        w * 0.95f, h * 0.42f,
                        w * 0.88f, h * 0.28f,
                        w * 0.78f, h * 0.38f
                    )
                    cubicTo(
                        w * 0.70f, h * 0.48f,
                        w * 0.92f, h * 0.58f,
                        w * 0.96f, h * 0.32f
                    )
                }
                drawPath(
                    path = trailPath2,
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0x00D4AF37), Color(0x99FFE89C), Color(0x11D4AF37))
                    ),
                    style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                )

                drawMusicNote(
                    scope = this,
                    offset = Offset(w * 0.21f, h * 0.26f),
                    scale = 0.85f,
                    color = goldBright,
                    isEighthNote = true
                )

                drawMusicNote(
                    scope = this,
                    offset = Offset(w * 0.24f, h * 0.42f),
                    scale = 0.55f,
                    color = goldMid,
                    isEighthNote = false
                )

                drawMusicNote(
                    scope = this,
                    offset = Offset(w * 0.84f, h * 0.38f),
                    scale = 0.7f,
                    color = goldBright,
                    isEighthNote = false
                )

                drawMusicNote(
                    scope = this,
                    offset = Offset(w * 0.85f, h * 0.50f),
                    scale = 0.9f,
                    color = goldBright,
                    isEighthNote = true
                )

                val sparkles = listOf(
                    Offset(w * 0.15f, h * 0.20f),
                    Offset(w * 0.28f, h * 0.18f),
                    Offset(w * 0.12f, h * 0.35f),
                    Offset(w * 0.26f, h * 0.32f),
                    Offset(w * 0.88f, h * 0.25f),
                    Offset(w * 0.78f, h * 0.22f),
                    Offset(w * 0.92f, h * 0.44f),
                    Offset(w * 0.82f, h * 0.56f)
                )
                sparkles.forEachIndexed { idx, pos ->
                    val r = if ((idx % 2) == 0) 2.5.dp.toPx() else 1.5.dp.toPx()
                    drawCircle(color = goldBright.copy(alpha = 0.8f), radius = r, center = pos)
                    drawCircle(color = Color.White.copy(alpha = 0.6f), radius = r * 0.5f, center = pos)
                }

                val shadowPath = Path().apply {
                    moveTo(w * 0.10f, h * 0.82f)
                    lineTo(w * 0.62f, h * 0.92f)
                    lineTo(w * 0.92f, h * 0.75f)
                    lineTo(w * 0.38f, h * 0.66f)
                    close()
                }
                drawPath(
                    path = shadowPath,
                    color = Color(0x99000000)
                )

                val backBoxPath = Path().apply {
                    moveTo(w * 0.35f, h * 0.12f)
                    lineTo(w * 0.81f, h * 0.15f)
                    lineTo(w * 0.78f, h * 0.65f)
                    lineTo(w * 0.12f, h * 0.54f)
                    close()
                }
                drawPath(
                    path = backBoxPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F0905), Color(0xFF1C110A))
                    )
                )

                val interiorPath = Path().apply {
                    moveTo(w * 0.32f, h * 0.44f)
                    lineTo(w * 0.75f, h * 0.47f)
                    lineTo(w * 0.63f, h * 0.64f)
                    lineTo(w * 0.18f, h * 0.56f)
                    close()
                }
                drawPath(
                    path = interiorPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF080503), Color(0xFF18100A))
                    )
                )

                val basePlatePath = Path().apply {
                    moveTo(w * 0.24f, h * 0.52f)
                    lineTo(w * 0.68f, h * 0.54f)
                    lineTo(w * 0.60f, h * 0.63f)
                    lineTo(w * 0.20f, h * 0.58f)
                    close()
                }
                drawPath(
                    path = basePlatePath,
                    brush = Brush.linearGradient(
                        colors = listOf(goldDark, goldMid, goldBright, goldDark)
                    )
                )

                val cylLeft = Offset(w * 0.38f, h * 0.535f)
                val cylRight = Offset(w * 0.58f, h * 0.555f)

                drawCylinderMechanism(
                    scope = this,
                    start = cylLeft,
                    end = cylRight,
                    goldBright = goldBright,
                    goldMid = goldMid,
                    goldDark = goldDark
                )

                val lidOuterPath = Path().apply {
                    moveTo(w * 0.35f, h * 0.08f)
                    lineTo(w * 0.81f, h * 0.13f)
                    lineTo(w * 0.72f, h * 0.47f)
                    lineTo(w * 0.28f, h * 0.42f)
                    close()
                }
                drawPath(
                    path = lidOuterPath,
                    brush = Brush.linearGradient(
                        colors = listOf(woodHighlight, woodMedium, woodDark),
                        start = Offset(w * 0.35f, h * 0.08f),
                        end = Offset(w * 0.72f, h * 0.47f)
                    )
                )

                drawPath(
                    path = lidOuterPath,
                    brush = Brush.linearGradient(
                        colors = listOf(goldBright, goldMid, goldDark, goldBright)
                    ),
                    style = Stroke(width = 3.5.dp.toPx(), join = StrokeJoin.Round)
                )

                val lidInnerPath = Path().apply {
                    moveTo(w * 0.37f, h * 0.11f)
                    lineTo(w * 0.79f, h * 0.15f)
                    lineTo(w * 0.70f, h * 0.45f)
                    lineTo(w * 0.30f, h * 0.40f)
                    close()
                }
                drawPath(
                    path = lidInnerPath,
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF2C1C12), Color(0xFF140D08)),
                        center = Offset(w * 0.54f, h * 0.28f),
                        radius = w * 0.25f
                    )
                )
                drawPath(
                    path = lidInnerPath,
                    color = goldMid.copy(alpha = 0.5f),
                    style = Stroke(width = 1.dp.toPx())
                )

                drawLidBranding(
                    scope = this,
                    center = Offset(w * 0.54f, h * 0.28f),
                    goldBright = goldBright,
                    goldMid = goldMid,
                    goldDark = goldDark,
                    textMeasurer = textMeasurer,
                    titleText = crankTitle,
                    subtitleText = crankSubtitle
                )

                val frontPanelPath = Path().apply {
                    moveTo(w * 0.12f, h * 0.52f)
                    lineTo(w * 0.62f, h * 0.63f)
                    lineTo(w * 0.60f, h * 0.88f)
                    lineTo(w * 0.12f, h * 0.76f)
                    close()
                }
                drawPath(
                    path = frontPanelPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(woodHighlight, woodMedium, woodDark)
                    )
                )

                drawPath(
                    path = frontPanelPath,
                    brush = Brush.linearGradient(
                        colors = listOf(goldBright, goldMid, goldDark)
                    ),
                    style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round)
                )

                val sidePanelPath = Path().apply {
                    moveTo(w * 0.62f, h * 0.63f)
                    lineTo(w * 0.78f, h * 0.50f)
                    lineTo(w * 0.76f, h * 0.72f)
                    lineTo(w * 0.60f, h * 0.88f)
                    close()
                }
                drawPath(
                    path = sidePanelPath,
                    brush = Brush.horizontalGradient(
                        colors = listOf(woodMedium, woodDark, Color(0xFF0C0704))
                    )
                )
                drawPath(
                    path = sidePanelPath,
                    brush = Brush.linearGradient(
                        colors = listOf(goldMid, goldDark)
                    ),
                    style = Stroke(width = 1.5.dp.toPx(), join = StrokeJoin.Round)
                )

                drawFrontPanelDetails(
                    scope = this,
                    tl = Offset(w * 0.12f, h * 0.52f),
                    tr = Offset(w * 0.62f, h * 0.63f),
                    br = Offset(w * 0.60f, h * 0.88f),
                    bl = Offset(w * 0.12f, h * 0.76f),
                    goldBright = goldBright,
                    goldMid = goldMid,
                    goldDark = goldDark
                )

                drawCrankHandle(
                    scope = this,
                    attachPoint = Offset(w * 0.71f, h * 0.61f),
                    goldBright = goldBright,
                    goldMid = goldMid,
                    goldDark = goldDark,
                    woodDark = woodDark
                )
            }
        }
    }
}

private fun drawMusicNote(
    scope: DrawScope,
    offset: Offset,
    scale: Float,
    color: Color,
    isEighthNote: Boolean
) {
    with(scope) {
        val path = Path().apply {
            val headWidth = 14.dp.toPx() * scale
            val headHeight = 10.dp.toPx() * scale
            val stemHeight = 28.dp.toPx() * scale

            addOval(
                Rect(
                    left = offset.x,
                    top = offset.y,
                    right = offset.x + headWidth,
                    bottom = offset.y + headHeight
                )
            )

            moveTo(offset.x + headWidth * 0.85f, offset.y + headHeight * 0.5f)
            lineTo(offset.x + headWidth * 0.85f, offset.y - stemHeight)

            if (isEighthNote) {
                cubicTo(
                    offset.x + headWidth * 1.5f, offset.y - stemHeight * 0.8f,
                    offset.x + headWidth * 1.8f, offset.y - stemHeight * 0.4f,
                    offset.x + headWidth * 1.2f, offset.y - stemHeight * 0.2f
                )
            }
        }

        drawPath(
            path = path,
            color = color,
            style = Fill
        )

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = (2.5f * scale).dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

private fun drawCylinderMechanism(
    scope: DrawScope,
    start: Offset,
    end: Offset,
    goldBright: Color,
    goldMid: Color,
    goldDark: Color
) {
    with(scope) {
        val angle = atan2(end.y - start.y, end.x - start.x)
        val cylThickness = 18.dp.toPx()

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(goldBright, goldMid, goldDark)
            ),
            radius = cylThickness * 0.7f,
            center = start
        )

        val bodyPath = Path().apply {
            val perpX = -sin(angle) * cylThickness * 0.5f
            val perpY = cos(angle) * cylThickness * 0.5f

            moveTo(start.x + perpX, start.y + perpY)
            lineTo(end.x + perpX, end.y + perpY)
            lineTo(end.x - perpX, end.y - perpY)
            lineTo(start.x - perpX, start.y - perpY)
            close()
        }

        drawPath(
            path = bodyPath,
            brush = Brush.linearGradient(
                colors = listOf(goldBright, goldMid, goldDark, goldBright),
                start = start,
                end = end
            )
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(goldBright, goldDark)
            ),
            radius = cylThickness * 0.8f,
            center = end
        )

        for (i in 0..12) {
            val toothAngle = (i / 12f) * 2 * PI
            val gearR = cylThickness * 0.85f
            val tx = end.x + cos(toothAngle).toFloat() * gearR
            val ty = end.y + sin(toothAngle).toFloat() * gearR
            drawLine(
                color = goldBright,
                start = end,
                end = Offset(tx, ty),
                strokeWidth = 2.dp.toPx()
            )
        }

        val pinsCount = 28
        for (i in 0 until pinsCount) {
            val fraction = (i + 1) / (pinsCount + 1f)
            val px = start.x + (end.x - start.x) * fraction
            val py = start.y + (end.y - start.y) * fraction
            val offsetVariance = ((i * 7) % 11 - 5) * 1.2f

            val perpX = -sin(angle) * offsetVariance
            val perpY = cos(angle) * offsetVariance

            drawCircle(
                color = goldBright,
                radius = 1.8.dp.toPx(),
                center = Offset(px + perpX, py + perpY)
            )
        }
    }
}

private fun drawLidBranding(
    scope: DrawScope,
    center: Offset,
    goldBright: Color,
    goldMid: Color,
    goldDark: Color,
    textMeasurer: TextMeasurer,
    titleText: String,
    subtitleText: String
) {
    with(scope) {
        val logoCenter = Offset(center.x - 2.dp.toPx(), center.y - 22.dp.toPx())

        for (i in 1..3) {
            val arcRadius = (12 + i * 7).dp.toPx()
            drawArc(
                brush = Brush.linearGradient(
                    colors = listOf(goldBright.copy(alpha = 0.9f), goldMid.copy(alpha = 0.4f))
                ),
                startAngle = 135f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(logoCenter.x - arcRadius - 10.dp.toPx(), logoCenter.y - arcRadius),
                size = Size(arcRadius * 2, arcRadius * 2),
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        val cPath = Path().apply {
            val r = 16.dp.toPx()
            addArc(
                oval = Rect(
                    left = logoCenter.x - r,
                    top = logoCenter.y - r,
                    right = logoCenter.x + r,
                    bottom = logoCenter.y + r
                ),
                startAngleDegrees = 40f,
                sweepAngleDegrees = 280f
            )
        }
        drawPath(
            path = cPath,
            brush = Brush.linearGradient(
                colors = listOf(goldBright, goldMid, goldDark)
            ),
            style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
        )

        drawLine(
            brush = Brush.linearGradient(colors = listOf(goldBright, goldDark)),
            start = logoCenter,
            end = Offset(logoCenter.x + 22.dp.toPx(), logoCenter.y - 6.dp.toPx()),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(color = goldBright, radius = 3.dp.toPx(), center = logoCenter)

        val titleStyle = TextStyle(
            color = goldBright,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Serif,
            letterSpacing = 3.sp,
            textAlign = TextAlign.Center
        )
        val titleResult = textMeasurer.measure(text = titleText, style = titleStyle)

        drawText(
            textLayoutResult = titleResult,
            topLeft = Offset(
                center.x - titleResult.size.width / 2f,
                center.y + 4.dp.toPx()
            )
        )

        val subStyle = TextStyle(
            color = goldMid,
            fontSize = 7.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = 1.5.sp,
            textAlign = TextAlign.Center
        )
        val subResult = textMeasurer.measure(text = subtitleText, style = subStyle)

        drawText(
            textLayoutResult = subResult,
            topLeft = Offset(
                center.x - subResult.size.width / 2f,
                center.y + 30.dp.toPx()
            )
        )
    }
}

private fun drawFrontPanelDetails(
    scope: DrawScope,
    tl: Offset,
    tr: Offset,
    br: Offset,
    bl: Offset,
    goldBright: Color,
    goldMid: Color,
    goldDark: Color
) {
    with(scope) {
        val innerPadding = 8.dp.toPx()
        val innerTl = Offset(tl.x + innerPadding * 0.8f, tl.y + innerPadding)
        val innerTr = Offset(tr.x - innerPadding * 0.8f, tr.y + innerPadding)
        val innerBr = Offset(br.x - innerPadding * 0.8f, br.y - innerPadding)
        val innerBl = Offset(bl.x + innerPadding * 0.8f, bl.y - innerPadding)

        val borderPath = Path().apply {
            moveTo(innerTl.x, innerTl.y)
            lineTo(innerTr.x, innerTr.y)
            lineTo(innerBr.x, innerBr.y)
            lineTo(innerBl.x, innerBl.y)
            close()
        }

        drawPath(
            path = borderPath,
            brush = Brush.linearGradient(listOf(goldMid, goldDark, goldBright)),
            style = Stroke(width = 1.2.dp.toPx())
        )

        val corners = listOf(tl, tr, br, bl)
        corners.forEach { corner ->
            val cornerSize = 10.dp.toPx()
            val capPath = Path().apply {
                addOval(
                    Rect(
                        left = corner.x - cornerSize * 0.5f,
                        top = corner.y - cornerSize * 0.5f,
                        right = corner.x + cornerSize * 0.5f,
                        bottom = corner.y + cornerSize * 0.5f
                    )
                )
            }
            drawPath(
                path = capPath,
                brush = Brush.radialGradient(
                    colors = listOf(goldBright, goldMid, goldDark)
                )
            )
            drawCircle(
                color = goldDark,
                radius = 1.5.dp.toPx(),
                center = corner
            )
        }

        val centerX = (tl.x + tr.x + br.x + bl.x) / 4f
        val centerY = (tl.y + tr.y + br.y + bl.y) / 4f

        val starPath = Path().apply {
            val size = 12.dp.toPx()
            moveTo(centerX, centerY - size)
            quadraticTo(centerX, centerY, centerX + size, centerY)
            quadraticTo(centerX, centerY, centerX, centerY + size)
            quadraticTo(centerX, centerY, centerX - size, centerY)
            quadraticTo(centerX, centerY, centerX, centerY - size)
            close()
        }
        drawPath(
            path = starPath,
            brush = Brush.radialGradient(
                colors = listOf(goldBright, goldMid, goldDark)
            )
        )

        val barHeightsLeft = listOf(8, 14, 22, 16, 28, 20, 12, 18, 10, 6)
        val barHeightsRight = listOf(6, 10, 18, 12, 20, 28, 16, 22, 14, 8)

        val leftStartX = tl.x + 20.dp.toPx()
        val leftEndX = centerX - 18.dp.toPx()
        val rightStartX = centerX + 18.dp.toPx()
        val rightEndX = tr.x - 20.dp.toPx()

        for (i in barHeightsLeft.indices) {
            val frac = i / (barHeightsLeft.size - 1f)
            val bx = leftStartX + (leftEndX - leftStartX) * frac
            val by = centerY + (tr.y - tl.y) * 0.15f * (frac - 0.5f)
            val h = barHeightsLeft[i].dp.toPx() * 0.7f

            drawLine(
                brush = Brush.verticalGradient(
                    colors = listOf(goldBright, goldMid)
                ),
                start = Offset(bx, by - h * 0.5f),
                end = Offset(bx, by + h * 0.5f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        for (i in barHeightsRight.indices) {
            val frac = i / (barHeightsRight.size - 1f)
            val bx = rightStartX + (rightEndX - rightStartX) * frac
            val by = centerY + (tr.y - tl.y) * 0.15f * (frac - 0.5f)
            val h = barHeightsRight[i].dp.toPx() * 0.7f

            drawLine(
                brush = Brush.verticalGradient(
                    colors = listOf(goldBright, goldMid)
                ),
                start = Offset(bx, by - h * 0.5f),
                end = Offset(bx, by + h * 0.5f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

private fun drawCrankHandle(
    scope: DrawScope,
    attachPoint: Offset,
    goldBright: Color,
    goldMid: Color,
    goldDark: Color,
    woodDark: Color
) {
    with(scope) {
        val p2 = Offset(attachPoint.x + 22.dp.toPx(), attachPoint.y + 4.dp.toPx())
        val p3 = Offset(p2.x + 6.dp.toPx(), p2.y + 26.dp.toPx())
        val p4 = Offset(p3.x + 38.dp.toPx(), p3.y + 8.dp.toPx())

        val rodPath = Path().apply {
            moveTo(attachPoint.x, attachPoint.y)
            lineTo(p2.x, p2.y)
            lineTo(p3.x, p3.y)
            lineTo(p4.x, p4.y)
        }

        drawPath(
            path = rodPath,
            brush = Brush.linearGradient(
                colors = listOf(goldBright, goldMid, goldDark, goldBright)
            ),
            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        drawCircle(
            brush = Brush.radialGradient(colors = listOf(goldBright, goldDark)),
            radius = 5.dp.toPx(),
            center = attachPoint
        )

        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(woodDark, Color(0xFF3D271B), woodDark)
            ),
            topLeft = Offset(p4.x, p4.y - 7.dp.toPx()),
            size = Size(28.dp.toPx(), 14.dp.toPx()),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )

        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(goldBright, goldMid, goldDark)
            ),
            topLeft = Offset(p4.x - 2.dp.toPx(), p4.y - 8.dp.toPx()),
            size = Size(4.dp.toPx(), 16.dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )

        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(goldBright, goldMid, goldDark)
            ),
            topLeft = Offset(p4.x + 26.dp.toPx(), p4.y - 8.dp.toPx()),
            size = Size(4.dp.toPx(), 16.dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun CrankMusicBoxCardPreview() {
    CrankTheme {
        Box(
            modifier = Modifier
                .background(ObsidianBlack)
                .padding(16.dp)
        ) {
            CrankMusicBoxCard()
        }
    }
}
