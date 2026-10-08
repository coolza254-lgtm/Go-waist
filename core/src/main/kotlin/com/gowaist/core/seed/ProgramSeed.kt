package com.gowaist.core.seed

import com.gowaist.core.model.PlanDayType
import com.gowaist.core.model.PlanDayType.BW_CORE
import com.gowaist.core.model.PlanDayType.BW_FULL
import com.gowaist.core.model.PlanDayType.BW_LEGS
import com.gowaist.core.model.PlanDayType.BW_LOWER
import com.gowaist.core.model.PlanDayType.BW_PULL
import com.gowaist.core.model.PlanDayType.BW_PUSH
import com.gowaist.core.model.PlanDayType.BW_UPPER
import com.gowaist.core.model.PlanDayType.REST
import com.gowaist.core.model.PlanDayType.RUN_EASY
import com.gowaist.core.model.PlanDayType.RUN_INTERVAL
import com.gowaist.core.model.PlanDayType.RUN_LONG
import com.gowaist.core.model.PlanDayType.RUN_TEMPO
import kotlin.math.round

// ------------------------------------------------------------------ progression chains

data class ChainStepDef(val exerciseKey: String, val sets: Int, val repsOrSec: Int, val sessions: Int = 2)

data class ChainDef(val key: String, val nameTh: String, val steps: List<ChainStepDef>)

object ChainSeed {
    val all = listOf(
        ChainDef(
            "push", "สายวิดพื้น",
            listOf(
                ChainStepDef("wall_pushup", 3, 20), ChainStepDef("incline_pushup", 3, 15), ChainStepDef("knee_pushup", 3, 15),
                ChainStepDef("pushup", 3, 12), ChainStepDef("diamond_pushup", 3, 12), ChainStepDef("archer_pushup", 3, 8),
                ChainStepDef("one_arm_pushup", 3, 5),
            ),
        ),
        ChainDef(
            "squat", "สายสควอท",
            listOf(
                ChainStepDef("air_squat", 3, 20), ChainStepDef("split_squat", 3, 12), ChainStepDef("bulgarian_split_squat", 3, 12),
                ChainStepDef("assisted_pistol", 3, 8), ChainStepDef("pistol_squat", 3, 5),
            ),
        ),
        ChainDef(
            "pull", "สายดึงข้อ",
            listOf(
                ChainStepDef("inverted_row", 3, 12), ChainStepDef("chin_up", 3, 8), ChainStepDef("pull_up", 3, 8),
                ChainStepDef("archer_pullup", 3, 5), ChainStepDef("one_arm_pullup", 3, 3),
            ),
        ),
        ChainDef(
            "core", "สายแกนกลางลำตัว",
            listOf(
                ChainStepDef("plank", 3, 60), ChainStepDef("long_lever_plank", 3, 45), ChainStepDef("hollow_hold", 3, 40),
                ChainStepDef("tuck_l_sit", 3, 20), ChainStepDef("l_sit", 3, 20),
            ),
        ),
        ChainDef("dip", "สายดิป", listOf(ChainStepDef("bench_dip", 3, 15), ChainStepDef("parallel_dip", 3, 12))),
        ChainDef("hanging", "สายห้อยบาร์ยกขา", listOf(ChainStepDef("hanging_knee_raise", 3, 12), ChainStepDef("hanging_leg_raise", 3, 10))),
    )
}

// ------------------------------------------------------------------ workout templates

data class TemplateItemDef(
    val exerciseKey: String,
    val sets: Int,
    val repsOrSec: Int,
    val restSec: Int = 60,
    /** Items sharing a group number are done back-to-back as a superset / circuit. */
    val group: Int? = null,
)

data class TemplateDef(val key: String, val nameTh: String, val descriptionTh: String, val items: List<TemplateItemDef>)

