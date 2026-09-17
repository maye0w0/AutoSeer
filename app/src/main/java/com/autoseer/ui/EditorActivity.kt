package com.autoseer.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.autoseer.R
import com.autoseer.core.SeerPrefs
import com.autoseer.databinding.ActivityEditorBinding
import com.autoseer.scripts.BattlePlanParser

/**
 * Visual skill-plan editor: tap the skill regions (1..5) on a mock battle screen
 * to build each stage's per-turn cast order, switch stages, and save. Writes the
 * same plan-string format used by the text field and [BattlePlanParser].
 */
class EditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditorBinding

    // stages[stageIndex] = ordered list of skill slots, one per turn
    private val stages = mutableListOf<MutableList<Int>>()
    private var current = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadFromPrefs()

        binding.editorView.onTap = { code ->
            stages[current].add(code)
            render()
        }
        binding.btnMode.setOnClickListener { toggleMode() }
        binding.btnPrevStage.setOnClickListener {
            if (current > 0) { current--; render() }
        }
        binding.btnNextStage.setOnClickListener {
            current++
            if (current >= stages.size) stages.add(mutableListOf())
            render()
        }
        binding.btnUndo.setOnClickListener {
            stages[current].removeLastOrNull(); render()
        }
        binding.btnClear.setOnClickListener {
            stages[current].clear(); render()
        }
        binding.btnDone.setOnClickListener { save(); finish() }

        render()
    }

    private fun toggleMode() {
        val toPet = binding.editorView.mode == BattleEditorView.Mode.SKILL
        binding.editorView.mode = if (toPet) BattleEditorView.Mode.PET else BattleEditorView.Mode.SKILL
        binding.btnMode.setText(if (toPet) R.string.editor_to_skill else R.string.editor_to_pet)
        binding.editorHint.setText(if (toPet) R.string.editor_hint_pet else R.string.editor_hint)
    }

    private fun loadFromPrefs() {
        val loaded = BattlePlanParser.toStages(SeerPrefs.planText(this))
        stages.clear()
        loaded.forEach { stages.add(it.toMutableList()) }
        if (stages.isEmpty()) stages.add(mutableListOf())
        current = 0
    }

    private fun render() {
        binding.stageLabel.text = "關卡 ${current + 1}／${stages.size}"
        val turns = stages[current]
        binding.sequenceLabel.text = if (turns.isEmpty()) {
            getString(R.string.seq_empty)
        } else {
            turns.mapIndexed { i, s -> "回合${i + 1}:${com.autoseer.scripts.SeerLayout.labelFor(s)}" }
                .joinToString("  →  ")
        }
    }

    private fun save() {
        val trimmed = stages.dropLastWhile { it.isEmpty() }
        val text = BattlePlanParser.serialize(trimmed)
        SeerPrefs.save(
            ctx = this,
            planText = text,
            maxBattles = SeerPrefs.maxBattles(this),
            healBeforeBattle = SeerPrefs.healBeforeBattle(this),
            advanceMap = SeerPrefs.advanceMap(this),
            defaultSlot = SeerPrefs.defaultSlot(this),
            startStage = SeerPrefs.startStage(this),
            maxRetries = SeerPrefs.maxRetries(this),
        )
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
    }
}
