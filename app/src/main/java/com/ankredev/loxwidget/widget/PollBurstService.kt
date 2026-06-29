package com.ankredev.loxwidget.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.ankredev.loxwidget.data.config.ConfigStore
import com.ankredev.loxwidget.ui.config.ConfigActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Kurzlebiger Vordergrund-Dienst, der nach einer Widget-Interaktion (Refresh oder
 * Tippen auf einen Wert) für [BURST_MILLIS] (5 Min) alle paar Sekunden die Werte
 * aktualisiert. Jede neue Interaktion verlängert das Fenster. Schläft sofort bei
 * Bildschirm-aus. So gibt es Live-Updates nur dann, wenn man wirklich hinschaut –
 * ohne Dauerbenachrichtigung und ohne Akkuverbrauch im Leerlauf.
 *
 * Start aus dem Hintergrund ist erlaubt, weil er durch eine Widget-Interaktion
 * ausgelöst wird (Android-Ausnahme für FGS-Start bei Widget-Bedienung).
 */
class PollBurstService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null

    @Volatile private var endElapsed: Long = 0L
    @Volatile private var screenOn: Boolean = true

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> screenOn = true
                Intent.ACTION_SCREEN_OFF -> screenOn = false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        screenOn = (getSystemService(POWER_SERVICE) as PowerManager).isInteractive
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        // Burst-Fenster (neu) auf 5 Min setzen – jede Interaktion verlängert.
        endElapsed = SystemClock.elapsedRealtime() + BURST_MILLIS

        if (loop?.isActive != true) {
            loop = scope.launch {
                val lastRead = HashMap<String, Long>() // "appWidgetId:stateUuid" -> elapsedRealtime
                val tickMs = TICK_SECONDS * 1000L
                while (isActive) {
                    // Ein Durchgang: fällige Items lesen (bei Bildschirm an) und prüfen, ob Live aktiv.
                    val live = tick(lastRead, poll = screenOn)
                    val burstActive = SystemClock.elapsedRealtime() < endElapsed
                    // Stop, wenn weder Live-Modus noch ein laufendes Burst-Fenster aktiv ist.
                    if (!live && !burstActive) break
                    // Burst (ohne Live) stoppt bei Bildschirm-aus; Live bleibt aktiv, pausiert nur.
                    if (!screenOn && !live) break
                    delay(tickMs)
                }
                stopBurst()
            }
        }
        return START_NOT_STICKY
    }

    /**
     * Liest pro Widget die jetzt fälligen Items (Intervall pro Item) und merged sie.
     * @return true, wenn irgendein Widget den dauerhaften Live-Modus aktiviert hat.
     */
    private suspend fun tick(lastRead: MutableMap<String, Long>, poll: Boolean): Boolean {
        val mgr = GlanceAppWidgetManager(applicationContext)
        val store = ConfigStore(applicationContext)
        val now = SystemClock.elapsedRealtime()
        var anyLive = false
        for (gid in mgr.getGlanceIds(LoxoneGlanceWidget::class.java)) {
            val appWidgetId = mgr.getAppWidgetId(gid)
            val config = store.getWidgetConfig(appWidgetId) ?: continue
            if (config.liveMode) anyLive = true
            if (!poll) continue

            val due = config.items.filter { item ->
                val key = "$appWidgetId:${item.stateUuid}"
                val last = lastRead[key] ?: 0L
                now - last >= item.pollSeconds.coerceAtLeast(MIN_POLL_SECONDS) * 1000L
            }
            if (due.isNotEmpty()) {
                runCatching { WidgetUpdater.updateItems(applicationContext, appWidgetId, due) }
                due.forEach { lastRead["$appWidgetId:${it.stateUuid}"] = now }
            }
        }
        return anyLive
    }

    private fun stopBurst() {
        loop?.cancel()
        loop = null
        stopForegroundRemove()
        stopSelf()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- Notification / Foreground ----------------------------------------

    private fun startForegroundCompat() {
        ensureChannel()
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, ConfigActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Loxone Widget")
            .setContentText("Live-Aktualisierung läuft…")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(tapIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun stopForegroundRemove() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Live-Aktualisierung",
                        NotificationManager.IMPORTANCE_MIN,
                    ).apply { description = "Kurzzeitige Live-Updates des Loxone-Widgets" },
                )
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "loxone_live"
        private const val NOTIF_ID = 1001
        private const val BURST_MILLIS = 5 * 60 * 1000L // 5 Minuten
        private const val TICK_SECONDS = 1L             // Basistakt; Fälligkeit pro Item
        private const val MIN_POLL_SECONDS = 2          // Untergrenze pro Item

        /** Startet bzw. verlängert das Live-Fenster. Sicher aus Action-Callbacks aufrufbar. */
        fun startOrExtend(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context, Intent(context, PollBurstService::class.java),
                )
            }.onFailure {
                // Falls der FGS-Start (z. B. OS-Restriktion) scheitert: wenigstens einmal aktualisieren.
                WidgetUpdateWorker.enqueueOnce(context, -1)
            }
        }
    }
}
