package com.manikyndan.crank.domain.repository

import com.manikyndan.crank.core.util.Result
import com.manikyndan.crank.domain.model.Item
import kotlinx.coroutines.flow.Flow

/** Contract for item data, implemented in the data layer. */
interface ItemRepository {
    /** Emits [Result.Loading], then [Result.Success] or [Result.Error]. */
    fun getItems(): Flow<Result<List<Item>>>
}
