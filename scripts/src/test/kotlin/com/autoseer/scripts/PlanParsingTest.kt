package com.autoseer.scripts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanParsingTest {

    private val groups = mapOf(
        "尤莉米特" to
            "1 ; 3,1 ; 3,1 ; Switch 4,Switch 1,5,1*N ; " +
            "Switch 2,1*N(避免因為敵方未出招),Switch 3,4*N,Switch 1,1*N,Switch 5,1*N,Switch 6,2*N",
        "萬里春北鳥" to
            "3,1*N ; 3,1*N ; 4*N ; Switch 2,1*N,Switch 3,4*N,Switch 1,1*N ; " +
            "Switch 2,1*N,Switch 3,4*N,Switch 1,1*N,Switch 5,1*N,Switch 6,2*N",
        "靈巢之主索傑爾德" to
            "3,1 ; 3,1 ; 3,1 ; 4,4 ; " +
            "Switch 2,1*N,Switch 3,4*N,Switch 1,1*N,Switch 5,1*N,Switch 6,2*N",
    )

    @Test
    fun parsesAllGroupsWithoutWarnings() {
        for ((name, text) in groups) {
            val r = BattlePlanParser.parse(text)
            println("== $name ==")
            println(BattlePlanParser.describe(r.plan))
            if (r.warnings.isNotEmpty()) println("  警告: ${r.warnings}")
            assertEquals("$name 應為 5 關", 5, r.plan.stages.size)
            assertTrue("$name 不應有解析警告: ${r.warnings}", r.warnings.isEmpty())
        }
    }

    @Test
    fun switchAndUntilDefeatDecodeCorrectly() {
        val r = BattlePlanParser.parse("Switch 2,1*N,Switch 3")
        val steps = r.plan.stages[0].steps
        assertEquals(3, steps.size)
        assertTrue(steps[0].isSwitch); assertEquals(2, steps[0].petIndex)
        assertEquals(1, steps[1].code); assertTrue(steps[1].untilDefeat)
        assertTrue(steps[2].isSwitch); assertEquals(3, steps[2].petIndex)
    }

    @Test
    fun serializeRoundTripsCodes() {
        // editor codes: skill 3, switch pet 2 (code 12), skill 1
        val text = BattlePlanParser.serialize(listOf(listOf(3, 12, 1)))
        assertEquals("3, Switch 2, 1", text)
        val back = BattlePlanParser.toStages(text)
        assertEquals(listOf(listOf(3, 12, 1)), back)
    }
}
