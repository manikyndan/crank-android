package com.manikyndan.crank.core.util

/** Central place for app-wide constants. Keep secrets out of here — use local.properties / CI secrets. */
object Constants {
    /** Base URL for the sample REST API used by [com.manikyndan.crank.data.remote.ApiService]. */
    const val BASE_URL = "https://api.example.com/"

    /** Network timeouts in seconds. */
    const val CONNECT_TIMEOUT_SECONDS = 15L
    const val READ_TIMEOUT_SECONDS = 15L

    /** DataStore preferences file name. */
    const val PREFERENCES_NAME = "crank_preferences"
}
