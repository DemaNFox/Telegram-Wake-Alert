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
import com.nick.telegramalarm.data.model.TelegramPerson
import com.nick.telegramalarm.data.model.TelegramGroup
import com.nick.telegramalarm.data.people.PeopleCacheRepository
import com.nick.telegramalarm.data.settings.SettingsRepository
import com.nick.telegramalarm.network.AlarmWebSocketClient
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val recentPeople: List<TelegramPerson> = emptyList(),
    val recentGroups: List<TelegramGroup> = emptyList(),
    val peopleLoadResult: String? = null,
    val groupsLoadResult: String? = null,
    val backendTestResult: String? = null
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val backendRepository: BackendRepository,
    private val alarmHistoryRepository: AlarmHistoryRepository,
    private val peopleCacheRepository: PeopleCacheRepository,
    webSocketClient: AlarmWebSocketClient
) : ViewModel() {
    private val draft = MutableStateFlow(SettingsDraft())
    private val baseUiState = combine(
        settingsRepository.settings,
        webSocketClient.status,
        webSocketClient.diagnostics,
        alarmHistoryRepository.history,
        peopleCacheRepository.people
    ) { settings, status, diagnostics, history, cachedPeople ->
        BaseUiState(
            settings = settings,
            connectionStatus = status,
            diagnostics = diagnostics,
            history = history,
            recentPeople = cachedPeople
        )
    }

    val uiState: StateFlow<MainUiState> = combine(
        baseUiState,
        draft
    ) { base, draft ->
        MainUiState(
            settings = base.settings.copy(
                backendUrl = draft.backendUrl ?: base.settings.backendUrl,
                authToken = draft.authToken ?: base.settings.authToken
            ),
            connectionStatus = base.connectionStatus,
            diagnostics = base.diagnostics,
            backendStatus = draft.backendStatus,
            history = base.history,
            recentPeople = base.recentPeople,
            recentGroups = draft.recentGroups,
            peopleLoadResult = draft.peopleLoadResult,
            groupsLoadResult = draft.groupsLoadResult,
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
    fun setAlertPrivateUsers(value: Boolean) = update { settingsRepository.updateAlertPrivateUsers(value) }
    fun setAlertPrivateBots(value: Boolean) = update { settingsRepository.updateAlertPrivateBots(value) }
    fun setAlertGroupMentions(value: Boolean) = update { settingsRepository.updateAlertGroupMentions(value) }
    fun setAlertGroupReplies(value: Boolean) = update { settingsRepository.updateAlertGroupReplies(value) }
    fun setSelectedGroupsEnabled(value: Boolean) = update { settingsRepository.updateSelectedGroupsEnabled(value) }
    fun setVolume(value: Float) = update { settingsRepository.updateVolume(value) }
    fun setUseDefaultAlarmSound(value: Boolean) = update { settingsRepository.updateUseDefaultAlarmSound(value) }
    fun setAutoReconnect(value: Boolean) = update { settingsRepository.updateAutoReconnect(value) }
    fun setQuietHoursEnabled(value: Boolean) = update { settingsRepository.updateQuietHoursEnabled(value) }
    fun setVolumeRampEnabled(value: Boolean) = update { settingsRepository.updateVolumeRampEnabled(value) }
    fun setAlarmDurationSeconds(value: Int) = update { settingsRepository.updateAlarmDurationSeconds(value) }

    fun setBackendUrl(value: String) {
        draft.update { it.copy(backendUrl = value) }
    }

    fun setAuthToken(value: String) {
        draft.update { it.copy(authToken = value) }
    }

    fun resetBackendUrl() {
        setBackendUrl(DEFAULT_BACKEND_URL)
    }

    fun applyConnectionSettings() = viewModelScope.launch {
        val state = uiState.value
        settingsRepository.updateBackendUrl(state.settings.backendUrl)
        settingsRepository.updateAuthToken(state.settings.authToken)
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
        val result = backendRepository.sendTestEvent(settings.backendUrl, settings.authToken)
        draft.update { it.copy(backendTestResult = result.message) }
    }

    fun clearHistory() = viewModelScope.launch {
        alarmHistoryRepository.clear()
    }

    fun refreshRecentPeople() = viewModelScope.launch {
        val settings = uiState.value.settings
        draft.update { it.copy(peopleLoadResult = "Loading people...") }
        val result = backendRepository.fetchRecentPeople(settings.backendUrl, settings.authToken)
        if (result.success) {
            peopleCacheRepository.replacePeople(result.people)
        }
        draft.update { it.copy(peopleLoadResult = result.message) }
    }

    fun refreshRecentGroups() = viewModelScope.launch {
        val settings = uiState.value.settings
        draft.update { it.copy(groupsLoadResult = "Loading groups...") }
        val result = backendRepository.fetchRecentGroups(settings.backendUrl, settings.authToken)
        draft.update {
            it.copy(
                recentGroups = if (result.success) result.groups else it.recentGroups,
                groupsLoadResult = result.message
            )
        }
    }

    fun selectGroup(chatId: String) = updateGroupSelection(chatId, selected = true)
    fun removeGroup(chatId: String) = updateGroupSelection(chatId, selected = false)

    fun allowPerson(senderId: String) = updatePeopleList(senderId, addToAllowed = true)
    fun blockPerson(senderId: String) = updatePeopleList(senderId, addToBlocked = true)
    fun removeAllowedPerson(senderId: String) = updatePeopleList(senderId, removeFromAllowed = true)
    fun removeBlockedPerson(senderId: String) = updatePeopleList(senderId, removeFromBlocked = true)

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private data class SettingsDraft(
        val backendUrl: String? = null,
        val authToken: String? = null,
        val backendStatus: BackendStatus? = null,
        val peopleLoadResult: String? = null,
        val recentGroups: List<TelegramGroup> = emptyList(),
        val groupsLoadResult: String? = null,
        val backendTestResult: String? = null
    )

    private data class BaseUiState(
        val settings: AppSettings,
        val connectionStatus: ConnectionStatus,
        val diagnostics: ConnectionDiagnostics,
        val history: List<AlarmHistoryItem>,
        val recentPeople: List<TelegramPerson>
    )

    private fun updatePeopleList(
        senderId: String,
        addToAllowed: Boolean = false,
        addToBlocked: Boolean = false,
        removeFromAllowed: Boolean = false,
        removeFromBlocked: Boolean = false
    ) {
        viewModelScope.launch {
            val settings = uiState.value.settings
            val allowed = parseSenderIds(settings.allowedSenderIds).toMutableSet()
            val blocked = parseSenderIds(settings.blockedSenderIds).toMutableSet()
            if (addToAllowed) {
                allowed.add(senderId)
                blocked.remove(senderId)
            }
            if (addToBlocked) {
                blocked.add(senderId)
                allowed.remove(senderId)
            }
            if (removeFromAllowed) allowed.remove(senderId)
            if (removeFromBlocked) blocked.remove(senderId)
            settingsRepository.updateAllowedSenderIds(allowed.joinToString(","))
            settingsRepository.updateBlockedSenderIds(blocked.joinToString(","))
        }
    }

    private fun parseSenderIds(value: String): Set<String> =
        value.split(",", "\n", " ")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()

    private fun updateGroupSelection(chatId: String, selected: Boolean) {
        viewModelScope.launch {
            val ids = parseSenderIds(uiState.value.settings.selectedGroupIds).toMutableSet()
            if (selected) ids.add(chatId) else ids.remove(chatId)
            settingsRepository.updateSelectedGroupIds(ids.joinToString(","))
        }
    }

    companion object {
        private const val DEFAULT_BACKEND_URL = "ws://10.0.2.2:8000/ws"
    }
}
