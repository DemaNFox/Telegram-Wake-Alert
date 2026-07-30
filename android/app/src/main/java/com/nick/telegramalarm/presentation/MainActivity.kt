package com.nick.telegramalarm.presentation

import android.Manifest
import android.app.NotificationManager
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
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.nick.telegramalarm.service.AlarmForegroundService
import com.nick.telegramalarm.service.ServiceActions
import com.nick.telegramalarm.ui.theme.TelegramAlarmTheme
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var notificationsEnabled by mutableStateOf(false)
    private var batteryUnrestricted by mutableStateOf(false)
    private var fullScreenAlertsAllowed by mutableStateOf(true)
    private var overlayAllowed by mutableStateOf(false)
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshSystemAccess()
        AlarmForegroundService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        setContent {
            TelegramAlarmTheme {
                MainApp(
                    viewModel = viewModel,
                    notificationsEnabled = notificationsEnabled,
                    batteryUnrestricted = batteryUnrestricted,
                    fullScreenAlertsAllowed = fullScreenAlertsAllowed,
                    overlayAllowed = overlayAllowed,
                    onTestAlarm = { AlarmForegroundService.action(this, ServiceActions.TEST_ALARM) },
                    onBatteryOptimization = { requestBatteryOptimizationIgnore() },
                    onFullScreenAlerts = { requestFullScreenAlertAccess() },
                    onOverlay = { requestOverlayAccess() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSystemAccess()
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

    private fun refreshSystemAccess() {
        notificationsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
        batteryUnrestricted = isBatteryUnrestricted()
        fullScreenAlertsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        overlayAllowed = Settings.canDrawOverlays(this)
    }

    private fun requestFullScreenAlertAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        }
    }

    private fun requestOverlayAccess() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainApp(
    viewModel: MainViewModel,
    notificationsEnabled: Boolean,
    batteryUnrestricted: Boolean,
    fullScreenAlertsAllowed: Boolean,
    overlayAllowed: Boolean,
    onTestAlarm: () -> Unit,
    onBatteryOptimization: () -> Unit,
    onFullScreenAlerts: () -> Unit,
    onOverlay: () -> Unit
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
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Main") }, icon = { Icon(Icons.Default.NotificationsActive, null) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Diag") }, icon = { Icon(Icons.Default.CloudSync, null) })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("People") }, icon = { Icon(Icons.Default.People, null) })
                Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("Groups") }, icon = { Icon(Icons.Default.People, null) })
                Tab(selected = tab == 4, onClick = { tab = 4 }, text = { Text("History") }, icon = { Icon(Icons.Default.History, null) })
                Tab(selected = tab == 5, onClick = { tab = 5 }, text = { Text("Settings") }, icon = { Icon(Icons.Default.Settings, null) })
            }
            when (tab) {
                0 -> MainScreen(
                    uiState,
                    viewModel,
                    notificationsEnabled,
                    batteryUnrestricted,
                    fullScreenAlertsAllowed,
                    overlayAllowed,
                    onTestAlarm,
                    onBatteryOptimization,
                    onFullScreenAlerts,
                    onOverlay
                )
                1 -> DiagnosticsScreen(uiState, viewModel)
                2 -> PeopleLazyScreen(uiState, viewModel)
                3 -> GroupsLazyScreen(uiState, viewModel)
                4 -> HistoryLazyScreen(uiState, viewModel)
                else -> SettingsScreen(uiState, viewModel)
            }
        }
    }
}

@Composable
private fun MainScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    notificationsEnabled: Boolean,
    batteryUnrestricted: Boolean,
    fullScreenAlertsAllowed: Boolean,
    overlayAllowed: Boolean,
    onTestAlarm: () -> Unit,
    onBatteryOptimization: () -> Unit,
    onFullScreenAlerts: () -> Unit,
    onOverlay: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        AlertsMasterCard(
            enabled = uiState.settings.alertsEnabled,
            onEnabledChange = viewModel::setAlertsEnabled
        )
        Text("Connection", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Text(uiState.connectionStatus.name, color = Color(0xFF93C5FD), style = MaterialTheme.typography.titleLarge)
        Text("Setup checklist", style = MaterialTheme.typography.titleMedium, color = Color.White)
        DiagnosticRow("Notifications", if (notificationsEnabled) "ok" else "missing")
        DiagnosticRow("Full-screen alerts", if (fullScreenAlertsAllowed) "ok" else "needs action")
        DiagnosticRow("Display over other apps", if (overlayAllowed) "ok" else "needs action")
        DiagnosticRow("Battery unrestricted", if (batteryUnrestricted) "ok" else "needs action")
        DiagnosticRow("Backend token", if (uiState.settings.authToken.isNotBlank()) "ok" else "missing")
        DiagnosticRow("Push delivery", if (uiState.settings.pushRegistered) "registered" else "not registered")
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
        if (!fullScreenAlertsAllowed) {
            Button(onClick = onFullScreenAlerts, modifier = Modifier.fillMaxWidth()) {
                Text("Allow full-screen alerts")
            }
        }
        if (!overlayAllowed) {
            Button(onClick = onOverlay, modifier = Modifier.fillMaxWidth()) {
                Text("Allow display over other apps")
            }
        }
    }
}

