package com.ankredev.loxwidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.ankredev.loxwidget.data.config.ConfigStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoxoneWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = LoxoneGlanceWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // Einmaliges Update (z. B. nach Neustart/Hinzufügen). Live-Takt läuft erst
        // nach einer Widget-Interaktion über den PollBurstService.
        WidgetUpdateWorker.enqueueOnce(context, -1)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val store = ConfigStore(context.applicationContext)
        CoroutineScope(Dispatchers.IO).launch {
            appWidgetIds.forEach { store.deleteWidgetConfig(it) }
        }
    }
}
