package com.nick.telegramalarm.domain

import com.nick.telegramalarm.data.model.AlarmEvent

interface AlarmController {
    fun trigger(event: AlarmEvent, volume: Float)
    fun stop()
    fun muteOneMinute()
}
