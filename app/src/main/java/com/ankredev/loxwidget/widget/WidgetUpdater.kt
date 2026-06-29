package com.ankredev.loxwidget.widget

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.ankredev.loxwidget.data.LoxoneRepository
import com.ankredev.loxwidget.data.config.ConfigStore
import com.ankredev.loxwidget.data.config.WidgetItem
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Gemeinsame Update-Logik: liest Werte (über die uuidAction) vom Miniserver und schreibt
 * sie in den Glance-State. Genutzt vom [WidgetUpdateWorker] (einmalig) und vom
 * [PollBurstService] (per-Item-Takt). Teil-Updates werden in den bestehenden Snapshot
 * gemergt, damit Items mit unterschiedlichem Intervall nebeneinander bestehen.
 */
object WidgetUpdater {

    private val json = Json

    /** Aktualisiert ein einzelnes Widget ([targetId]) oder – bei null – alle Widgets, alle Items. */
    suspend fun update(context: Context, targetId: Int? = null) {
        val manager = GlanceAppWidgetManager(context)
        val configStore = ConfigStore(context)
        for (glanceId in manager.getGlanceIds(LoxoneGlanceWidget::class.java)) {
            val appWidgetId = manager.getAppWidgetId(glanceId)
            if (targetId != null && appWidgetId != targetId) continue
            val items = configStore.getWidgetConfig(appWidgetId)?.items ?: continue
            readMergeAndRender(context, manager, appWidgetId, items)
        }
    }

    /**
     * Rendert ein Widget sofort neu – ohne Netz-Abfrage, nur aus dem aktuellen Glance-State
     * und der frisch gespeicherten Config. Damit erscheint z. B. eine neue Reihenfolge sofort,
     * während die eigentlichen Werte parallel über Worker/Service nachgeladen werden.
     */
    suspend fun rerender(context: Context, appWidgetId: Int) {
        val manager = GlanceAppWidgetManager(context)
        val glanceId = manager.getGlanceIds(LoxoneGlanceWidget::class.java)
            .firstOrNull { manager.getAppWidgetId(it) == appWidgetId } ?: return
        LoxoneGlanceWidget().update(context, glanceId)
    }

    /** Liest nur die übergebenen [items] eines Widgets und merged sie in den Snapshot. */
    suspend fun updateItems(context: Context, appWidgetId: Int, items: List<WidgetItem>) {
        if (items.isEmpty()) return
        val manager = GlanceAppWidgetManager(context)
        readMergeAndRender(context, manager, appWidgetId, items)
    }

    private suspend fun readMergeAndRender(
        context: Context,
        manager: GlanceAppWidgetManager,
        appWidgetId: Int,
        items: List<WidgetItem>,
    ) {
        val readUuids = items.map { it.uuidAction.ifBlank { it.stateUuid } }.distinct()
        if (readUuids.isEmpty()) return

        val byRead = LoxoneRepository.get(context).readValues(readUuids)
        val newValues: Map<String, String> = items.mapNotNull { item ->
            byRead[item.uuidAction.ifBlank { item.stateUuid }]?.raw?.let { item.stateUuid to it }
        }.toMap()
        if (newValues.isEmpty()) return

        val glanceId = manager.getGlanceIds(LoxoneGlanceWidget::class.java)
            .firstOrNull { manager.getAppWidgetId(it) == appWidgetId } ?: return

        updateAppWidgetState(context, glanceId) { prefs: MutablePreferences ->
            val existing: Map<String, String> = prefs[LoxoneGlanceWidget.VALUES_KEY]
                ?.let { runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull() }
                ?: emptyMap()
            prefs[LoxoneGlanceWidget.VALUES_KEY] = json.encodeToString(existing + newValues)
        }
        LoxoneGlanceWidget().update(context, glanceId)
    }
}
