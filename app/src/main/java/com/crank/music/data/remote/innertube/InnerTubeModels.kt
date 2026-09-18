package com.crank.music.data.remote.innertube

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class InnerTubeContext(
    val client: InnerTubeClient = InnerTubeClient()
)

@Serializable
data class InnerTubeClient(
    val clientName: String = "WEB_REMIX",
    val clientVersion: String = "1.20231212.00.00",
    val hl: String = "en",
    val gl: String = "US"
)

@Serializable
data class InnerTubeSearchRequest(
    val context: InnerTubeContext = InnerTubeContext(),
    val query: String
)

@Serializable
data class InnerTubeBrowseRequest(
    val context: InnerTubeContext = InnerTubeContext(),
    val browseId: String = "FEmusic_home"
)

@Serializable
data class InnerTubePlayerRequest(
    val context: InnerTubeContext = InnerTubeContext(),
    val videoId: String
)

@Serializable
data class InnerTubeSearchResponse(
    val contents: JsonObject? = null
)

@Serializable
data class InnerTubeBrowseResponse(
    val contents: JsonObject? = null
)

@Serializable
data class InnerTubePlayerResponse(
    val streamingData: JsonObject? = null,
    val playabilityStatus: JsonObject? = null
)
