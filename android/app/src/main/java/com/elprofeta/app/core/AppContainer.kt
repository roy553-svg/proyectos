package com.elprofeta.app.core

import android.content.Context
import com.elprofeta.app.BuildConfig
import com.elprofeta.app.data.local.FeedCache
import com.elprofeta.app.data.remote.ProfetaApi
import com.elprofeta.app.data.repository.DefaultNewsRepository
import com.elprofeta.app.data.repository.NewsRepository
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

/**
 * Inyeccion de dependencias manual.
 *
 * La app tiene un unico repositorio y un unico endpoint: un contenedor
 * explicito es mas simple de leer (y de testear) que anadir Hilt/KSP.
 */
interface AppContainer {
    val newsRepository: NewsRepository
}

class DefaultAppContainer(context: Context) : AppContainer {

    private val json = Json {
        ignoreUnknownKeys = true   // el backend puede anadir campos nuevos
        explicitNulls = false
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
                )
            }
        }
        .build()

    private val api: ProfetaApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(httpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ProfetaApi::class.java)

    override val newsRepository: NewsRepository =
        DefaultNewsRepository(api = api, cache = FeedCache(context.applicationContext, json))

    private companion object {
        const val TIMEOUT_SECONDS = 20L
    }
}
