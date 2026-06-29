package com.ankredev.loxwidget.data.config

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "loxone_config")

/**
 * Persistiert Verbindungsdaten (global) und Widget-Konfigurationen (pro appWidgetId)
 * als JSON in DataStore. Passwörter/Token liegen separat im [SecretStore].
 */
class ConfigStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private val connectionKey = stringPreferencesKey("connection")
    private fun widgetKey(appWidgetId: Int) = stringPreferencesKey("widget_$appWidgetId")

    // --- Verbindung --------------------------------------------------------

    val connection: Flow<ConnectionConfig?> = context.dataStore.data.map { prefs ->
        prefs[connectionKey]?.let { json.decodeFromString(ConnectionConfig.serializer(), it) }
    }

    suspend fun getConnection(): ConnectionConfig? = connection.first()

    suspend fun saveConnection(config: ConnectionConfig) {
        context.dataStore.edit { it[connectionKey] = json.encodeToString(ConnectionConfig.serializer(), config) }
    }

    // --- Widget-Konfiguration ---------------------------------------------

    suspend fun getWidgetConfig(appWidgetId: Int): WidgetConfig? =
        context.dataStore.data.first()[widgetKey(appWidgetId)]
            ?.let { json.decodeFromString(WidgetConfig.serializer(), it) }

    fun widgetConfigFlow(appWidgetId: Int): Flow<WidgetConfig?> =
        context.dataStore.data.map { prefs ->
            prefs[widgetKey(appWidgetId)]
                ?.let { json.decodeFromString(WidgetConfig.serializer(), it) }
        }

    suspend fun saveWidgetConfig(config: WidgetConfig) {
        context.dataStore.edit {
            it[widgetKey(config.appWidgetId)] =
                json.encodeToString(WidgetConfig.serializer(), config)
        }
    }

    suspend fun deleteWidgetConfig(appWidgetId: Int) {
        context.dataStore.edit { it.remove(widgetKey(appWidgetId)) }
    }
}
