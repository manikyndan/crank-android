package com.crank.music.di

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.timeout
import io.ktor.http.headers
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    private const val HTTP_TAG = "CRANK_HTTP"

    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            prettyPrint = false
            isLenient = true
            encodeDefaults = true
        }
    }

    @Provides
    @Singleton
    fun provideHttpClient(json: Json): HttpClient {
        return HttpClient(Android) {
            install(ContentNegotiation) {
                json(json)
            }
            install(io.ktor.client.plugins.HttpTimeout) {
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
                requestTimeoutMillis = 20_000
            }
            defaultRequest {
                headers.append("User-Agent", USER_AGENT)
            }

            // Without this, a non-2xx response does NOT throw. Callers that then do
            // `.body<JsonObject>()` try to decode an HTML/plain-text error page as
            // JSON, raise a JsonDecodingException, and any surrounding
            // `catch (e: Exception)` reports the *symptom* ("unexpected token")
            // rather than the *cause* (401, 404, 429...). That made upstream
            // API breakage indistinguishable from a genuinely empty result.
            //
            // Turning it on centrally means every caller's error handling is
            // meaningful and failures name their status code.
            expectSuccess = true

            HttpResponseValidator {
                handleResponseExceptionWithRequest { cause, request ->
                    // Surface which endpoint failed, so a dead service is
                    // immediately identifiable in logcat instead of looking like
                    // a generic parse error.
                    if (cause is ResponseException) {
                        Log.e(
                            HTTP_TAG,
                            "HTTP ${cause.response.status.value} from " +
                                "${request.method.value} ${request.url}"
                        )
                    }
                }
            }
        }
    }
}
