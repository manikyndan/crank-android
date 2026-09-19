package com.crank.music.data.remote.innertube

/**
 * The InnerTube client identities CRANK uses, and the order in which they are tried.
 *
 * Adapted from the Echo Music project (GPL-3.0), whose maintainers measured these behaviours
 * on-device and recorded the evidence inline. The measurements are reproduced here because
 * they are the difference between a chain that works and one that looks like it should.
 *
 * ## Do not reorder [fallbackChain] without re-measuring
 *
 * The order encodes measured results. It is not a preference, and it is not alphabetical or
 * "most modern first". See the per-entry notes.
 */
object YouTubeClients {

    // region Metadata / browse clients

    /** Anonymous web client. Used for search and browse where no token is required. */
    val WEB =
        YouTubeClient(
            clientName = "WEB",
            clientVersion = "2.20260213.00.00",
            clientId = "1",
            userAgent = YouTubeClient.USER_AGENT_WEB,
        )

    /**
     * YouTube Music web client. This is CRANK's **metadata and history** client.
     *
     * It is deliberately *not* the client streams are taken from for normal content. Its
     * formats sit behind the signature cipher / `n`-challenge, and it additionally requires a
     * PoToken to avoid a first-byte 403. In the cascade it appears only at `clientIndex == -1`.
     */
    val WEB_REMIX =
        YouTubeClient(
            clientName = "WEB_REMIX",
            clientVersion = "1.20260213.01.00",
            clientId = "67",
            userAgent = YouTubeClient.USER_AGENT_WEB,
            loginSupported = true,
            useSignatureTimestamp = true,
            useWebPoTokens = true,
        )

    /**
     * The only client that answers `OK` for age-restricted / explicit tracks, because it is the
     * only authenticated identity left in the chain. Every other client is refused with an
     * age-verification demand.
     *
     * `useWebPoTokens` is load-bearing. With it, `YTPlayerUtils` appends `pot=` to the
     * deciphered URL. Without it we produced a URL with a correct signature and a transformed
     * `n` that googlevideo still rejected with 403 on the very first byte.
     */
    val WEB_CREATOR =
        YouTubeClient(
            clientName = "WEB_CREATOR",
            clientVersion = "1.20260213.00.00",
            clientId = "62",
            userAgent = YouTubeClient.USER_AGENT_WEB,
            loginSupported = true,
            loginRequired = true,
            useSignatureTimestamp = true,
            useWebPoTokens = true,
        )

    /**
     * TV client. Required for uploaded / privately-owned tracks, which no mobile client will
     * serve — hence its position in the private-track start index below.
     */
    val TVHTML5 =
        YouTubeClient(
            clientName = "TVHTML5",
            clientVersion = "7.20260213.00.00",
            clientId = "7",
            userAgent =
                "Mozilla/5.0(SMART-TV; Linux; Tizen 4.0.0.2) AppleWebkit/605.1.15 (KHTML, like Gecko) SamsungBrowser/9.2 TV Safari/605.1.15",
            loginSupported = true,
            loginRequired = true,
            useSignatureTimestamp = true,
            useWebPoTokens = true,
        )

