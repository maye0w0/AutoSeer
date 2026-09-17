package com.autoseer.scripts

/** Ready-made stage plans (5 stages each) the user authored per stage group. */
object SeerPresets {
    data class Preset(val name: String, val plan: String)

    val ALL = listOf(
        Preset(
            "尤莉米特",
            "1 ; 3,1 ; 3,1 ; Switch 4,Switch 1,5,1*N ; " +
                "Switch 2,1*N,Switch 3,4*N,Switch 1,1*N,Switch 5,1*N,Switch 6,2*N",
        ),
        Preset(
            "萬里春北鳥",
            "3,1*N ; 3,1*N ; 4*N ; Switch 2,1*N,Switch 3,4*N,Switch 1,1*N ; " +
                "Switch 2,1*N,Switch 3,4*N,Switch 1,1*N,Switch 5,1*N,Switch 6,2*N",
        ),
        Preset(
            "靈巢之主索傑爾德",
            "3,1 ; 3,1 ; 3,1*N ; 4,4*N ; " +
                "Switch 2,1*N,Switch 3,4*N,Switch 1,1*N,Switch 5,1*N,Switch 6,2*N",
        ),
    )
}
