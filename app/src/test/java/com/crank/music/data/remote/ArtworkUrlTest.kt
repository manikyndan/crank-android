package com.crank.music.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for turning an offered artwork URL into the largest one the source will serve.
 *
 * This is the logic behind a real bug: the same album art arrived as `=w120-h120` in one response
 * and `=w544-h544` in another, was passed to the UI untouched, and a 120 px image stretched across
 * the 300 dp player artwork is what the user reported as a blurry song image. Every case below is
 * about not repeating that.
 *
 * The section that matters most is the *unchanged* cases. Rewriting a URL we do not understand is
 * how artwork turns into a 404 or a distorted crop, which is worse than a soft image, so each
 * guard has its own test.
 */
class ArtworkUrlTest {

    // --- googleusercontent: the size suffix is a request, not a limit --------

    @Test
    fun raisesGoogleSizeTo1080() {
        val raised = ArtworkUrl.upgrade(
            "https://lh3.googleusercontent.com/abc123=w120-h120-l90-rj"
        )
        assertEquals("https://lh3.googleusercontent.com/abc123=w1080-h1080-l90-rj", raised)
    }

    @Test
    fun raisingPreservesCroppingFlags() {
        // `-l90-rj` is what asks for the cropped, re-encoded square the UI wants. Dropping it
        // changes the framing, so only the two numbers may be replaced.
        val raised = ArtworkUrl.upgrade(
            "https://lh3.googleusercontent.com/abc=w544-h544-l90-rj"
        )
        assertEquals("https://lh3.googleusercontent.com/abc=w1080-h1080-l90-rj", raised)
    }

    @Test
    fun raisingIsIdempotent() {
        val once = ArtworkUrl.upgrade("https://lh3.googleusercontent.com/abc=w120-h120-l90-rj")
        assertEquals(once, ArtworkUrl.upgrade(once))
    }

    @Test
    fun googleUrlWithoutSizeSuffixIsLeftAlone() {
        // No size to raise, and guessing a shape could distort the image.
        val url = "https://lh3.googleusercontent.com/abc123"
        assertEquals(url, ArtworkUrl.upgrade(url))
    }

    // --- i.ytimg.com: hqdefault is 480x360, maxresdefault is 1280x720 -------

    @Test
    fun rewritesYtimgToMaxres() {
        assertEquals(
            "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg",
            ArtworkUrl.upgrade("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg")
        )
    }

    @Test
    fun rewritesYtimgToMaxresFromAnyVariant() {
        for (variant in listOf("default.jpg", "mqdefault.jpg", "sddefault.jpg", "hq720.jpg")) {
            assertEquals(
                "https://i.ytimg.com/vi/abc123/maxresdefault.jpg",
                ArtworkUrl.upgrade("https://i.ytimg.com/vi/abc123/$variant")
            )
        }
    }

    @Test
    fun dropsSizeSignatureQueryWhenRewritingYtimg() {
        // NewPipe hands back `?sqp=...&rs=...`, which belongs to the variant being replaced.
        assertEquals(
            "https://i.ytimg.com/vi/abc123/maxresdefault.jpg",
            ArtworkUrl.upgrade("https://i.ytimg.com/vi/abc123/hqdefault.jpg?sqp=-oaymwEj&rs=AOn4CLA")
        )
    }

    @Test
    fun ytimgWithoutVideoIdIsLeftAlone() {
        val url = "https://i.ytimg.com/some/other/path.jpg"
        assertEquals(url, ArtworkUrl.upgrade(url))
    }

    // --- Apple / iTunes -----------------------------------------------------

    @Test
    fun raisesAppleArtworkFrom100To600() {
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/Music124/v4/ab/cd/600x600bb.jpg",
            ArtworkUrl.upgrade(
                "https://is1-ssl.mzstatic.com/image/thumb/Music124/v4/ab/cd/100x100bb.jpg"
            )
        )
    }

    @Test
    fun raisesAppleArtworkWithoutTheBbSuffix() {
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/Music/v4/ab/600x600.jpg",
            ArtworkUrl.upgrade("https://is1-ssl.mzstatic.com/image/thumb/Music/v4/ab/100x100.jpg")
        )
    }

    // --- URL shape and unknown hosts ----------------------------------------

    @Test
    fun protocolRelativeUrlGetsAScheme() {
        val raised = ArtworkUrl.upgrade("//lh3.googleusercontent.com/abc=w120-h120-l90-rj")
        assertEquals("https://lh3.googleusercontent.com/abc=w1080-h1080-l90-rj", raised)
    }

    @Test
    fun blankStaysBlank() {
        assertEquals("", ArtworkUrl.upgrade(""))
        assertEquals("", ArtworkUrl.upgrade("   "))
    }

    @Test
    fun unknownHostIsLeftAlone() {
        val url = "https://example.com/art/small.jpg"
        assertEquals(url, ArtworkUrl.upgrade(url))
    }

    // --- bestOf: pick the widest offer, then upgrade ------------------------

    @Test
    fun bestOfPicksTheWidestOffer() {
        val picked = ArtworkUrl.bestOf(
            listOf(
                "https://lh3.googleusercontent.com/a=w120-h120-l90-rj",
                "https://lh3.googleusercontent.com/a=w544-h544-l90-rj",
                "https://lh3.googleusercontent.com/a=w226-h226-l90-rj"
            )
        )
        assertEquals("https://lh3.googleusercontent.com/a=w1080-h1080-l90-rj", picked)
    }

    @Test
    fun bestOfDoesNotAssumeAscendingOrder() {
        // "last" is the usual answer but silently degrades to the smallest if a source ever ships
        // the array the other way round, so the choice is by width rather than by position.
        val picked = ArtworkUrl.bestOf(
            listOf(
                "https://lh3.googleusercontent.com/a=w544-h544-l90-rj",
                "https://lh3.googleusercontent.com/a=w120-h120-l90-rj"
            )
        )
        assertEquals("https://lh3.googleusercontent.com/a=w1080-h1080-l90-rj", picked)
    }

    @Test
    fun bestOfPrefersMaxresOverHqWhenBothOffered() {
        val picked = ArtworkUrl.bestOf(
            listOf(
                "https://i.ytimg.com/vi/abc123/hqdefault.jpg",
                "https://i.ytimg.com/vi/abc123/maxresdefault.jpg"
            )
        )
        assertEquals("https://i.ytimg.com/vi/abc123/maxresdefault.jpg", picked)
    }

    @Test
    fun bestOfEmptyIsBlank() {
        assertEquals("", ArtworkUrl.bestOf(emptyList()))
    }

    @Test
    fun bestOfSkipsBlankEntries() {
        val picked = ArtworkUrl.bestOf(listOf("", "https://lh3.googleusercontent.com/a=w120-h120-rj"))
        assertEquals("https://lh3.googleusercontent.com/a=w1080-h1080-rj", picked)
    }
}
