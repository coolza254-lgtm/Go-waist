package com.gowaist.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * End-to-end smoke test on the real app (real Hilt graph, Room, DataStore): onboarding, every
 * tab and sub-tab, and a complete bodyweight session. Catches crashes in composition,
 * navigation and data wiring that unit tests cannot see.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [32], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppFlowTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun await(what: String, condition: () -> Boolean) {
        try {
            rule.waitUntil(10_000, condition)
        } catch (e: Throwable) {
            throw AssertionError("Not found: $what\n" + rule.onAllNodes(isRoot()).printToString(maxDepth = 60), e)
        }
    }

    /** Celebration dialogs (badges, goals) can pop up after saves; close them like a user would. */
    private fun dismissCelebrations() {
        repeat(5) {
            if (rule.onAllNodesWithText("เย้!").fetchSemanticsNodes().isEmpty()) return
            rule.onAllNodesWithText("เย้!").onFirst().performClick()
            rule.waitForIdle()
        }
    }

    private fun click(text: String) {
        dismissCelebrations()
        await(text) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText(text).onFirst().performClick()
        rule.waitForIdle()
    }

    private fun tag(t: String) {
        await(t) { rule.onAllNodes(hasTestTag(t)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasTestTag(t)).performClick()
        rule.waitForIdle()
    }

    private fun scrollTag(t: String) {
        await(t) { rule.onAllNodes(hasTestTag(t)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasTestTag(t)).performScrollTo().performClick()
        rule.waitForIdle()
    }

    /** Scrolls the screen's lazy list until a node with [text] is composed. */
    private fun scrollTo(text: String) {
        await("list") { rule.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToNode(hasText(text))
        rule.waitForIdle()
    }

    private fun waitFor(text: String, substring: Boolean = false) {
        await(text) { rule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun onboardingTabsAndWorkoutSession() {
        // Onboarding
        waitFor("สวัสดี! ฉันชื่อ Gogo")
        click("ถัดไป")
        waitFor("ตั้งหน่วยที่ใช้")
        click("ถัดไป")
        waitFor("มีอุปกรณ์อะไรบ้าง?")
        click("บาร์โหน")
        click("ถัดไป")
        waitFor("ตั้งเป้าหมายแรกกัน!")
        click("เริ่มใช้งาน!")

        // Home
        waitFor("วันนี้")
        waitFor("สัปดาห์นี้")

        // Run tab: game menu hub, then the run log with its empty state
        click("วิ่ง")
        waitFor("สนามวิ่ง")
        scrollTag("menu_log")
        waitFor("ยังไม่มีผลวิ่ง")

        // Manual run entry
        tag("fab_run")
        click("กรอกเอง")
        waitFor("บันทึกการวิ่ง")
        // Fields in order: distance, hours, minutes, seconds, ...
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("5.2")
        rule.onAllNodes(hasSetTextAction())[2].performTextInput("31")
        click("บันทึก")
        waitFor("ประวัติ")
        waitFor("5.2", substring = true)
        click("สถิติ")
        waitFor("Personal Best")
        click("ปฏิทิน")
        click("ประวัติ")
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        // Free run program: start, finish, record result
        waitFor("สนามวิ่ง")
        scrollTag("prog_FREE")
        tag("program_start")
        tag("hud_finish")
        waitFor("ระยะทาง", substring = true)
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("3")
        rule.onAllNodes(hasSetTextAction())[2].performTextInput("20")
        tag("result_save")
        tag("saved_done")

        // Tracking screens
        waitFor("สนามวิ่ง")
        scrollTag("menu_vo2")
        waitFor("VO2 Max", substring = true)
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        scrollTag("menu_perf")
        waitFor("Performance", substring = true)
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        scrollTag("menu_weight")
        tag("weight_save")
        waitFor("70 กก.", substring = true)
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        // Train tab and its sub-tabs
        click("ฝึก")
        waitFor("เริ่มเปล่า")
        click("คลังท่า")
        waitFor("ครันช์", substring = true)
        click("สายความก้าวหน้า")
        waitFor("สายวิดพื้น")
        click("สถิติ")
        click("เริ่มฝึก")

        // Session from a template
        waitFor("Full Body มือใหม่")
        click("Full Body มือใหม่")
        waitFor("บันทึกเซ็ต")
        click("บันทึกเซ็ต")
        waitFor("พัก")
        click("บันทึกเซ็ต")
        click("ทำซ้ำเซ็ตก่อนหน้า")
        waitFor("3/3") // third set stored (writes finish off the UI thread)
        // The rest timer ticks continuously (by design); skip it so the UI can settle.
        await("rest bar") { rule.onAllNodes(hasContentDescription("ข้ามการพัก")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasContentDescription("ข้ามการพัก")).performClick()
        click("จบเซสชัน")
        waitFor("จบเซสชันนี้?")
        click("😄")
        rule.onAllNodesWithText("จบเซสชัน")[1].performClick()
        waitFor("สรุปเซสชัน")
        click("กลับหน้าหลัก")

        // Stats now have data
        click("สถิติ")
        scrollTo("กล้ามเนื้อที่ฝึก")
        scrollTo("ความสมดุล (30 วัน)")

        // Plan tab: create a prebuilt plan
        click("แผน")
        waitFor("ยังไม่มีแผนซ้อม")
        click("เลือกแผน")
        scrollTo("วิ่ง + Bodyweight")
        click("วิ่ง + Bodyweight")
        scrollTo("ใช้แผนนี้")
        click("ใช้แผนนี้")
        waitFor("ตารางซ้อม")
        tag("fab_plan")
        click("กำหนดเอง")
        scrollTo("ตรวจโหลดของตาราง")
        scrollTo("สร้าง")
        click("สร้าง")
        waitFor("ตารางซ้อม")

        // Goals tab
        click("เป้าหมาย")
        waitFor("เหรียญตรา")
        tag("fab_goal")
        waitFor("ชนิดเป้าหมาย")
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("20")
        click("บันทึก")
        waitFor("กำลังทำ")

        // Home again with data, then settings and body metrics
        click("หน้าหลัก")
        rule.onNodeWithContentDescription("เมนู").performClick()
        click("Body Metrics")
        waitFor("บันทึกน้ำหนักวันนี้")
        rule.activity.onBackPressedDispatcher.onBackPressed()
        rule.onNodeWithContentDescription("เมนู").performClick()
        click("ตั้งค่า")
        waitFor("ข้อมูล & สำรอง")
        rule.onNodeWithText("หน่วย").assertExists()
    }
}
