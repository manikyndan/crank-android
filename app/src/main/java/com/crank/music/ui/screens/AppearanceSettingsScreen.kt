package com.crank.music.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.crank.music.ui.theme.ChampagneGold
import com.crank.music.ui.theme.CharcoalElevated
import com.crank.music.ui.theme.CharcoalSurface
import com.crank.music.ui.theme.ObsidianBlack
import com.crank.music.ui.theme.TextSecondary
import com.crank.music.ui.theme.TextTertiary
import com.crank.music.ui.theme.WarmWhite
import com.crank.music.ui.viewmodel.AppearanceSettingsViewModel
import com.crank.music.ui.viewmodel.AlbumArtShape
import com.crank.music.ui.viewmodel.AppearanceUiState
import com.crank.music.ui.viewmodel.BackgroundStyle
import com.crank.music.ui.viewmodel.ThemeMode
import com.crank.music.ui.viewmodel.TypographyScale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    viewModel: AppearanceSettingsViewModel = hiltViewModel(),
    onBackClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val view = LocalView.current

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
                text = "Appearance",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = WarmWhite
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                LivePreviewSection(uiState = uiState)
            }

            item {
                ThemeSection(
                    selected = uiState.themeMode,
                    onSelect = {
                        viewModel.setThemeMode(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                )
            }

            item {
                AccentColorSection(
                    selectedName = uiState.accentColorName,
                    selectedColor = uiState.accentColor,
                    presetColors = viewModel.presetColors,
                    showColorPicker = uiState.showColorPicker,
                    customHue = uiState.customHue,
                    customSaturation = uiState.customSaturation,
                    customLightness = uiState.customLightness,
                    onSelect = { name, color ->
                        viewModel.setAccentColor(name, color)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onTogglePicker = { viewModel.toggleColorPicker(it) },
                    onCustomColorChange = { h, s, l -> viewModel.setCustomColor(h, s, l) }
                )
            }

            item {
                DynamicMaterialYouSection(
                    enabled = uiState.isDynamicMaterialYou,
                    onToggle = {
                        viewModel.toggleDynamicMaterialYou(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                )
            }

            item {
                TypographySection(
                    selected = uiState.typographyScale,
                    onSelect = {
                        viewModel.setTypographyScale(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                )
            }

            item {
                AlbumArtShapeSection(
                    selected = uiState.albumArtShape,
                    onSelect = {
                        viewModel.setAlbumArtShape(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                )
            }

            item {
                BackgroundStyleSection(
                    selected = uiState.backgroundStyle,
                    onSelect = {
                        viewModel.setBackgroundStyle(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                )
            }

            item {
                TogglesSection(
                    showLyricsBlur = uiState.showLyricsBlur,
                    animatedTransitions = uiState.animatedTransitions,
                    reduceMotion = uiState.reduceMotion,
                    onLyricsBlurToggle = {
                        viewModel.toggleLyricsBlur(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onAnimatedTransitionsToggle = {
                        viewModel.toggleAnimatedTransitions(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onReduceMotionToggle = {
                        viewModel.toggleReduceMotion(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                ResetSection(
                    onReset = {
                        viewModel.resetToDefaults()
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                )
            }
        }
    }
}

@Composable
private fun LivePreviewSection(uiState: com.crank.music.ui.viewmodel.AppearanceUiState) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            text = "Live Preview",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = WarmWhite
        )
        Spacer(modifier = Modifier.height(10.dp))

        val previewBg = when (uiState.themeMode) {
            ThemeMode.LIGHT -> Color(0xFFF5F5F5)
            ThemeMode.OLED -> Color(0xFF000000)
            ThemeMode.DARK -> Color(0xFF0A0A0A)
            ThemeMode.AUTO -> Color(0xFF0A0A0A)
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = previewBg,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, CharcoalElevated)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(64.dp),
                        color = CharcoalSurface,
                        shape = when (uiState.albumArtShape) {
                            AlbumArtShape.CIRCLE -> CircleShape
                            AlbumArtShape.SQUIRCLE -> RoundedCornerShape(16.dp)
                            AlbumArtShape.ROUNDED_SQUARE -> RoundedCornerShape(12.dp)
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = uiState.accentColor,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Song Title",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = when (uiState.typographyScale) {
                                    TypographyScale.SMALL -> 14.sp
                                    TypographyScale.MEDIUM -> 16.sp
                                    TypographyScale.LARGE -> 18.sp
                                    TypographyScale.EXTRA_LARGE -> 20.sp
                                }
                            ),
                            color = if (uiState.themeMode == ThemeMode.LIGHT) Color(0xFF1A1A1A) else WarmWhite
                        )
                        Text(
                            text = "Artist Name",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(CharcoalElevated)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(uiState.accentColor, uiState.accentColor.copy(alpha = 0.6f))
                                )
                            )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "1:24", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                    Text(text = "3:45", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Icon(Icons.Default.MusicNote, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                    Icon(Icons.Default.MusicNote, null, tint = uiState.accentColor, modifier = Modifier.size(28.dp))
                    Icon(Icons.Default.MusicNote, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun ThemeSection(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        SectionTitle(icon = Icons.Default.DarkMode, title = "Theme")
        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(ThemeMode.entries.toList()) { mode ->
                val isSelected = mode == selected
                val animatedScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.05f else 1f,
                    animationSpec = tween(200, easing = FastOutSlowInEasing),
                    label = "scale"
                )

                Surface(
                    modifier = Modifier
                        .graphicsLayer { scaleX = animatedScale; scaleY = animatedScale }
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelect(mode) },
                    color = if (isSelected) uiStateToColor(mode) else CharcoalSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = if (isSelected) BorderStroke(2.dp, ChampagneGold) else null
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val previewColor = when (mode) {
                            ThemeMode.LIGHT -> Color(0xFFF5F5F5)
                            ThemeMode.OLED -> Color(0xFF000000)
                            ThemeMode.DARK -> Color(0xFF0A0A0A)
                            ThemeMode.AUTO -> Color(0xFF1A1A2E)
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(previewColor)
                                .border(1.dp, CharcoalElevated, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (mode) {
                                    ThemeMode.LIGHT -> Icons.Default.WbSunny
                                    ThemeMode.OLED -> Icons.Default.DarkMode
                                    ThemeMode.DARK -> Icons.Default.DarkMode
                                    ThemeMode.AUTO -> Icons.Default.Tune
                                },
                                contentDescription = null,
                                tint = if (mode == ThemeMode.LIGHT) Color(0xFF1A1A1A) else WarmWhite,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) ChampagneGold else TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccentColorSection(
    selectedName: String,
    selectedColor: Color,
    presetColors: List<Triple<String, Color, Float>>,
    showColorPicker: Boolean,
    customHue: Float,
    customSaturation: Float,
    customLightness: Float,
    onSelect: (String, Color) -> Unit,
    onTogglePicker: (Boolean) -> Unit,
    onCustomColorChange: (Float, Float, Float) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        SectionTitle(icon = Icons.Default.Tune, title = "Accent Color")
        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(presetColors) { (name, color, _) ->
                val isSelected = name == selectedName && !showColorPicker
                AccentColorChip(
                    name = name,
                    color = color,
                    isSelected = isSelected,
                    onClick = {
                        onSelect(name, color)
                        onTogglePicker(false)
                    }
                )
            }
            item {
                AccentColorChip(
                    name = "Custom",
                    color = selectedColor,
                    isSelected = showColorPicker,
                    onClick = { onTogglePicker(true) }
                )
            }
        }

        if (showColorPicker) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CharcoalSurface,
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Hue",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Slider(
                        value = customHue,
                        onValueChange = { onCustomColorChange(it, customSaturation, customLightness) },
                        valueRange = 0f..360f,
                        colors = SliderDefaults.colors(
                            thumbColor = selectedColor,
                            activeTrackColor = selectedColor,
                            inactiveTrackColor = CharcoalElevated
                        )
                    )
                    Text(
                        text = "Saturation",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Slider(
                        value = customSaturation,
                        onValueChange = { onCustomColorChange(customHue, it, customLightness) },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = selectedColor,
                            activeTrackColor = selectedColor,
                            inactiveTrackColor = CharcoalElevated
                        )
                    )
                    Text(
                        text = "Lightness",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Slider(
                        value = customLightness,
                        onValueChange = { onCustomColorChange(customHue, customSaturation, it) },
                        valueRange = 10f..90f,
                        colors = SliderDefaults.colors(
                            thumbColor = selectedColor,
                            activeTrackColor = selectedColor,
                            inactiveTrackColor = CharcoalElevated
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun AccentColorChip(
    name: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        color = if (isSelected) color.copy(alpha = 0.2f) else CharcoalSurface,
        shape = RoundedCornerShape(14.dp),
        border = if (isSelected) BorderStroke(2.dp, color) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) color else WarmWhite
            )
            if (isSelected) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun DynamicMaterialYouSection(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        SectionTitle(icon = Icons.Default.Image, title = "Dynamic Colors")
        Spacer(modifier = Modifier.height(10.dp))

        SettingToggleRow(
            title = "Material You",
            subtitle = "Extract colors from album art",
            isEnabled = enabled,
            onToggle = onToggle
        )
    }
}

@Composable
private fun TypographySection(
    selected: TypographyScale,
    onSelect: (TypographyScale) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        SectionTitle(icon = Icons.Default.TextFields, title = "Typography")
        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CharcoalSurface,
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                TypographyScale.entries.forEach { scale ->
                    val isSelected = scale == selected
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(scale) },
                        color = if (isSelected) ChampagneGold.copy(alpha = 0.15f) else Color.Transparent,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = scale.label,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = when (scale) {
                                            TypographyScale.SMALL -> 12.sp
                                            TypographyScale.MEDIUM -> 14.sp
                                            TypographyScale.LARGE -> 16.sp
                                            TypographyScale.EXTRA_LARGE -> 18.sp
                                        }
                                    ),
                                    color = if (isSelected) ChampagneGold else WarmWhite
                                )
                                Text(
                                    text = "The quick brown fox jumps",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = when (scale) {
                                            TypographyScale.SMALL -> 11.sp
                                            TypographyScale.MEDIUM -> 13.sp
                                            TypographyScale.LARGE -> 15.sp
                                            TypographyScale.EXTRA_LARGE -> 17.sp
                                        }
                                    ),
                                    color = TextSecondary
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = ChampagneGold,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumArtShapeSection(
    selected: AlbumArtShape,
    onSelect: (AlbumArtShape) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        SectionTitle(icon = Icons.Default.Image, title = "Album Art Shape")
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AlbumArtShape.entries.forEach { shape ->
                val isSelected = shape == selected
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(shape) }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(
                                when (shape) {
                                    AlbumArtShape.CIRCLE -> CircleShape
                                    AlbumArtShape.SQUIRCLE -> RoundedCornerShape(16.dp)
                                    AlbumArtShape.ROUNDED_SQUARE -> RoundedCornerShape(8.dp)
                                }
                            )
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(ChampagneGold, ChampagneGold.copy(alpha = 0.5f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = ObsidianBlack,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = shape.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) ChampagneGold else TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = ChampagneGold,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BackgroundStyleSection(
    selected: BackgroundStyle,
    onSelect: (BackgroundStyle) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        SectionTitle(icon = Icons.Default.FormatQuote, title = "Background Style")
        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(BackgroundStyle.entries.toList()) { style ->
                val isSelected = style == selected
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelect(style) },
                    color = if (isSelected) ChampagneGold.copy(alpha = 0.15f) else CharcoalSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = if (isSelected) BorderStroke(2.dp, ChampagneGold) else null
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when (style) {
                                        BackgroundStyle.SOLID -> Brush.verticalGradient(
                                            colors = listOf(ObsidianBlack, ObsidianBlack)
                                        )
                                        BackgroundStyle.GRADIENT -> Brush.verticalGradient(
                                            colors = listOf(ChampagneGold.copy(alpha = 0.3f), ObsidianBlack)
                                        )
                                        BackgroundStyle.DYNAMIC -> Brush.verticalGradient(
                                            colors = listOf(Color(0xFF4A90D9), Color(0xFF9C27B0), ObsidianBlack)
                                        )
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (style) {
                                    BackgroundStyle.SOLID -> Icons.Default.DarkMode
                                    BackgroundStyle.GRADIENT -> Icons.Default.WbSunny
                                    BackgroundStyle.DYNAMIC -> Icons.Default.Image
                                },
                                contentDescription = null,
                                tint = WarmWhite,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = style.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) ChampagneGold else TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TogglesSection(
    showLyricsBlur: Boolean,
    animatedTransitions: Boolean,
    reduceMotion: Boolean,
    onLyricsBlurToggle: (Boolean) -> Unit,
    onAnimatedTransitionsToggle: (Boolean) -> Unit,
    onReduceMotionToggle: (Boolean) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        SectionTitle(icon = Icons.Default.Tune, title = "Effects & Accessibility")
        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CharcoalSurface,
            shape = RoundedCornerShape(14.dp)
        ) {
            Column {
                SettingToggleRow(
                    title = "Lyrics Background Blur",
                    subtitle = "Frosted glass effect on lyrics",
                    isEnabled = showLyricsBlur,
                    onToggle = onLyricsBlurToggle
                )
                SettingToggleRow(
                    title = "Animated Transitions",
                    subtitle = "Smooth screen animations",
                    isEnabled = animatedTransitions,
                    onToggle = onAnimatedTransitionsToggle
                )
                SettingToggleRow(
                    title = "Reduce Motion",
                    subtitle = "Accessibility: minimize animations",
                    isEnabled = reduceMotion,
                    onToggle = onReduceMotionToggle
                )
            }
        }
    }
}

@Composable
private fun ResetSection(onReset: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { onReset() },
            color = CharcoalSurface,
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Reset to Defaults",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = WarmWhite
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
        }
        Switch(
            checked = isEnabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ObsidianBlack,
                checkedTrackColor = ChampagneGold,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = CharcoalElevated
            )
        )
    }
}

@Composable
private fun SectionTitle(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ChampagneGold,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = WarmWhite
        )
    }
}

private fun uiStateToColor(mode: ThemeMode): Color {
    return when (mode) {
        ThemeMode.LIGHT -> Color(0xFFF5F5F5)
        ThemeMode.OLED -> Color(0xFF000000)
        ThemeMode.DARK -> Color(0xFF0A0A0A)
        ThemeMode.AUTO -> Color(0xFF1A1A2E)
    }
}
