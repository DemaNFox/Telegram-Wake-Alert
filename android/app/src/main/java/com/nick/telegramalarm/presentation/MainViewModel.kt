package com.nick.telegramalarm.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nick.telegramalarm.data.model.AppSettings
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
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    webSocketClient: AlarmWebSocketClient
) : ViewModel() {
    private val draft = MutableStateFlow(SettingsDraft())
    private var backendUrlSaveJob: Job? = null
    private var authTokenSaveJob: Job? = null

    val uiState: StateFlow<MainUiState> = combine(
        settingsRepository.settings,
        webSocketClient.status,
        draft
    ) { settings, status, draft ->
        MainUiState(
            settings = settings.copy(
                backendUrl = draft.backendUrl ?: settings.backendUrl,
                authToken = draft.authToken ?: settings.authToken
            ),
            connectionStatus = status
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

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private data class SettingsDraft(
        val backendUrl: String? = null,
        val authToken: String? = null
    )

    companion object {
        private const val TEXT_SAVE_DELAY_MS = 600L
    }
}
