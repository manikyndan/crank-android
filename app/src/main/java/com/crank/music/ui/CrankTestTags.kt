package com.crank.music.ui

/**
 * Stable identifiers for the app's primary controls.
 *
 * ## Why this exists
 *
 * There were zero `testTag` usages in the whole codebase, so no Compose UI test could address a
 * control without depending on visible text — and much of this UI is icons with no text at all. That
 * is why the 331 unit tests cover only pure logic and the instrumented tests only touch the network
 * and database layers: there was no way to drive the interface.
 *
 * Centralising the names rather than inlining string literals matters for the same reason a colour
 * token does — a test and the composable must agree on one definition. An inlined `"play_button"` in a
 * screen and `"playButton"` in a test is a silently skipped assertion, because a missing tag makes
 * `onNodeWithTag` throw rather than return the wrong node... unless the test asserts on a *set* of
 * nodes, in which case it quietly finds nothing and passes.
 *
 * Naming convention: lower_snake_case, `<area>_<control>`. Only controls that a test would genuinely
 * need are listed. Tagging every `Text` would create a maintenance surface with no test behind it.
 */
object CrankTestTags {

    /** The playback transport, shared by the full Now Playing screen and the mini player. */
    object Player {
        const val ROOT = "player_root"
        const val NOW_PLAYING_SCREEN = "now_playing_screen"
        const val MINI_PLAYER = "mini_player"
        const val PLAY_PAUSE = "player_play_pause"
        const val NEXT = "player_next"
        const val PREVIOUS = "player_previous"
        const val SHUFFLE = "player_shuffle"
        const val REPEAT = "player_repeat"
        const val LIKE = "player_like"
        const val ADD_TO_PLAYLIST = "player_add_to_playlist"
        const val SEEK_BAR = "player_seek_bar"
        const val ALBUM_ART = "player_album_art"
        const val TITLE = "player_title"
        const val ARTIST = "player_artist"
        const val QUEUE = "player_queue"
        const val LYRICS = "player_lyrics"
        const val DOWNLOAD = "player_download"
    }

    /**
     * The bottom navigation bar.
     *
     * [tab] derives the tag from the route rather than listing four constants, because the tag and the
     * route then cannot drift apart. This matters here in particular: an earlier revision of this app
     * had a `Profile`/`Create` tab pair removed, and a hand-maintained constant list is exactly what
     * would have kept asserting on a tab that no longer exists.
     */
    object Navigation {
        const val BAR = "nav_bar"

        /** @param route the navigation route, e.g. `"home"`, `"explore"`, `"library"`, `"search"`. */
        fun tab(route: String): String = "nav_tab_$route"
    }

    /** Search, used to assert on real query results. */
    object Search {
        const val FIELD = "search_field"
        const val RESULTS = "search_results"
        const val RESULT_ROW_PREFIX = "search_result_"
    }

    /** Settings and the settings sub-screens. */
    object Settings {
        const val LIST = "settings_list"
        const val APPEARANCE = "settings_appearance"
        const val AUDIO_QUALITY = "settings_audio_quality"
        const val PLAYBACK = "settings_playback"
        const val EQUALIZER = "settings_equalizer"
        const val PRIVACY = "settings_privacy"
        const val OFFLINE = "settings_offline"
        const val UPDATE_CHECKER = "settings_update_checker"
    }

    /** Appearance, where a wrong setting is only visible after a restart. */
    object Appearance {
        const val THEME_MODE_PREFIX = "appearance_theme_"
        const val ACCENT_PREFIX = "appearance_accent_"
        const val TYPOGRAPHY_SCALE_PREFIX = "appearance_scale_"
        const val RESET = "appearance_reset"
    }

    /** Equalizer, whose effects must survive leaving the screen. */
    object Equalizer {
        const val ENABLED_SWITCH = "eq_enabled"
        const val PRESET_PREFIX = "eq_preset_"
        const val BASS_BOOST = "eq_bass_boost"
        const val PREAMP = "eq_preamp"
        const val BAND_PREFIX = "eq_band_"
    }

    /** Offline music: the download list and its real file sizes. */
    object Offline {
        const val TAB_DOWNLOADS = "offline_tab_downloads"
        const val TAB_SONGS = "offline_tab_songs"
        const val STORAGE_SUMMARY = "offline_storage_summary"
        const val ITEM_PREFIX = "offline_item_"
        const val RETRY_PREFIX = "offline_retry_"
    }

    /**
     * Privacy, where the destructive actions live.
     *
     * These are the tags worth having first: "Delete listening history" clearing nothing is exactly
     * the class of defect a UI test catches and a unit test cannot, because the bug was in the
     * binding between the button and the database rather than in either one.
     */
    object Privacy {
        const val CLEAR_HISTORY = "privacy_clear_history"
        const val CLEAR_SEARCH_HISTORY = "privacy_clear_search_history"
        const val REMOVE_DOWNLOADS = "privacy_remove_downloads"
        const val CONFIRM_DIALOG = "privacy_confirm_dialog"
        const val CONFIRM_ACTION = "privacy_confirm_action"
        const val CANCEL_ACTION = "privacy_cancel_action"
        const val HISTORY_TOGGLE = "privacy_history_toggle"
        const val RECS_TOGGLE = "privacy_recs_toggle"
    }

    /** Music DNA, which must render nothing rather than invented data when history is empty. */
    object Stats {
        const val SCREEN = "stats_screen"
        const val EMPTY_STATE = "stats_empty_state"
        const val STAT_CARD_PREFIX = "stats_card_"
        const val HEATMAP = "stats_heatmap"
    }

    /** Offline / error states, shared across screens. */
    object State {
        const val LOADING = "state_loading"
        const val EMPTY = "state_empty"
        const val ERROR = "state_error"
        const val RETRY = "state_retry"
    }
}
