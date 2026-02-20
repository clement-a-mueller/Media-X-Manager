package com.example.mediaxmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.mediaxmanager.media.MediaViewModel
import com.example.mediaxmanager.ui.MediaScreen
import com.example.mediaxmanager.ui.theme.MediaControllerTheme
import android.provider.Settings
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import android.view.WindowManager

class MainActivity : ComponentActivity() {
    private val viewModel: MediaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // ← add this

        if (!isNotificationListenerEnabled()) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        setContent {
            MediaControllerTheme {
                MediaScreen(viewModel)
            }
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(packageName) == true
    }

    override fun onResume() {
        super.onResume()
        viewModel.reconnect()
    }
}