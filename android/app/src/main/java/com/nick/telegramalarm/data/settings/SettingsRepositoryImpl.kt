package com.nick.telegramalarm.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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
        val serviceEnabled = booleanPreferencesKey("service_enabled")
        val quietHoursEnabled = booleanPreferencesKey("quiet_hours_enabled")
        val quietHoursStart = stringPreferencesKey("quiet_hours_start")
        val quietHoursEnd = stringPreferencesKey("quiet_hours_end")
        val customAlarmSoundUri = stringPreferencesKey("custom_alarm_sound_uri")
        val alarmDurationSeconds = intPreferencesKey("alarm_duration_seconds")
        val volumeRampEnabled = booleanPreferencesKey("volume_ramp_enabled")
    }

    private val securePrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "telegram_alarm_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            alertsEnabled = prefs[Keys.alertsEnabled] ?: true,
            volume = prefs[Keys.volume] ?: 1f,
            useDefaultAlarmSound = prefs[Keys.useDefaultAlarmSound] ?: true,
            autoReconnect = prefs[Keys.autoReconnect] ?: true,
            backendUrl = prefs[Keys.backendUrl] ?: "ws://10.0.2.2:8000/ws",
            authToken = securePrefs.getString(KEY_AUTH_TOKEN, "").orEmpty(),
            serviceEnabled = prefs[Keys.serviceEnabled] ?: true,
            quietHoursEnabled = prefs[Keys.quietHoursEnabled] ?: false,
            quietHoursStart = prefs[Keys.quietHoursStart] ?: "23:00",
            quietHoursEnd = prefs[Keys.quietHoursEnd] ?: "08:00",
            customAlarmSoundUri = prefs[Keys.customAlarmSoundUri] ?: "",
            alarmDurationSeconds = prefs[Keys.alarmDurationSeconds] ?: 0,
            volumeRampEnabled = prefs[Keys.volumeRampEnabled] ?: false
        )
    }

    override suspend fun updateAlertsEnabled(enabled: Boolean) = update(Keys.alertsEnabled, enabled)
    override suspend fun updateVolume(volume: Float) = update(Keys.volume, volume.coerceIn(0f, 1f))
    override suspend fun updateUseDefaultAlarmSound(enabled: Boolean) = update(Keys.useDefaultAlarmSound, enabled)
    override suspend fun updateAutoReconnect(enabled: Boolean) = update(Keys.autoReconnect, enabled)
    override suspend fun updateBackendUrl(url: String) = update(Keys.backendUrl, url.trim())
    override suspend fun updateAuthToken(token: String) {
        securePrefs.edit().putString(KEY_AUTH_TOKEN, token.trim()).apply()
        context.dataStore.edit { it[Keys.serviceEnabled] = it[Keys.serviceEnabled] ?: true }
    }
    override suspend fun updateServiceEnabled(enabled: Boolean) = update(Keys.serviceEnabled, enabled)
    override suspend fun updateQuietHoursEnabled(enabled: Boolean) = update(Keys.quietHoursEnabled, enabled)
    override suspend fun updateQuietHoursStart(value: String) = update(Keys.quietHoursStart, value.trim())
    override suspend fun updateQuietHoursEnd(value: String) = update(Keys.quietHoursEnd, value.trim())
    override suspend fun updateCustomAlarmSoundUri(value: String) = update(Keys.customAlarmSoundUri, value.trim())
    override suspend fun updateAlarmDurationSeconds(value: Int) = update(Keys.alarmDurationSeconds, value.coerceIn(0, 3600))
    override suspend fun updateVolumeRampEnabled(enabled: Boolean) = update(Keys.volumeRampEnabled, enabled)

    private suspend fun <T> update(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) {
        context.dataStore.edit { it[key] = value }
    }

    companion object {
        private const val KEY_AUTH_TOKEN = "auth_token"
    }
}
