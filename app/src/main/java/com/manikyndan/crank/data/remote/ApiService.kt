package com.manikyndan.crank.data.remote

import com.manikyndan.crank.domain.model.Item
import retrofit2.http.GET

/** Retrofit service for the sample REST API. DTOs map 1:1 to [Item] here for brevity. */
interface ApiService {
    /** Fetches all items. Suspends without blocking a thread (OkHttp + coroutines). */
    @GET("items")
    suspend fun getItems(): List<Item>
}
