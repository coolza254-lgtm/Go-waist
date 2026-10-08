package com.gowaist.core.seed

import com.gowaist.core.model.Equipment
import com.gowaist.core.model.Equipment.BAND
import com.gowaist.core.model.Equipment.BAR
import com.gowaist.core.model.Equipment.DIP_BARS
import com.gowaist.core.model.Equipment.NONE
import com.gowaist.core.model.Equipment.RINGS
import com.gowaist.core.model.MovementPattern
import com.gowaist.core.model.MovementPattern.CORE
import com.gowaist.core.model.MovementPattern.FULL_BODY
import com.gowaist.core.model.MovementPattern.LEGS
import com.gowaist.core.model.MovementPattern.PULL
import com.gowaist.core.model.MovementPattern.PUSH
import com.gowaist.core.model.MovementPattern.SKILL
import com.gowaist.core.model.Muscle
import com.gowaist.core.model.Muscle.ABS
import com.gowaist.core.model.Muscle.BACK
import com.gowaist.core.model.Muscle.BICEPS
import com.gowaist.core.model.Muscle.CALVES
import com.gowaist.core.model.Muscle.CHEST
import com.gowaist.core.model.Muscle.GLUTES
import com.gowaist.core.model.Muscle.LEGS as LEG
import com.gowaist.core.model.Muscle.LOWER_BACK
import com.gowaist.core.model.Muscle.OBLIQUES
import com.gowaist.core.model.Muscle.SHOULDERS
import com.gowaist.core.model.Muscle.TRICEPS
import com.gowaist.core.model.TrackingType
import com.gowaist.core.model.TrackingType.ASSISTED
import com.gowaist.core.model.TrackingType.CARDIO
import com.gowaist.core.model.TrackingType.HOLD
import com.gowaist.core.model.TrackingType.REPS
import com.gowaist.core.model.TrackingType.WEIGHTED

data class ExerciseDef(
    val key: String,
    val nameTh: String,
    val nameEn: String,
    val pattern: MovementPattern,
    val primary: Set<Muscle>,
    val secondary: Set<Muscle>,
    val tracking: TrackingType,
    val difficulty: Int,
    val equipment: Set<Equipment>,
    val factor: Double,
    val description: String,
    val caution: String,
    val restSec: Int = 90,
)

/** Built-in exercise library installed on first launch. Keys are stable and used by templates and backups. */
object ExerciseSeed {

    private fun ex(
        key: String, th: String, en: String, pattern: MovementPattern,
        primary: Set<Muscle>, secondary: Set<Muscle>, tracking: TrackingType, difficulty: Int,
        equipment: Set<Equipment> = setOf(NONE), factor: Double = 0.65, rest: Int = 90,
        desc: String, caution: String,
    ) = ExerciseDef(key, th, en, pattern, primary, secondary, tracking, difficulty, equipment, factor, desc, caution, rest)

