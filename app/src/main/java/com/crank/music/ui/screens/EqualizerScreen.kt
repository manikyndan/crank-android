package com.crank.music.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.crank.music.ui.components.SectionHeader
import com.crank.music.ui.theme.ChampagneGold
import com.crank.music.ui.theme.CharcoalElevated
import com.crank.music.ui.theme.CharcoalSurface
import com.crank.music.ui.theme.GoldMuted
import com.crank.music.ui.theme.ObsidianBlack
import com.crank.music.ui.theme.WarningAmber
import com.crank.music.ui.theme.TextSecondary
import com.crank.music.ui.theme.TextTertiary
import com.crank.music.ui.theme.WarmWhite
import com.crank.music.data.audio.AudioEffectsController
import com.crank.music.ui.viewmodel.EqualizerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    audioSessionId: Int?,
    equalizerViewModel: EqualizerViewModel = hiltViewModel(),
    onBackClick: () -> Unit = {}
) {
    val uiState by equalizerViewModel.uiState.collectAsStateWithLifecycle()
    val view = LocalView.current
    var showSaveDialog by remember { mutableStateOf(false) }

    LaunchedEffect(audioSessionId) {
        equalizerViewModel.attachAudioSession(audioSessionId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)

            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = WarmWhite,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Equalizer",
                    style = MaterialTheme.typography.headlineSmall,
                    color = WarmWhite
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = ChampagneGold,
                    modifier = Modifier.size(24.dp)
                )
            }
            Switch(
                checked = uiState.isEnabled,
                onCheckedChange = {
                    equalizerViewModel.toggleEqualizer(it)
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ObsidianBlack,
                    checkedTrackColor = ChampagneGold,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = CharcoalSurface
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // The real attachment state, so a device that refuses the effects says so instead of showing
        // sliders that move while nothing happens.
        Text(
            text = uiState.statusMessage,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
            modifier = Modifier.padding(start = 4.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        SectionHeader(
            icon = Icons.Default.Speed,
            title = "Preamp",
            subtitle = "Boost, up to +${AudioEffectsController.MAX_PREAMP_DB} dB"
        )
        Spacer(modifier = Modifier.height(8.dp))
        PreampSlider(
            dbValue = uiState.preampBoostDb,
            isEnabled = uiState.isEnabled,
            onValueChange = { newDb ->
                equalizerViewModel.onPreampChanged(newDb)
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        SectionHeader(
            icon = Icons.Default.Equalizer,
            title = "Frequency Bands",
            subtitle = "${uiState.bandFrequencies.size}-Band Equalizer"
        )
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CharcoalSurface,
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                uiState.bandFrequencies.forEachIndexed { index, freqLabel ->
                    val dbValue = uiState.bandLevels.getOrElse(index) { 0 }
                    GradientVerticalSlider(
                        frequencyLabel = freqLabel,
                        dbValue = dbValue,
                        isEnabled = uiState.isEnabled,
                        onDbValueChange = { newDb ->
                            equalizerViewModel.onBandLevelChanged(index, newDb)
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SectionHeader(
            icon = Icons.Default.GridView,
            title = "Presets",
            subtitle = "Select or create"
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(equalizerViewModel.getAllPresets().keys.toList()) { preset ->
                val isSelected = preset == uiState.currentPreset
                val isCustom = uiState.customPresets.any { it.name == preset }
                PresetChip(
                    name = preset,
                    isSelected = isSelected,
                    isCustom = isCustom,
                    onClick = {
                        equalizerViewModel.selectPreset(preset)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onDelete = {
                        equalizerViewModel.deleteCustomPreset(preset)
                    }
                )
            }
            item {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { showSaveDialog = true },
                    color = CharcoalElevated,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Save Current",
                            tint = ChampagneGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Save",
                            style = MaterialTheme.typography.labelLarge,
                            color = ChampagneGold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SectionHeader(
            icon = Icons.Default.Bolt,
            title = "Enhancements",
            subtitle = "Audio processing"
        )
        Spacer(modifier = Modifier.height(8.dp))

        EnhancementRow(
            title = "Bass Boost",
            subtitle = "Enhanced low-end frequencies",
            icon = Icons.Default.MusicNote,
            isEnabled = uiState.isBassBoostEnabled,
            onToggle = {
                equalizerViewModel.toggleBassBoost(it)
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        )

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showSaveDialog) {
        SaveCustomPresetDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { name, icon ->
                equalizerViewModel.saveCustomPreset(name, icon)
                showSaveDialog = false
            }
        )
    }
}

@Composable
private fun PreampSlider(
    dbValue: Int,
    isEnabled: Boolean,
    onValueChange: (Int) -> Unit
) {
    var trackHeightPx by remember { mutableFloatStateOf(1f) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = CharcoalSurface,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // The range starts at 0 dB: `LoudnessEnhancer` cannot attenuate, so a negative preamp
            // would be a control that does nothing at the bottom half of its travel.
            Text(
                text = "0",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                modifier = Modifier.width(32.dp),
                textAlign = TextAlign.End
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .onGloballyPositioned { trackHeightPx = it.size.width.toFloat().coerceAtLeast(1f) }
                    .pointerInput(isEnabled) {
                        if (!isEnabled) return@pointerInput
                        detectVerticalDragGestures { change, _ ->
                            val x = change.position.x
                            val fraction = (x / trackHeightPx).coerceIn(0f, 1f)
                            val newDb = (fraction * AudioEffectsController.MAX_PREAMP_DB)
                                .toInt()
                                .coerceIn(0, AudioEffectsController.MAX_PREAMP_DB)
                            onValueChange(newDb)
                        }
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(CharcoalElevated)
                )

                val fillFraction = (dbValue.toFloat() / AudioEffectsController.MAX_PREAMP_DB)
                    .coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fillFraction)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = if (isEnabled) listOf(
                                    ChampagneGold.copy(alpha = 0.4f),
                                    ChampagneGold
                                ) else listOf(TextTertiary, TextSecondary)
                            )
                        )
                )

                val density = LocalDensity.current
                val thumbXDp = with(density) {
                    val thumbPx = fillFraction * (trackHeightPx - 20.dp.toPx())
                    thumbPx.toDp()
                }
                Box(
                    modifier = Modifier
                        .offset(x = thumbXDp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (isEnabled) ChampagneGold else TextSecondary)
                        .border(2.dp, ObsidianBlack, CircleShape)
                )
            }

            Text(
                text = if (dbValue > 0) "+$dbValue dB" else "Off",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = if (isEnabled) ChampagneGold else TextSecondary,
            )

            Text(
                text = "+${AudioEffectsController.MAX_PREAMP_DB}",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                modifier = Modifier.width(32.dp)
            )
        }
    }
}

@Composable
private fun GradientVerticalSlider(
    frequencyLabel: String,
    dbValue: Int,
    isEnabled: Boolean,
    onDbValueChange: (Int) -> Unit
) {
    var trackHeightPx by remember { mutableFloatStateOf(1f) }
    val animatedDb = remember { Animatable(dbValue.toFloat()) }

    LaunchedEffect(dbValue) {
        animatedDb.animateTo(
            targetValue = dbValue.toFloat(),
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxHeight()
    ) {
        Text(
            text = if (dbValue > 0) "+$dbValue" else "$dbValue",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = if (isEnabled) ChampagneGold else TextSecondary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .width(32.dp)
                .onGloballyPositioned { trackHeightPx = it.size.height.toFloat().coerceAtLeast(1f) }
                .pointerInput(isEnabled) {
                    if (!isEnabled) return@pointerInput
                    detectVerticalDragGestures { change, _ ->
                        val y = change.position.y
                        val fraction = 1f - (y / trackHeightPx).coerceIn(0f, 1f)
                        val newDb = (-12 + fraction * 24).toInt().coerceIn(-12, 12)
                        onDbValueChange(newDb)
                    }
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(CharcoalElevated)
            )

            val fillFraction = ((dbValue + 12) / 24f).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight(fillFraction)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = if (isEnabled) listOf(
                                ChampagneGold,
                                GoldMuted,
                                ChampagneGold.copy(alpha = 0.3f)
                            ) else listOf(TextTertiary, TextSecondary)
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = (fillFraction * (trackHeightPx / 3f)).dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = if (isEnabled) listOf(
                                ChampagneGold,
                                WarningAmber
                            ) else listOf(TextSecondary, TextTertiary)
                        )
                    )
                    .border(2.dp, ObsidianBlack, CircleShape)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = frequencyLabel,
            style = MaterialTheme.typography.labelSmall,
            color = WarmWhite,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PresetChip(
    name: String,
    isSelected: Boolean,
    isCustom: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        color = if (isSelected) ChampagneGold else CharcoalSurface,
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) ObsidianBlack else WarmWhite
            )
            if (isCustom) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete preset",
                    tint = if (isSelected) ObsidianBlack.copy(alpha = 0.6f) else TextTertiary,
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { onDelete() }
                )
            }
        }
    }
}

@Composable
private fun EnhancementRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = CharcoalSurface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isEnabled) ChampagneGold else TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
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
}