@Composable
private fun AlertsMasterCard(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (enabled) Color(0xFF22C55E) else Color(0xFF64748B),
                shape = RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) Color(0xFF143524) else Color(0xFF1E293B)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "Alerts",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (enabled) "Incoming Telegram alerts are enabled" else "All incoming alerts are paused",
                    color = Color(0xFFCBD5E1),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.width(16.dp))
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
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
            DiagnosticRow("Firebase push", status.pushEnabled?.toString() ?: "-")
            DiagnosticRow("Push devices", status.pushRegisteredDevices?.toString() ?: "-")
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
private fun GroupsScreen(uiState: MainUiState, viewModel: MainViewModel) {
    val selected = remember(uiState.settings.selectedGroupIds) {
        parseSenderIds(uiState.settings.selectedGroupIds)
    }
    var query by remember { mutableStateOf("") }
    val visibleGroups = remember(uiState.recentGroups, query) {
        uiState.recentGroups.filter { it.title.contains(query.trim(), ignoreCase = true) }
    }
    val selectedGroups = remember(uiState.recentGroups, selected) {
        selected.map { chatId ->
            uiState.recentGroups.firstOrNull { it.chatId == chatId }
                ?: com.nick.telegramalarm.data.model.TelegramGroup(chatId, chatId, null)
        }.sortedBy { it.title.lowercase() }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Group tracking", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        SwitchRow("Notify for selected groups", uiState.settings.selectedGroupsEnabled) {
            viewModel.setSelectedGroupsEnabled(it)
        }
        Text("Selected groups (${selected.size})", color = Color.White, style = MaterialTheme.typography.titleMedium)
        if (selectedGroups.isEmpty()) {
            Text("No groups selected", color = Color(0xFF94A3B8))
        } else {
            selectedGroups.forEach { group ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(group.title, color = Color.White, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { viewModel.removeGroup(group.chatId) }) {
                        Text("Remove")
                    }
                }
            }
        }
        Button(onClick = { viewModel.refreshRecentGroups() }, modifier = Modifier.fillMaxWidth()) {
            Text("Load Telegram groups")
        }
        uiState.groupsLoadResult?.let { Text(it, color = Color(0xFFCBD5E1)) }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Find group by name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        visibleGroups.forEach { group ->
            val isSelected = group.chatId in selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111827))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(group.title, color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text(group.chatId, color = Color(0xFF94A3B8))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (isSelected) viewModel.removeGroup(group.chatId)
                        else viewModel.selectGroup(group.chatId)
                    }
                ) {
                    Text(if (isSelected) "Remove" else "Select")
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

@Composable
private fun PeopleLazyScreen(uiState: MainUiState, viewModel: MainViewModel) {
    val allowed = remember(uiState.settings.allowedSenderIds) {
        parseSenderIds(uiState.settings.allowedSenderIds)
    }
    val blocked = remember(uiState.settings.blockedSenderIds) {
        parseSenderIds(uiState.settings.blockedSenderIds)
    }
    val allowedPeople = remember(uiState.recentPeople, allowed) {
        allowed.map { senderId ->
            uiState.recentPeople.firstOrNull { it.senderId == senderId }
                ?: com.nick.telegramalarm.data.model.TelegramPerson(senderId, senderId, null, null)
        }.sortedBy { it.name.lowercase() }
    }
    val blockedPeople = remember(uiState.recentPeople, blocked) {
        blocked.map { senderId ->
            uiState.recentPeople.firstOrNull { it.senderId == senderId }
                ?: com.nick.telegramalarm.data.model.TelegramPerson(senderId, senderId, null, null)
        }.sortedBy { it.name.lowercase() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("People filter", style = MaterialTheme.typography.headlineSmall, color = Color.White) }
        item {
            Text("Allowed people (${allowed.size})", color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        if (allowedPeople.isEmpty()) {
            item { Text("No explicitly allowed people", color = Color(0xFF94A3B8)) }
        } else {
            items(allowedPeople, key = { "allowed-${it.senderId}" }) { person ->
                PersonSelectionRow(person.name, person.senderId, "Remove") {
                    viewModel.removeAllowedPerson(person.senderId)
                }
            }
        }
        item {
            Text("Blocked people (${blocked.size})", color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        if (blockedPeople.isEmpty()) {
            item { Text("No blocked people", color = Color(0xFF94A3B8)) }
        } else {
            items(blockedPeople, key = { "blocked-${it.senderId}" }) { person ->
                PersonSelectionRow(person.name, person.senderId, "Unblock") {
                    viewModel.removeBlockedPerson(person.senderId)
                }
            }
        }
        item {
            Button(onClick = { viewModel.refreshRecentPeople() }, modifier = Modifier.fillMaxWidth()) {
                Text("Load recent Telegram people")
            }
        }
        uiState.peopleLoadResult?.let { result ->
            item { Text(result, color = Color(0xFFCBD5E1)) }
        }
        item {
            Text("Telegram people", color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        items(uiState.recentPeople, key = { it.senderId }) { person ->
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
            }
        }
    }
}

@Composable
private fun PersonSelectionRow(name: String, senderId: String, action: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E293B))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = Color.White)
            if (name != senderId) Text(senderId, color = Color(0xFF94A3B8))
        }
        Spacer(Modifier.width(8.dp))
        Button(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun GroupsLazyScreen(uiState: MainUiState, viewModel: MainViewModel) {
    val selected = remember(uiState.settings.selectedGroupIds) {
        parseSenderIds(uiState.settings.selectedGroupIds)
    }
    var query by remember { mutableStateOf("") }
    val visibleGroups = remember(uiState.recentGroups, query) {
        uiState.recentGroups.filter { it.title.contains(query.trim(), ignoreCase = true) }
    }
    val selectedGroups = remember(uiState.recentGroups, selected) {
        selected.map { chatId ->
            uiState.recentGroups.firstOrNull { it.chatId == chatId }
                ?: com.nick.telegramalarm.data.model.TelegramGroup(chatId, chatId, null)
        }.sortedBy { it.title.lowercase() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Group tracking", style = MaterialTheme.typography.headlineSmall, color = Color.White) }
        item {
            SwitchRow("Notify for selected groups", uiState.settings.selectedGroupsEnabled) {
                viewModel.setSelectedGroupsEnabled(it)
            }
        }
        item {
            Text("Selected groups (${selected.size})", color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        if (selectedGroups.isEmpty()) {
            item { Text("No groups selected", color = Color(0xFF94A3B8)) }
        } else {
            items(selectedGroups, key = { "selected-${it.chatId}" }) { group ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(group.title, color = Color.White, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { viewModel.removeGroup(group.chatId) }) { Text("Remove") }
                }
            }
        }
        item {
            Button(onClick = { viewModel.refreshRecentGroups() }, modifier = Modifier.fillMaxWidth()) {
                Text("Load Telegram groups")
            }
        }
        uiState.groupsLoadResult?.let { result ->
            item { Text(result, color = Color(0xFFCBD5E1)) }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Find group by name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        items(visibleGroups, key = { it.chatId }) { group ->
            val isSelected = group.chatId in selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111827))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(group.title, color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text(group.chatId, color = Color(0xFF94A3B8))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (isSelected) viewModel.removeGroup(group.chatId)
                        else viewModel.selectGroup(group.chatId)
                    }
                ) {
                    Text(if (isSelected) "Remove" else "Select")
                }
            }
        }
    }
}

@Composable
private fun HistoryLazyScreen(uiState: MainUiState, viewModel: MainViewModel) {
    val blockedChats = remember(uiState.settings.blockedChatIds) {
        parseSenderIds(uiState.settings.blockedChatIds)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Alarm history", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Button(onClick = { viewModel.clearHistory() }) { Text("Clear") }
            }
        }
        item {
            Text(
                "Press and hold a message to block notifications from its chat. Blocked chats: ${blockedChats.size}",
                color = Color(0xFF94A3B8),
                style = MaterialTheme.typography.bodySmall
            )
        }
        items(uiState.history) { historyItem ->
            HistoryItemCard(
                item = historyItem,
                isBlocked = historyItem.chatId in blockedChats,
                onBlockChat = { viewModel.blockChat(historyItem.chatId) },
                onUnblockChat = { viewModel.unblockChat(historyItem.chatId) }
            )
        }
    }
}

@Composable
private fun HistoryItemCard(
    item: com.nick.telegramalarm.data.model.AlarmHistoryItem,
    isBlocked: Boolean,
    onBlockChat: () -> Unit,
    onUnblockChat: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (isBlocked) Color(0xFFEF4444) else Color(0xFF334155),
                    shape = RoundedCornerShape(14.dp)
                )
                .pointerInput(item.chatId, isBlocked) {
                    detectTapGestures(onLongPress = { menuExpanded = true })
                },
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.senderName,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        item.chatTitle?.takeIf { it.isNotBlank() }?.let { title ->
                            Text(title, color = Color(0xFF93C5FD), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(
                        if (isBlocked) "BLOCKED" else item.status.uppercase(),
                        color = if (isBlocked) Color(0xFFFCA5A5) else Color(0xFF86EFAC),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                HorizontalDivider(color = Color(0xFF334155))
                Text(item.message, color = Color(0xFFE2E8F0), style = MaterialTheme.typography.bodyLarge)
                Text(
                    formatHistoryTimestamp(item.timestamp),
                    color = Color(0xFF94A3B8),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        when {
                            item.chatId.isBlank() -> "Chat unavailable for this older entry"
                            isBlocked -> "Unblock this chat"
                            else -> "Block this chat"
                        }
                    )
                },
                enabled = item.chatId.isNotBlank(),
                onClick = {
                    if (isBlocked) onUnblockChat() else onBlockChat()
                    menuExpanded = false
                }
            )
        }
    }
}

