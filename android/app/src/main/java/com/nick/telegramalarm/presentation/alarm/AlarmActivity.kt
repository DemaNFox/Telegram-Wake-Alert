package com.nick.telegramalarm.presentation.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nick.telegramalarm.data.model.AlarmEvent
import com.nick.telegramalarm.service.AlarmForegroundService
import com.nick.telegramalarm.service.ServiceActions
import com.nick.telegramalarm.ui.theme.TelegramAlarmTheme

class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        val senderName = intent.getStringExtra(EXTRA_SENDER_NAME).orEmpty()
        val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
        val chatTitle = intent.getStringExtra(EXTRA_CHAT_TITLE).orEmpty()
        setContent {
            TelegramAlarmTheme {
                AlarmScreen(
                    senderName = senderName,
                    message = message,
                    chatTitle = chatTitle,
                    onStop = {
                        AlarmForegroundService.action(this, ServiceActions.STOP_ALARM)
                        finish()
                    },
                    onMute = {
                        AlarmForegroundService.action(this, ServiceActions.MUTE_ONE_MINUTE)
                        finish()
                    },
                    onSnoozeFive = {
                        AlarmForegroundService.action(this, ServiceActions.SNOOZE_FIVE_MINUTES)
                        finish()
                    },
                    onSnoozeTen = {
                        AlarmForegroundService.action(this, ServiceActions.SNOOZE_TEN_MINUTES)
                        finish()
                    }
                )
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_MUTE -> {
                AlarmForegroundService.action(this, ServiceActions.STOP_ALARM)
                finish()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java).requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }

    companion object {
        private const val EXTRA_SENDER_NAME = "sender_name"
        private const val EXTRA_MESSAGE = "message"
        private const val EXTRA_CHAT_TITLE = "chat_title"

        fun intent(context: Context, event: AlarmEvent): Intent = Intent(context, AlarmActivity::class.java).apply {
            putExtra(EXTRA_SENDER_NAME, event.senderName)
            putExtra(EXTRA_MESSAGE, event.message)
            putExtra(EXTRA_CHAT_TITLE, event.chatTitle.orEmpty())
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
    }
}

@Composable
private fun AlarmScreen(
    senderName: String,
    message: String,
    chatTitle: String,
    onStop: () -> Unit,
    onMute: () -> Unit,
    onSnoozeFive: () -> Unit,
    onSnoozeTen: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111827))
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Telegram message", style = MaterialTheme.typography.headlineMedium, color = Color(0xFFF87171))
        Spacer(Modifier.height(20.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1F2937))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Sender", style = MaterialTheme.typography.labelLarge, color = Color(0xFF93C5FD))
            Text(
                senderName.ifBlank { "Unknown sender" },
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            if (chatTitle.isNotBlank()) {
                Text("Chat", style = MaterialTheme.typography.labelLarge, color = Color(0xFF93C5FD))
                Text(chatTitle, style = MaterialTheme.typography.titleMedium, color = Color(0xFFE5E7EB))
            }
        }
        Spacer(Modifier.height(14.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F172A))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Message", style = MaterialTheme.typography.labelLarge, color = Color(0xFFFCA5A5))
            Text(
                message.ifBlank { "No text preview" },
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFFE5E7EB)
            )
        }
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onStop, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Stop, null)
                Text("Stop")
            }
            Button(onClick = onMute, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.NotificationsOff, null)
                Text("Mute 1 min")
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onSnoozeFive, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.NotificationsOff, null)
                Text("Sleep 5 min")
            }
            Button(onClick = onSnoozeTen, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.NotificationsOff, null)
                Text("Sleep 10 min")
            }
        }
    }
}
