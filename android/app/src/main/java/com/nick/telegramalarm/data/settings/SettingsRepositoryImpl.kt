package com.nick.telegramalarm.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nick.telegramalarm.data.model.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("telegram_alarm_settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {
    private object Keys {
        val alertsEnabled = booleanPreferencesKey("alerts_enabled")
        val volume = floatPreferencesKey("volume")
        val useDefaultAlarmSound = booleanPreferencesKey("use_default_alarm_sound")
        val autoReconnect = booleanPreferencesKey("auto_reconnect")
        val backendUrl = stringPreferencesKey("backend_url")
        val authToken = stringPreferencesKey("auth_token")
        val serviceEnabled = booleanPreferencesKey("service_enabled")
    }

    override val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            alertsEnabled = prefs[Keys.alertsEnabled] ?: true,
            volume = prefs[Keys.volume] ?: 1f,
            useDefaultAlarmSound = prefs[Keys.useDefaultAlarmSound] ?: true,
            autoReconnect = prefs[Keys.autoReconnect] ?: true,
            backendUrl = prefs[Keys.backendUrl] ?: "ws://10.0.2.2:8000/ws",
            authToken = prefs[Keys.authToken] ?: "",
            serviceEnabled = prefs[Keys.serviceEnabled] ?: true
        )
    }

    override suspend fun updateAlertsEnabled(enabled: Boolean) = update(Keys.alertsEnabled, enabled)
    override suspend fun updateVolume(volume: Float) = update(Keys.volume, volume.coerceIn(0f, 1f))
    override suspend fun updateUseDefaultAlarmSound(enabled: Boolean) = update(Keys.useDefaultAlarmSound, enabled)
    override suspend fun updateAutoReconnect(enabled: Boolean) = update(Keys.autoReconnect, enabled)
    override suspend fun updateBackendUrl(url: String) = update(Keys.backendUrl, url.trim())
    override suspend fun updateAuthToken(token: String) = update(Keys.authToken, token.trim())
    override suspend fun updateServiceEnabled(enabled: Boolean) = update(Keys.serviceEnabled, enabled)

    private suspend fun <T> update(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }
}
