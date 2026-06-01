package com.nick.telegramalarm.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nick.telegramalarm.data.backend.BackendRepository
import com.nick.telegramalarm.data.history.AlarmHistoryRepository
import com.nick.telegramalarm.data.model.AlarmHistoryItem
import com.nick.telegramalarm.data.model.AppSettings
import com.nick.telegramalarm.data.model.BackendStatus
import com.nick.telegramalarm.data.model.ConnectionDiagnostics
import com.nick.telegramalarm.data.model.ConnectionStatus
import com.nick.telegramalarm.data.settings.SettingsRepository
import com.nick.telegramalarm.network.AlarmWebSocketClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val settings: AppSettings = AppSettings(),
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val diagnostics: ConnectionDiagnostics = ConnectionDiagnostics(),
    val backendStatus: BackendStatus? = null,
    val history: List<AlarmHistoryItem> = emptyList(),
    val backendTestResult: String? = null
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val backendRepository: BackendRepository,
    private val alarmHistoryRepository: AlarmHistoryRepository,
    webSocketClient: AlarmWebSocketClient
) : ViewModel() {
    private val draft = MutableStateFlow(SettingsDraft())
    private var backendUrlSaveJob: Job? = null
    private var authTokenSaveJob: Job? = null

    val uiState: StateFlow<MainUiState> = combine(
        settingsRepository.settings,
        webSocketClient.status,
        webSocketClient.diagnostics,
        alarmHistoryRepository.history,
        draft
    ) { settings, status, diagnostics, history, draft ->
        MainUiState(
            settings = settings.copy(
                backendUrl = draft.backendUrl ?: settings.backendUrl,
                authToken = draft.authToken ?: settings.authToken
            ),
            connectionStatus = status,
            diagnostics = diagnostics,
            backendStatus = draft.backendStatus,
            history = history,
            backendTestResult = draft.backendTestResult
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                draft.update {
                    SettingsDraft(
                        backendUrl = it.backendUrl ?: settings.backendUrl,
                        authToken = it.authToken ?: settings.authToken
                    )
                }
            }
        }
    }

    fun setAlertsEnabled(value: Boolean) = update { settingsRepository.updateAlertsEnabled(value) }
    fun setVolume(value: Float) = update { settingsRepository.updateVolume(value) }
    fun setUseDefaultAlarmSound(value: Boolean) = update { settingsRepository.updateUseDefaultAlarmSound(value) }
    fun setAutoReconnect(value: Boolean) = update { settingsRepository.updateAutoReconnect(value) }
    fun setQuietHoursEnabled(value: Boolean) = update { settingsRepository.updateQuietHoursEnabled(value) }
    fun setVolumeRampEnabled(value: Boolean) = update { settingsRepository.updateVolumeRampEnabled(value) }
    fun setAlarmDurationSeconds(value: Int) = update { settingsRepository.updateAlarmDurationSeconds(value) }

    fun setBackendUrl(value: String) {
        draft.update { it.copy(backendUrl = value) }
        backendUrlSaveJob?.cancel()
        backendUrlSaveJob = viewModelScope.launch {
            delay(TEXT_SAVE_DELAY_MS)
            settingsRepository.updateBackendUrl(value)
        }
    }

    fun setAuthToken(value: String) {
        draft.update { it.copy(authToken = value) }
        authTokenSaveJob?.cancel()
        authTokenSaveJob = viewModelScope.launch {
            delay(TEXT_SAVE_DELAY_MS)
            settingsRepository.updateAuthToken(value)
        }
    }

    fun setQuietHoursStart(value: String) = update { settingsRepository.updateQuietHoursStart(value) }
    fun setQuietHoursEnd(value: String) = update { settingsRepository.updateQuietHoursEnd(value) }
    fun setCustomAlarmSoundUri(value: String) = update { settingsRepository.updateCustomAlarmSoundUri(value) }
    fun setAllowedSenderIds(value: String) = update { settingsRepository.updateAllowedSenderIds(value) }
    fun setBlockedSenderIds(value: String) = update { settingsRepository.updateBlockedSenderIds(value) }

    fun refreshBackendStatus() = viewModelScope.launch {
        val settings = uiState.value.settings
        draft.update { it.copy(backendStatus = backendRepository.fetchStatus(settings.backendUrl, settings.authToken)) }
    }

    fun sendBackendTest() = viewModelScope.launch {
        val settings = uiState.value.settings
        val sent = backendRepository.sendTestEvent(settings.backendUrl, settings.authToken)
        draft.update { it.copy(backendTestResult = if (sent) "Backend test sent" else "Backend test failed") }
    }

    fun clearHistory() = viewModelScope.launch {
        alarmHistoryRepository.clear()
    }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private data class SettingsDraft(
        val backendUrl: String? = null,
        val authToken: String? = null,
        val backendStatus: BackendStatus? = null,
        val backendTestResult: String? = null
    )

    companion object {
        private const val TEXT_SAVE_DELAY_MS = 600L
    }
}
