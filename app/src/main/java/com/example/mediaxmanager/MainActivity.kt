package com.example.mediaxmanager

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.mediaxmanager.media.MediaViewModel
import com.example.mediaxmanager.ui.MediaScreen
import com.example.mediaxmanager.ui.theme.MediaControllerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MediaViewModel by viewModels()

    // Register the permission launcher before onCreate
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Permissions resolved — UI is already shown, tracks will load on next
        // LaunchedEffect cycle in SearchScreen now that permission is granted.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        applyFullscreenSetting()

        // Request storage permissions before anything else.
        // On Android 13+ we need READ_MEDIA_AUDIO.
        // On Android 12 and below we need READ_EXTERNAL_STORAGE.
        requestStoragePermissionsIfNeeded()

        if (!isNotificationListenerEnabled()) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        setContent {
            MediaControllerTheme {
                MediaScreen(viewModel)
            }
        }
    }

    private fun requestStoragePermissionsIfNeeded() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.reconnect()
        applyFullscreenSetting()
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