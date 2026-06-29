package com.ankredev.loxwidget.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf

/**
 * Einmaliger, on-demand Update-Lauf (z. B. nach Konfiguration oder Befehl). Läuft auch
 * im Hintergrund ohne Benachrichtigung. Das fortlaufende 5s-Polling übernimmt dagegen
 * der [PollBurstService] (nur in 5-Minuten-Fenstern nach Widget-Interaktion).
 */
class WidgetUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val targetId = inputData.getInt(KEY_WIDGET_ID, -1).takeIf { it != -1 }
        WidgetUpdater.update(applicationContext, targetId)
        return Result.success()
    }

    companion object {
        private const val KEY_WIDGET_ID = "appWidgetId"

        private val networkConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun enqueueOnce(context: Context, appWidgetId: Int) {
            val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .setConstraints(networkConstraint)
                .setInputData(workDataOf(KEY_WIDGET_ID to appWidgetId))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("loxone_once_$appWidgetId", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
