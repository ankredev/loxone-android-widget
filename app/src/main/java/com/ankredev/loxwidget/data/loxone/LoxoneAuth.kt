package com.ankredev.loxwidget.data.loxone

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Token-basierte Loxone-Authentifizierung (lokal, aktuelle Firmware).
 *
 * Ablauf (vom [LoxoneClient] orchestriert):
 *  1. GET /jdev/sys/getkey2/{user}  -> key (hex), salt, hashAlg (SHA1/SHA256)
 *  2. pwHash = UPPER(HEX(hash(password + ":" + salt)))
 *  3. authHash = HEX(HMAC(key = hexDecode(key), msg = user + ":" + pwHash))
 *  4. GET /jdev/sys/getjwt/{authHash}/{user}/{permission}/{clientUuid}/{clientInfo}
 *
 * Hinweis: Über reines HTTP im LAN ist der Token-Hash zwar geschützt, der Traffic
 * selbst aber unverschlüsselt. Für v1 (nur Heimnetz) akzeptiert. Für Remote später
 * AES-Session-Verschlüsselung (keyexchange) bzw. HTTPS ergänzen.
 */
object LoxoneAuth {

    /** Berechtigung: 2 = kurzlebig (Web), 4 = langlebig (App). */
    const val PERMISSION_APP = 4

    fun computeAuthHash(
        user: String,
        password: String,
        keyHex: String,
        salt: String,
        hashAlg: String,
    ): String {
        val alg = normalizeAlg(hashAlg)
        // 2. pwHash
        val pwHash = sha(alg, "$password:$salt").toHex().uppercase()
        // 3. HMAC über user:pwHash mit hex-dekodiertem key
        return hmac(alg, hexToBytes(keyHex), "$user:$pwHash").toHex()
    }

    private fun normalizeAlg(hashAlg: String): String =
        if (hashAlg.equals("SHA256", ignoreCase = true)) "SHA-256" else "SHA-1"

    private fun sha(alg: String, msg: String): ByteArray =
        MessageDigest.getInstance(alg).digest(msg.toByteArray(Charsets.UTF_8))

    private fun hmac(shaAlg: String, key: ByteArray, msg: String): ByteArray {
        val macAlg = if (shaAlg == "SHA-256") "HmacSHA256" else "HmacSHA1"
        val mac = Mac.getInstance(macAlg)
        mac.init(SecretKeySpec(key, macAlg))
        return mac.doFinal(msg.toByteArray(Charsets.UTF_8))
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim()
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) {
            out[i] = ((hexDigit(clean[i * 2]) shl 4) or hexDigit(clean[i * 2 + 1])).toByte()
        }
        return out
    }

    private fun hexDigit(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> throw IllegalArgumentException("Ungültiges Hex-Zeichen: $c")
    }

    private fun ByteArray.toHex(): String {
        val sb = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xFF
            sb.append("0123456789abcdef"[v ushr 4])
            sb.append("0123456789abcdef"[v and 0x0F])
        }
        return sb.toString()
    }
}

/** Vom Miniserver ausgestellter Token samt Gültigkeit. */
data class LoxoneToken(
    val token: String,
    val validUntil: Long,      // Loxone-Zeit (Sekunden seit 2009-01-01)
    val tokenRights: Int,
    val key: String? = null,   // für Token-Refresh
)