@Composable
private fun SaveCustomPresetDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, icon: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("equalizer") }

    val icons = mapOf(
        "equalizer" to Icons.Default.Equalizer,
        "music" to Icons.Default.MusicNote,
        "headphones" to Icons.Default.Headphones,
        "bolt" to Icons.Default.Bolt,
        "speed" to Icons.Default.Speed
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CharcoalSurface,
        title = {
            Text(
                text = "Save Custom Preset",
                style = MaterialTheme.typography.headlineSmall,
                color = WarmWhite
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Preset Name", color = TextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ChampagneGold,
                        unfocusedBorderColor = CharcoalElevated,
                        focusedTextColor = WarmWhite,
                        unfocusedTextColor = WarmWhite,
                        cursorColor = ChampagneGold
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Icon",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    icons.forEach { (iconName, vector) ->
                        val isSelected = selectedIcon == iconName
                        Surface(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .clickable { selectedIcon = iconName },
                            color = if (isSelected) ChampagneGold else CharcoalElevated,
                            shape = CircleShape
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = vector,
                                    contentDescription = iconName,
                                    tint = if (isSelected) ObsidianBlack else TextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onSave(name, selectedIcon) },
                enabled = name.isNotBlank()
            ) {
                Text("Save", color = if (name.isNotBlank()) ChampagneGold else TextTertiary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

// The private SectionHeader that lived here was one of five per-screen copies of the same
// Icon + title row. It is now the shared com.crank.music.ui.components.SectionHeader, so an
// icon-size or typography change lands on every settings screen at once instead of drifting.