object TemplateSeed {
    val all = listOf(
        TemplateDef(
            "full_body_beginner", "Full Body มือใหม่", "ทั้งตัว 6 ท่า ไม่ใช้อุปกรณ์ เหมาะเริ่มต้น 3 วัน/สัปดาห์",
            listOf(
                TemplateItemDef("incline_pushup", 3, 10), TemplateItemDef("air_squat", 3, 15), TemplateItemDef("prone_y_raise", 3, 12, 45),
                TemplateItemDef("glute_bridge", 3, 15), TemplateItemDef("plank", 3, 30, 45), TemplateItemDef("dead_bug", 2, 10, 45),
            ),
        ),
        TemplateDef(
            "push_day", "Push (ดัน)", "อก ไหล่ ไตรเซป",
            listOf(
                TemplateItemDef("pushup", 4, 10, 90), TemplateItemDef("pike_pushup", 3, 8, 90), TemplateItemDef("bench_dip", 3, 12),
                TemplateItemDef("diamond_pushup", 3, 8), TemplateItemDef("plank", 3, 45, 45),
            ),
        ),
        TemplateDef(
            "pull_day", "Pull (ดึง)", "หลังและไบเซป ต้องมีบาร์",
            listOf(
                TemplateItemDef("pull_up", 4, 6, 120), TemplateItemDef("inverted_row", 3, 10, 90), TemplateItemDef("chin_up", 3, 6, 120),
                TemplateItemDef("hanging_knee_raise", 3, 10), TemplateItemDef("dead_hang", 2, 30),
            ),
        ),
        TemplateDef(
            "legs_day", "Legs (ขา)", "ขา ก้น น่อง",
            listOf(
                TemplateItemDef("bulgarian_split_squat", 3, 10, 90), TemplateItemDef("jump_squat", 3, 12, 75), TemplateItemDef("lunge", 3, 12),
                TemplateItemDef("single_leg_glute_bridge", 3, 12), TemplateItemDef("calf_raise", 3, 20, 45), TemplateItemDef("wall_sit", 2, 45),
            ),
        ),
        TemplateDef(
            "upper_day", "Upper (ร่างกายส่วนบน)", "ดัน + ดึง ส่วนบน",
            listOf(
                TemplateItemDef("pushup", 3, 12, 90), TemplateItemDef("inverted_row", 3, 10, 90), TemplateItemDef("pike_pushup", 3, 8, 90),
                TemplateItemDef("chin_up", 3, 6, 120), TemplateItemDef("bench_dip", 3, 10),
            ),
        ),
        TemplateDef(
            "lower_day", "Lower (ร่างกายส่วนล่าง)", "ขาและแกนกลาง",
            listOf(
                TemplateItemDef("air_squat", 3, 20), TemplateItemDef("split_squat", 3, 10, 75), TemplateItemDef("glute_bridge", 3, 15),
                TemplateItemDef("calf_raise", 3, 20, 45), TemplateItemDef("leg_raise", 3, 12),
            ),
        ),
        TemplateDef(
            "core_15", "Core 15 นาที", "วงจรหน้าท้อง 5 ท่า ทำต่อเนื่องแล้วพักท้ายรอบ",
            listOf(
                TemplateItemDef("plank", 3, 40, 15, group = 1), TemplateItemDef("dead_bug", 3, 12, 15, group = 1),
                TemplateItemDef("russian_twist", 3, 20, 15, group = 1), TemplateItemDef("leg_raise", 3, 12, 15, group = 1),
                TemplateItemDef("side_plank", 3, 30, 60, group = 1),
            ),
        ),
        TemplateDef(
            "calisthenics_20", "Calisthenics ทั้งตัว 20 นาที", "วงจรทั้งตัวที่บ้าน ไม่ใช้อุปกรณ์ 3 รอบ",
            listOf(
                TemplateItemDef("burpee", 3, 8, 20, group = 1), TemplateItemDef("pushup", 3, 10, 20, group = 1),
                TemplateItemDef("jump_squat", 3, 12, 20, group = 1), TemplateItemDef("mountain_climber", 3, 30, 20, group = 1),
                TemplateItemDef("lunge", 3, 10, 20, group = 1), TemplateItemDef("plank", 3, 30, 90, group = 1),
            ),
        ),
    )
    val byKey = all.associateBy { it.key }
}

// ------------------------------------------------------------------ training plans

data class PlanDayDef(
    val type: PlanDayType,
    val distanceKm: Double? = null,
    val durationMin: Int? = null,
    val templateKey: String? = null,
    val note: String? = null,
)

data class PlanDef(
    val key: String,
    val nameTh: String,
    val descriptionTh: String,
    val level: String,
    /** weeks[w][d] where d = 0 (Monday) … 6 (Sunday). */
    val weeks: List<List<PlanDayDef>>,
)

object PlanSeed {
    private fun r(km: Double) = round(km * 2) / 2.0 // nearest 0.5 km
    private val rest = PlanDayDef(REST)
    private fun easy(km: Double) = PlanDayDef(RUN_EASY, distanceKm = r(km), note = "วิ่งสบายๆ พูดคุยได้")
    private fun long(km: Double) = PlanDayDef(RUN_LONG, distanceKm = r(km), note = "วิ่งยาวช้าๆ")
    private fun tempo(km: Double) = PlanDayDef(RUN_TEMPO, distanceKm = r(km), note = "วอร์ม 1 กม. เทมโปเร็วพอเหนื่อย แล้วคูลดาวน์")
    private fun interval(km: Double, note: String) = PlanDayDef(RUN_INTERVAL, distanceKm = r(km), note = note)
    private fun bw(type: PlanDayType, template: String) = PlanDayDef(type, durationMin = 30, templateKey = template)

    private fun fiveK(): List<List<PlanDayDef>> = (1..8).map { w ->
        val deload = if (w == 4) 0.85 else 1.0
        val walkRun = listOf(
            "วิ่ง 1 นาที เดิน 2 นาที × 8", "วิ่ง 2 นาที เดิน 2 นาที × 6", "วิ่ง 3 นาที เดิน 2 นาที × 5", "วิ่ง 3 นาที เดิน 1 นาที × 5",
            "วิ่ง 5 นาที เดิน 1 นาที × 4", "วิ่ง 8 นาที เดิน 1 นาที × 3", "วิ่ง 10 นาที เดิน 1 นาที × 2", "วิ่งเร็ว 400 ม. × 4",
        )[w - 1]
        if (w == 8) {
            listOf(rest, easy(3.0), rest, interval(3.0, walkRun), rest, PlanDayDef(RUN_EASY, 2.0, note = "วิ่งเบาๆ เตรียมวันจริง"), PlanDayDef(RUN_LONG, 5.0, note = "วันวัดผล 5K!"))
        } else {
            listOf(
                rest, easy((2.0 + 0.25 * (w - 1)) * deload), rest, interval((2.0 + 0.2 * (w - 1)) * deload, walkRun),
                rest, rest, long((3.0 + 0.3 * (w - 1)) * deload),
            )
        }
    }

