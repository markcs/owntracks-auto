package com.owntracks.auto.data

import com.owntracks.auto.settings.BasicAuth
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds [ApiService] instances for a runtime-configurable base URL. The base
 * URL changes when the user edits it in Settings, so a service is rebuilt
 * rather than cached as a singleton.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
object ApiClientFactory {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    private val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /** Normalise a user-entered base URL to a Retrofit-acceptable form. */
    fun normalizeBaseUrl(raw: String): String {
        var url = raw.trim()
        if (url.isEmpty()) return url
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        if (!url.endsWith("/")) url += "/"
        return url
    }

    /**
     * Build an [ApiService] for [baseUrl]. When [auth] is enabled, an
     * `Authorization: Basic …` header is attached to every request.
     */
    fun create(baseUrl: String, auth: BasicAuth? = null): ApiService {
        val normalized = normalizeBaseUrl(baseUrl)
        val client = if (auth != null && auth.isEnabled) {
            val header = Credentials.basic(auth.username, auth.password)
            okHttp.newBuilder()
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("Authorization", header)
                        .build()
                    chain.proceed(request)
                }
                .build()
        } else {
            okHttp
        }
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }
}
