package com.crank.music.data.remote.gaana

/**
 * Where the GaanaPy server lives, and whether a given value is usable.
 *
 * ## Why this is shared rather than inline in the screen
 *
 * Two places must agree on what a "valid" base URL is: the settings screen, which has to tell the
 * user *why* their input was refused, and [GaanaApi.baseUrl], which is the code that actually
 * decides at runtime. If they disagreed, the app would happily store a value it later ignored and
 * report the source as configured while it silently returned nothing — precisely the "setting
 * appears to save then reverts" bug this store was written to remove. One function, one verdict.
 */
internal sealed interface GaanaUrlValidation {
    /**
     * Blank input. This is not an error: empty means the source is switched off, and
     * [GaanaApi.baseUrl] treats it that way too.
     */
    data object Disabled : GaanaUrlValidation

    /** Usable. [value] is normalised and is exactly what should be persisted. */
    data class Ok(val value: String) : GaanaUrlValidation

    /** Unusable. [reason] is written for a human and is safe to render directly. */
    data class Rejected(val reason: String) : GaanaUrlValidation
}

/**
 * Classifies [raw] as a GaanaPy base URL.
 *
 * The rules are deliberately the same as [GaanaApi.baseUrl]'s runtime filter, and both live here so
 * the screen cannot accept something the client would later reject.
 *
 * Only the scheme is checked, not reachability — a URL is a *claim about where a server might be*,
 * and nothing here has tried it yet. "Does it work" is a separate question answered by the explicit
 * Test connection action, because validating liveness on every keystroke would hammer a server the
 * user is halfway through typing.
 */
internal fun validateGaanaBaseUrl(raw: String): GaanaUrlValidation {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return GaanaUrlValidation.Disabled

    // No scheme: Ktor would treat the value as a relative reference and resolve it against nothing,
    // which surfaces later as an opaque connection failure rather than as a mistyped setting.
    if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
        return GaanaUrlValidation.Rejected("Must start with http:// or https://")
    }

    val afterScheme = trimmed.substringAfter("://")
    if (afterScheme.isBlank() || afterScheme.startsWith("/")) {
        return GaanaUrlValidation.Rejected("Needs a host, for example 192.168.1.42:8000")
    }
    if (afterScheme.contains(' ')) {
        return GaanaUrlValidation.Rejected("A host cannot contain spaces")
    }

    // Trailing slashes are stripped so GaanaApi can concatenate paths without doubling up.
    return GaanaUrlValidation.Ok(trimmed.removeSuffix("/"))
}
