package com.manikyndan.crank.domain.model

import kotlinx.serialization.Serializable

/**
 * Sample domain model. The domain layer is pure Kotlin — no Android, no Retrofit,
 * no Room annotations — so it stays unit-testable on the JVM.
 */
@Serializable
data class Item(
    val id: Int,
    val title: String,
    val description: String,
)
