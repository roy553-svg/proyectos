package com.elprofeta.app.data.remote

import com.elprofeta.app.data.remote.dto.NewsFeedDto
import retrofit2.http.GET
import retrofit2.http.Query

/** Endpoints del backend que consume la app. */
interface ProfetaApi {

    /**
     * Edicion semanal publicada, filtrada por las preferencias del lector.
     *
     * Devuelve 404 si el usuario no existe o si no hay edicion publicada.
     */
    @GET("api/v1/news")
    suspend fun getNews(@Query("user_id") userId: Int): NewsFeedDto
}
