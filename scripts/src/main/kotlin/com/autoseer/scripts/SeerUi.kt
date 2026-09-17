package com.autoseer.scripts

import com.autoseer.libautomata.Location
import com.autoseer.libautomata.Region

/**
 * Template asset ids (files under app/src/main/assets/images/<id>.png), captured
 * from 《賽爾號：巔峰之戰》. Ids without a captured asset yet are detected as absent
 * by [com.autoseer.libautomata.Templates.has] and simply skipped.
 */
object SeerTemplates {
    const val BATTLE_ACTION = "state/battle_action"   // 「你的回合」
    const val RESULT_WIN = "state/result_win"         // 「勝利」
    const val ENTER_BATTLE = "stage/enter_battle"     // 「進入戰鬥」按鈕
    const val WAIT_CHALLENGE = "stage/wait_challenge" // 可挑戰節點的「等待挑戰」標籤
    const val PET_DEFEATED = "state/pet_defeated"     // 換精靈介面某卡的「已戰敗」＝上場精靈陣亡
    const val OUT_OF_STAMINA = "state/out_of_stamina" // 「挑戰次數不足」彈窗（尚無樣板）
}

/**
 * Battle-screen regions and tap points in the normalized 1280x720 space,
 * measured from the user's UI design (UI/戰鬥UI/戰鬥UI設計OG&1.pptx). Converted
 * to device pixels automatically by AutomataApi.
 */
object SeerLayout {
    /**
     * The five skill button regions, numbered 1..5 left-to-right:
     *   1 = 圓形招牌技（圓鈕）, 2..5 = 四個矩形技能（由左至右）. Index = slot - 1.
     */
    val SKILL_REGIONS = listOf(
        Region(43, 550, 139, 139),  // 1 圓形招牌技
        Region(218, 593, 191, 96),  // 2 矩形1（最左）
        Region(422, 593, 191, 96),  // 3 矩形2
        Region(627, 593, 191, 96),  // 4 矩形3
        Region(831, 593, 191, 96),  // 5 矩形4（最右）
    )
    val SKILL_SLOTS: List<Location> = SKILL_REGIONS.map { it.center }
    val SKILL_COUNT = SKILL_SLOTS.size

    /** Right-side action buttons on the battle screen (codes 6..9). */
    val FIGHT = Region(1062, 560, 90, 67)   // 6 戰鬥
    val ITEM = Region(1158, 560, 90, 67)    // 7 道具
    val PET = Region(1062, 626, 90, 67)     // 8 精靈
    val RETREAT = Region(1158, 626, 90, 67) // 9 撤退

    val ACTION_REGIONS = listOf(FIGHT, ITEM, PET, RETREAT)
    val ACTION_NAMES = listOf("戰鬥", "道具", "精靈", "撤退")

    /**
     * Simple tap actions, indexed by (code - 1):
     *   codes 1..5 = skills, codes 6..9 = 戰鬥/道具/精靈/撤退.
     */
    val TAP_POINTS: List<Location> = SKILL_SLOTS + ACTION_REGIONS.map { it.center }
    val TAP_COUNT = TAP_POINTS.size  // 9

    /**
     * Pet-switch slots on the 換精靈 sub-screen (opened by tapping 精靈), 精靈1..6
     * left-to-right (codes 11..16). Switch = tap 精靈 → tap slot → drag up to deploy.
     */
    // Measured from the real 換精靈 screen (cards along the bottom, center y≈630).
    val PET_REGIONS = listOf(
        Region(10, 575, 175, 110),  // 精靈1
        Region(192, 575, 175, 110), // 精靈2
        Region(377, 575, 175, 110), // 精靈3
        Region(562, 575, 175, 110), // 精靈4
        Region(747, 575, 175, 110), // 精靈5
        Region(925, 575, 130, 110), // 精靈6（右側較窄，避開動作鈕）
    )
    val PET_SLOTS: List<Location> = PET_REGIONS.map { it.center }
    val PET_COUNT = PET_SLOTS.size

    /** Base code for pet-switch actions: PET_CODE_BASE + n = 換精靈(n+1). */
    const val PET_CODE_BASE = 10  // codes 11..16
    /** Drag this many normalized px upward from a pet slot to deploy it (拖動出戰). */
    const val PET_DEPLOY_UP_PX = 340

    /** Tap point for a simple code (1..9), or null. Pet codes handled separately. */
    fun pointFor(code: Int): Location? = TAP_POINTS.getOrNull(code - 1)

    fun isPetCode(code: Int): Boolean = code in (PET_CODE_BASE + 1)..(PET_CODE_BASE + PET_COUNT)
    fun isValidCode(code: Int): Boolean = code in 1..TAP_COUNT || isPetCode(code)

    /** Short label for a code: "1".."5" skills, action name, or "精靈N". */
    fun labelFor(code: Int): String = when {
        code in 1..SKILL_COUNT -> code.toString()
        code in (SKILL_COUNT + 1)..TAP_COUNT -> ACTION_NAMES[code - SKILL_COUNT - 1]
        isPetCode(code) -> "精靈${code - PET_CODE_BASE}"
        else -> "?"
    }

    /** 「精靈恢復」按鈕（在關卡畫面，進入戰鬥前必按）。 */
    val HEAL = Location(945, 560)

    /**
     * Where to tap to dismiss the 勝利/失敗 result screen. Both say 「點擊任意位置繼續」
     * but only the empty (non-image) area on the right responds; this point sits in
     * that blank region and clears of the 失敗 upgrade icons (y≈450).
     */
    val VICTORY_CONTINUE = Location(1025, 350)

    /** 「你確定要撤退嗎?」對話框的「確認」按鈕。 */
    val RETREAT_CONFIRM = Location(720, 485)

    /** 「恭喜你，成功撤退!」對話框的「確認」按鈕（撤退後多一層確認）。 */
    val RETREAT_SUCCESS_CONFIRM = Location(640, 485)

    /** 撤退/失敗後，關卡地圖右下角的「繼續挑戰」（直達當前關卡前置準備）。 */
    val CONTINUE_CHALLENGE = Location(1168, 622)

    /** The challengeable node icon sits this many px above its 「等待挑戰」 label. */
    const val NODE_ABOVE_LABEL_PX = 52
}
