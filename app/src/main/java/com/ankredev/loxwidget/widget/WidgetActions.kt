package com.ankredev.loxwidget.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.ankredev.loxwidget.data.LoxoneRepository
import com.ankredev.loxwidget.data.config.ConfigStore
import com.ankredev.loxwidget.data.loxone.StructureParser

/** Refresh-Button im Widget: startet/verlängert das 5-Minuten-Live-Fenster (5s-Takt). */
class RefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        // Der Burst-Dienst aktualisiert sofort beim Start und dann im Takt.
        PollBurstService.startOrExtend(context)
    }
}

/** „live"-Schalter im Widget: dauerhaften Live-Modus dieses Widgets an/aus schalten. */
class ToggleLiveAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val appWidgetId = parameters[LoxoneGlanceWidget.AppWidgetIdParam] ?: return
        val store = ConfigStore(context)
        val config = store.getWidgetConfig(appWidgetId) ?: return
        store.saveWidgetConfig(config.copy(liveMode = !config.liveMode))

        // Dienst starten (greift Live sofort ab); neu rendern, damit die Farbe umschaltet.
        PollBurstService.startOrExtend(context)
        WidgetUpdateWorker.enqueueOnce(context, appWidgetId)
    }
}

/** Tap auf einen steuerbaren Wert: sendet den Befehl und aktualisiert anschließend. */
class SendCommandAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val uuidAction = parameters[LoxoneGlanceWidget.UuidActionParam] ?: return
        val command = parameters[LoxoneGlanceWidget.CommandParam] ?: return
        val appWidgetId = parameters[LoxoneGlanceWidget.AppWidgetIdParam] ?: return
        val repo = LoxoneRepository.get(context)

        // Jalousie-Stopp via kurzem Impuls: "PULSEUP:<ms>" / "PULSEDOWN:<ms>" sendet die Bewegung
        // und nach <ms> das Loslassen (UpOff/DownOff). Der KNX/EIB-Beschattungs-Aktor wertet einen
        // KURZEN Auf-/Ab-Impuls (~50 ms) als Stopp einer laufenden Fahrt; längere Impulse als Weiterfahrt.
        if (command.startsWith("PULSEUP:") || command.startsWith("PULSEDOWN:")) {
            val up = command.startsWith("PULSEUP:")
            val delayMs = command.substringAfter(':').toLongOrNull() ?: 50L
            repo.sendCommand(uuidAction, if (up) "up" else "down")
            kotlinx.coroutines.delay(delayMs)
            repo.sendCommand(uuidAction, if (up) "UpOff" else "DownOff")
            WidgetUpdateWorker.enqueueOnce(context, appWidgetId)
            PollBurstService.startOrExtend(context)
            return
        }

        // Für Lichter/Schalter: aktuellen Status (über die uuidAction) lesen und
        // gezielt On/Off senden – kein blindes Schalten.
        val actualCommand = if (command == StructureParser.TOGGLE) {
            val raw = repo.readValues(listOf(uuidAction))[uuidAction]?.raw
            if (isOn(raw)) "Off" else "On"
        } else {
            command
        }

        repo.sendCommand(uuidAction, actualCommand)
        // Sofortiges Feedback für dieses Widget + Live-Fenster (neu) starten/verlängern.
        WidgetUpdateWorker.enqueueOnce(context, appWidgetId)
        PollBurstService.startOrExtend(context)
    }

    private fun isOn(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        raw.toDoubleOrNull()?.let { return it != 0.0 }
        return raw.equals("on", true) || raw.equals("true", true)
    }
}
