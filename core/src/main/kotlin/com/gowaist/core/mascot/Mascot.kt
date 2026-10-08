package com.gowaist.core.mascot

import com.gowaist.core.model.PlanDayStatus
import com.gowaist.core.model.PlanDayType

enum class MascotMood { HAPPY, PROUD, CHEER, SLEEPY, SAD, CALM }

/** Situation the mascot comments on; the app maps each to a set of localized lines. */
enum class MascotLine(val mood: MascotMood) {
    WELCOME(MascotMood.HAPPY),
    NEW_PB(MascotMood.PROUD),
    TODAY_DONE(MascotMood.PROUD),
    WEEK_COMPLETE(MascotMood.PROUD),
    STREAK(MascotMood.CHEER),
    TODAY_RUN(MascotMood.CHEER),
    TODAY_TRAIN(MascotMood.CHEER),
    REST_DAY(MascotMood.CALM),
    MISSED_SOME(MascotMood.SAD),
    LONG_BREAK(MascotMood.SLEEPY),
    DEFAULT(MascotMood.HAPPY),
}

data class MascotContext(
    val hasAnyActivity: Boolean,
    val daysSinceLastActivity: Int?,
    val pbWithinDays: Int?,
    val todayPlan: PlanDayType?,
    val todayStatus: PlanDayStatus?,
    val weekPlanned: Int,
    val weekDone: Int,
    val streak: Int,
    val missedThisWeek: Int,
)

object MascotBrain {
    fun line(c: MascotContext): MascotLine = when {
        !c.hasAnyActivity -> MascotLine.WELCOME
        c.pbWithinDays != null && c.pbWithinDays <= 2 -> MascotLine.NEW_PB
        c.weekPlanned > 0 && c.weekDone >= c.weekPlanned -> MascotLine.WEEK_COMPLETE
        c.todayStatus == PlanDayStatus.DONE || c.todayStatus == PlanDayStatus.OVER -> MascotLine.TODAY_DONE
        (c.daysSinceLastActivity ?: 0) >= 5 -> MascotLine.LONG_BREAK
        c.missedThisWeek >= 2 -> MascotLine.MISSED_SOME
        c.todayPlan == PlanDayType.REST -> MascotLine.REST_DAY
        c.todayPlan?.isRun == true -> MascotLine.TODAY_RUN
        c.todayPlan?.isBodyweight == true -> MascotLine.TODAY_TRAIN
        c.streak >= 3 -> MascotLine.STREAK
        else -> MascotLine.DEFAULT
    }
}
