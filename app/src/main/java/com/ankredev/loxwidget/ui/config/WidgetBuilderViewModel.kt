package com.ankredev.loxwidget.ui.config

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ankredev.loxwidget.data.LoxoneRepository
import com.ankredev.loxwidget.data.config.ConfigStore
import com.ankredev.loxwidget.data.config.ValueFormat
import com.ankredev.loxwidget.data.config.WidgetConfig
import com.ankredev.loxwidget.data.config.WidgetItem
import com.ankredev.loxwidget.data.loxone.SelectableValue
import com.ankredev.loxwidget.data.loxone.StructureParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BuilderUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val query: String = "",
    val allValues: List<SelectableValue> = emptyList(),
    // WICHTIG: geordnete Liste, NICHT Map. Map.equals() ignoriert die Reihenfolge, dadurch
    // verwarf MutableStateFlow umsortierte (aber inhaltsgleiche) Zustände per Konflation –
    // die Sortierung kam nie an. Eine List hat reihenfolge-sensitives equals.
    val selected: List<WidgetItem> = emptyList(),
    val title: String = "",
    val liveMode: Boolean = false,
    val backgroundColor: Long = 0xFFFFFFFF,
    val noConnection: Boolean = false,
    val roomFilter: String? = null,
    val catFilter: String? = null,
    val typeFilter: StructureParser.TypeGroup? = null,
    /** Beim Öffnen gespeicherte Auswahl – nur für die Sortierung (stabil pro Session). */
    val initiallySelected: Set<String> = emptySet(),
) {
    val rooms: List<String> get() = allValues.mapNotNull { it.roomName }.distinct().sorted()
    val cats: List<String> get() = allValues.mapNotNull { it.catName }.distinct().sorted()

    /** Schneller Lookup nach stateUuid (computed → nicht Teil von equals). */
    val selectedByUuid: Map<String, WidgetItem> get() = selected.associateBy { it.stateUuid }

    val filtered: List<SelectableValue>
        get() {
            var list = allValues
            if (query.isNotBlank()) {
                list = list.filter {
                    it.controlName.contains(query, ignoreCase = true) ||
                        (it.roomName?.contains(query, ignoreCase = true) == true) ||
                        (it.catName?.contains(query, ignoreCase = true) == true)
                }
            }
            roomFilter?.let { r -> list = list.filter { it.roomName == r } }
            catFilter?.let { c -> list = list.filter { it.catName == c } }
            typeFilter?.let { t -> list = list.filter { StructureParser.typeGroup(it.controlType) == t } }
            // Anfangs ausgewählte (aus der gespeicherten Config) nach oben – stabil pro Session.
            return list.sortedBy { if (it.stateUuid in initiallySelected) 0 else 1 }
        }
}

class WidgetBuilderViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = LoxoneRepository.get(app)
    private val configStore = ConfigStore(app)

    private val _state = MutableStateFlow(BuilderUiState())
    val state: StateFlow<BuilderUiState> = _state.asStateFlow()

    private var appWidgetId: Int = -1
    // Nur einmal laden: das ViewModel überlebt Activity-Neuerstellungen (viewModels()).
    // Sonst würde ein erneutes init() die bereits gemachten Änderungen (z. B. Sortierung)
    // mit dem gespeicherten Stand überschreiben.
    private var initialized = false

    fun init(appWidgetId: Int) {
        if (initialized) return
        initialized = true
        this.appWidgetId = appWidgetId
        viewModelScope.launch {
            if (configStore.getConnection() == null) {
                _state.value = _state.value.copy(loading = false, noConnection = true)
                return@launch
            }
            // Bestehende Konfiguration vorbelegen
            val existing = configStore.getWidgetConfig(appWidgetId)
            val result = repo.loadSelectableValues()
            _state.value = result.fold(
                onSuccess = { values ->
                    _state.value.copy(
                        loading = false,
                        allValues = values,
                        title = existing?.title.orEmpty(),
                        liveMode = existing?.liveMode ?: false,
                        backgroundColor = existing?.backgroundColor ?: 0xFFFFFFFF,
                        selected = existing?.items ?: emptyList(),
                        initiallySelected = existing?.items?.map { it.stateUuid }?.toSet() ?: emptySet(),
                    )
                },
                onFailure = {
                    _state.value.copy(loading = false, error = it.message)
                },
            )
        }
    }

    fun onQuery(q: String) { _state.value = _state.value.copy(query = q) }
    fun onTitle(t: String) { _state.value = _state.value.copy(title = t) }
    fun onLiveMode(b: Boolean) { _state.value = _state.value.copy(liveMode = b) }
    fun onBackgroundColor(c: Long) { _state.value = _state.value.copy(backgroundColor = c) }
    fun onRoomFilter(r: String?) { _state.value = _state.value.copy(roomFilter = r) }
    fun onCatFilter(c: String?) { _state.value = _state.value.copy(catFilter = c) }
    fun onTypeFilter(t: StructureParser.TypeGroup?) { _state.value = _state.value.copy(typeFilter = t) }

    fun toggle(value: SelectableValue) {
        val current = _state.value.selected
        val newList = if (current.any { it.stateUuid == value.stateUuid }) {
            current.filterNot { it.stateUuid == value.stateUuid }
        } else {
            val switchable = StructureParser.isSwitchable(value)
            current + WidgetItem(
                stateUuid = value.stateUuid,
                uuidAction = value.uuidAction,
                label = value.displayName,
                controlType = value.controlType,
                // Schaltbare Controls sind standardmäßig per Tipp schaltbar und als An/Aus formatiert.
                format = if (switchable) ValueFormat.ON_OFF else ValueFormat.AUTO,
                tapCommand = if (switchable) StructureParser.defaultTapCommand(value.controlType) else null,
            )
        }
        _state.value = _state.value.copy(selected = newList)
    }

    /**
     * Übernimmt die per Drag erzeugte Reihenfolge (Liste der stateUuids in Anzeige-Reihenfolge)
     * 1:1 in die Auswahl. Bewusst über die komplette Reihenfolge statt Index-Verschiebungen,
     * damit der gespeicherte Zustand exakt dem entspricht, was im Sortier-Screen sichtbar ist.
     */
    fun reorderSelected(orderedUuids: List<String>) {
        val byUuid = _state.value.selected.associateBy { it.stateUuid }
        val reordered = orderedUuids.mapNotNull { byUuid[it] } +
            // Sicherheit: evtl. nicht in der Liste enthaltene Einträge hinten anhängen.
            _state.value.selected.filter { it.stateUuid !in orderedUuids }
        _state.value = _state.value.copy(selected = reordered)
    }

    /** Entfernt ein ausgewähltes Element aus der Auswahl. */
    fun removeSelected(stateUuid: String) {
        _state.value = _state.value.copy(
            selected = _state.value.selected.filterNot { it.stateUuid == stateUuid },
        )
    }

    /** Setzt das Abfrage-Intervall (Sekunden) eines ausgewählten Elements. */
    fun setPollSeconds(value: SelectableValue, seconds: Int) {
        _state.value = _state.value.copy(
            selected = _state.value.selected.map {
                if (it.stateUuid == value.stateUuid) it.copy(pollSeconds = seconds.coerceAtLeast(2)) else it
            },
        )
    }

    /** Schaltet "Tippen schaltet" für ein bereits ausgewähltes Element an/aus. */
    fun setSwitchable(value: SelectableValue, switchable: Boolean) {
        _state.value = _state.value.copy(
            selected = _state.value.selected.map {
                if (it.stateUuid != value.stateUuid) {
                    it
                } else {
                    it.copy(
                        tapCommand = if (switchable) {
                            StructureParser.defaultTapCommand(value.controlType) ?: StructureParser.TOGGLE
                        } else {
                            null
                        },
                    )
                }
            },
        )
    }

    /** Speichert die Konfiguration; ruft [onSaved] mit der appWidgetId auf. */
    fun save(onSaved: (Int) -> Unit) {
        viewModelScope.launch {
            val config = WidgetConfig(
                appWidgetId = appWidgetId,
                title = _state.value.title,
                items = _state.value.selected,
                liveMode = _state.value.liveMode,
                backgroundColor = _state.value.backgroundColor,
            )
            configStore.saveWidgetConfig(config)
            // Sofort neu rendern (z. B. geänderte Reihenfolge), ohne auf den Worker zu warten.
            // Die frischen Werte holt der PollBurstService (Start in onSaved) gleich beim
            // ersten Tick – nicht-blockierend, daher schließt "Speichern" sofort.
            com.ankredev.loxwidget.widget.WidgetUpdater.rerender(getApplication(), appWidgetId)
            onSaved(appWidgetId)
        }
    }
}
