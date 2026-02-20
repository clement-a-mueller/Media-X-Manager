package com.example.mediaxmanager

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.mediaxmanager.media.MediaViewModel
import com.example.mediaxmanager.ui.MediaScreen
import com.example.mediaxmanager.ui.theme.MediaControllerTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MediaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        applyFullscreenSetting()

        if (!isNotificationListenerEnabled()) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        setContent {
            MediaControllerTheme {
                MediaScreen(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.reconnect()
        applyFullscreenSetting()  // re-apply when returning from settings
    }

    private fun applyFullscreenSetting() {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val fullscreenEnabled = prefs.getBoolean("fullscreen", true)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (fullscreenEnabled) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = 2
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(packageName) == true
    }
}