    private fun tenK(): List<List<PlanDayDef>> = (1..10).map { w ->
        val deload = if (w == 4 || w == 8) 0.85 else 1.0
        if (w == 10) {
            listOf(rest, easy(5.0), rest, tempo(4.0), rest, easy(3.0), PlanDayDef(RUN_LONG, 10.0, note = "วันวัดผล 10K!"))
        } else {
            listOf(
                rest, easy((4.0 + 0.2 * w) * deload), rest, interval((4.0 + 0.2 * w) * deload, if (w % 2 == 0) "วิ่งเร็ว 800 ม. × ${3 + w / 3} พักเดิน 2 นาที" else "วิ่งเร็ว 400 ม. × ${5 + w / 2} พักเดิน 90 วิ"),
                easy((3.0 + 0.2 * w) * deload), rest, long((5.0 + 0.6 * w) * deload),
            )
        }
    }

    private fun half(): List<List<PlanDayDef>> = (1..12).map { w ->
        val deload = if (w == 4 || w == 8) 0.85 else 1.0
        val longKm = when (w) { 11 -> 14.0; 12 -> 21.1; else -> (10.0 + 1.0 * w) * deload }
        if (w == 12) {
            listOf(rest, easy(6.0), rest, tempo(5.0), rest, easy(3.0), PlanDayDef(RUN_LONG, 21.1, note = "วันวัดผลฮาล์ฟมาราธอน!"))
        } else {
            listOf(
                rest, easy((6.0 + 0.2 * w) * deload),
                interval((7.0 + 0.2 * w) * deload, "วิ่งเร็ว 1 กม. × ${3 + w / 3} พักจ็อก 2 นาที"),
                easy((5.0 + 0.2 * w) * deload), tempo((6.0 + 0.3 * w) * deload), rest, long(longKm),
            )
        }
    }

    private fun bwFull3(): List<List<PlanDayDef>> = (1..8).map {
        listOf(bw(BW_FULL, "full_body_beginner"), rest, bw(BW_FULL, "full_body_beginner"), rest, bw(BW_FULL, "full_body_beginner"), rest, rest)
    }

    private fun ppl6(): List<List<PlanDayDef>> = (1..8).map {
        listOf(
            bw(BW_PUSH, "push_day"), bw(BW_PULL, "pull_day"), bw(BW_LEGS, "legs_day"),
            bw(BW_PUSH, "push_day"), bw(BW_PULL, "pull_day"), bw(BW_LEGS, "legs_day"), rest,
        )
    }

    private fun combo(): List<List<PlanDayDef>> = (1..8).map { w ->
        val deload = if (w == 4) 0.85 else 1.0
        listOf(
            bw(BW_LOWER, "lower_day"), easy((3.0 + 0.3 * w) * deload), bw(BW_UPPER, "upper_day"),
            interval((3.0 + 0.3 * w) * deload, "วิ่งเร็ว 400 ม. × ${4 + w / 2} พักเดิน 90 วิ"), bw(BW_CORE, "core_15"), rest, long((5.0 + 0.5 * w) * deload),
        )
    }

    val all: List<PlanDef> = listOf(
        PlanDef("run_5k", "วิ่ง 5K มือใหม่", "8 สัปดาห์ วิ่ง 3 วัน/สัปดาห์ เริ่มจากวิ่งสลับเดิน", "มือใหม่", fiveK()),
        PlanDef("run_10k", "วิ่ง 10K", "10 สัปดาห์ วิ่ง 4 วัน/สัปดาห์ สำหรับคนที่วิ่ง 5K ได้แล้ว", "มือใหม่-กลาง", tenK()),
        PlanDef("run_half", "ฮาล์ฟมาราธอน", "12 สัปดาห์ วิ่ง 5 วัน/สัปดาห์ สำหรับคนที่วิ่ง 10K ได้สบาย", "กลาง", half()),
        PlanDef("bw_full3", "Bodyweight Full Body 3 วัน", "8 สัปดาห์ ฝึกทั้งตัว จันทร์/พุธ/ศุกร์", "มือใหม่", bwFull3()),
        PlanDef("bw_ppl6", "Bodyweight Push/Pull/Legs 6 วัน", "8 สัปดาห์ แยกส่วน 6 วัน พัก 1 วัน (ต้องมีบาร์)", "กลาง", ppl6()),
        PlanDef("combo", "วิ่ง + Bodyweight", "8 สัปดาห์ วิ่ง 3 วัน + Bodyweight 3 วัน จัดโหลดไม่ให้ชนกัน", "มือใหม่-กลาง", combo()),
    )
}
