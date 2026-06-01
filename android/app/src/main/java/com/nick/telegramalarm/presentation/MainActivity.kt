package com.nick.telegramalarm.presentation

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nick.telegramalarm.service.AlarmForegroundService
import com.nick.telegramalarm.service.ServiceActions
import com.nick.telegramalarm.ui.theme.TelegramAlarmTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        AlarmForegroundService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        setContent {
            TelegramAlarmTheme {
                MainApp(
                    viewModel = viewModel,
                    notificationsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled(),
                    batteryUnrestricted = isBatteryUnrestricted(),
                    onTestAlarm = { AlarmForegroundService.action(this, ServiceActions.TEST_ALARM) },
                    onBatteryOptimization = { requestBatteryOptimizationIgnore() }
                )
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            AlarmForegroundService.start(this)
        }
    }

    private fun requestBatteryOptimizationIgnore() {
        val powerManager = getSystemService(PowerManager::class.java)
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        } else {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun isBatteryUnrestricted(): Boolean =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainApp(
    viewModel: MainViewModel,
    notificationsEnabled: Boolean,
    batteryUnrestricted: Boolean,
    onTestAlarm: () -> Unit,
    onBatteryOptimization: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Telegram Alarm") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Main") }, icon = { Icon(Icons.Default.NotificationsActive, null) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Diag") }, icon = { Icon(Icons.Default.CloudSync, null) })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("People") }, icon = { Icon(Icons.Default.People, null) })
                Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("History") }, icon = { Icon(Icons.Default.History, null) })
                Tab(selected = tab == 4, onClick = { tab = 4 }, text = { Text("Settings") }, icon = { Icon(Icons.Default.Settings, null) })
            }
            when (tab) {
                0 -> MainScreen(uiState, notificationsEnabled, batteryUnrestricted, onTestAlarm, onBatteryOptimization)
                1 -> DiagnosticsScreen(uiState, viewModel)
                2 -> PeopleScreen(uiState, viewModel)
                3 -> HistoryScreen(uiState, viewModel)
                else -> SettingsScreen(uiState, viewModel)
            }
        }
    }
}

@Composable
private fun MainScreen(
    uiState: MainUiState,
    notificationsEnabled: Boolean,
    batteryUnrestricted: Boolean,
    onTestAlarm: () -> Unit,
    onBatteryOptimization: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("Connection", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Text(uiState.connectionStatus.name, color = Color(0xFF93C5FD), style = MaterialTheme.typography.titleLarge)
        Text("Setup checklist", style = MaterialTheme.typography.titleMedium, color = Color.White)
        DiagnosticRow("Notifications", if (notificationsEnabled) "ok" else "missing")
        DiagnosticRow("Battery unrestricted", if (batteryUnrestricted) "ok" else "needs action")
        DiagnosticRow("Backend token", if (uiState.settings.authToken.isNotBlank()) "ok" else "missing")
        DiagnosticRow("Backend connected", if (uiState.connectionStatus == com.nick.telegramalarm.data.model.ConnectionStatus.CONNECTED) "ok" else "no")
        Button(onClick = onTestAlarm, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text("Test alarm")
        }
        Button(onClick = onBatteryOptimization, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.BatterySaver, null)
            Spacer(Modifier.width(8.dp))
            Text("Battery optimization")
        }
    }
}

@Composable
private fun DiagnosticsScreen(uiState: MainUiState, viewModel: MainViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Diagnostics", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        DiagnosticRow("WebSocket", uiState.connectionStatus.name)
        DiagnosticRow("Last connected", uiState.diagnostics.lastConnectedAt?.toString() ?: "-")
        DiagnosticRow("Last heartbeat", uiState.diagnostics.lastHeartbeatAt?.toString() ?: "-")
        DiagnosticRow("Last event", uiState.diagnostics.lastEventAt?.toString() ?: "-")
        DiagnosticRow("Reconnect attempts", uiState.diagnostics.reconnectAttempts.toString())
        DiagnosticRow("Last failure", uiState.diagnostics.lastFailureReason ?: "-")
        Button(onClick = { viewModel.refreshBackendStatus() }, modifier = Modifier.fillMaxWidth()) {
            Text("Refresh backend status")
        }
        uiState.backendStatus?.let { status ->
            DiagnosticRow("Backend reachable", status.reachable.toString())
            DiagnosticRow("Telegram connected", status.telegramConnected?.toString() ?: "-")
            DiagnosticRow("WS clients", status.websocketClients?.toString() ?: "-")
            DiagnosticRow("Backend error", status.error ?: "-")
        }
        Button(onClick = { viewModel.sendBackendTest() }, modifier = Modifier.fillMaxWidth()) {
            Text("Send backend test")
        }
        uiState.backendTestResult?.let { Text(it, color = Color(0xFFCBD5E1)) }
    }
}

