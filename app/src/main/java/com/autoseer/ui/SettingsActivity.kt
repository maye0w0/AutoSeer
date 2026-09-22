package com.autoseer.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.autoseer.R
import com.autoseer.core.RunPrefs
import com.autoseer.databinding.ActivitySettingsBinding

/**
 * 更多設定: global execution options ([RunPrefs] — 循環輪數／預設技能格／戰前恢復)
 * plus a link into the global delay settings ([DelaySettingsActivity]).
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = getString(R.string.settings_title)

        load()
        binding.btnDelays.setOnClickListener {
            startActivity(Intent(this, DelaySettingsActivity::class.java))
        }
        binding.btnSaveSettings.setOnClickListener { save(); finish() }
    }

    private fun load() {
        binding.editLoops.setText(RunPrefs.loops(this).toString())
        binding.editDefaultSlot.setText(RunPrefs.defaultSlot(this).toString())
        binding.healSwitch.isChecked = RunPrefs.healBeforeBattle(this)
    }

    private fun save() {
        RunPrefs.save(
            ctx = this,
            loops = binding.editLoops.text.toString().toIntOrNull() ?: 3,
            defaultSlot = binding.editDefaultSlot.text.toString().toIntOrNull() ?: 2,
            healBeforeBattle = binding.healSwitch.isChecked,
        )
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
    }
}
