package com.nick.telegramalarm.domain

import com.nick.telegramalarm.data.model.AlarmEvent

interface AlarmController {
    fun trigger(event: AlarmEvent, volume: Float, soundUri: String?, volumeRampEnabled: Boolean)
    fun stop()
    fun muteOneMinute()
}
