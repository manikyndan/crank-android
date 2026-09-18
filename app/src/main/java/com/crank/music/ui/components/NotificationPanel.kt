package com.crank.music.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
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
import com.crank.music.ui.viewmodel.NotificationCategory
import com.crank.music.ui.viewmodel.NotificationViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationPanel(
    viewModel: NotificationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (uiState.showPanel) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.closePanel() },
            sheetState = sheetState,
            containerColor = Color.Transparent,
            dragHandle = null
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * 0.85f),
                color = Color(0xCC0A0A0A),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xF0111111),
                                    Color(0xE60A0A0A)
                                )
                            )
                        )
                ) {
                    NotificationHeader(
                        unreadCount = uiState.unreadCount,
                        showSettings = uiState.showSettings,
                        onMarkAllRead = {
                            viewModel.markAllRead()
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        },
                        onToggleSettings = {
                            viewModel.toggleSettings()
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        },
                        onClose = {
                            viewModel.closePanel()
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        }
                    )

                    AnimatedVisibility(
                        visible = uiState.showSettings,
                        enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                        exit = shrinkVertically(tween(300)) + fadeOut(tween(300))
                    ) {
                        NotificationSettingsSection(
                            settings = uiState.settings,
                            onToggleNewReleases = {
                                viewModel.toggleNewReleases()
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            },
                            onToggleRecommendations = {
                                viewModel.toggleRecommendations()
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            },
                            onToggleSocial = {
                                viewModel.toggleSocial()
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            },
                            onToggleSystem = {
                                viewModel.toggleSystem()
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            },
                            onToggleSound = {
                                viewModel.toggleSound()
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            },
                            onToggleVibration = {
                                viewModel.toggleVibration()
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            }
                        )
                    }

                    NotificationTabs(
                        selectedTab = uiState.selectedTab,
                        onTabSelect = { tab ->
                            viewModel.selectTab(tab)
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        }
                    )

                    val filteredNotifications = uiState.notifications.filter {
                        uiState.selectedTab == NotificationCategory.ALL || it.category == uiState.selectedTab
                    }

                    if (filteredNotifications.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = TextTertiary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No notifications",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            items(
                                items = filteredNotifications,
                                key = { it.id }
                            ) { notification ->
                                NotificationCard(
                                    notification = notification,
                                    isSwiped = uiState.swipedId == notification.id,
                                    onSwiped = {
                                        viewModel.setSwipedId(notification.id)
                                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    },
                                    onDismiss = {
                                        viewModel.dismissNotification(notification.id)
                                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    },
                                    onCancelSwipe = {
                                        viewModel.setSwipedId(null)
                                    },
                                    onClick = {
                                        viewModel.markAsRead(notification.id)
                                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    }
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
private fun NotificationHeader(
    unreadCount: Int,
    showSettings: Boolean,
    onMarkAllRead: () -> Unit,
    onToggleSettings: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Notifications",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = WarmWhite
                )
                if (unreadCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = ChampagneGold,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "$unreadCount",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = ObsidianBlack,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        if (unreadCount > 0) {
            Text(
                text = "Mark all read",
                style = MaterialTheme.typography.labelLarge,
                color = ChampagneGold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onMarkAllRead() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        IconButton(
            onClick = onToggleSettings,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = if (showSettings) ChampagneGold else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun NotificationSettingsSection(
    settings: com.crank.music.ui.viewmodel.NotificationSettingsState,
    onToggleNewReleases: () -> Unit,
    onToggleRecommendations: () -> Unit,
    onToggleSocial: () -> Unit,
    onToggleSystem: () -> Unit,
    onToggleSound: () -> Unit,
    onToggleVibration: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        color = CharcoalSurface,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Notification Settings",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = ChampagneGold
            )
            Spacer(modifier = Modifier.height(8.dp))

            SettingsToggleRow("New Releases", settings.newReleasesEnabled, onToggleNewReleases)
            SettingsToggleRow("Recommendations", settings.recommendationsEnabled, onToggleRecommendations)
            SettingsToggleRow("Social", settings.socialEnabled, onToggleSocial)
            SettingsToggleRow("System", settings.systemEnabled, onToggleSystem)

            HorizontalDivider(color = CharcoalElevated, modifier = Modifier.padding(vertical = 4.dp))

            SettingsToggleRow("Sound", settings.soundEnabled, onToggleSound)
            SettingsToggleRow("Vibration", settings.vibrationEnabled, onToggleVibration)

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quiet Hours",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WarmWhite
                )
                Text(
                    text = "${settings.quietHoursStart} – ${settings.quietHoursEnd}",
                    style = MaterialTheme.typography.labelMedium,
                    color = ChampagneGold
                )
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    isEnabled: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = WarmWhite,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = isEnabled,
            onCheckedChange = { onToggle() },
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
private fun NotificationTabs(
    selectedTab: NotificationCategory,
    onTabSelect: (NotificationCategory) -> Unit
) {
    val tabs = listOf(
        NotificationCategory.ALL to "All",
        NotificationCategory.NEW_RELEASES to "Releases",
        NotificationCategory.RECOMMENDATIONS to "AI Picks",
        NotificationCategory.SOCIAL to "Social",
        NotificationCategory.SYSTEM to "System"
    )
    val selectedIndex = tabs.indexOfFirst { it.first == selectedTab }

    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        containerColor = Color.Transparent,
        contentColor = ChampagneGold,
        edgePadding = 16.dp,
        divider = {},
        indicator = { tabPositions ->
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                height = 3.dp,
                color = ChampagneGold
            )
        }
    ) {
        tabs.forEachIndexed { index, (category, title) ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onTabSelect(category) },
                text = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (selectedIndex == index) ChampagneGold else TextSecondary
                    )
                }
            )
        }
    }
}

@Composable
private fun NotificationCard(
    notification: com.crank.music.ui.viewmodel.AppNotification,
    isSwiped: Boolean,
    onSwiped: () -> Unit,
    onDismiss: () -> Unit,
    onCancelSwipe: () -> Unit,
    onClick: () -> Unit
) {
    val offsetX = remember { Animatable(0f) }
    val density = LocalDensity.current
    val view = LocalView.current

    val borderColor by animateColorAsState(
        targetValue = if (notification.isRead) Color(0x33FFFFFF) else ChampagneGold,
        animationSpec = tween(200),
        label = "border"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        if (isSwiped) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                color = Color(0xFFFF5252).copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX.value < -120f) {
                                onSwiped()
                            }
                        },
                        onHorizontalDrag = { _, dragAmount -> }
                    )
                },
            color = if (isSwiped) Color.Transparent else CharcoalSurface,
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onClick()
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent)
                ) {}

                Surface(
                    modifier = Modifier
                        .width(3.dp)
                        .height(60.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = borderColor,
                    shape = RoundedCornerShape(2.dp)
                ) {}

                Spacer(modifier = Modifier.width(12.dp))

                Surface(
                    modifier = Modifier.size(40.dp),
                    color = CharcoalElevated,
                    shape = CircleShape
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = notification.iconEmoji,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = if (notification.isRead) FontWeight.Normal else FontWeight.Bold
                        ),
                        color = WarmWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = notification.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = notification.timestamp,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary
                        )

                        if (notification.actionLabel != null) {
                            Surface(
                                color = ChampagneGold.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = notification.actionLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = ChampagneGold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
