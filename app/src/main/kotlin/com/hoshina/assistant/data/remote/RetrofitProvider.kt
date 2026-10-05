package com.hoshina.assistant.data.remote

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitProvider {

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            redactHeader("X-Hoshina-Key")
            level = HttpLoggingInterceptor.Level.BASIC
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    fun createApiService(baseUrl: String, apiKey: String = ""): ApiService {
        val normalized = normalizeBaseUrl(baseUrl)
        require(apiKey.isBlank() || normalized.startsWith("https://") || BackendUrlResolver.isLocalBackend(normalized)) {
            "External connections with an API key require HTTPS."
        }
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(okHttpClient.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                    if (apiKey.isNotBlank()) request.header("X-Hoshina-Key", apiKey.trim())
                    chain.proceed(request.build())
                }
                .build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    fun normalizeBaseUrl(url: String): String {
        val trimmed = url.trim()
        require(trimmed.isNotEmpty()) { "API base URL must not be empty." }
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
        val parsed = withScheme.toHttpUrl()
        require(parsed.username.isEmpty() && parsed.password.isEmpty()) { "Do not put credentials in the URL." }
        require(parsed.query == null && parsed.fragment == null) { "Use a base URL without query or fragment." }
        return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
    }
}