@Composable
private fun PeopleScreen(uiState: MainUiState, viewModel: MainViewModel) {
    val allowed = remember(uiState.settings.allowedSenderIds) { parseSenderIds(uiState.settings.allowedSenderIds) }
    val blocked = remember(uiState.settings.blockedSenderIds) { parseSenderIds(uiState.settings.blockedSenderIds) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("People filter", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Button(onClick = { viewModel.refreshRecentPeople() }, modifier = Modifier.fillMaxWidth()) {
            Text("Load recent Telegram people")
        }
        uiState.peopleLoadResult?.let { Text(it, color = Color(0xFFCBD5E1)) }
        Text("Allowed: ${allowed.size} · Blocked: ${blocked.size}", color = Color(0xFFCBD5E1))
        uiState.recentPeople.forEach { person ->
            val state = when (person.senderId) {
                in blocked -> "blocked"
                in allowed -> "allowed"
                else -> "default"
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111827))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(person.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text("@${person.username ?: "-"} · ${person.senderId} · $state", color = Color(0xFF94A3B8))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { viewModel.allowPerson(person.senderId) }, modifier = Modifier.weight(1f)) {
                        Text("Allow")
                    }
                    Button(onClick = { viewModel.blockPerson(person.senderId) }, modifier = Modifier.weight(1f)) {
                        Text("Block")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { viewModel.removeAllowedPerson(person.senderId) }, modifier = Modifier.weight(1f)) {
                        Text("Unallow")
                    }
                    Button(onClick = { viewModel.removeBlockedPerson(person.senderId) }, modifier = Modifier.weight(1f)) {
                        Text("Unblock")
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(uiState: MainUiState, viewModel: MainViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Alarm history", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Button(onClick = { viewModel.clearHistory() }) { Text("Clear") }
        }
        uiState.history.forEach { item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111827))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(item.senderName, color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(item.message, color = Color(0xFFCBD5E1))
                Text("${item.timestamp} · ${item.status}", color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun parseSenderIds(value: String): Set<String> =
    value.split(",", "\n", " ")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFFCBD5E1))
        Text(value, color = Color.White)
    }
}

@Composable
private fun SettingsScreen(uiState: MainUiState, viewModel: MainViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SwitchRow("Enable alerts", uiState.settings.alertsEnabled) { viewModel.setAlertsEnabled(it) }
        SwitchRow("Auto reconnect", uiState.settings.autoReconnect) { viewModel.setAutoReconnect(it) }
        SwitchRow("Use default alarm sound", uiState.settings.useDefaultAlarmSound) { viewModel.setUseDefaultAlarmSound(it) }
        SwitchRow("Gradual volume ramp", uiState.settings.volumeRampEnabled) { viewModel.setVolumeRampEnabled(it) }
        SwitchRow("Quiet hours", uiState.settings.quietHoursEnabled) { viewModel.setQuietHoursEnabled(it) }
        Text("Alarm volume ${(uiState.settings.volume * 100).toInt()}%", color = Color.White)
        Slider(value = uiState.settings.volume, onValueChange = { viewModel.setVolume(it) }, valueRange = 0.1f..1f)
        Text("Alarm duration ${if (uiState.settings.alarmDurationSeconds == 0) "until stopped" else "${uiState.settings.alarmDurationSeconds}s"}", color = Color.White)
        Slider(
            value = uiState.settings.alarmDurationSeconds.toFloat(),
            onValueChange = { viewModel.setAlarmDurationSeconds(it.toInt()) },
            valueRange = 0f..600f
        )
        OutlinedTextField(
            value = uiState.settings.quietHoursStart,
            onValueChange = { viewModel.setQuietHoursStart(it) },
            label = { Text("Quiet hours start HH:mm") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = uiState.settings.quietHoursEnd,
            onValueChange = { viewModel.setQuietHoursEnd(it) },
            label = { Text("Quiet hours end HH:mm") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = uiState.settings.customAlarmSoundUri,
            onValueChange = { viewModel.setCustomAlarmSoundUri(it) },
            label = { Text("Custom alarm sound URI") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = uiState.settings.backendUrl,
            onValueChange = { viewModel.setBackendUrl(it) },
            label = { Text("Backend WebSocket URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Button(onClick = { viewModel.resetBackendUrl() }, modifier = Modifier.fillMaxWidth()) {
            Text("Reset backend URL")
        }
        OutlinedTextField(
            value = uiState.settings.authToken,
            onValueChange = { viewModel.setAuthToken(it) },
            label = { Text("Auth token") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
