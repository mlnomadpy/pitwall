package com.pitwall.app.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.pitwall.app.BuildConfig
import com.pitwall.app.di.SessionHolder
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object NetworkModule {

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }

    /** For manual encode/decode (SSE bodies, stream chunks). */
    val serializationJson: Json
        get() = json

    private val logging =
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val pitwallApi: PitwallApi by lazy {
        val contentType = "application/json".toMediaType()
        Retrofit.Builder()
            .baseUrl(BuildConfig.PITWALL_API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(PitwallApi::class.java)
    }

    fun cuesStreamUrl(sessionId: String? = SessionHolder.activeSessionId): String {
        val base = BuildConfig.PITWALL_API_BASE_URL.trimEnd('/') + "/cues/stream"
        return if (sessionId.isNullOrBlank()) {
            base
        } else {
            "$base?session_id=${java.net.URLEncoder.encode(sessionId, Charsets.UTF_8.name())}"
        }
    }

    /** SSE: async notification center (see `GET /notifications` in the bridge). */
    fun notificationsStreamUrl(): String {
        val base = BuildConfig.PITWALL_API_BASE_URL.trimEnd('/') + "/notifications"
        val d = SessionHolder.activeDriver.trim()
        val q =
            if (d.isNotBlank()) {
                java.net.URLEncoder.encode(d, Charsets.UTF_8.name())
            } else {
                "*"
            }
        return "$base?driver=$q"
    }
}
