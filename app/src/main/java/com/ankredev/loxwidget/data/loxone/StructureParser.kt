package com.ankredev.loxwidget.data.loxone

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Wandelt die hierarchische [LoxoneStructure] in eine flache, in der Admin-Oberfläche
 * durchsuch- und auswählbare Liste von [SelectableValue] um.
 */
object StructureParser {

    /** Control-Typen, die typischerweise rein informativ sind (kein Befehl sinnvoll). */
    private val readOnlyTypes = setOf("InfoOnlyAnalog", "InfoOnlyDigital", "TextState")

    fun toSelectableValues(structure: LoxoneStructure): List<SelectableValue> {
        val roomNames = structure.rooms.mapValues { it.value.name }
        val catNames = structure.cats.mapValues { it.value.name }

        val result = mutableListOf<SelectableValue>()
        for ((uuid, control) in structure.controls) {
            collectControl(uuid, control, roomNames, catNames, result)
        }
        return result.sortedWith(
            compareBy({ it.roomName ?: "" }, { it.controlName }, { it.stateName }),
        )
    }

    private fun collectControl(
        uuid: String,
        control: LoxControl,
        roomNames: Map<String, String>,
        catNames: Map<String, String>,
        out: MutableList<SelectableValue>,
    ) {
        val room = control.room?.let { roomNames[it] }
        val cat = control.cat?.let { catNames[it] }

        for ((stateName, stateRef) in control.states) {
            val stateUuid = (stateRef as? JsonPrimitive)?.contentOrNull ?: continue
            if (stateUuid.isBlank()) continue
            out += SelectableValue(
                controlUuid = uuid,
                controlName = control.name,
                controlType = control.type,
                stateName = stateName,
                stateUuid = stateUuid,
                roomName = room,
                catName = cat,
                uuidAction = control.uuidAction,
            )
        }

        // SubControls (z. B. Räume, Zentralfunktionen) ebenfalls flach aufnehmen
        for ((subUuid, sub) in control.subControls) {
            collectControl(subUuid, sub, roomNames, catNames, out)
        }
    }

    fun isControllable(value: SelectableValue): Boolean =
        value.controlType !in readOnlyTypes && value.uuidAction.isNotBlank()

    /** Sentinel-Befehl: im Widget zur Laufzeit abhängig vom aktuellen Zustand An/Aus. */
    const val TOGGLE = "TOGGLE"

    /**
     * Jalousie-Steuerbefehle (UpDownDigital / EIB-Beschattung) – an der realen KNX-Anlage verifiziert:
     *  - `up`/`down` = Dauerfahrt ganz auf/ab (Langzeit-Klick).
     *  - **Stopp** geht NICHT über `stop`/`stepup`/`FullUp` (werden mit Code 200 quittiert, tun aber
     *    nichts bzw. liefern value=0). Der KNX-Aktor stoppt nur auf einen **kurzen** Auf-Impuls:
     *    `up` direkt gefolgt von `UpOff` (~50 ms). Längere Impulse (≥150 ms) wertet er als „weiterfahren".
     *    Dieser Impuls wird vom [SendCommandAction] über das Synthetik-Kommando [JAL_STOP] ausgeführt.
     */
    const val JAL_UP = "up"
    const val JAL_DOWN = "down"

    /** Synthetik-Kommando: kurzer up->UpOff-Impuls (50 ms) = Stopp. Von [SendCommandAction] interpretiert. */
    const val JAL_STOP = "PULSEUP:50"

    /** Ob dieser Control-Typ als Jalousie (Auf/Stop/Ab) gesteuert werden soll. */
    fun isJalousieType(controlType: String): Boolean = typeGroup(controlType) == TypeGroup.JALOUSIE

    /**
     * Liefert den passenden Tap-Befehl für einen Control-Typ, falls schaltbar:
     *  - Switch/TimedSwitch -> [TOGGLE] (An<->Aus)
     *  - Pushbutton         -> "Pulse"
     *  - sonst null (nicht schaltbar).
     */
    fun defaultTapCommand(controlType: String): String? = when (controlType) {
        "Switch", "TimedSwitch", "LightController" -> TOGGLE
        "Pushbutton" -> "Pulse"
        else -> null
    }

    /** Ob dieser Wert sinnvoll per Tipp geschaltet werden kann. */
    fun isSwitchable(value: SelectableValue): Boolean =
        value.uuidAction.isNotBlank() && defaultTapCommand(value.controlType) != null

    /** Grobe Typ-Gruppe für die Filter im Builder. */
    enum class TypeGroup(val label: String) {
        VALUE("Werte"), SWITCH("Schalter"), JALOUSIE("Jalousie");

        companion object {
            fun fromLabel(label: String?): TypeGroup? = entries.firstOrNull { it.label == label }
        }
    }

    private val jalousieTypes = setOf(
        "Jalousie", "Gate", "CentralJalousie", "CentralGate",
        "UpDownDigital", // KNX Auf/Ab (Jalousie) – so heißen die Jalousien in dieser Installation
    )
    private val extraSwitchTypes = setOf(
        "Dimmer", "EIBDimmer", "LightControllerV2", "ColorPicker", "ColorPickerV2",
    )

    fun typeGroup(controlType: String): TypeGroup = when {
        controlType in jalousieTypes -> TypeGroup.JALOUSIE
        defaultTapCommand(controlType) != null || controlType in extraSwitchTypes -> TypeGroup.SWITCH
        else -> TypeGroup.VALUE
    }
}
