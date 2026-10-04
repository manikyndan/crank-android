package com.manikyndan.crank.data.repository

import com.manikyndan.crank.core.util.Result
import com.manikyndan.crank.data.remote.ApiService
import com.manikyndan.crank.domain.model.Item
import com.manikyndan.crank.domain.repository.ItemRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Data-layer implementation of [ItemRepository].
 * Converts Retrofit exceptions into [Result.Error] so callers only handle [Result].
 */
class ItemRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
) : ItemRepository {

    override fun getItems(): Flow<Result<List<Item>>> = flow {
        emit(Result.Loading())
        try {
            val items: List<Item> = apiService.getItems()
            emit(Result.Success(items))
        } catch (e: Exception) {
            emit(Result.Error(exception = e))
        }
    }
}
