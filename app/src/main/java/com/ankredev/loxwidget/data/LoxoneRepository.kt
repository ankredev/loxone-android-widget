package com.ankredev.loxwidget.data

import android.content.Context
import com.ankredev.loxwidget.data.config.ConfigStore
import com.ankredev.loxwidget.data.config.ConnectionConfig
import com.ankredev.loxwidget.data.config.SecretStore
import com.ankredev.loxwidget.data.loxone.AuthParams
import com.ankredev.loxwidget.data.loxone.LoxoneClient
import com.ankredev.loxwidget.data.loxone.LoxoneException
import com.ankredev.loxwidget.data.loxone.SelectableValue
import com.ankredev.loxwidget.data.loxone.StateValue
import com.ankredev.loxwidget.data.loxone.StructureParser
import java.util.UUID

/**
 * Fasst Loxone-Client, Konfiguration und Token-Caching zu einer einfachen API
 * für UI, Widget-Worker und Befehle zusammen.
 */
class LoxoneRepository private constructor(
    private val configStore: ConfigStore,
    private val secretStore: SecretStore,
) {
    /** Verbindungstest: holt einen Token mit den übergebenen Daten. */
    suspend fun testConnection(host: String, user: String, password: String): Result<Unit> {
        val clientUuid = configStore.getConnection()?.clientUuid?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()
        val client = LoxoneClient(host)
        return client.acquireToken(user, password, clientUuid, CLIENT_INFO).map { }
    }

    /** Speichert Verbindung + Passwort und legt eine Client-UUID an. */
    suspend fun saveConnection(host: String, user: String, password: String, pollSeconds: Int) {
        val existing = configStore.getConnection()
        val clientUuid = existing?.clientUuid?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()
        configStore.saveConnection(
            ConnectionConfig(host, user, clientUuid, pollSeconds.coerceAtLeast(MIN_POLL_SECONDS)),
        )
        secretStore.password = password
        secretStore.clearToken()
    }

    suspend fun loadSelectableValues(): Result<List<SelectableValue>> {
        val (client, auth) = authedClient().getOrElse { return Result.failure(it) }
        return client.loadStructure(auth).map { StructureParser.toSelectableValues(it) }
    }

    suspend fun readValues(stateUuids: List<String>): Map<String, StateValue> {
        val (client, auth) = authedClient().getOrElse { return emptyMap() }
        return client.readStates(stateUuids, auth)
    }

    suspend fun sendCommand(uuidAction: String, command: String): Result<String> {
        val (client, auth) = authedClient().getOrElse { return Result.failure(it) }
        return client.sendCommand(uuidAction, command, auth)
    }

    // --- Token-Verwaltung --------------------------------------------------

    private suspend fun authedClient(): Result<Pair<LoxoneClient, AuthParams.Token>> {
        val conn = configStore.getConnection()
            ?: return Result.failure(LoxoneException("Keine Verbindung konfiguriert"))
        val password = secretStore.password
            ?: return Result.failure(LoxoneException("Kein Passwort gespeichert"))
        val client = LoxoneClient(conn.host)

        val token = validCachedToken() ?: run {
            val fresh = client.acquireToken(conn.user, password, conn.clientUuid, CLIENT_INFO)
                .getOrElse { return Result.failure(it) }
            secretStore.token = fresh.token
            secretStore.tokenValidUntil = fresh.validUntil
            fresh.token
        }
        return Result.success(client to AuthParams.Token(token, conn.user))
    }

    private fun validCachedToken(): String? {
        val token = secretStore.token ?: return null
        val nowLox = System.currentTimeMillis() / 1000 - LOX_EPOCH
        return if (secretStore.tokenValidUntil - nowLox > TOKEN_REFRESH_BUFFER) token else null
    }

    companion object {
        const val MIN_POLL_SECONDS = 5
        private const val CLIENT_INFO = "LoxoneWidgetAndroid"
        private const val LOX_EPOCH = 1230768000L // 2009-01-01T00:00:00Z in Unix-Sekunden
        private const val TOKEN_REFRESH_BUFFER = 3600L // 1h Puffer vor Ablauf

        @Volatile private var instance: LoxoneRepository? = null

        fun get(context: Context): LoxoneRepository =
            instance ?: synchronized(this) {
                instance ?: LoxoneRepository(
                    ConfigStore(context.applicationContext),
                    SecretStore(context.applicationContext),
                ).also { instance = it }
            }
    }
}
