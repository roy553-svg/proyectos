package com.elprofeta.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.elprofeta.app.data.remote.dto.NewsFeedDto
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "el_profeta")

/**
 * Cache local de la ultima edicion descargada.
 *
 * El periodico se actualiza una vez por semana, asi que guardar la ultima
 * respuesta permite abrir la app sin conexion. Se almacena el JSON tal cual:
 * no hace falta una base de datos para un unico documento.
 */
class FeedCache(
    private val context: Context,
    private val json: Json,
) {

    suspend fun save(userId: Int, feed: NewsFeedDto) {
        context.dataStore.edit { preferences ->
            preferences[KEY_FEED] = json.encodeToString(NewsFeedDto.serializer(), feed)
            preferences[KEY_USER_ID] = userId
            preferences[KEY_SAVED_AT] = System.currentTimeMillis()
        }
    }

    /** Devuelve la edicion cacheada de ese lector, o `null` si no sirve. */
    suspend fun read(userId: Int): NewsFeedDto? {
        val preferences = context.dataStore.data.first()
        if (preferences[KEY_USER_ID] != userId) return null
        val raw = preferences[KEY_FEED] ?: return null
        return try {
            json.decodeFromString(NewsFeedDto.serializer(), raw)
        } catch (_: SerializationException) {
            // Cache de una version anterior del contrato: se ignora.
            null
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    private companion object {
        val KEY_FEED = stringPreferencesKey("cached_feed")
        val KEY_USER_ID = intPreferencesKey("cached_user_id")
        val KEY_SAVED_AT = longPreferencesKey("cached_at")
    }
}
