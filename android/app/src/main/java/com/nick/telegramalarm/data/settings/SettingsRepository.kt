package com.nick.telegramalarm.data.settings

import com.nick.telegramalarm.data.model.AppSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun updateAlertsEnabled(enabled: Boolean)
    suspend fun updateVolume(volume: Float)
    suspend fun updateUseDefaultAlarmSound(enabled: Boolean)
    suspend fun updateAutoReconnect(enabled: Boolean)
    suspend fun updateBackendUrl(url: String)
    suspend fun updateAuthToken(token: String)
    suspend fun updateServiceEnabled(enabled: Boolean)
    suspend fun updateQuietHoursEnabled(enabled: Boolean)
    suspend fun updateQuietHoursStart(value: String)
    suspend fun updateQuietHoursEnd(value: String)
    suspend fun updateCustomAlarmSoundUri(value: String)
    suspend fun updateAlarmDurationSeconds(value: Int)
    suspend fun updateVolumeRampEnabled(enabled: Boolean)
}
