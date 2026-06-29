package com.ankredev.loxwidget.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ankredev.loxwidget.data.config.WidgetItem
import com.ankredev.loxwidget.data.loxone.SelectableValue
import com.ankredev.loxwidget.data.loxone.StructureParser
import com.ankredev.loxwidget.ui.config.WidgetBuilderViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Konfigurations-Activity, die beim Hinzufügen eines Widgets (oder zum Bearbeiten)
 * geöffnet wird. Lässt alle im Miniserver konfigurierten Werte auswählen.
 */
class WidgetConfigActivity : ComponentActivity() {

    private val viewModel: WidgetBuilderViewModel by viewModels()
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Bis zum Speichern: Abbruch-Ergebnis vormerken.
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        viewModel.init(appWidgetId)

        setContent {
            MaterialTheme {
                BuilderScreen(
                    viewModel = viewModel,
                    onSave = { viewModel.save(::onSaved) },
                )
            }
        }
    }

    private fun onSaved(id: Int) {
        WidgetUpdateWorker.enqueueOnce(this, id)
        // Dienst starten: greift Live-Modus sofort ab bzw. zeigt nach dem Einrichten kurz Live-Werte.
        PollBurstService.startOrExtend(this)
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        setResult(Activity.RESULT_OK, result)
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BuilderScreen(viewModel: WidgetBuilderViewModel, onSave: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sortMode by remember { mutableStateOf(false) }
    // Reihenfolge beim Betreten des Sortiermodus – für "Verwerfen" zum Zurücksetzen.
    var orderBeforeSort by remember { mutableStateOf<List<String>>(emptyList()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (sortMode) "Sortieren" else "Werte auswählen") },
                actions = {
                    TextButton(
                        onClick = {
                            if (sortMode) {
                                // Verwerfen: live umsortierte Reihenfolge zurücksetzen.
                                viewModel.reorderSelected(orderBeforeSort)
                                sortMode = false
                            } else {
                                orderBeforeSort = state.selected.map { it.stateUuid }
                                sortMode = true
                            }
                        },
                        enabled = state.selected.isNotEmpty(),
                    ) { Text(if (sortMode) "Verwerfen" else "Sortieren") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            when {
                state.loading -> {
                    Row(modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.noConnection -> Text(
                    "Keine Verbindung konfiguriert. Bitte zuerst die App öffnen und " +
                        "die Loxone-Verbindung einrichten.",
                )
                state.error != null -> Text("Fehler beim Laden: ${state.error}")
                else -> {
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = viewModel::onTitle,
                        label = { Text("Widget-Titel") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Hintergrundfarbe",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    ColorPaletteRow(
                        selected = state.backgroundColor,
                        onSelect = viewModel::onBackgroundColor,
                    )
                    if (!sortMode) {
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = viewModel::onQuery,
                            label = { Text("Suche (Name / Raum / Kategorie)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            FilterDropdown(
                                placeholder = "Raum",
                                selected = state.roomFilter,
                                options = state.rooms,
                                onSelect = viewModel::onRoomFilter,
                                modifier = Modifier.weight(1f),
                            )
                            FilterDropdown(
                                placeholder = "Kategorie",
                                selected = state.catFilter,
                                options = state.cats,
                                onSelect = viewModel::onCatFilter,
                                modifier = Modifier.weight(1f),
                            )
                            FilterDropdown(
                                placeholder = "Typ",
                                selected = state.typeFilter?.label,
                                options = StructureParser.TypeGroup.entries.map { it.label },
                                onSelect = { viewModel.onTypeFilter(StructureParser.TypeGroup.fromLabel(it)) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Text(
                        if (sortMode) {
                            "Eintrag lang drücken und ziehen zum Sortieren"
                        } else {
                            "${state.selected.size} ausgewählt"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    if (sortMode) {
                        SortableSelectedList(
                            items = state.selected,
                            onReorder = viewModel::reorderSelected,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(state.filtered, key = { it.stateUuid }) { value ->
                                val item = state.selectedByUuid[value.stateUuid]
                                ValueListItem(
                                    value = value,
                                    checked = item != null,
                                    switchable = StructureParser.isSwitchable(value),
                                    tapEnabled = item?.tapCommand != null,
                                    label = item?.label ?: value.displayName,
                                    pollSeconds = item?.pollSeconds ?: 5,
                                    onToggle = { viewModel.toggle(value) },
                                    onTapEnabledChange = { viewModel.setSwitchable(value, it) },
                                    onLabelChange = { viewModel.setLabel(value, it) },
                                    onPollSecondsChange = { viewModel.setPollSeconds(value, it) },
                                )
                            }
                        }
                    }
                    Button(
                        onClick = onSave,
                        enabled = state.selected.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) { Text("Speichern") }
                }
            }
        }
    }
}

@Composable
private fun ValueListItem(
    value: SelectableValue,
    checked: Boolean,
    switchable: Boolean,
    tapEnabled: Boolean,
    label: String,
    pollSeconds: Int,
    onToggle: () -> Unit,
    onTapEnabledChange: (Boolean) -> Unit,
    onLabelChange: (String) -> Unit,
    onPollSecondsChange: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onToggle)
                    .padding(start = 4.dp),
            ) {
                Text(value.displayName, style = MaterialTheme.typography.bodyLarge)
                val sub = listOfNotNull(value.roomName, value.catName, value.controlType)
                    .joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(sub, style = MaterialTheme.typography.bodySmall)
                }
            }
            // Nur bei schaltbaren & ausgewählten Controls: "Tippen schaltet" anbieten.
            if (switchable && checked) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Schalten", style = MaterialTheme.typography.labelSmall)
                    Switch(checked = tapEnabled, onCheckedChange = onTapEnabledChange)
                }
            }
        }
        // Pro ausgewähltem Wert: editierbarer Anzeigename + eigenes Abfrage-Intervall.
        if (checked) {
            var labelText by remember(value.stateUuid) { mutableStateOf(label) }
            OutlinedTextField(
                value = labelText,
                onValueChange = { input ->
                    labelText = input
                    onLabelChange(input)
                },
                label = { Text("Anzeigename") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 48.dp, top = 2.dp),
            )
            Row(
                modifier = Modifier.padding(start = 48.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Intervall (s):", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(8.dp))
                var text by remember(value.stateUuid) { mutableStateOf(pollSeconds.toString()) }
                OutlinedTextField(
                    value = text,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }.take(4)
                        text = digits
                        digits.toIntOrNull()?.let(onPollSecondsChange)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(96.dp),
                )
            }
        }
    }
}

/**
 * Sortier-Modus: die ausgewählten Einträge selbst. Ein Eintrag wird per Langdruck
 * „gegriffen" (Hervorhebung) und kann direkt verschoben werden.
 */
@Composable
private fun SortableSelectedList(
    items: List<WidgetItem>,
    onReorder: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // Lokale Snapshot-Liste: wird im onMove SOFORT umsortiert (sonst snappt der Eintrag
    // beim Loslassen zurück, weil der StateFlow-Roundtrip zu spät kommt). Nach jedem Zug
    // wird die KOMPLETTE sichtbare Reihenfolge ins ViewModel gespiegelt -> exakt das, was
    // gespeichert wird.
    val localItems = remember(items.map { it.stateUuid }.toSet()) {
        mutableStateListOf<WidgetItem>().apply { addAll(items) }
    }
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        localItems.add(to.index, localItems.removeAt(from.index))
        onReorder(localItems.map { it.stateUuid })
    }
    LazyColumn(state = listState, modifier = modifier.fillMaxWidth()) {
        items(localItems, key = { it.stateUuid }) { item ->
            ReorderableItem(reorderState, key = item.stateUuid) { isDragging ->
                val elevation = if (isDragging) 8.dp else 1.dp
                Surface(
                    tonalElevation = elevation,
                    shadowElevation = elevation,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .longPressDraggableHandle()
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.DragHandle, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text(item.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

/** Auswahl der Widget-Hintergrundfarbe aus einer festen Palette. */
@Composable
private fun ColorPaletteRow(selected: Long, onSelect: (Long) -> Unit) {
    val palette = listOf(
        0xFFFFFFFFL, 0xFFECEFF1L, 0xFF90A4AEL, 0xFF263238L, 0xFF000000L,
        0xFF0E639CL, 0xFF2E7D32L, 0xFFC62828L, 0xFFF9A825L,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        palette.forEach { c ->
            val sel = c == selected
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(c))
                    .border(
                        BorderStroke(
                            if (sel) 3.dp else 1.dp,
                            if (sel) Color(0xFF1565C0) else Color.LightGray,
                        ),
                        RoundedCornerShape(6.dp),
                    )
                    .clickable { onSelect(c) },
            )
        }
    }
}

/** Kompakter Filter-Button mit Dropdown; [selected] = null bedeutet „Alle". */
@Composable
private fun FilterDropdown(
    placeholder: String,
    selected: String?,
    options: List<String>,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = selected ?: placeholder,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Alle ($placeholder)") },
                onClick = { onSelect(null); expanded = false },
            )
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = { onSelect(opt); expanded = false },
                )
            }
        }
    }
}
