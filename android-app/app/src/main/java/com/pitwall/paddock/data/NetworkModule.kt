package com.pitwall.paddock.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object NetworkModule {
    private var _api: PitwallApi? = null
    val api: PitwallApi
        get() = requireNotNull(_api) { "Call NetworkModule.init() in Application" }

    fun init(baseUrl: String) {
        if (_api != null) return
        val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        val media = "application/json".toMediaType()
        _api = Retrofit.Builder()
            .baseUrl(url)
            .client(client)
            .addConverterFactory(json.asConverterFactory(media))
            .build()
            .create(PitwallApi::class.java)
    }
}
