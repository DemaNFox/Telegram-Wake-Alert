package com.nick.telegramalarm.domain

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.nick.telegramalarm.data.model.AlarmEvent
import com.nick.telegramalarm.notifications.NotificationFactory
import com.nick.telegramalarm.presentation.alarm.AlarmActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class AlarmControllerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationFactory: NotificationFactory
) : AlarmController {
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var mutedUntilMillis: Long = 0
    private val handler = Handler(Looper.getMainLooper())
    private var rampRunnable: Runnable? = null

    override fun trigger(event: AlarmEvent, volume: Float, soundUri: String?, volumeRampEnabled: Boolean) {
        if (System.currentTimeMillis() < mutedUntilMillis) return
        acquireWakeLock()
        maximizeAlarmVolume(volume)
        showFullScreenActivity(event)
        NotificationManagerCompat.from(context).notify(ALARM_NOTIFICATION_ID, notificationFactory.alarm(event))
        playAlarm(volume, soundUri, volumeRampEnabled)
    }

    override fun stop() {
        player?.runCatching { stop() }
        player?.release()
        player = null
        rampRunnable?.let { handler.removeCallbacks(it) }
        rampRunnable = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        context.getSystemService(NotificationManager::class.java).cancel(ALARM_NOTIFICATION_ID)
    }

    override fun muteOneMinute() {
        mutedUntilMillis = System.currentTimeMillis() + 60_000
        stop()
    }

    private fun acquireWakeLock() {
        val powerManager = context.getSystemService(PowerManager::class.java)
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "TelegramAlarm:AlarmWakeLock"
        ).apply { acquire(60_000) }
    }

    private fun maximizeAlarmVolume(volume: Float) {
        runCatching {
            val audio = context.getSystemService(AudioManager::class.java)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audio.setStreamVolume(AudioManager.STREAM_ALARM, (max * volume.coerceIn(0f, 1f)).roundToInt().coerceAtLeast(1), 0)
        }
    }

    private fun playAlarm(volume: Float, soundUri: String?, volumeRampEnabled: Boolean) {
        player?.release()
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, soundUri?.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: Settings.System.DEFAULT_ALARM_ALERT_URI)
                isLooping = true
                val initialVolume = if (volumeRampEnabled) 0.1f else volume.coerceIn(0f, 1f)
                setVolume(initialVolume, initialVolume)
                prepare()
                start()
                if (volumeRampEnabled) startVolumeRamp(this, volume.coerceIn(0f, 1f))
            }
        }.getOrNull()
    }

    private fun startVolumeRamp(mediaPlayer: MediaPlayer, targetVolume: Float) {
        var step = 1
        rampRunnable = object : Runnable {
            override fun run() {
                val nextVolume = (targetVolume * step / RAMP_STEPS).coerceIn(0.1f, targetVolume)
                runCatching { mediaPlayer.setVolume(nextVolume, nextVolume) }
                step += 1
                if (step <= RAMP_STEPS) handler.postDelayed(this, RAMP_INTERVAL_MS)
            }
        }.also { handler.postDelayed(it, RAMP_INTERVAL_MS) }
    }

    private fun showFullScreenActivity(event: AlarmEvent) {
        context.startActivity(
            AlarmActivity.intent(context, event).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )
    }

    companion object {
        private const val ALARM_NOTIFICATION_ID = 2001
        private const val RAMP_STEPS = 10
        private const val RAMP_INTERVAL_MS = 3_000L
    }
}
