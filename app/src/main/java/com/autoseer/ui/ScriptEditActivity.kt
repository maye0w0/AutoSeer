package com.autoseer.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.autoseer.R
import com.autoseer.core.ScriptStore
import com.autoseer.core.SeerScript
import com.autoseer.databinding.ActivityScriptEditBinding
import com.autoseer.scripts.BattlePlanParser
import com.autoseer.scripts.SeerLayout

/**
 * Edits a single [SeerScript]: name, note, skill order (via the visual
 * [EditorActivity]), optional per-slot display names, and run options. Reached
 * from [ScriptListActivity] by tapping a script or the add button.
 */
class ScriptEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScriptEditBinding

    private lateinit var script: SeerScript
    private var planText: String = ""
    private val skillNameFields = mutableListOf<EditText>()

    private val editorLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.getStringExtra(EditorActivity.RESULT_PLAN)?.let {
                    planText = it
                    refreshSummary()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScriptEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.title = getString(R.string.script_edit_title)

        val id = intent.getStringExtra(EXTRA_ID)
        script = id?.let { ScriptStore.get(this, it) } ?: SeerScript.new(getString(R.string.script_new_name))
        planText = script.planText

        buildSkillNameFields()
        bindToUi()

        binding.btnOpenEditor.setOnClickListener {
            editorLauncher.launch(EditorActivity.intent(this, planText, currentSkillNames()))
        }
        binding.btnSaveScript.setOnClickListener {
            persist(); Toast.makeText(this, R.string.script_saved, Toast.LENGTH_SHORT).show(); finish()
        }
        binding.btnSetActive.setOnClickListener {
            val saved = persist()
            ScriptStore.setSelected(this, saved.id)
            Toast.makeText(this, R.string.script_activated, Toast.LENGTH_SHORT).show()
            finish()
        }
        binding.btnDeleteScript.setOnClickListener {
            ScriptStore.delete(this, script.id)
            Toast.makeText(this, R.string.script_deleted, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun buildSkillNameFields() {
        binding.skillNamesContainer.removeAllViews()
        skillNameFields.clear()
        for (i in 1..SeerLayout.SKILL_COUNT) {
            val field = EditText(this).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                inputType = InputType.TYPE_CLASS_TEXT
                hint = getString(R.string.script_skill_name_hint, i)
                setHintTextColor(getColor(R.color.text_secondary))
                setTextColor(getColor(R.color.white))
                setText(script.skillNames.getOrNull(i - 1) ?: "")
            }
            skillNameFields += field
            binding.skillNamesContainer.addView(field)
        }
    }

    private fun bindToUi() {
        binding.editName.setText(script.name)
        binding.editNote.setText(script.note)
        binding.editMaxBattles.setText(script.maxBattles.toString())
        binding.editStartStage.setText(script.startStage.toString())
        binding.editMaxRetries.setText(script.maxRetries.toString())
        binding.editDefaultSlot.setText(script.defaultSlot.toString())
        binding.healSwitch.isChecked = script.healBeforeBattle
        binding.advanceSwitch.isChecked = script.advanceMap
        refreshSummary()
    }

    private fun currentSkillNames(): List<String> = skillNameFields.map { it.text.toString().trim() }

    private fun refreshSummary() {
        binding.skillOrderSummary.text = summarize(planText, currentSkillNames())
    }

    /** Human-readable per-stage summary using custom skill names when present. */
    private fun summarize(plan: String, names: List<String>): String {
        val stages = BattlePlanParser.toStepStages(plan)
        if (stages.isEmpty()) return getString(R.string.skill_not_recorded)
        return stages.mapIndexed { i, steps ->
            val body = steps.joinToString(" ") { st ->
                labelFor(st.code, names) + if (st.untilDefeat) "*" else ""
            }
            "關${i + 1}: $body"
        }.joinToString("   |   ")
    }

    private fun labelFor(code: Int, names: List<String>): String {
        if (code in 1..SeerLayout.SKILL_COUNT) {
            names.getOrNull(code - 1)?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return SeerLayout.labelFor(code)
    }

    private fun persist(): SeerScript {
        val updated = script.copy(
            name = binding.editName.text.toString().trim(),
            note = binding.editNote.text.toString().trim(),
            planText = planText,
            maxBattles = binding.editMaxBattles.text.toString().toIntOrNull() ?: 30,
            healBeforeBattle = binding.healSwitch.isChecked,
            advanceMap = binding.advanceSwitch.isChecked,
            defaultSlot = (binding.editDefaultSlot.text.toString().toIntOrNull() ?: 2).coerceIn(1, SeerLayout.SKILL_COUNT),
            startStage = (binding.editStartStage.text.toString().toIntOrNull() ?: 1).coerceAtLeast(1),
            maxRetries = (binding.editMaxRetries.text.toString().toIntOrNull() ?: 5).coerceAtLeast(0),
            skillNames = currentSkillNames(),
        )
        ScriptStore.upsert(this, updated)
        script = updated
        return updated
    }

    companion object {
        const val EXTRA_ID = "extra_id"

        /** Open the editor for an existing script, or pass null id for a new one. */
        fun intent(ctx: Context, id: String?): Intent =
            Intent(ctx, ScriptEditActivity::class.java).apply {
                if (id != null) putExtra(EXTRA_ID, id)
            }
    }
}
