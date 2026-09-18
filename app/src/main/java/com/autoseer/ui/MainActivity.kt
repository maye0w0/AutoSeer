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
import com.autoseer.core.ScriptStore
import com.autoseer.core.SeerScript
import com.autoseer.databinding.ActivityMainBinding
import com.autoseer.input.GestureAccessibilityService
import com.autoseer.service.AutoSeerService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** Id of the script the home screen is quick-editing (the active one). */
    private var editingId: String? = null

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

    private val editorLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.getStringExtra(EditorActivity.RESULT_PLAN)?.let { plan ->
                    binding.planInput.setText(plan)
                    saveSettings()
                }
            }
        }

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
        binding.btnScripts.setOnClickListener {
            startActivity(Intent(this, ScriptListActivity::class.java))
        }
        binding.btnDelays.setOnClickListener {
            startActivity(Intent(this, DelaySettingsActivity::class.java))
        }
        binding.btnSave.setOnClickListener { saveSettings() }
        binding.btnEditor.setOnClickListener {
            editorLauncher.launch(
                EditorActivity.intent(this, binding.planInput.text.toString(), currentSkillNames()),
            )
        }
        binding.btnStart.setOnClickListener {
            saveSettings()
            ensureNotificationThenCapture()
        }
        binding.btnStop.setOnClickListener { startService(AutoSeerService.stopIntent(this)) }

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
        reloadSelected()
    }

    /** Load the active script from the store into the home's quick-edit fields. */
    private fun reloadSelected() {
        ScriptStore.ensureSeeded(this)
        val s = ScriptStore.selected(this) ?: return
        editingId = s.id
        binding.currentScript.text = getString(R.string.home_current, s.displayName)
        binding.planInput.setText(s.planText)
        binding.editMaxBattles.setText(s.maxBattles.toString())
        binding.editStartStage.setText(s.startStage.toString())
        binding.editMaxRetries.setText(s.maxRetries.toString())
        binding.editDefaultSlot.setText(s.defaultSlot.toString())
        binding.healSwitch.isChecked = s.healBeforeBattle
        binding.advanceSwitch.isChecked = s.advanceMap
    }

    private fun currentSkillNames(): List<String> =
        editingId?.let { ScriptStore.get(this, it)?.skillNames } ?: emptyList()

    private fun saveSettings() {
        val base = editingId?.let { ScriptStore.get(this, it) }
            ?: ScriptStore.selected(this)
            ?: SeerScript.new(getString(R.string.script_default_name))
        val updated = base.copy(
            planText = binding.planInput.text.toString(),
            maxBattles = binding.editMaxBattles.text.toString().toIntOrNull() ?: 30,
            healBeforeBattle = binding.healSwitch.isChecked,
            advanceMap = binding.advanceSwitch.isChecked,
            defaultSlot = (binding.editDefaultSlot.text.toString().toIntOrNull() ?: 2).coerceIn(1, 5),
            startStage = (binding.editStartStage.text.toString().toIntOrNull() ?: 1).coerceAtLeast(1),
            maxRetries = (binding.editMaxRetries.text.toString().toIntOrNull() ?: 5).coerceAtLeast(0),
        )
        ScriptStore.upsert(this, updated)
        ScriptStore.setSelected(this, updated.id)
        editingId = updated.id
        binding.currentScript.text = getString(R.string.home_current, updated.displayName)
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
