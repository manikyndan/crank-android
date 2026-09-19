package com.crank.music.data.remote

/**
 * Turns whatever artwork URL a source happened to hand us into the largest variant it will
 * actually serve.
 *
 * Every source offers artwork at several sizes and, left alone, each of them hands the UI the
 * smallest one often enough to be visible: YouTube Music serves the same album art as `=w120-h120`
 * in one response and `=w544-h544` in another, and a 120 px image stretched across the 300 dp
 * now-playing artwork is what reads as a "blurry song image". The size in these URLs is a
 * *request*, not a limit — asking `googleusercontent` for `=w1080-h1080` returns a real 1080x1080
 * image, so raising it is free.
 *
 * One vocabulary, because three separate call sites each grew their own partial version of this
 * and they disagreed: the InnerTube parser upgraded its URLs while the NewPipe fallback passed
 * `lastOrNull()` through untouched, so the same album rendered sharp or blurry depending on which
 * path happened to answer.
 */
object ArtworkUrl {

    /**
     * Width requested for square artwork (album art).
     *
     * 1080 rather than something larger: `googleusercontent` caps out at 1200, and the biggest
     * surface in the UI is the 300 dp now-playing artwork, which is roughly 790 px on a typical
     * phone. Going past 1080 buys nothing and costs bandwidth on every scroll.
     */
    private const val TARGET_PX = 1080

    /**
     * Matches the leading size token of a `googleusercontent` suffix.
     *
     * Both spellings occur and they are not interchangeable: album art uses `=w544-h544-l90-rj`
     * while artist and channel art uses `=s192` / `=s1200`. Matching only `=w` makes every
     * `=s`-style candidate score zero, and `maxByOrNull` then returns the *first* of them — the
     * smallest — so a row offering 1200 px was served at 192 px.
     */
    private val GOOGLE_SIZE = Regex("""=[ws]\d+""")

    /** The width a thumbnail URL asks for. Used only to order candidates. */
    fun requestedWidthOf(url: String): Int =
        GOOGLE_SIZE.find(url)?.value?.drop(2)?.toIntOrNull() ?: 0

    /**
     * Picks the widest of several offered thumbnails, then upgrades it.
     *
     * Ordering matters even though the result is upgraded anyway: for hosts we do not rewrite, the
     * widest offered is still the best available, and a source that stops using `=wNNN` suffixes
     * silently degrades to the first entry rather than the last if we assumed ascending order.
     */
    fun bestOf(candidates: Iterable<String>): String {
        val widest = candidates
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .maxByOrNull { requestedWidthOf(it) }
            .orEmpty()
        return upgrade(widest)
    }

    /** Upgrades a single artwork URL. Blank in, blank out. */
    fun upgrade(raw: String): String {
        if (raw.isBlank()) return ""

        // Protocol-relative URLs are common in InnerTube responses and are not loadable as-is.
        val url = if (raw.startsWith("//")) "https:$raw" else raw

        return when {
            url.contains("ytimg.com") -> upgradeYtimg(url)
            // `mzstatic.com` is the CDN iTunes artwork actually arrives on, so matching only on
            // "apple"/"iTunes" would silently leave this source at its original size.
            url.contains("iTunes") || url.contains("apple.com") || url.contains("mzstatic") ->
                upgradeApple(url)
            url.contains("googleusercontent") -> upgradeGoogle(url)
            else -> url
        }
    }

    /**
     * Rewrites a YouTube image URL to `maxresdefault.jpg`.
     *
     * `hqdefault.jpg` is 480x360 and `maxresdefault.jpg` is 1280x720 — the difference between a
     * sharp and a visibly soft image when it is scaled up to fill the player. Any size-signature
     * query string (`?sqp=...`) is dropped, since it belongs to the variant being replaced.
     *
     * Falls back to the original URL when no video id can be read, because at that point there is
     * nothing to raise and guessing a shape could distort the image.
     */
    private fun upgradeYtimg(url: String): String {
        val videoId = Regex("""vi/([^/]+)/""").find(url)?.groupValues[1] ?: return url
        return "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"
    }

    /** iTunes hands out 100x100 by default; 600x600 is the largest it serves. */
    private fun upgradeApple(url: String): String =
        url.replace("100x100bb", "600x600bb")
            .replace("100x100", "600x600")

    /**
     * Rewrites a `googleusercontent` artwork URL to request [TARGET_PX], replacing whatever size
     * suffix it carries.
     *
     * The size lives in a suffix rather than a query parameter, and it appears in several shapes:
     * `=w544-h544-l90-rj` (album art), `=s192` (artist art), and `=w60-c-h60-k-c0x00ffffff`. A
     * rule written against only the first shape leaves the other two at their original size, which
     * is how rows offering 1200 px were served at 192 px and 60 px.
     *
     * The trailing flags are dropped rather than preserved. That is measured, not assumed: across
     * the three shapes, `=w1080-h1080` returns the same image as `=w1080-h1080-l90-rj`
     * (1080x1080, 356811 vs 356930 bytes) and is the only form that works on the `=s` and
     * `=w60-c-h60` URLs at all. Keeping them would mean three separate rules.
     *
     * Returns the URL unchanged when it carries no size suffix, because at that point there is
     * nothing to raise and guessing a shape could distort the image.
     */
    private fun upgradeGoogle(url: String): String {
        val size = GOOGLE_SIZE.find(url) ?: return url
        return url.substring(0, size.range.first) + "=w$TARGET_PX-h$TARGET_PX"
    }
}
