package com.autoseer.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.autoseer.R
import com.autoseer.databinding.ActivityMainBinding
import com.autoseer.input.GestureAccessibilityService
import com.autoseer.service.AutoSeerService

/**
 * FGA-style home: entry buttons (周回腳本／更多設定／更多選項／Debug), an
 * authorization-status card (無障礙／懸浮), and a start/stop bar. Script content,
 * run options and delays all live behind their own pages now.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == RESULT_OK && data != null) {
                ContextCompat.startForegroundService(
                    this,
                    AutoSeerService.startIntent(this, result.resultCode, data),
                )
            }
        }

    private val notificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { requestCapture() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnMissions.setOnClickListener {
            startActivity(Intent(this, MissionListActivity::class.java))
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnMore.setOnClickListener {
            startActivity(Intent(this, MoreActivity::class.java))
        }
        binding.btnDebug.setOnClickListener {
            startActivity(Intent(this, DebugActivity::class.java))
        }

        binding.btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.btnOverlay.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }

        binding.btnStart.setOnClickListener { ensureNotificationThenCapture() }
        binding.btnStop.setOnClickListener { startService(AutoSeerService.stopIntent(this)) }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val accOn = GestureAccessibilityService.isConnected
        binding.statusAccessibility.text =
            getString(if (accOn) R.string.status_accessibility_on else R.string.status_accessibility_off)
        val overlayOn = Settings.canDrawOverlays(this)
        binding.statusOverlay.text =
            getString(if (overlayOn) R.string.status_overlay_on else R.string.status_overlay_off)
    }

    private fun ensureNotificationThenCapture() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requestCapture()
        }
    }

    private fun requestCapture() {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }
}
