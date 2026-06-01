package com.nick.telegramalarm.data.model

data class AppSettings(
    val alertsEnabled: Boolean = true,
    val volume: Float = 1f,
    val useDefaultAlarmSound: Boolean = true,
    val autoReconnect: Boolean = true,
    val backendUrl: String = "ws://10.0.2.2:8000/ws",
    val authToken: String = "",
    val serviceEnabled: Boolean = true,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStart: String = "23:00",
    val quietHoursEnd: String = "08:00",
    val customAlarmSoundUri: String = "",
    val alarmDurationSeconds: Int = 0,
    val volumeRampEnabled: Boolean = false
)
