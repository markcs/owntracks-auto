package com.owntracks.auto.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.ui.semantics.Role
import com.owntracks.auto.settings.SettingsStore
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.owntracks.auto.settings.NavApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(),
) {
    val savedUrl by vm.baseUrl.collectAsState()
    val savedAuth by vm.basicAuth.collectAsState()
    val navApp by vm.navApp.collectAsState()
    val testResult by vm.testResult.collectAsState()
    val autoRefresh by vm.autoRefresh.collectAsState()

    var urlField by remember(savedUrl) { mutableStateOf(savedUrl) }
    var usernameField by remember(savedAuth) { mutableStateOf(savedAuth.username) }
    var passwordField by remember(savedAuth) { mutableStateOf(savedAuth.password) }
    var passwordVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Server", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = urlField,
                onValueChange = { urlField = it },
                label = { Text("Base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = usernameField,
                onValueChange = { usernameField = it },
                label = { Text("Username (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    autoCorrect = false,
                    keyboardType = KeyboardType.Email,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = passwordField,
                onValueChange = { passwordField = it },
                label = { Text("Password (optional)") },
                singleLine = true,
                visualTransformation =
                    if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector =
                                if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription =
                                if (passwordVisible) "Hide password" else "Show password",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "HTTP Basic authentication. Leave blank if the server needs none.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.saveServer(urlField, usernameField, passwordField) }) { Text("Save") }
                OutlinedButton(onClick = { vm.testConnection(urlField, usernameField, passwordField) }) {
                    Text("Test connection")
                }
            }
            when (val r = testResult) {
                is TestResult.Testing -> Text("Testing…")
                is TestResult.Success -> Text(r.message)
                is TestResult.Failure -> Text("Failed: ${r.message}")
                TestResult.Idle -> {}
            }

            Text("Navigation app", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            NavApp.entries.forEach { app ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = app == navApp, onClick = { vm.saveNavApp(app) })
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = app == navApp, onClick = { vm.saveNavApp(app) })
                    Text(app.displayName())
                }
            }

            Text("Automatic refresh", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = autoRefresh.enabled,
                        role = Role.Switch,
                        onValueChange = { vm.setAutoRefreshEnabled(it) },
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Refresh map and devices")
                    Text(
                        "While the app is on screen, for today's date",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // The Row handles the toggle, so the Switch itself takes no input.
                Switch(checked = autoRefresh.enabled, onCheckedChange = null)
            }
            if (autoRefresh.enabled) {
                Text("Update every")
                SettingsStore.REFRESH_INTERVALS_SECONDS.forEach { seconds ->
                    val selected = seconds == autoRefresh.intervalSeconds
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = selected, role = Role.RadioButton, onClick = { vm.setRefreshInterval(seconds) })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Text(intervalLabel(seconds))
                    }
                }
            }
        }
    }
}

/** "30 seconds", "1 minute", "5 minutes", … */
fun intervalLabel(seconds: Int): String = when {
    seconds < 60 -> "$seconds seconds"
    seconds == 60 -> "1 minute"
    else -> "${seconds / 60} minutes"
}

private fun NavApp.displayName(): String = when (this) {
    NavApp.ASK -> "Ask each time"
    NavApp.GOOGLE_MAPS -> "Google Maps"
    NavApp.WAZE -> "Waze"
}