private fun formatHistoryTimestamp(timestamp: Long): String {
    val milliseconds = if (timestamp < 10_000_000_000L) timestamp * 1_000 else timestamp
    return runCatching {
        HISTORY_TIME_FORMATTER.format(Instant.ofEpochMilli(milliseconds))
    }.getOrDefault(timestamp.toString())
}

private val HISTORY_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
        .withZone(ZoneId.systemDefault())

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
    val blockedChats = remember(uiState.settings.blockedChatIds) {
        parseSenderIds(uiState.settings.blockedChatIds)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Alert sources", color = Color.White, style = MaterialTheme.typography.titleMedium)
        SwitchRow("Private users", uiState.settings.alertPrivateUsers) { viewModel.setAlertPrivateUsers(it) }
        SwitchRow("Private bots", uiState.settings.alertPrivateBots) { viewModel.setAlertPrivateBots(it) }
        SwitchRow("Group mentions", uiState.settings.alertGroupMentions) { viewModel.setAlertGroupMentions(it) }
        SwitchRow("Group replies", uiState.settings.alertGroupReplies) { viewModel.setAlertGroupReplies(it) }
        Text(
            "Blocked chats (${blockedChats.size})",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium
        )
        if (blockedChats.isEmpty()) {
            Text("No blocked chats", color = Color(0xFF94A3B8))
        } else {
            blockedChats.forEach { chatId ->
                val chatName = uiState.history
                    .firstOrNull { it.chatId == chatId }
                    ?.chatTitle
                    ?.takeIf { it.isNotBlank() }
                    ?: chatId
                PersonSelectionRow(chatName, chatId, "Unblock") {
                    viewModel.unblockChat(chatId)
                }
            }
        }
        Text("Push delivery", color = Color.White, style = MaterialTheme.typography.titleMedium)
        DiagnosticRow(
            "Registration",
            if (uiState.settings.pushRegistered) "registered" else "not registered"
        )
        Text(uiState.settings.pushRegistrationMessage, color = Color(0xFF94A3B8))
        Button(
            onClick = viewModel::refreshPushRegistration,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Register push delivery")
        }
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
        Button(onClick = { viewModel.applyConnectionSettings() }, modifier = Modifier.fillMaxWidth()) {
            Text("Apply connection settings")
        }
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
