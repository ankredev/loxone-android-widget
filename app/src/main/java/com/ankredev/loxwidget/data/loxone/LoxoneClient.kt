package com.ankredev.loxwidget.data.loxone

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * REST-Client für einen Loxone Miniserver (lokales Netz).
 *
 * Verantwortlich für: Token holen, Struktur (LoxAPP3.json) laden,
 * State-Werte lesen, Befehle senden. Alle Aufrufe sind suspend (IO-Dispatcher).
 */
class LoxoneClient(
    private val baseUrl: String, // z. B. "http://192.168.1.50"
    private val http: OkHttpClient = defaultHttp(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // --- Token holen -------------------------------------------------------

    suspend fun acquireToken(
        user: String,
        password: String,
        clientUuid: String,
        clientInfo: String,
    ): Result<LoxoneToken> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. getkey2
            val keyResp = getLL("/jdev/sys/getkey2/${enc(user)}").jsonObject
            val keyHex = keyResp["key"]!!.jsonPrimitive.content
            val salt = keyResp["salt"]!!.jsonPrimitive.content
            val hashAlg = keyResp["hashAlg"]?.jsonPrimitive?.contentOrNull ?: "SHA1"

            // 2./3. Auth-Hash
            val authHash = LoxoneAuth.computeAuthHash(user, password, keyHex, salt, hashAlg)

            // 4. getjwt
            val path = "/jdev/sys/getjwt/$authHash/${enc(user)}/" +
                "${LoxoneAuth.PERMISSION_APP}/${enc(clientUuid)}/${enc(clientInfo)}"
            val tokenObj = getLL(path).jsonObject
            LoxoneToken(
                token = tokenObj["token"]!!.jsonPrimitive.content,
                validUntil = tokenObj["validUntil"]?.jsonPrimitive?.longOrNull ?: 0L,
                tokenRights = tokenObj["tokenRights"]?.jsonPrimitive?.intOrNull ?: 0,
                key = tokenObj["key"]?.jsonPrimitive?.contentOrNull,
            )
        }
    }

    // --- Struktur laden ----------------------------------------------------

    suspend fun loadStructure(auth: AuthParams): Result<LoxoneStructure> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = url("/data/LoxAPP3.json", auth)
                val body = execute(url)
                json.decodeFromString(LoxoneStructure.serializer(), body)
            }
        }

    // --- Werte lesen -------------------------------------------------------

    /**
     * Liest den aktuellen Wert eines Controls über `GET /jdev/sps/io/{uuid}/all`.
     * Wichtig (empirisch verifiziert gegen reale Firmware):
     *  - der einfache Pfad `/jdev/sps/io/{uuid}` und `/state` liefern bei Schaltern
     *    konstant "0" und spiegeln den Live-Status NICHT wider,
     *  - nur `/all` liefert im Top-Level-`value` den echten Zustand ("1"/"0").
     * Loxone adressiert hier die **uuidAction** des Controls (reine State-UUIDs -> 404),
     * der Aufrufer übergibt daher die uuidAction.
     */
    suspend fun readState(uuid: String, auth: AuthParams): Result<StateValue> =
        withContext(Dispatchers.IO) {
            runCatching {
                val value = getLL("/jdev/sps/io/${enc(uuid)}/all", auth)
                StateValue(uuid, llValueToString(value))
            }.onFailure { Log.w(TAG, "readState($uuid) fehlgeschlagen: ${it.message}") }
        }

    suspend fun readStates(
        uuids: List<String>,
        auth: AuthParams,
    ): Map<String, StateValue> = coroutineScope {
        // Parallel lesen, damit die Gesamtdauer nicht mit der Anzahl der Werte wächst.
        uuids.map { uuid -> async { uuid to readState(uuid, auth) } }
            .awaitAll()
            .mapNotNull { (uuid, res) -> res.getOrNull()?.let { uuid to it } }
            .toMap()
    }

    // --- Befehle senden ----------------------------------------------------

    suspend fun sendCommand(
        uuidAction: String,
        command: String,
        auth: AuthParams,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            llValueToString(getLL("/jdev/sps/io/${enc(uuidAction)}/${enc(command)}", auth))
        }
    }

    // --- intern ------------------------------------------------------------

    private fun getLL(path: String, auth: AuthParams? = null): JsonElement {
        val body = execute(url(path, auth))
        val root = json.parseToJsonElement(body).jsonObject
        val ll = root["LL"]?.jsonObject ?: return root
        val code = ll["Code"]?.jsonPrimitive?.content ?: ll["code"]?.jsonPrimitive?.content
        if (code != null && code != "200") {
            throw LoxoneException("Miniserver-Fehlercode $code für $path")
        }
        return ll["value"] ?: JsonObject(emptyMap())
    }

    private fun llValueToString(value: JsonElement): String = when (value) {
        is JsonPrimitive -> value.content
        is JsonObject -> value["value"]?.jsonPrimitive?.contentOrNull ?: value.toString()
        else -> value.toString()
    }

    private fun url(path: String, auth: AuthParams?): HttpUrl {
        val builder = (baseUrl.trimEnd('/') + path).toHttpUrl().newBuilder()
        when (auth) {
            is AuthParams.Token -> builder
                .addQueryParameter("autht", auth.token)
                .addQueryParameter("user", auth.user)
            null -> {}
        }
        return builder.build()
    }

    private fun execute(url: HttpUrl): String {
        val request = Request.Builder().url(url).get().build()
        http.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw LoxoneException("HTTP ${resp.code} für ${url.encodedPath}")
            }
            return body
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    companion object {
        private const val TAG = "LoxClient"

        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }
}

/** Authentifizierungs-Parameter für einen Request. */
sealed interface AuthParams {
    data class Token(val token: String, val user: String) : AuthParams
}

class LoxoneException(message: String) : Exception(message)
