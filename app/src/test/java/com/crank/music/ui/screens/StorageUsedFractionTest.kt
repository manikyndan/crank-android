package com.crank.music.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the storage-indicator fraction.
 *
 * The bug this guards against was a hard crash, not a wrong number. The screen's `totalStorage`
 * starts at 0 until its IO query resolves, so the first composition divided 0f by 0f. The
 * resulting NaN then survived `coerceIn` — NaN compares false against both bounds, so it fails
 * the clamp's minimum and maximum tests and is returned untouched — and `animateFloatAsState`
 * threw `IllegalStateException: AnimationVector cannot contain a NaN`, killing the app as soon as
 * the Offline screen was opened.
 *
 * The NaN case is asserted explicitly, because "it is not NaN" is precisely the property whose
 * absence caused the crash and the one an eye reading `coerceIn(0f, 1f)` would assume.
 */
class StorageUsedFractionTest {

    @Test
    fun `an unknown volume size yields zero rather than NaN`() {
        val fraction = storageUsedFraction(totalStorage = 0L, usedStorage = 0L)

        assertFalse(
            "0f / 0f is NaN, and NaN reaching an animation crashes the app",
            fraction.isNaN(),
        )
        assertEquals(0f, fraction, 0f)
    }

    @Test
    fun `an unknown size with usage reported still yields zero rather than NaN or infinity`() {
        // The two queries resolve independently, so usage can be known before the size is.
        val fraction = storageUsedFraction(totalStorage = 0L, usedStorage = 5_000L)

        assertFalse(fraction.isNaN())
        assertTrue("must stay finite", fraction.isFinite())
        assertEquals(0f, fraction, 0f)
    }

    @Test
    fun `a normal volume reports the used share`() {
        assertEquals(0.25f, storageUsedFraction(1000L, 250L), 0.0001f)
        assertEquals(0.5f, storageUsedFraction(1000L, 500L), 0.0001f)
    }

    @Test
    fun `the fraction never leaves zero to one`() {
        // Usage can briefly exceed the reported size (cache growth between the two reads).
        assertEquals(1f, storageUsedFraction(1000L, 1500L), 0.0001f)
        assertEquals(0f, storageUsedFraction(1000L, 0L), 0.0001f)
    }

    @Test
    fun `the result is always finite for any input`() {
        // The invariant that matters: whatever the numbers, this must never hand an animation a
        // value it cannot represent.
        val cases = listOf(0L to 0L, 0L to Long.MAX_VALUE, Long.MAX_VALUE to 0L, 1L to Long.MAX_VALUE)
        cases.forEach { (total, used) ->
            val fraction = storageUsedFraction(total, used)
            assertTrue(
                "total=$total used=$used produced $fraction",
                fraction.isFinite(),
            )
        }
    }
}
