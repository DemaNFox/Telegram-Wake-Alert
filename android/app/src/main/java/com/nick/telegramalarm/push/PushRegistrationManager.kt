package com.nick.telegramalarm.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.installations.FirebaseInstallations
import com.nick.telegramalarm.data.backend.BackendRepository
import com.nick.telegramalarm.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushRegistrationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val backendRepository: BackendRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun refresh() {
        if (FirebaseApp.getApps(context).isEmpty()) {
            scope.launch {
                settingsRepository.updatePushRegistration(
                    installationId = "",
                    registered = false,
                    message = "Add google-services.json to enable Firebase"
                )
            }
            return
        }
        FirebaseInstallations.getInstance().id.addOnCompleteListener { task ->
            if (task.isSuccessful && !task.result.isNullOrBlank()) {
                register(task.result)
            } else {
                scope.launch {
                    settingsRepository.updatePushRegistration(
                        installationId = "",
                        registered = false,
                        message = "FCM registration error: ${task.exception?.message ?: "unknown error"}"
                    )
                }
            }
        }
    }

    fun register(installationId: String) {
        if (installationId.isBlank()) return
        scope.launch {
            val settings = settingsRepository.settings.first()
            if (settings.authToken.isBlank()) {
                settingsRepository.updatePushRegistration(
                    installationId = installationId,
                    registered = false,
                    message = "Save the backend auth token to register push delivery"
                )
                return@launch
            }
            val previousInstallationId = settings.pushInstallationId
                .takeIf { it.isNotBlank() && it != installationId }
            val result = backendRepository.registerPushInstallation(
                backendUrl = settings.backendUrl,
                authToken = settings.authToken,
                installationId = installationId,
                previousInstallationId = previousInstallationId
            )
            settingsRepository.updatePushRegistration(
                installationId = installationId,
                registered = result.success,
                message = result.message
            )
        }
    }
}
