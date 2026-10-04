package com.crank.music.data.local

import android.content.Context
import java.io.File

/**
 * The one place that knows where Media3 stores downloaded audio.
 *
 * This exists because the location was previously computed independently in two places, and they
 * disagreed: [com.crank.music.di.DownloadModule] wrote downloads to
 * `<external-files>/crank_downloads` while the Offline screen searched `<internal-files>/downloads`.
 * Nothing ever matched, so every downloaded row reported a blank size and a blank date no matter
 * how much audio was actually on disk.
 *
 * External files are preferred because downloaded audio is large and should not consume the
 * internal-storage budget; the internal directory is the fallback for a device with no external
 * app storage mounted.
 */
object DownloadLocations {

    private const val DIRECTORY_NAME = "crank_downloads"

    /** The directory Media3's cache is rooted at. Not created here — `SimpleCache` creates it. */
    fun directory(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, DIRECTORY_NAME)
}
