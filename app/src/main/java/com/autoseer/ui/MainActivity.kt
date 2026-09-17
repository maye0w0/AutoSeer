package com.autoseer.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.autoseer.R
import com.autoseer.core.SeerPrefs
import com.autoseer.databinding.ActivityMainBinding
import com.autoseer.input.GestureAccessibilityService
import com.autoseer.service.AutoSeerService

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

        binding.btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.btnOverlay.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
        binding.btnSave.setOnClickListener { saveSettings() }
        binding.btnEditor.setOnClickListener {
            startActivity(Intent(this, EditorActivity::class.java))
        }
        binding.btnStart.setOnClickListener {
            saveSettings()
            ensureNotificationThenCapture()
        }
        binding.btnStop.setOnClickListener { startService(AutoSeerService.stopIntent(this)) }

        loadSettings()
        buildPresetButtons()
    }

    private fun buildPresetButtons() {
        binding.presetContainer.removeAllViews()
        for (preset in com.autoseer.scripts.SeerPresets.ALL) {
            val btn = com.google.android.material.button.MaterialButton(
                this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle,
            ).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                text = "載入：${preset.name}（5關）"
                setOnClickListener {
                    binding.planInput.setText(preset.plan)
                    saveSettings()
                    Toast.makeText(this@MainActivity, R.string.preset_loaded, Toast.LENGTH_SHORT).show()
                }
            }
            binding.presetContainer.addView(btn)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        // Reflect any plan changes made in the visual editor.
        binding.planInput.setText(SeerPrefs.planText(this))
    }

    private fun loadSettings() {
        binding.planInput.setText(SeerPrefs.planText(this))
        binding.editMaxBattles.setText(SeerPrefs.maxBattles(this).toString())
        binding.editStartStage.setText(SeerPrefs.startStage(this).toString())
        binding.editMaxRetries.setText(SeerPrefs.maxRetries(this).toString())
        binding.editDefaultSlot.setText(SeerPrefs.defaultSlot(this).toString())
        binding.healSwitch.isChecked = SeerPrefs.healBeforeBattle(this)
        binding.advanceSwitch.isChecked = SeerPrefs.advanceMap(this)
    }

    private fun saveSettings() {
        val max = binding.editMaxBattles.text.toString().toIntOrNull() ?: 30
        val slot = (binding.editDefaultSlot.text.toString().toIntOrNull() ?: 2).coerceIn(1, 5)
        val start = (binding.editStartStage.text.toString().toIntOrNull() ?: 1).coerceAtLeast(1)
        val retries = (binding.editMaxRetries.text.toString().toIntOrNull() ?: 5).coerceAtLeast(0)
        SeerPrefs.save(
            ctx = this,
            planText = binding.planInput.text.toString(),
            maxBattles = max,
            healBeforeBattle = binding.healSwitch.isChecked,
            advanceMap = binding.advanceSwitch.isChecked,
            defaultSlot = slot,
            startStage = start,
            maxRetries = retries,
        )
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
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
