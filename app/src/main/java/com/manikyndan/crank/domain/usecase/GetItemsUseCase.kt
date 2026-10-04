package com.manikyndan.crank.domain.usecase

import com.manikyndan.crank.core.util.Result
import com.manikyndan.crank.domain.model.Item
import com.manikyndan.crank.domain.repository.ItemRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Single-responsibility use case: fetch the list of items.
 * Keeps business rules out of the ViewModel so they are JVM-testable.
 */
class GetItemsUseCase @Inject constructor(
    private val repository: ItemRepository,
) {
    /** Invokes the repository and returns the UI-ready stream of states. */
    operator fun invoke(): Flow<Result<List<Item>>> = repository.getItems()
}