    val all: List<ExerciseDef> = listOf(
        // ---------------- Push
        ex("wall_pushup", "วิดพื้นกับผนัง", "Wall push-up", PUSH, setOf(CHEST), setOf(TRICEPS, SHOULDERS), REPS, 1, factor = 0.2, rest = 60,
            desc = "ยืนห่างผนังประมาณหนึ่งช่วงแขน วางมือที่ผนังระดับอก งอศอกให้อกเข้าใกล้ผนังแล้วดันกลับ",
            caution = "เกร็งหน้าท้อง ลำตัวตรงเป็นแนวเดียว ไม่แอ่นหลัง"),
        ex("incline_pushup", "วิดพื้นแบบเอียง", "Incline push-up", PUSH, setOf(CHEST), setOf(TRICEPS, SHOULDERS), REPS, 1, factor = 0.45, rest = 60,
            desc = "วางมือบนโต๊ะ/เก้าอี้ที่มั่นคง ลำตัวเอียง ลดอกลงหาขอบแล้วดันขึ้น ยิ่งที่วางมือต่ำยิ่งยาก",
            caution = "ตรวจว่าที่วางมือไม่ลื่นหรือเลื่อน"),
        ex("knee_pushup", "วิดพื้นคุกเข่า", "Knee push-up", PUSH, setOf(CHEST), setOf(TRICEPS, SHOULDERS), REPS, 1, factor = 0.5, rest = 60,
            desc = "คุกเข่า ลำตัวตั้งแต่เข่าถึงศีรษะเป็นเส้นตรง ลดอกลงใกล้พื้นแล้วดันขึ้น",
            caution = "รองเข่าด้วยผ้าหรือเสื่อ ไม่ยกก้นสูง"),
        ex("pushup", "วิดพื้น", "Push-up", PUSH, setOf(CHEST), setOf(TRICEPS, SHOULDERS, ABS), REPS, 2, factor = 0.64,
            desc = "มือกว้างกว่าไหล่เล็กน้อย ลำตัวตรง ลดอกลงจนเกือบแตะพื้น ศอกทำมุมประมาณ 45° กับลำตัว แล้วดันขึ้น",
            caution = "ไม่กางศอกออกข้าง 90° เพื่อถนอมหัวไหล่ ไม่ให้สะโพกตก"),
        ex("wide_pushup", "วิดพื้นมือกว้าง", "Wide push-up", PUSH, setOf(CHEST), setOf(SHOULDERS, TRICEPS), REPS, 2, factor = 0.64,
            desc = "วางมือกว้างกว่าวิดพื้นปกติ เน้นกล้ามอกด้านนอก",
            caution = "ลดระยะลงถ้ารู้สึกตึงหรือเจ็บหัวไหล่"),
        ex("diamond_pushup", "วิดพื้นมือเพชร", "Diamond push-up", PUSH, setOf(TRICEPS, CHEST), setOf(SHOULDERS), REPS, 3, factor = 0.65,
            desc = "นิ้วโป้งและนิ้วชี้ชนกันเป็นรูปเพชรใต้หน้าอก ศอกแนบลำตัว",
            caution = "ถ้าเจ็บข้อมือให้แยกมือกว้างขึ้นเล็กน้อย"),
        ex("decline_pushup", "วิดพื้นเท้ายกสูง", "Decline push-up", PUSH, setOf(CHEST, SHOULDERS), setOf(TRICEPS), REPS, 3, factor = 0.72,
            desc = "วางเท้าบนเก้าอี้หรือเตียง มือบนพื้น ทำวิดพื้นตามปกติ เน้นอกส่วนบนและไหล่",
            caution = "เกร็งหน้าท้อง ไม่ปล่อยหลังแอ่น"),
        ex("pike_pushup", "วิดพื้นไพค์", "Pike push-up", PUSH, setOf(SHOULDERS), setOf(TRICEPS), REPS, 3, factor = 0.7,
            desc = "ยกสะโพกสูงเป็นรูปตัว V ลดศีรษะลงหาพื้นระหว่างมือแล้วดันขึ้น เตรียมสู่ Handstand push-up",
            caution = "ลงช้าๆ ควบคุมศีรษะไม่กระแทกพื้น"),
        ex("archer_pushup", "วิดพื้นอาร์เชอร์", "Archer push-up", PUSH, setOf(CHEST), setOf(TRICEPS, SHOULDERS), REPS, 4, factor = 0.78,
            desc = "มือกว้างมาก ลดตัวไปด้านหนึ่ง แขนอีกข้างเหยียดตรง สลับซ้ายขวา (นับข้างละครั้ง)",
            caution = "ต้องทำวิดพื้นปกติได้ 3×15 ก่อน"),
        ex("one_arm_pushup", "วิดพื้นแขนเดียว (ฝึกต่อยอด)", "One-arm push-up progression", PUSH, setOf(CHEST, TRICEPS), setOf(SHOULDERS, OBLIQUES), REPS, 5, factor = 0.85, rest = 120,
            desc = "กางขากว้างเพื่อทรงตัว มือหนึ่งไขว้หลัง ลดตัวลงด้วยแขนเดียว เริ่มจากแบบเอียงหรือช่วงสั้นก่อน",
            caution = "ห้ามบิดลำตัว หยุดถ้าเจ็บไหล่หรือข้อศอก"),
        ex("weighted_pushup", "วิดพื้นถ่วงน้ำหนัก", "Weighted push-up", PUSH, setOf(CHEST), setOf(TRICEPS, SHOULDERS), WEIGHTED, 3, factor = 0.64, rest = 120,
            desc = "ใส่เป้หรือเสื้อถ่วงน้ำหนักแล้ววิดพื้นตามปกติ ใช้เพิ่มความหนักเมื่อทำครั้งเยอะได้แล้ว",
            caution = "รัดเป้ให้แน่นไม่ให้ไถลไปที่คอ"),
        ex("bench_dip", "ดิปกับเก้าอี้", "Bench dip", PUSH, setOf(TRICEPS), setOf(SHOULDERS, CHEST), REPS, 2, factor = 0.5, rest = 60,
            desc = "มือจับขอบเก้าอี้ด้านหลัง ขาเหยียดหรืองอ ลดตัวลงจนศอกประมาณ 90° แล้วดันขึ้น",
            caution = "อย่าลงต่ำเกินไปจนหัวไหล่ตึง ใช้เก้าอี้ที่ไม่มีล้อ"),
        ex("parallel_dip", "ดิปบาร์คู่", "Parallel bar dip", PUSH, setOf(TRICEPS, CHEST), setOf(SHOULDERS), REPS, 4, setOf(DIP_BARS), factor = 0.95, rest = 120,
            desc = "พยุงตัวบนบาร์คู่ ลดตัวลงจนศอกประมาณ 90° โน้มตัวไปข้างหน้าเล็กน้อยแล้วดันขึ้น",
            caution = "เริ่มจากช่วงสั้นหากไหล่ยังไม่แข็งแรง"),
        ex("ring_pushup", "วิดพื้นบนห่วง", "Ring push-up", PUSH, setOf(CHEST), setOf(TRICEPS, SHOULDERS, ABS), REPS, 3, setOf(RINGS), factor = 0.64,
            desc = "วิดพื้นโดยจับห่วงที่ห้อยใกล้พื้น ต้องคุมห่วงไม่ให้แกว่ง",
            caution = "ปรับความสูงห่วงให้ง่ายก่อน"),
        // ---------------- Pull
        ex("prone_y_raise", "ยกแขนตัว Y นอนคว่ำ", "Prone Y raise", PULL, setOf(BACK), setOf(SHOULDERS, LOWER_BACK), REPS, 1, factor = 0.1, rest = 45,
            desc = "นอนคว่ำ แขนเหยียดเป็นรูปตัว Y ยกแขนขึ้นจากพื้นโดยบีบสะบัก ค้าง 1 วินาทีแล้ววางลง",
            caution = "ไม่เงยคอแรง มองพื้น"),
        ex("towel_row", "โรว์ผ้าขนหนูกับประตู", "Towel door row", PULL, setOf(BACK), setOf(BICEPS), REPS, 1, factor = 0.35, rest = 60,
            desc = "คล้องผ้าขนหนูกับลูกบิดประตูทั้งสองด้าน (ปิดประตูให้แน่น) เอนตัวไปด้านหลังแล้วดึงตัวเข้าหาประตู",
            caution = "ตรวจว่าประตูล็อกแน่นและผ้าแข็งแรง"),
        ex("band_row", "โรว์ยางยืด", "Band row", PULL, setOf(BACK), setOf(BICEPS), REPS, 1, setOf(BAND), factor = 0.0, rest = 60,
            desc = "คล้องยางกับเสาหรือเท้า ดึงยางเข้าหาลำตัว บีบสะบักเข้าหากัน",
            caution = "ตรวจยางว่าไม่มีรอยฉีกก่อนใช้"),
        ex("band_pull_apart", "ดึงยางแยกออก", "Band pull-apart", PULL, setOf(SHOULDERS, BACK), emptySet(), REPS, 1, setOf(BAND), factor = 0.0, rest = 45,
            desc = "จับยางระดับไหล่ แขนตรง ดึงยางแยกออกจนยางแตะอก",
            caution = "ไม่ยกไหล่ขึ้นหาหู"),
        ex("inverted_row", "โรว์ใต้บาร์ต่ำ", "Inverted row", PULL, setOf(BACK), setOf(BICEPS, SHOULDERS), REPS, 2, setOf(BAR), factor = 0.6,
            desc = "นอนใต้บาร์ต่ำหรือโต๊ะที่แข็งแรง ลำตัวตรง ดึงอกขึ้นหาบาร์แล้วลดลงช้าๆ",
            caution = "ถ้าใช้โต๊ะ ตรวจว่าโต๊ะไม่ล้มหรือพลิก"),
        ex("ring_row", "โรว์บนห่วง", "Ring row", PULL, setOf(BACK), setOf(BICEPS), REPS, 2, setOf(RINGS), factor = 0.6,
            desc = "จับห่วงแล้วเอนตัวไปด้านหลัง ดึงอกเข้าหาห่วง ยิ่งตัวขนานพื้นยิ่งยาก",
            caution = "เกร็งก้นและท้องให้ตัวตรง"),
        ex("scapula_pullup", "ดึงสะบักบนบาร์", "Scapular pull-up", PULL, setOf(BACK), setOf(SHOULDERS), REPS, 1, setOf(BAR), factor = 0.3, rest = 60,
            desc = "ห้อยบาร์แขนตรง ดึงสะบักลงและเข้าหากันให้ตัวยกขึ้นเล็กน้อยโดยไม่งอศอก",
            caution = "เคลื่อนไหวช้าๆ ไม่เหวี่ยงตัว"),
        ex("dead_hang", "ห้อยบาร์", "Dead hang", PULL, setOf(BACK), setOf(SHOULDERS), HOLD, 1, setOf(BAR), factor = 0.0, rest = 60,
            desc = "ห้อยตัวบนบาร์แขนตรง ไหล่ผ่อนคลายพอดี ฝึกแรงบีบมือ",
            caution = "ลงจากบาร์อย่างระมัดระวัง"),
        ex("negative_pullup", "ดึงข้อแบบลงช้า", "Negative pull-up", PULL, setOf(BACK), setOf(BICEPS), REPS, 2, setOf(BAR), factor = 1.0, rest = 120,
            desc = "กระโดดหรือใช้เก้าอี้ขึ้นไปอยู่ตำแหน่งคางพ้นบาร์ แล้วลดตัวลงช้าๆ 3–5 วินาที",
            caution = "อย่ากระโดดลงแรงจนกระแทกข้อ"),
        ex("band_assisted_pullup", "ดึงข้อใช้ยางช่วย", "Band-assisted pull-up", PULL, setOf(BACK), setOf(BICEPS), ASSISTED, 2, setOf(BAR, BAND), factor = 1.0, rest = 120,
            desc = "คล้องยางกับบาร์แล้ววางเข่าหรือเท้าบนยางเพื่อช่วยแรง บันทึกแรงช่วยเป็น kg (ประมาณจากสเปกยาง)",
            caution = "คล้องยางให้แน่น ระวังยางดีดกลับ"),
        ex("chin_up", "ชินอัพ (หงายมือ)", "Chin-up", PULL, setOf(BACK, BICEPS), setOf(SHOULDERS), REPS, 3, setOf(BAR), factor = 1.0, rest = 120,
            desc = "จับบาร์หงายมือกว้างเท่าไหล่ ดึงตัวขึ้นจนคางพ้นบาร์ แล้วลดลงจนแขนตรง",
            caution = "ไม่เหวี่ยงตัว ลงให้สุดทุกครั้ง"),
        ex("pull_up", "ดึงข้อ", "Pull-up", PULL, setOf(BACK), setOf(BICEPS, SHOULDERS), REPS, 3, setOf(BAR), factor = 1.0, rest = 120,
            desc = "จับบาร์คว่ำมือกว้างกว่าไหล่เล็กน้อย ดึงตัวขึ้นจนคางพ้นบาร์ แล้วลดลงช้าๆ",
            caution = "เริ่มจากดึงสะบักก่อนงอศอก ไม่ใช้แรงเหวี่ยง"),
        ex("weighted_pullup", "ดึงข้อถ่วงน้ำหนัก", "Weighted pull-up", PULL, setOf(BACK), setOf(BICEPS, SHOULDERS), WEIGHTED, 4, setOf(BAR), factor = 1.0, rest = 150,
            desc = "ดึงข้อโดยสะพายเป้หรือห้อยน้ำหนักที่เอว",
            caution = "เพิ่มน้ำหนักทีละน้อย 1–2.5 kg"),
        ex("archer_pullup", "ดึงข้ออาร์เชอร์", "Archer pull-up", PULL, setOf(BACK), setOf(BICEPS, SHOULDERS), REPS, 5, setOf(BAR), factor = 1.0, rest = 150,
            desc = "จับบาร์กว้างมาก ดึงตัวไปทางมือข้างหนึ่ง แขนอีกข้างเหยียดตรงช่วย สลับข้าง",
            caution = "ต้องดึงข้อปกติได้ 3×10 ก่อน"),
        ex("one_arm_pullup", "ดึงข้อแขนเดียว (ฝึกต่อยอด)", "One-arm pull-up progression", PULL, setOf(BACK, BICEPS), setOf(SHOULDERS), REPS, 5, setOf(BAR), factor = 1.0, rest = 180,
            desc = "ฝึกด้วยการจับข้อมือ/ผ้าช่วยด้วยมืออีกข้าง หรือ negative แขนเดียว",
            caution = "ท่าขั้นสูง เสี่ยงเอ็นข้อศอก ค่อยๆ เพิ่ม"),
        // ---------------- Legs
        ex("air_squat", "สควอท", "Air squat", LEGS, setOf(LEG, GLUTES), setOf(LOWER_BACK), REPS, 1, factor = 0.7, rest = 60,
            desc = "เท้ากว้างเท่าไหล่ นั่งลงเหมือนนั่งเก้าอี้ให้ต้นขาขนานพื้น หลังตรง แล้วดันขึ้น",
            caution = "เข่าชี้ตามปลายเท้า ส้นเท้าไม่ยก"),
        ex("weighted_squat", "สควอทถ่วงน้ำหนัก", "Weighted squat", LEGS, setOf(LEG, GLUTES), setOf(LOWER_BACK), WEIGHTED, 2, factor = 0.7,
            desc = "สะพายเป้หรือถือของหนักที่อก แล้วสควอทตามปกติ",
            caution = "หลังตรงตลอด ไม่ก้มตัวมาก"),
        ex("split_squat", "สปลิทสควอท", "Split squat", LEGS, setOf(LEG, GLUTES), emptySet(), REPS, 2, factor = 0.75,
            desc = "ยืนก้าวขาหน้า-หลัง ลดเข่าหลังลงใกล้พื้นแล้วดันขึ้น ทำครบข้างหนึ่งแล้วสลับ (นับต่อข้าง)",
            caution = "ลำตัวตั้งตรง เข่าหน้าไม่บิดเข้าด้านใน"),
        ex("lunge", "ลันจ์", "Lunge", LEGS, setOf(LEG, GLUTES), setOf(CALVES), REPS, 2, factor = 0.75,
            desc = "ก้าวไปข้างหน้ายาวๆ ลดเข่าหลังลงใกล้พื้นแล้วถีบกลับ สลับขา",
            caution = "ก้าวยาวพอให้เข่าหน้าไม่เลยปลายเท้ามาก"),
        ex("step_up", "ก้าวขึ้นกล่อง/บันได", "Step-up", LEGS, setOf(LEG, GLUTES), setOf(CALVES), REPS, 1, factor = 0.75, rest = 60,
            desc = "วางเท้าหนึ่งบนขั้นบันไดหรือกล่องที่มั่นคง ดันตัวขึ้นด้วยขานั้น แล้วลงช้าๆ",
            caution = "ใช้ที่เหยียบที่ไม่ลื่น"),
        ex("bulgarian_split_squat", "บัลแกเรียนสปลิทสควอท", "Bulgarian split squat", LEGS, setOf(LEG, GLUTES), emptySet(), REPS, 3, factor = 0.8,
            desc = "วางหลังเท้าข้างหลังบนเก้าอี้ ลดตัวลงด้วยขาหน้าจนต้นขาขนานพื้น (นับต่อข้าง)",
            caution = "ทรงตัวให้ดี ใช้เก้าอี้ที่ไม่เลื่อน"),
        ex("jump_squat", "สควอทกระโดด", "Jump squat", LEGS, setOf(LEG, GLUTES), setOf(CALVES), REPS, 2, factor = 0.7, rest = 75,
            desc = "สควอทลงแล้วกระโดดขึ้นสุดแรง ลงพื้นนุ่มๆ ต่อเข้าครั้งถัดไป",
            caution = "ลงพื้นด้วยปลายเท้าแล้วงอเข่ารับแรง หลีกเลี่ยงถ้ามีปัญหาเข่า"),
        ex("assisted_pistol", "พิสทอลสควอทมีตัวช่วย", "Assisted pistol squat", LEGS, setOf(LEG, GLUTES), setOf(ABS), REPS, 4, factor = 0.85, rest = 120,
            desc = "จับเสาหรือขอบประตูช่วยทรงตัว ยืนขาเดียว เหยียดอีกขาไปข้างหน้า แล้วนั่งลงลึกด้วยขาเดียว",
            caution = "ค่อยๆ เพิ่มความลึก เข่าไม่บิด"),
        ex("pistol_squat", "พิสทอลสควอท", "Pistol squat", LEGS, setOf(LEG, GLUTES), setOf(ABS), REPS, 5, factor = 0.9, rest = 120,
            desc = "สควอทขาเดียวลงลึกสุด ขาอีกข้างเหยียดขนานพื้น แล้วยืนขึ้นโดยไม่ใช้ตัวช่วย",
            caution = "ต้องมีความยืดหยุ่นข้อเท้าดี ฝึกแบบมีตัวช่วยก่อน"),
        ex("glute_bridge", "สะพานก้น", "Glute bridge", LEGS, setOf(GLUTES), setOf(LOWER_BACK, LEG), REPS, 1, factor = 0.4, rest = 60,
            desc = "นอนหงายชันเข่า ดันสะโพกขึ้นจนลำตัวตรงจากเข่าถึงไหล่ บีบก้นค้าง 1 วินาที",
            caution = "ใช้แรงก้น ไม่แอ่นหลังส่วนล่าง"),
        ex("single_leg_glute_bridge", "สะพานก้นขาเดียว", "Single-leg glute bridge", LEGS, setOf(GLUTES), setOf(LOWER_BACK, LEG), REPS, 2, factor = 0.55, rest = 60,
            desc = "ทำสะพานก้นโดยยกขาข้างหนึ่งขึ้น (นับต่อข้าง)",
            caution = "สะโพกไม่เอียงไปข้างใดข้างหนึ่ง"),
        ex("hip_thrust", "ฮิปทรัสต์", "Hip thrust", LEGS, setOf(GLUTES), setOf(LEG), REPS, 2, factor = 0.5, rest = 75,
            desc = "พิงหลังส่วนบนกับโซฟาหรือเตียง ดันสะโพกขึ้นจนลำตัวขนานพื้น บีบก้น",
            caution = "คางชิดอกเล็กน้อย ไม่แอ่นหลัง"),
        ex("calf_raise", "เขย่งน่อง", "Calf raise", LEGS, setOf(CALVES), emptySet(), REPS, 1, factor = 0.9, rest = 45,
            desc = "ยืนบนขอบบันได เขย่งปลายเท้าขึ้นสุดแล้วลดส้นลงต่ำกว่าขั้นช้าๆ",
            caution = "จับราวช่วยทรงตัว"),
        ex("single_leg_calf_raise", "เขย่งน่องขาเดียว", "Single-leg calf raise", LEGS, setOf(CALVES), emptySet(), REPS, 2, factor = 1.0, rest = 45,
            desc = "เขย่งด้วยขาเดียว นับต่อข้าง",
            caution = "เคลื่อนไหวเต็มช่วงและช้า"),
        ex("wall_sit", "นั่งพิงผนัง", "Wall sit", LEGS, setOf(LEG), setOf(GLUTES), HOLD, 1, factor = 0.0, rest = 60,
            desc = "พิงหลังกับผนัง ย่อตัวจนต้นขาขนานพื้นแล้วค้างไว้",
            caution = "เข่าอยู่เหนือข้อเท้า ไม่ยื่นเลยปลายเท้า"),
        // ---------------- Core
        ex("plank", "แพลงก์", "Front plank", CORE, setOf(ABS), setOf(SHOULDERS, LOWER_BACK), HOLD, 1, factor = 0.0, rest = 60,
            desc = "วางศอกใต้ไหล่ ลำตัวตรงจากศีรษะถึงส้นเท้า เกร็งท้องและก้นค้างไว้",
            caution = "ไม่ให้สะโพกตกหรือยกสูง หายใจตลอด"),
        ex("side_plank", "แพลงก์ด้านข้าง", "Side plank", CORE, setOf(OBLIQUES), setOf(ABS, SHOULDERS), HOLD, 2, factor = 0.0, rest = 45,
            desc = "นอนตะแคงยันศอก ยกสะโพกให้ลำตัวตรง ค้างไว้ แล้วสลับข้าง (บันทึกต่อข้าง)",
            caution = "ศอกอยู่ใต้ไหล่พอดี"),
        ex("long_lever_plank", "แพลงก์แขนยาว", "Long-lever plank", CORE, setOf(ABS), setOf(SHOULDERS), HOLD, 3, factor = 0.0, rest = 60,
            desc = "ทำแพลงก์โดยวางศอกยื่นไปข้างหน้าเลยศีรษะ ทำให้หน้าท้องทำงานหนักขึ้น",
            caution = "หยุดถ้าหลังส่วนล่างเริ่มแอ่น"),
        ex("hollow_hold", "ฮอลโลว์โฮลด์", "Hollow body hold", CORE, setOf(ABS), setOf(LEG), HOLD, 3, factor = 0.0, rest = 60,
            desc = "นอนหงาย หลังส่วนล่างกดติดพื้น ยกไหล่และขาขึ้นเล็กน้อย แขนเหยียดเหนือศีรษะ ค้างไว้",
            caution = "ถ้าหลังลอยจากพื้นให้งอเข่าหรือเอาแขนลง"),
        ex("dead_bug", "เดดบัก", "Dead bug", CORE, setOf(ABS), setOf(LOWER_BACK), REPS, 1, factor = 0.0, rest = 45,
            desc = "นอนหงาย ยกแขนขาขึ้น เหยียดแขนและขาตรงข้ามลงช้าๆ แล้วสลับ (นับต่อข้าง)",
            caution = "หลังส่วนล่างติดพื้นตลอด"),
        ex("bird_dog", "เบิร์ดด็อก", "Bird dog", CORE, setOf(LOWER_BACK, ABS), setOf(GLUTES), REPS, 1, factor = 0.0, rest = 45,
            desc = "คุกเข่าตั้งศอก เหยียดแขนและขาตรงข้ามให้ขนานพื้น ค้าง 2 วินาทีแล้วสลับ",
            caution = "สะโพกไม่บิด เคลื่อนช้าๆ"),
        ex("crunch", "ครันช์", "Crunch", CORE, setOf(ABS), emptySet(), REPS, 1, factor = 0.0, rest = 45,
            desc = "นอนหงายชันเข่า ม้วนไหล่ขึ้นจากพื้นด้วยแรงหน้าท้อง แล้วลดลงช้าๆ",
            caution = "ไม่ดึงคอด้วยมือ"),
        ex("bicycle_crunch", "ครันช์ปั่นจักรยาน", "Bicycle crunch", CORE, setOf(OBLIQUES, ABS), emptySet(), REPS, 2, factor = 0.0, rest = 45,
            desc = "นอนหงาย บิดศอกไปหาเข่าตรงข้ามสลับกันคล้ายปั่นจักรยาน (นับรวมสองข้าง)",
            caution = "ทำช้าและควบคุม"),
        ex("leg_raise", "ยกขานอน", "Lying leg raise", CORE, setOf(ABS), setOf(LOWER_BACK), REPS, 2, factor = 0.0, rest = 60,
            desc = "นอนหงายขาตรง ยกขาขึ้นจนตั้งฉากแล้วลดลงช้าๆ โดยไม่แตะพื้น",
            caution = "กดหลังส่วนล่างติดพื้น งอเข่าถ้ายังไม่ไหว"),
        ex("russian_twist", "รัสเซียนทวิสต์", "Russian twist", CORE, setOf(OBLIQUES), setOf(ABS), REPS, 2, factor = 0.0, rest = 45,
            desc = "นั่งเอนหลัง ยกเท้าหรือวางเท้า บิดลำตัวซ้ายขวา (นับรวมสองข้าง)",
            caution = "บิดจากลำตัว ไม่ใช่แค่แกว่งแขน"),
        ex("superman", "ซูเปอร์แมน", "Superman", CORE, setOf(LOWER_BACK), setOf(GLUTES, BACK), REPS, 1, factor = 0.0, rest = 45,
            desc = "นอนคว่ำ ยกแขนและขาขึ้นจากพื้นพร้อมกัน ค้าง 2 วินาที",
            caution = "ยกพอประมาณ ไม่ฝืนแอ่นหลังมาก"),
        ex("hanging_knee_raise", "ห้อยบาร์ยกเข่า", "Hanging knee raise", CORE, setOf(ABS), setOf(OBLIQUES), REPS, 3, setOf(BAR), factor = 0.0, rest = 60,
            desc = "ห้อยบาร์ ยกเข่าขึ้นหาหน้าอก แล้วลดลงช้าๆ ไม่แกว่งตัว",
            caution = "คุมแรงเหวี่ยง ใช้แรงหน้าท้อง"),
        ex("hanging_leg_raise", "ห้อยบาร์ยกขาตรง", "Hanging leg raise", CORE, setOf(ABS), setOf(OBLIQUES), REPS, 4, setOf(BAR), factor = 0.0, rest = 75,
            desc = "ห้อยบาร์ ยกขาตรงขึ้นจนขนานพื้นหรือสูงกว่า",
            caution = "ฝึกยกเข่าให้ได้ 3×12 ก่อน"),
        // ---------------- Full body / cardio
        ex("mountain_climber", "เมาท์เทนไคลม์เบอร์", "Mountain climber", FULL_BODY, setOf(ABS), setOf(SHOULDERS, LEG), CARDIO, 2, factor = 0.0, rest = 45,
            desc = "ท่าวิดพื้นแขนตรง สลับดึงเข่าเข้าหาอกเร็วๆ บันทึกเป็นเวลา",
            caution = "สะโพกไม่ยกสูง"),
        ex("burpee", "เบอร์ปี้", "Burpee", FULL_BODY, setOf(LEG, CHEST), setOf(SHOULDERS, ABS), REPS, 3, factor = 0.5, rest = 60,
            desc = "ย่อตัววางมือ ดีดขาไปหลังเป็นท่าวิดพื้น (วิดพื้น 1 ครั้งถ้าไหว) ดึงขากลับแล้วกระโดดขึ้น",
            caution = "ลงพื้นนุ่มๆ ลดความเร็วถ้าท่าเสีย"),
        ex("jumping_jack", "จัมปิ้งแจ็ค", "Jumping jack", FULL_BODY, setOf(CALVES), setOf(SHOULDERS, LEG), CARDIO, 1, factor = 0.0, rest = 30,
            desc = "กระโดดกางขาพร้อมยกแขนเหนือศีรษะ แล้วกลับท่าเดิม บันทึกเป็นเวลา",
            caution = "ลงด้วยปลายเท้า"),
        ex("high_knees", "วิ่งยกเข่าสูง", "High knees", FULL_BODY, setOf(LEG), setOf(ABS, CALVES), CARDIO, 1, factor = 0.0, rest = 30,
            desc = "วิ่งอยู่กับที่ยกเข่าสูงระดับสะโพกให้เร็วที่สุด บันทึกเป็นเวลา",
            caution = "ลำตัวตั้งตรง"),
        ex("bear_crawl", "คลานหมี", "Bear crawl", FULL_BODY, setOf(SHOULDERS, ABS), setOf(LEG), CARDIO, 2, factor = 0.0, rest = 60,
            desc = "คลานด้วยมือและปลายเท้า เข่าลอยจากพื้นเล็กน้อย บันทึกเป็นระยะหรือเวลา",
            caution = "หลังขนานพื้น ไม่ยกก้นสูง"),
        ex("sprint_drill", "สปรินต์ระยะสั้น", "Sprint drill", FULL_BODY, setOf(LEG), setOf(GLUTES, CALVES), CARDIO, 2, factor = 0.0, rest = 90,
            desc = "วิ่งเร็วเต็มที่ระยะสั้น (เช่น 50–100 ม.) แล้วเดินกลับเป็นการพัก บันทึกระยะ/เวลา",
            caution = "วอร์มอัพให้ดีก่อน"),
        // ---------------- Skill / isometric
        ex("handstand_hold", "ยืนมือพิงผนัง", "Wall handstand hold", SKILL, setOf(SHOULDERS), setOf(TRICEPS, ABS), HOLD, 4, factor = 0.0, rest = 90,
            desc = "เดินเท้าขึ้นผนังจนตัวตั้งตรง หน้าท้องหันเข้าผนัง ค้างไว้",
            caution = "ฝึกลงจากท่าอย่างปลอดภัยก่อน มีพื้นที่ว่างรอบตัว"),
        ex("crow_pose", "ท่ากา", "Crow pose", SKILL, setOf(SHOULDERS, ABS), setOf(TRICEPS), HOLD, 3, factor = 0.0, rest = 60,
            desc = "วางมือบนพื้น เข่าวางบนต้นแขนด้านหลัง โน้มตัวไปข้างหน้าจนเท้าลอย ค้างไว้",
            caution = "วางหมอนด้านหน้ากันหน้ากระแทก"),
        ex("tuck_l_sit", "แอลซิตงอเข่า", "Tuck L-sit", SKILL, setOf(ABS), setOf(TRICEPS, SHOULDERS), HOLD, 3, factor = 0.0, rest = 60,
            desc = "วางมือบนพื้นหรือขอบเก้าอี้สองตัว ดันตัวลอยแล้วงอเข่าเข้าหาอก ค้างไว้",
            caution = "กดไหล่ลง ไม่ยกไหล่หาหู"),
        ex("l_sit", "แอลซิต", "L-sit", SKILL, setOf(ABS), setOf(TRICEPS, SHOULDERS, LEG), HOLD, 4, factor = 0.0, rest = 90,
            desc = "ดันตัวลอยจากพื้น/บาร์คู่ ขาเหยียดตรงขนานพื้นเป็นรูปตัว L ค้างไว้",
            caution = "ต้องค้างแบบงอเข่าได้ 3×20 วินาทีก่อน"),
    )

    val byKey: Map<String, ExerciseDef> = all.associateBy { it.key }
}
