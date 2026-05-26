package com.nick.telegramalarm.data.model

data class AppSettings(
    val alertsEnabled: Boolean = true,
    val volume: Float = 1f,
    val useDefaultAlarmSound: Boolean = true,
    val autoReconnect: Boolean = true,
    val backendUrl: String = "ws://10.0.2.2:8000/ws",
    val authToken: String = "",
    val serviceEnabled: Boolean = true
)
