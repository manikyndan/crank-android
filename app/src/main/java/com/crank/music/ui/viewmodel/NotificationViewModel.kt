package com.crank.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

enum class NotificationCategory { ALL, NEW_RELEASES, RECOMMENDATIONS, SOCIAL, SYSTEM }

enum class NotificationType { NEW_RELEASE, RECOMMENDATION, PLAYLIST_UPDATE, FRIEND_ACTIVITY, CONCERT, APP_UPDATE, DOWNLOAD_COMPLETE }

data class AppNotification(
    val id: String,
    val type: NotificationType,
    val category: NotificationCategory,
    val title: String,
    val message: String,
    val timestamp: String,
    val isRead: Boolean = false,
    val actionLabel: String? = null,
    val iconEmoji: String = ""
)

data class NotificationSettingsState(
    val newReleasesEnabled: Boolean = true,
    val recommendationsEnabled: Boolean = true,
    val socialEnabled: Boolean = true,
    val systemEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val quietHoursStart: String = "22:00",
    val quietHoursEnd: String = "07:00"
)

data class NotificationUiState(
    val notifications: List<AppNotification> = listOf(
        AppNotification("1", NotificationType.NEW_RELEASE, NotificationCategory.NEW_RELEASES,
            "New Release: The Weeknd", "The Weeknd just dropped 'After Hours (Deluxe)' — 6 new tracks",
            "2h ago", actionLabel = "Play Now", iconEmoji = "\uD83C\uDFB5"),
        AppNotification("2", NotificationType.RECOMMENDATION, NotificationCategory.RECOMMENDATIONS,
            "Crank AI Picks for You", "Based on your listening: 5 songs you'll love this weekend",
            "3h ago", actionLabel = "View", iconEmoji = "\uD83E\uDD16"),
        AppNotification("3", NotificationType.FRIEND_ACTIVITY, NotificationCategory.SOCIAL,
            "Alex is listening to", "Your friend Alex started a listening party — join now!",
            "4h ago", actionLabel = "Join", iconEmoji = "\uD83D\uDC65"),
        AppNotification("4", NotificationType.PLAYLIST_UPDATE, NotificationCategory.NEW_RELEASES,
            "Daily Mix Updated", "Your Daily Mix has 3 new songs added",
            "5h ago", actionLabel = "Play Now", iconEmoji = "\uD83D\uDCCB"),
        AppNotification("5", NotificationType.CONCERT, NotificationCategory.SOCIAL,
            "Concert Near You", "The Weeknd is performing in your area on Feb 14",
            "6h ago", actionLabel = "View", iconEmoji = "\uD83C\uDFB6"),
        AppNotification("6", NotificationType.APP_UPDATE, NotificationCategory.SYSTEM,
            "Update Available", "Crank Music v2.0.0 is ready to install",
            "1d ago", actionLabel = "Update", iconEmoji = "\u2B06\uFE0F"),
        AppNotification("7", NotificationType.DOWNLOAD_COMPLETE, NotificationCategory.SYSTEM,
            "Download Complete", "3 songs have been downloaded for offline listening",
            "1d ago", iconEmoji = "\u2705"),
        AppNotification("8", NotificationType.NEW_RELEASE, NotificationCategory.NEW_RELEASES,
            "New Album: Dua Lipa", "Dua Lipa released 'Radical Optimism' — listen now",
            "2d ago", actionLabel = "Play Now", iconEmoji = "\uD83C\uDFB5"),
        AppNotification("9", NotificationType.RECOMMENDATION, NotificationCategory.RECOMMENDATIONS,
            "Discover Weekly", "Your weekly mixtape of fresh music is ready",
            "3d ago", actionLabel = "View", iconEmoji = "\uD83E\uDD16"),
        AppNotification("10", NotificationType.FRIEND_ACTIVITY, NotificationCategory.SOCIAL,
            "Sam liked your playlist", "Sam added 'Chill Vibes' to their library",
            "3d ago", iconEmoji = "\u2764\uFE0F")
    ),
    val selectedTab: NotificationCategory = NotificationCategory.ALL,
    val hasUnread: Boolean = true,
    val unreadCount: Int = 5,
    val showPanel: Boolean = false,
    val showSettings: Boolean = false,
    val settings: NotificationSettingsState = NotificationSettingsState(),
    val swipedId: String? = null
)

@HiltViewModel
class NotificationViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationUiState())
    val uiState: StateFlow<NotificationUiState> = _uiState.asStateFlow()

    fun togglePanel() {
        _uiState.value = _uiState.value.copy(showPanel = !_uiState.value.showPanel)
    }

    fun openPanel() {
        _uiState.value = _uiState.value.copy(showPanel = true)
    }

    fun closePanel() {
        _uiState.value = _uiState.value.copy(showPanel = false)
    }

    fun selectTab(tab: NotificationCategory) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    fun markAllRead() {
        _uiState.value = _uiState.value.copy(
            notifications = _uiState.value.notifications.map { it.copy(isRead = true) },
            hasUnread = false,
            unreadCount = 0
        )
    }

    fun markAsRead(id: String) {
        val updated = _uiState.value.notifications.map {
            if (it.id == id) it.copy(isRead = true) else it
        }
        val unreadCount = updated.count { !it.isRead }
        _uiState.value = _uiState.value.copy(
            notifications = updated,
            unreadCount = unreadCount,
            hasUnread = unreadCount > 0
        )
    }

    fun dismissNotification(id: String) {
        val updated = _uiState.value.notifications.filter { it.id != id }
        val unreadCount = updated.count { !it.isRead }
        _uiState.value = _uiState.value.copy(
            notifications = updated,
            unreadCount = unreadCount,
            hasUnread = unreadCount > 0,
            swipedId = null
        )
    }

    fun setSwipedId(id: String?) {
        _uiState.value = _uiState.value.copy(swipedId = id)
    }

    fun toggleSettings() {
        _uiState.value = _uiState.value.copy(showSettings = !_uiState.value.showSettings)
    }

    fun toggleNewReleases() {
        _uiState.value = _uiState.value.copy(
            settings = _uiState.value.settings.copy(
                newReleasesEnabled = !_uiState.value.settings.newReleasesEnabled
            )
        )
    }

    fun toggleRecommendations() {
        _uiState.value = _uiState.value.copy(
            settings = _uiState.value.settings.copy(
                recommendationsEnabled = !_uiState.value.settings.recommendationsEnabled
            )
        )
    }

    fun toggleSocial() {
        _uiState.value = _uiState.value.copy(
            settings = _uiState.value.settings.copy(
                socialEnabled = !_uiState.value.settings.socialEnabled
            )
        )
    }

    fun toggleSystem() {
        _uiState.value = _uiState.value.copy(
            settings = _uiState.value.settings.copy(
                systemEnabled = !_uiState.value.settings.systemEnabled
            )
        )
    }

    fun toggleSound() {
        _uiState.value = _uiState.value.copy(
            settings = _uiState.value.settings.copy(
                soundEnabled = !_uiState.value.settings.soundEnabled
            )
        )
    }

    fun toggleVibration() {
        _uiState.value = _uiState.value.copy(
            settings = _uiState.value.settings.copy(
                vibrationEnabled = !_uiState.value.settings.vibrationEnabled
            )
        )
    }

    fun getFilteredNotifications(): List<AppNotification> {
        val state = _uiState.value
        return state.notifications.filter {
            state.selectedTab == NotificationCategory.ALL || it.category == state.selectedTab
        }
    }
}
