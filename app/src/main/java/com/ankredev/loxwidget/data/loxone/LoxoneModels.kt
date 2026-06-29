package com.ankredev.loxwidget.data.loxone

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Teil-Modell der vom Miniserver gelieferten LoxAPP3.json.
 * Es werden nur die für die Widget-Konfiguration relevanten Felder abgebildet;
 * unbekannte Felder werden ignoriert (siehe Json { ignoreUnknownKeys = true }).
 */
@Serializable
data class LoxoneStructure(
    val lastModified: String? = null,
    val msInfo: MsInfo? = null,
    val rooms: Map<String, LoxRoom> = emptyMap(),
    val cats: Map<String, LoxCat> = emptyMap(),
    val controls: Map<String, LoxControl> = emptyMap(),
)

@Serializable
data class MsInfo(
    val serialNr: String? = null,
    val msName: String? = null,
    val projectName: String? = null,
    val location: String? = null,
)

@Serializable
data class LoxRoom(
    val uuid: String? = null,
    val name: String = "",
    val image: String? = null,
)

@Serializable
data class LoxCat(
    val uuid: String? = null,
    val name: String = "",
    val image: String? = null,
    val type: String? = null,
)

@Serializable
data class LoxControl(
    val name: String = "",
    val type: String = "",
    val uuidAction: String = "",
    val room: String? = null,
    val cat: String? = null,
    @SerialName("isFavorite") val isFavorite: Boolean = false,
    val defaultIcon: String? = null,
    /** State-Name -> State-UUID (Wert wird über die UUID gelesen). */
    val states: Map<String, JsonElement> = emptyMap(),
    val details: JsonElement? = null,
    val subControls: Map<String, LoxControl> = emptyMap(),
)

/**
 * Flach aufbereitete, in der Admin-Oberfläche auswählbare "Werte".
 * Ein Control kann mehrere States haben (z. B. Switch -> "active"),
 * jeder relevante State ergibt einen [SelectableValue].
 */
data class SelectableValue(
    val controlUuid: String,
    val controlName: String,
    val controlType: String,
    val stateName: String,
    val stateUuid: String,
    val roomName: String?,
    val catName: String?,
    /** UUID, an die Steuerbefehle gesendet werden. */
    val uuidAction: String,
) {
    val displayName: String
        get() = if (stateName.isBlank() || stateName == "value" || stateName == "active") {
            controlName
        } else {
            "$controlName · $stateName"
        }
}

/** Aktueller Wert eines States, vom Miniserver gelesen. */
data class StateValue(
    val stateUuid: String,
    val raw: String,
    val asDouble: Double? = raw.toDoubleOrNull(),
)
