package com.elprofeta.app.data.repository

import com.elprofeta.app.data.local.FeedCache
import com.elprofeta.app.data.mapper.toDomain
import com.elprofeta.app.data.remote.ProfetaApi
import com.elprofeta.app.domain.model.NewsFeed
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/** Resultado de pedir la edicion semanal. */
sealed interface FeedResult {

    /** Edicion disponible. [fromCache] indica que se sirvio sin red. */
    data class Success(val feed: NewsFeed, val fromCache: Boolean) : FeedResult

    /** El backend respondio 404: usuario desconocido o sin edicion publicada. */
    data class NotAvailable(val reason: Reason) : FeedResult

    /** Fallo de red o del servidor; [cached] es lo ultimo que se descargo. */
    data class Failure(val cause: Throwable, val cached: NewsFeed?) : FeedResult

    enum class Reason { UNKNOWN_USER, NO_PUBLISHED_EDITION }
}

/** Acceso a las noticias: red primero, cache local como respaldo. */
interface NewsRepository {
    suspend fun getFeed(userId: Int): FeedResult
}

class DefaultNewsRepository(
    private val api: ProfetaApi,
    private val cache: FeedCache,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : NewsRepository {

    override suspend fun getFeed(userId: Int): FeedResult = withContext(dispatcher) {
        try {
            val dto = api.getNews(userId)
            cache.save(userId, dto)
            FeedResult.Success(dto.toDomain(), fromCache = false)
        } catch (exception: HttpException) {
            if (exception.code() == HTTP_NOT_FOUND) {
                // El backend distingue los dos casos en el cuerpo del error;
                // para la UI basta con el motivo.
                val reason = exception.detail()?.let { detail ->
                    if (detail.contains("usuario", ignoreCase = true)) {
                        FeedResult.Reason.UNKNOWN_USER
                    } else {
                        FeedResult.Reason.NO_PUBLISHED_EDITION
                    }
                } ?: FeedResult.Reason.NO_PUBLISHED_EDITION
                FeedResult.NotAvailable(reason)
            } else {
                FeedResult.Failure(exception, cache.read(userId)?.toDomain())
            }
        } catch (exception: IOException) {
            FeedResult.Failure(exception, cache.read(userId)?.toDomain())
        }
    }

    private fun HttpException.detail(): String? = try {
        response()?.errorBody()?.string()
    } catch (_: IOException) {
        null
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
    }
}