    /**
     * Current ANDROID_VR pin, matching yt-dlp master and YouTube.js.
     *
     * ## The version number is the whole point
     *
     * [ANDROID_VR_1_43_32] and the older 1.61.48 are both bot-gated **by client version**:
     * measured on three videoIds, both return `LOGIN_REQUIRED` / "Sign in to confirm you're not
     * a bot" with zero formats — anonymously *and* signed in, with or without visitor data, and
     * with the device fields stripped. Only the version differs from this entry, and 1.65.10
     * returns `OK` with direct URLs. So this is a server-side version gate, and bumping this
     * string is how the gate is passed.
     *
     * ## Why the field set looks inconsistent
     *
     * Note the deliberate omissions versus the older pins: no `buildId`, no `cronetVersion`, no
     * `packageName`; `osVersion` is `"12L"` rather than `"12"`; and the user agent is the
     * `... gzip` form rather than the Cronet form. These are byte-for-byte the values yt-dlp and
     * YouTube.js both pin. A Cronet-style variant was probed and behaves identically, so the
     * user agent is not the discriminator — but there is no reason to diverge from upstream.
     *
     * Requires a visitor data id. Without one it returns `LOGIN_REQUIRED` like its siblings.
     */
    val ANDROID_VR_1_65_10 =
        YouTubeClient(
            clientName = "ANDROID_VR",
            clientVersion = "1.65.10",
            clientId = "28",
            userAgent =
                "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
            osName = "Android",
            osVersion = "12L",
            deviceMake = "Oculus",
            deviceModel = "Quest 3",
            androidSdkVersion = "32",
            friendlyName = "Android VR 1.65",
            loginSupported = false,
            useSignatureTimestamp = false,
        )

    /** Kept as the control against [ANDROID_VR_1_65_10]. Bot-gated by version. */
    val ANDROID_VR_1_43_32 =
        YouTubeClient(
            clientName = "ANDROID_VR",
            clientVersion = "1.43.32",
            clientId = "28",
            userAgent =
                "com.google.android.apps.youtube.vr.oculus/1.43.32 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/107.0.5284.2)",
            osName = "Android",
            osVersion = "12",
            deviceMake = "Oculus",
            deviceModel = "Quest 3",
            androidSdkVersion = "32",
            buildId = "SQ3A.220605.009.A1",
            cronetVersion = "107.0.5284.2",
            packageName = "com.google.android.apps.youtube.vr.oculus",
            friendlyName = "Android VR 1.43",
            loginSupported = false,
            useSignatureTimestamp = false,
        )

    /**
     * visionOS client. **The only client measured to serve a complete file**, which is why it
     * leads the chain.
     *
     * The measurement that put it there: on three videoIds it read every byte of the file
     * (2.4 / 4.5 / 4.7 MB, 100% HTTP 206), survived 51 ranged reads paced over 300 seconds, and
     * returned 206 for the exact byte offsets that 403 in every other client above it.
     *
     * It is an internal client for an unreleased platform and may stop working at any time.
     * That is an accepted risk, not an oversight — see the chain-ordering note.
     */
    val VISIONOS =
        YouTubeClient(
            clientName = "VISIONOS",
            clientVersion = "0.1",
            clientId = "101",
            userAgent =
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15",
            osName = "visionOS",
            osVersion = "1.3.21O771",
            deviceMake = "Apple",
            deviceModel = "RealityDevice14,1",
            friendlyName = "visionOS",
            loginSupported = false,
            useSignatureTimestamp = false,
        )

    /** iPhone client. Serves roughly a 1 MiB preview only — see the cascade note. Last resort. */
    val IOS =
        YouTubeClient(
            clientName = "IOS",
            clientVersion = "21.03.1",
            clientId = "5",
            userAgent = "com.google.ios.youtube/21.03.1 (iPhone16,2; U; CPU iOS 18_2 like Mac OS X;)",
            osVersion = "18.2.22C152",
        )

    /**
     * iPad client. **Serves roughly a 1 MiB preview only** — see the cascade note. Kept at the
     * tail deliberately: a 1 MiB preview still beats no stream at all if everything above it
     * fails, but it must never be reached while a client above it can serve the file.
     */
    val IPADOS =
        YouTubeClient(
            clientName = "IOS",
            clientVersion = "21.03.3",
            clientId = "5",
            userAgent =
                "com.google.ios.youtube/21.03.3 (iPad7,6; U; CPU iPadOS 17_7_10 like Mac OS X; en-US)",
            osName = "iPadOS",
            osVersion = "17.7.10.21H450",
            deviceMake = "Apple",
            deviceModel = "iPad7,6",
            friendlyName = "iPadOS",
            loginSupported = false,
            useSignatureTimestamp = false,
            packageName = "com.google.ios.youtube",
        )

