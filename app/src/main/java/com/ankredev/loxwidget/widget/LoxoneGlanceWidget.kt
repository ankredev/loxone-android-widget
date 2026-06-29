package com.ankredev.loxwidget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ankredev.loxwidget.data.config.ConfigStore
import com.ankredev.loxwidget.data.config.WidgetConfig
import com.ankredev.loxwidget.data.config.WidgetItem
import com.ankredev.loxwidget.data.loxone.StructureParser
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Das eigentliche Home-Screen-Widget. Rendert die in der [WidgetConfig] hinterlegten
 * Werte. Die aktuellen Rohwerte werden vom [WidgetUpdateWorker] in den Glance-State
 * geschrieben (Key [VALUES_KEY], JSON: stateUuid -> raw).
 */
class LoxoneGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val config = ConfigStore(context).getWidgetConfig(appWidgetId)

        // Reines Rendern aus dem Glance-State – KEIN Netz-Trigger hier (sonst Update-Kaskade).
        // Werte laden: onUpdate (Receiver), Config-Speichern und der PollBurstService.
        provideContent { WidgetContent(config) }
    }

    @Composable
    private fun WidgetContent(config: WidgetConfig?) {
        val valuesJson = currentState(VALUES_KEY) ?: "{}"
        val values: Map<String, String> = runCatching {
            Json.decodeFromString<Map<String, String>>(valuesJson)
        }.getOrDefault(emptyMap())

        val bgColor = config?.let { Color(it.backgroundColor) } ?: Color.White
        val dark = bgColor.luminance() < 0.5f
        val onBg = if (dark) Color(0xFFECEFF1) else Color(0xFF333333)
        val titleColor = if (dark) Color(0xFF4FC3F7) else Color(0xFF0E639C)

        Column(
            modifier = GlanceModifier.fillMaxSize().background(bgColor).padding(12.dp),
        ) {
            if (config == null || config.items.isEmpty()) {
                Text("Nicht konfiguriert – tippen zum Einrichten",
                    style = TextStyle(color = ColorProvider(onBg)))
                return@Column
            }

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = config.title.ifBlank { "Loxone" },
                    style = TextStyle(
                        color = ColorProvider(titleColor),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = GlanceModifier.defaultWeight(),
                )
                // Live-Schalter: grün = an (läuft durchgehend bei Bildschirm an), grau = aus.
                Text(
                    text = "live",
                    style = TextStyle(
                        color = ColorProvider(
                            if (config.liveMode) Color(0xFF2E7D32) else Color.Gray,
                        ),
                        fontSize = 12.sp,
                        fontWeight = if (config.liveMode) FontWeight.Bold else FontWeight.Normal,
                    ),
                    modifier = GlanceModifier.clickable(
                        actionRunCallback<ToggleLiveAction>(
                            actionParametersOf(AppWidgetIdParam to appWidgetIdOf(config)),
                        ),
                    ),
                )
                Spacer(GlanceModifier.width(10.dp))
                Text(
                    text = "⟳",
                    style = TextStyle(color = ColorProvider(Color.Gray), fontSize = 16.sp),
                    modifier = GlanceModifier.clickable(
                        actionRunCallback<RefreshAction>(
                            actionParametersOf(AppWidgetIdParam to appWidgetIdOf(config)),
                        ),
                    ),
                )
            }
            Spacer(GlanceModifier.size(6.dp))

            // Scrollbare Liste: zeigt alle Werte, auch wenn mehr reinpassen als sichtbar.
            LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(config.items.size) { index ->
                    val item = config.items[index]
                    Column(modifier = GlanceModifier.padding(bottom = 4.dp)) {
                        ValueRow(item, values[item.stateUuid], config.appWidgetId, onBg)
                    }
                }
            }
        }
    }

    @Composable
    private fun ValueRow(item: WidgetItem, raw: String?, appWidgetId: Int, onBg: Color) {
        val isJalousie = item.uuidAction.isNotBlank() &&
            StructureParser.isJalousieType(item.controlType)
        val switchable = item.tapCommand != null && item.uuidAction.isNotBlank()
        if (isJalousie) {
            JalousieRow(item, appWidgetId, onBg)
        } else if (switchable) {
            SwitchButtonRow(item, raw, appWidgetId)
        } else {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.label,
                    style = TextStyle(color = ColorProvider(onBg), fontSize = 13.sp),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    text = ValueFormatter.format(item, raw),
                    style = TextStyle(
                        color = ColorProvider(onBg),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }
    }

    /** Schaltbares Element: wirkt wie ein Button, Hintergrund + Text zeigen den Status. */
    @Composable
    private fun SwitchButtonRow(item: WidgetItem, raw: String?, appWidgetId: Int) {
        val on = (raw?.toDoubleOrNull() ?: 0.0) != 0.0
        val bg = if (on) Color(0xFF2E7D32) else Color(0xFFB0BEC5)
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .cornerRadius(8.dp)
                .background(bg)
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .clickable(
                    actionRunCallback<SendCommandAction>(
                        actionParametersOf(
                            UuidActionParam to item.uuidAction,
                            CommandParam to (item.tapCommand ?: ""),
                            StateUuidParam to item.stateUuid,
                            AppWidgetIdParam to appWidgetId,
                        ),
                    ),
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.label,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = if (on) "AN" else "AUS",
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }

    /**
     * Jalousie (`UpDownDigital` / EIB-Beschattung): Label + drei Tasten (Auf / Stop / Ab).
     *  - ▲/▼ senden `up`/`down` → Dauerfahrt ganz auf/ab.
     *  - ■ sendet [StructureParser.JAL_STOP] = kurzer `up`→`UpOff`-Impuls (~50 ms), den der KNX-Aktor
     *    als Stopp einer laufenden Fahrt wertet.
     * Ein echtes Long-Press ist im Home-Screen-Widget nicht abfangbar (Launcher fängt es zum Bearbeiten ab).
     */
    @Composable
    private fun JalousieRow(item: WidgetItem, appWidgetId: Int, onBg: Color) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.label,
                style = TextStyle(color = ColorProvider(onBg), fontSize = 13.sp),
                modifier = GlanceModifier.defaultWeight(),
            )
            JalButton("▲", StructureParser.JAL_UP, item, appWidgetId)
            Spacer(GlanceModifier.width(6.dp))
            JalButton("■", StructureParser.JAL_STOP, item, appWidgetId)
            Spacer(GlanceModifier.width(6.dp))
            JalButton("▼", StructureParser.JAL_DOWN, item, appWidgetId)
        }
    }

    @Composable
    private fun JalButton(label: String, command: String, item: WidgetItem, appWidgetId: Int) {
        Text(
            text = label,
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
            modifier = GlanceModifier
                .cornerRadius(6.dp)
                .background(Color(0xFF546E7A))
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clickable(
                    actionRunCallback<SendCommandAction>(
                        actionParametersOf(
                            UuidActionParam to item.uuidAction,
                            CommandParam to command,
                            StateUuidParam to item.stateUuid,
                            AppWidgetIdParam to appWidgetId,
                        ),
                    ),
                ),
        )
    }

    private fun appWidgetIdOf(config: WidgetConfig): Int = config.appWidgetId

    companion object {
        val VALUES_KEY = stringPreferencesKey("values_json")
        val AppWidgetIdParam = ActionParameters.Key<Int>("appWidgetId")
        val UuidActionParam = ActionParameters.Key<String>("uuidAction")
        val CommandParam = ActionParameters.Key<String>("command")
        val StateUuidParam = ActionParameters.Key<String>("stateUuid")
    }
}
