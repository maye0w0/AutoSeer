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
    const val RESULT_WIN = "state/result_win"         // 戰鬥結束頂端「勝利」菱形
    const val RESULT_LOSE = "state/result_lose"       // 戰鬥結束頂端「失敗」菱形
    const val TAP_CONTINUE = "state/tap_continue"     // 「點擊任意位置繼續」字樣（勝/敗皆有）
    const val RETREAT_TIP = "state/retreat_tip"       // 「你確定要撤退嗎?」對話框
    const val RETREAT_SUCCESS = "state/retreat_success" // 「恭喜你，成功撤退!」對話框
    const val CONTINUE_CHALLENGE = "stage/continue_challenge" // 失敗後關卡頁「繼續挑戰」鈕
    const val ENTER_BATTLE = "stage/enter_battle"     // 「進入戰鬥」按鈕
    const val WAIT_CHALLENGE = "stage/wait_challenge" // 可挑戰節點的「等待挑戰」標籤
    const val PET_DEFEATED = "state/pet_defeated"     // 換精靈介面某卡的「已戰敗」＝上場精靈陣亡
    const val OUT_OF_STAMINA = "state/out_of_stamina" // 「挑戰次數不足」彈窗（尚無樣板）

    // 精靈因子掃蕩流程（樣板自標註 PPT 裁出，1280x720 正規尺度）
    const val OPEN_CHALLENGE = "stage/open_challenge"     // 「開啟挑戰」按鈕
    const val PET_RECOVER = "seer/pet_recover"           // 「精靈恢復」鈕
    const val FIRST_RECOVER_TIP = "seer/first_recover_tip" // 每日首次恢復提示訊息
    const val RECOVER_FULL = "seer/recover_full"         // 「恭喜…已全部恢復」字樣
    const val RECOVER_CANNOT = "seer/recover_cannot"     // 「已滿，無法重新恢復」字樣
    const val DAILY_LIMIT = "state/daily_limit"          // 「達到每天操作上限」字樣
    const val QUICK_MENU = "lobby/quick_menu"            // 快速功能選單鈕（左上）
    const val HOME_BTN = "lobby/home"                    // 小房子（回大廳）
    const val NAV_GUIDE = "lobby/nav_guide"              // 航行指南鈕（已回大廳）

    /** All template ids the automation looks for (used by the detection probe). */
    val ALL = listOf(
        BATTLE_ACTION, RESULT_WIN, RESULT_LOSE, TAP_CONTINUE, ENTER_BATTLE, WAIT_CHALLENGE,
        PET_DEFEATED, OUT_OF_STAMINA,
        OPEN_CHALLENGE, PET_RECOVER, FIRST_RECOVER_TIP, RECOVER_FULL, RECOVER_CANNOT,
        RETREAT_TIP, RETREAT_SUCCESS, CONTINUE_CHALLENGE,
        DAILY_LIMIT, QUICK_MENU, HOME_BTN, NAV_GUIDE,
    )
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
    // 換精靈子畫面 6 張卡的「頭像」區（正規化 1280x720），center 即點卡拖出戰的著點，
    // 也是與 [FIELD_HEAD] 比對的目標。校準自 1600x900 實機影片幀：舊值(整張卡、寬175)
    // 的 center 系統性偏右 50~100px，點在名字/間隔上而非頭像 → 換人只有 30~40% 成功。
    // 仍屬影片目測，若某卡實機仍點偏，微調該列 x 即可。
    val PET_REGIONS = listOf(
        Region(4, 580, 64, 64),    // 精靈1（頭像中心 ≈36,612）
        Region(168, 580, 64, 64),  // 精靈2（≈200,612）
        Region(364, 580, 64, 64),  // 精靈3（≈396,612）
        Region(516, 580, 64, 64),  // 精靈4（≈548,612）
        Region(716, 580, 64, 64),  // 精靈5（≈748,612）
        Region(908, 580, 64, 64),  // 精靈6（≈940,612）
    )
    val PET_SLOTS: List<Location> = PET_REGIONS.map { it.center }
    val PET_COUNT = PET_SLOTS.size

    /** Base code for pet-switch actions: PET_CODE_BASE + n = 換精靈(n+1). */
    const val PET_CODE_BASE = 10  // codes 11..16
    /** Drag this many normalized px upward from a pet slot to deploy it (拖動出戰). */
    const val PET_DEPLOY_UP_PX = 340

    /**
     * 場上「當前上場精靈」頭像區域（畫面左上角，正規化 1280x720）。換人後裁此處，與換人
     * 前記下的目標卡頭像比對（診斷 log；未來校準門檻後可重啟嚴格驗證）。校準自實機影片幀。
     */
    val FIELD_HEAD = Region(8, 8, 68, 64)

    /** 換精靈子畫面第 [n] 張卡(1..6)的頭像區＝ [PET_REGIONS]（已是頭像區），供與 [FIELD_HEAD] 比對。 */
    fun petCardHead(n: Int): Region? = PET_REGIONS.getOrNull(n - 1)

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
