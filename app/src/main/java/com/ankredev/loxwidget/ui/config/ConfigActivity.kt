package com.ankredev.loxwidget.ui.config

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Launcher-/Admin-Aktivität: Miniserver-Verbindung einrichten.
 * Die Auswahl der konkreten Werte pro Widget erfolgt in [com.ankredev.loxwidget.widget.WidgetConfigActivity].
 */
class ConfigActivity : ComponentActivity() {

    private val viewModel: ConnectionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ConnectionScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionScreen(viewModel: ConnectionViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Loxone-Verbindung") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Miniserver im Heimnetz. IP oder Hostname; http:// wird ergänzt.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = state.host,
                onValueChange = viewModel::onHost,
                label = { Text("Host (z. B. 192.168.1.50)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.user,
                onValueChange = viewModel::onUser,
                label = { Text("Benutzer") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPassword,
                label = { Text("Passwort") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = viewModel::testAndSave,
                enabled = !state.testing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.testing) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                }
                Text("Testen & Speichern")
            }
            state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

            if (state.saved) {
                Text(
                    "Verbindung gespeichert. Füge nun ein Widget zum Home-Screen hinzu, " +
                        "um Werte auszuwählen.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
