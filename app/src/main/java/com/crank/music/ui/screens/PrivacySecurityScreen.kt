package com.crank.music.ui.screens

import java.util.Locale

import com.crank.music.util.confirmHaptic

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.crank.music.ui.theme.ChampagneGold
import com.crank.music.ui.theme.CharcoalElevated
import com.crank.music.ui.theme.CharcoalSurface
import com.crank.music.ui.theme.GoldDark
import com.crank.music.ui.theme.ObsidianBlack
import com.crank.music.ui.theme.TextSecondary
import com.crank.music.ui.theme.TextTertiary
import com.crank.music.ui.theme.WarmWhite
import com.crank.music.ui.viewmodel.PrivacyViewModel
import com.crank.music.ui.viewmodel.PrivateSessionDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySecurityScreen(
    viewModel: PrivacyViewModel = hiltViewModel(),
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
                text = "Privacy & Security",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = WarmWhite
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                PrivacyDashboardSection(
                    listeningHistory = uiState.listeningHistoryEnabled,
                    personalizedRecs = uiState.personalizedRecsEnabled,
                    analytics = uiState.analyticsEnabled,
                    onListeningHistoryToggle = {
                        viewModel.toggleListeningHistory(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onPersonalizedRecsToggle = {
                        viewModel.togglePersonalizedRecs(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onAnalyticsToggle = {
                        viewModel.toggleAnalytics(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                )
            }

            item {
                DataManagementSection(
                    isClearing = uiState.isHistoryClearing,
                    onClearHistory = {
                        viewModel.showClearHistoryDialog(true)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onExportData = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                )
            }

            item {
                DeviceVerificationSection(
                    isVerified = uiState.isDeviceVerified,
                    isVerifying = uiState.isVerifying,
                    deviceInfo = uiState.deviceInfo,
                    trustedDevices = uiState.trustedDevices,
                    onVerifyDevice = {
                        viewModel.verifyDevice()
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onRemoveDevice = { deviceId ->
                        viewModel.removeTrustedDevice(deviceId)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                )
            }

            item {
                ListeningActivitySection(
                    showActivity = uiState.showActivityToFriends,
                    incognitoMode = uiState.incognitoMode,
                    privateSession = uiState.privateSession,
                    privateDuration = uiState.privateSessionDuration,
                    remainingMs = uiState.privateSessionRemainingMs,
                    onShowActivityToggle = {
                        viewModel.toggleShowActivity(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onIncognitoToggle = {
                        viewModel.toggleIncognito(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onPrivateSessionToggle = {
                        viewModel.togglePrivateSession(it)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    },
                    onDurationSelect = { viewModel.setPrivateSessionDuration(it) }
                )
            }
        }
    }

    if (uiState.showClearHistoryDialog) {
        ClearHistoryDialog(
            onDismiss = { viewModel.showClearHistoryDialog(false) },
            onConfirm = {
                viewModel.clearListeningHistory()
                confirmHaptic(view)
            }
        )
    }

    if (uiState.showDeleteSuccess) {
        DeleteSuccessOverlay()
    }
}

@Composable
private fun PrivacyDashboardSection(
    listeningHistory: Boolean,
    personalizedRecs: Boolean,
    analytics: Boolean,
    onListeningHistoryToggle: (Boolean) -> Unit,
    onPersonalizedRecsToggle: (Boolean) -> Unit,
    onAnalyticsToggle: (Boolean) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        SectionHeader(icon = Icons.Default.Shield, title = "Privacy Dashboard")
        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CharcoalSurface,
            shape = RoundedCornerShape(14.dp)
        ) {
            Column {
                PrivacyToggleRow(
                    icon = Icons.Default.History,
                    title = "Listening History",
                    subtitle = "Track songs you've played",
                    isEnabled = listeningHistory,
                    onToggle = onListeningHistoryToggle
                )
                PrivacyToggleRow(
                    icon = Icons.Default.Star,
                    title = "Personalized Recommendations",
                    subtitle = "AI-powered song suggestions",
                    isEnabled = personalizedRecs,
                    onToggle = onPersonalizedRecsToggle
                )
                PrivacyToggleRow(
                    icon = Icons.Default.Storage,
                    title = "Analytics Data",
                    subtitle = "Usage statistics & crash reports",
                    isEnabled = analytics,
                    onToggle = onAnalyticsToggle,
                    showDivider = false
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CharcoalSurface,
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Device Information is required for app functionality and cannot be disabled.",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary
                )
            }
        }
    }
}

@Composable
private fun DataManagementSection(
    isClearing: Boolean,
    onClearHistory: () -> Unit,
    onExportData: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        SectionHeader(icon = Icons.Default.Delete, title = "Data Management")
        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { onClearHistory() },
            color = Color(0xFF2A1010),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFF5252),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Clear Listening History",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFFF5252)
                    )
                    Text(
                        text = "Permanently delete all played songs",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFF8A80)
                    )
                }
                if (isClearing) {
                    LinearProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color(0xFFFF5252),
                        trackColor = Color(0xFF2A1010)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { onExportData() },
            color = CharcoalSurface,
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = ChampagneGold,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Export My Data",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = WarmWhite
                    )
                    Text(
                        text = "Download all personal data as JSON",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { },
            color = CharcoalSurface,
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = ChampagneGold,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Privacy Policy",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = WarmWhite
                    )
                    Text(
                        text = "How we protect your data",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceVerificationSection(
    isVerified: Boolean,
    isVerifying: Boolean,
    deviceInfo: com.crank.music.ui.viewmodel.DeviceInfo,
    trustedDevices: List<com.crank.music.ui.viewmodel.TrustedDevice>,
    onVerifyDevice: () -> Unit,
    onRemoveDevice: (String) -> Unit
) {
    val shieldScale = remember { Animatable(0.8f) }
    val infiniteTransition = rememberInfiniteTransition(label = "shield_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    LaunchedEffect(isVerified) {
        if (isVerified) {
            shieldScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        SectionHeader(icon = Icons.Default.Security, title = "Device Verification")
        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CharcoalSurface,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .graphicsLayer {
                            scaleX = shieldScale.value
                            scaleY = shieldScale.value
                        }
                        .shadow(
                            if (isVerified) 16.dp else 0.dp,
                            CircleShape,
                            spotColor = if (isVerified) ChampagneGold else Color.Transparent
                        )
                        .clip(CircleShape)
                        .background(
                            color = if (isVerified) ChampagneGold.copy(alpha = glowAlpha * 0.3f) else CharcoalElevated,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isVerified) Icons.Default.CheckCircle else Icons.Default.Shield,
                        contentDescription = null,
                        tint = if (isVerified) ChampagneGold else TextSecondary,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (isVerified) "Device Verified" else if (isVerifying) "Verifying..." else "Not Verified",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isVerified) ChampagneGold else if (isVerifying) WarmWhite else TextSecondary
                )

                if (isVerified) {
                    Text(
                        text = "Your device is secure",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!isVerified) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onVerifyDevice() },
                        color = ChampagneGold,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isVerifying) {
                                LinearProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = ObsidianBlack,
                                    trackColor = ChampagneGold.copy(alpha = 0.3f)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = null,
                                    tint = ObsidianBlack,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isVerifying) "Verifying..." else "Verify Device",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = ObsidianBlack
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        DeviceInfoCards(deviceInfo = deviceInfo)

        Spacer(modifier = Modifier.height(12.dp))

        if (trustedDevices.isNotEmpty()) {
            Text(
                text = "Trusted Devices",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = WarmWhite
            )
            Spacer(modifier = Modifier.height(8.dp))

            trustedDevices.forEach { device ->
                TrustedDeviceRow(
                    device = device,
                    onRemove = { onRemoveDevice(device.id) }
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun DeviceInfoCards(deviceInfo: com.crank.music.ui.viewmodel.DeviceInfo) {
    val cards = listOf(
        Triple("Device Model", deviceInfo.model, Icons.Default.PhoneAndroid),
        Triple("OS Version", deviceInfo.osVersion, Icons.Default.PhoneAndroid),
        Triple("Storage", "${deviceInfo.storageUsed} / ${deviceInfo.storageUsed + deviceInfo.storageAvailable}", Icons.Default.PhoneAndroid),
        Triple("App Version", "${deviceInfo.appVersion} (${deviceInfo.buildNumber})", Icons.Default.Info),
        Triple("Installed", deviceInfo.installDate, Icons.Default.Timer)
    )

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        items(cards) { (label, value, icon) ->
            Surface(
                modifier = Modifier.width(140.dp),
                color = CharcoalSurface.copy(alpha = 0.8f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, GlassBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = ChampagneGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                        color = WarmWhite,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun TrustedDeviceRow(
    device: com.crank.music.ui.viewmodel.TrustedDevice,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = CharcoalSurface,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PhoneAndroid,
                contentDescription = null,
                tint = if (device.isCurrent) ChampagneGold else TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = WarmWhite
                )
                Text(
                    text = device.lastSeen,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
            if (device.isCurrent) {
                Surface(
                    color = ChampagneGold.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "This Device",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = ChampagneGold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            } else {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ListeningActivitySection(
    showActivity: Boolean,
    incognitoMode: Boolean,
    privateSession: Boolean,
    privateDuration: PrivateSessionDuration,
    remainingMs: Long,
    onShowActivityToggle: (Boolean) -> Unit,
    onIncognitoToggle: (Boolean) -> Unit,
    onPrivateSessionToggle: (Boolean) -> Unit,
    onDurationSelect: (PrivateSessionDuration) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        SectionHeader(icon = Icons.Default.Visibility, title = "Listening Activity")
        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CharcoalSurface,
            shape = RoundedCornerShape(14.dp)
        ) {
            Column {
                PrivacyToggleRow(
                    icon = Icons.Default.Visibility,
                    title = "Show Activity to Friends",
                    subtitle = "Let friends see what you're playing",
                    isEnabled = showActivity,
                    onToggle = onShowActivityToggle
                )
                PrivacyToggleRow(
                    icon = Icons.Default.VisibilityOff,
                    title = "Incognito Mode",
                    subtitle = "Pause all history tracking",
                    isEnabled = incognitoMode,
                    onToggle = onIncognitoToggle
                )
                PrivacyToggleRow(
                    icon = Icons.Default.Lock,
                    title = "Private Session",
                    subtitle = "Temporary incognito mode",
                    isEnabled = privateSession,
                    onToggle = onPrivateSessionToggle,
                    showDivider = false
                )
            }
        }

        AnimatedVisibility(
            visible = privateSession,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                if (remainingMs > 0) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = CharcoalSurface,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val minutes = (remainingMs / 60_000).toInt()
                            val seconds = ((remainingMs % 60_000) / 1000).toInt()
                            Text(
                                text = "Active — ${minutes}:${String.format(Locale.US, "%02d", seconds)} remaining",
                                style = MaterialTheme.typography.labelMedium,
                                color = ChampagneGold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(
                    text = "Session Duration",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(PrivateSessionDuration.entries.toList()) { duration ->
                        val isSelected = duration == privateDuration
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onDurationSelect(duration) },
                            color = if (isSelected) ChampagneGold else CharcoalElevated,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = duration.label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSelected) ObsidianBlack else TextSecondary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    showDivider: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isEnabled) ChampagneGold else TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
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
private fun ClearHistoryDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var swipeProgress by remember { mutableFloatStateOf(0f) }
    val animatedAlpha by animateFloatAsState(
        targetValue = swipeProgress.coerceIn(0f, 1f),
        animationSpec = tween(200),
        label = "alpha"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CharcoalSurface,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFFF5252),
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Clear Listening History?",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = WarmWhite,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "This action is permanent and cannot be undone. All played songs will be removed from your history.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF1A0A0A))
                        .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (swipeProgress > 0.8f) {
                                        onConfirm()
                                    } else {
                                        swipeProgress = 0f
                                    }
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    swipeProgress = (swipeProgress + dragAmount / size.width).coerceIn(0f, 1f)
                                }
                            )
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(swipeProgress.coerceIn(0f, 1f))
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFFFF5252).copy(alpha = 0.3f * animatedAlpha),
                                        Color(0xFFFF5252).copy(alpha = 0.6f * animatedAlpha)
                                    )
                                )
                            )
                    )

                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(40.dp)
                            .offset(x = with(androidx.compose.ui.platform.LocalDensity.current) { (swipeProgress * (300.dp.toPx() - 40.dp.toPx())).toDp() })
                            .clip(CircleShape)
                            .background(Color(0xFFFF5252)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Swipe to delete",
                            tint = WarmWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = "Swipe to Delete →",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFFF5252).copy(alpha = 1f - swipeProgress),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun DeleteSuccessOverlay() {
    val scale = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
        delay(1500)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "History Cleared",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = WarmWhite
            )
        }
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
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

private val GlassBorder = Color(0x33FFFFFF)
