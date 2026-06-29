package com.ankredev.loxwidget.ui.config

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ankredev.loxwidget.data.LoxoneRepository
import com.ankredev.loxwidget.data.config.ConfigStore
import com.ankredev.loxwidget.data.config.SecretStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ConnectionUiState(
    val host: String = "",
    val user: String = "",
    val password: String = "",
    val pollSeconds: String = "5",
    val testing: Boolean = false,
    val message: String? = null,
    val saved: Boolean = false,
)

class ConnectionViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = LoxoneRepository.get(app)
    private val configStore = ConfigStore(app)
    private val secretStore = SecretStore(app)

    private val _state = MutableStateFlow(ConnectionUiState())
    val state: StateFlow<ConnectionUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val conn = configStore.getConnection()
            if (conn != null) {
                _state.value = _state.value.copy(
                    host = conn.host,
                    user = conn.user,
                    password = secretStore.password.orEmpty(),
                    pollSeconds = conn.pollSeconds.toString(),
                )
            }
        }
    }

    fun onHost(v: String) { _state.value = _state.value.copy(host = v, saved = false) }
    fun onUser(v: String) { _state.value = _state.value.copy(user = v, saved = false) }
    fun onPassword(v: String) { _state.value = _state.value.copy(password = v, saved = false) }
    fun onPollSeconds(v: String) {
        _state.value = _state.value.copy(pollSeconds = v.filter { it.isDigit() }, saved = false)
    }

    fun testAndSave() {
        val s = _state.value
        if (s.host.isBlank() || s.user.isBlank()) {
            _state.value = s.copy(message = "Host und Benutzer angeben")
            return
        }
        val host = normalizeHost(s.host)
        val poll = s.pollSeconds.toIntOrNull() ?: 30
        _state.value = s.copy(testing = true, message = null)
        viewModelScope.launch {
            val result = repo.testConnection(host, s.user, s.password)
            _state.value = if (result.isSuccess) {
                repo.saveConnection(host, s.user, s.password, poll)
                _state.value.copy(
                    testing = false,
                    message = "Verbindung OK – gespeichert",
                    saved = true,
                    host = host,
                )
            } else {
                _state.value.copy(
                    testing = false,
                    message = "Fehler: ${result.exceptionOrNull()?.message}",
                    saved = false,
                )
            }
        }
    }

    private fun normalizeHost(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
    }
}
