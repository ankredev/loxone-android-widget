package com.ankredev.loxwidget.data.config

import kotlinx.serialization.Serializable

/** Globale Verbindungsdaten zum Miniserver (Passwort separat im SecretStore). */
@Serializable
data class ConnectionConfig(
    val host: String = "",          // z. B. "http://192.168.1.50"
    val user: String = "",
    val clientUuid: String = "",    // persistente Client-Kennung für Token
    /** Status-Polling-Intervall in Sekunden (global für alle Widgets). */
    val pollSeconds: Int = 30,
)

enum class ValueFormat { AUTO, INTEGER, ONE_DECIMAL, ON_OFF, RAW }

/** Ein im Widget angezeigter Wert. */
@Serializable
data class WidgetItem(
    val stateUuid: String,
    val uuidAction: String = "",
    val label: String,
    val unit: String = "",
    val controlType: String = "",
    val format: ValueFormat = ValueFormat.AUTO,
    /** Optionaler Befehl bei Tap (z. B. "On"/"Off"/"pulse"); null = nicht steuerbar. */
    val tapCommand: String? = null,
    /** Abfrage-Intervall dieses Werts in Sekunden (im Live-/Burst-Polling). */
    val pollSeconds: Int = 5,
)

/** Konfiguration eines konkreten Home-Screen-Widgets (pro appWidgetId). */
@Serializable
data class WidgetConfig(
    val appWidgetId: Int,
    val title: String = "",
    val columns: Int = 1,
    val items: List<WidgetItem> = emptyList(),
    /** Hintergrundfarbe des Widgets als ARGB-Long (Default Weiß). */
    val backgroundColor: Long = 0xFFFFFFFF,
    /**
     * Dauerhafte Live-Aktualisierung: solange der Bildschirm an ist, wird durchgehend
     * (im 5s-Takt) aktualisiert – ohne Antippen. Dafür läuft der Vordergrund-Dienst
     * dauerhaft (sichtbare Benachrichtigung). Aus = Burst nur nach Widget-Interaktion.
     */
    val liveMode: Boolean = false,
)