    // endregion

    // region The stream chain

    /**
     * The stream fallback chain, ordered by **measured** ability to serve a whole file.
     *
     * ## The measurement that determines this order
     *
     * An IOS / IPADOS / old-ANDROID_VR stream URL is a **~1 MiB preview**. googlevideo serves a
     * fixed byte prefix and answers 403 to everything past it. The cap was binary-searched to
     * the byte and is stable across independent resolves:
     *
     * ```
     * videoId       itag  last readable byte   meaning
     * Rr1Cdli5nE8   1040807                     ~61s of 268s
     * phLb_SoPBlA   1049091                     ~61s of 274s
     * UbX5Yns8fHk   1019638                     ~67s of 159s
     * ```
     *
     * It is *not* request count, not rate limiting, and not expiry. A completely fresh URL asked
     * for `bytes=524288-1048575` as its **first** request already 403s, and a range straddling
     * the cap is rejected wholesale. Adding `cpn`/`rn`, the client's own user agent,
     * Origin/Referer, `alr`, `ratebypass`, or a bogus `pot` changes nothing.
     *
     * This is what produced field reports of playback dying after 30-90 seconds: ExoPlayer reads
     * ahead, so the 403 lands before the audible stall. It is the reason this file exists.
     *
     * ## Ordering rules
     *
     * - [VISIONOS] first: the only client observed serving a whole file.
     * - [ANDROID_VR_1_65_10] second: current upstream pin, whole-file capable.
     * - [TVHTML5] third: the client that handles uploaded / privately-owned tracks.
     * - [ANDROID_VR_1_43_32] fourth: retained as a control; currently version-gated.
     * - [IPADOS] / [IOS] fifth and sixth: preview-length only, genuine last resort.
     * - [WEB_CREATOR] last: the only age-restricted-capable identity, but requires login.
     */
    val fallbackChain: List<YouTubeClient> =
        listOf(
            VISIONOS,
            ANDROID_VR_1_65_10,
            TVHTML5,
            ANDROID_VR_1_43_32,
            IPADOS,
            IOS,
            WEB_CREATOR,
        )

    /**
     * The client whose formats are fetched for playback, at `clientIndex == -1` in the cascade.
     * Metadata and history always come from here regardless of which client supplies the audio.
     */
    val MAIN_CLIENT: YouTubeClient = WEB_REMIX

    /**
     * Where the cascade starts for normal content: index **0**, i.e. the top of the chain.
     *
     * ## This constant is pinned on purpose. Do not replace it with an `indexOf` call.
     *
     * It used to be expressed as `indexOf(ANDROID_VR_1_43_32)`, which under an earlier ordering
     * happened to equal the intended value. When the chain was reordered, that same expression
     * silently resolved to **4** — skipping visionOS, ANDROID_VR 1.65.10 and both TVHTML5
     * entries on *every* normal-content playback. In other words it would have quietly disabled
     * the entire fix while still compiling and still looking correct.
     *
     * The lesson is generalisable: never derive a start index from the position of a member
     * that can move. Pin it, and assert it in a test. See `YouTubeClientsTest`.
     */
    const val NORMAL_CONTENT_STREAM_START_INDEX: Int = 0

    /**
     * Where the cascade starts for privately-owned (uploaded) tracks.
     *
     * Resolved by *identity* rather than a hardcoded number, because the correct index is
     * "wherever TVHTML5 happens to be". A hardcoded `1` was correct under an old ordering and
     * would now point at [ANDROID_VR_1_65_10], which cannot serve these tracks.
     *
     * This is the one place where an `indexOf` is the right tool — the intent is genuinely
     * "follow this client", so it must track the client rather than a position. It is still
     * asserted in a test.
     */
    val PRIVATE_TRACK_STREAM_START_INDEX: Int =
        fallbackChain.indexOfFirst { it == TVHTML5 }.takeIf { it >= 0 } ?: 0

    // endregion
}
