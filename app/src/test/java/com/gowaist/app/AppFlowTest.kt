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
 * End-to-end smoke test on the real app (real Hilt graph, Room, DataStore): profile, a shoe,
 * a manual run, every tab and the tracking screens. Catches crashes in composition,
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

    private fun click(text: String) {
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

    private fun back() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    private fun input(index: Int, text: String) {
        rule.onAllNodes(hasSetTextAction())[index].performTextInput(text)
        rule.waitForIdle()
    }

    @Test
    fun profileShoeRunAndTrackingScreens() {
        // Home, first launch
        waitFor("Go waist")
        waitFor("ยังไม่มีการวิ่ง")

        // (The profile dialog is not driven here: text fields in dialogs never go idle under Robolectric.)
        waitFor("ตั้งค่าโปรไฟล์ร่างกาย")

        // A pair of shoes becomes the main pair
        click("รองเท้า")
        waitFor("ยังไม่มีรองเท้า")
        tag("shoe_add")
        rule.onNode(hasTestTag("shoe_name")).performTextInput("Pegasus 41")
        input(1, "4200")
        tag("shoe_save")
        waitFor("Pegasus 41")
        waitFor("คู่หลัก")

        // Manual run, the main shoe is preselected
        click("หน้าหลัก")
        scrollTag("home_manual")
        waitFor("บันทึกการวิ่ง")
        input(0, "5.2")
        input(2, "31")
        input(5, "150")
        click("บันทึก")
        waitFor("วิ่งล่าสุด", substring = true)
        waitFor("5.2", substring = true)

        // History and its sub-tabs
        click("ประวัติ")
        waitFor("5.2", substring = true)
        click("สถิติ")
        click("ปฏิทิน")
        click("รายการ")

        // Progress hub and detail screens
        click("พัฒนาการ")
        scrollTag("progress_vo2")
        waitFor("ระดับความฟิต", substring = true)
        back()
        scrollTag("progress_perf")
        waitFor("โซนชีพจร", substring = true)
        back()
        scrollTag("progress_weight")
        tag("weight_save")
        waitFor("70.00", substring = true)
        back()
        scrollTag("progress_goals")
        waitFor("5K")
        tag("fab_goal")
        waitFor("ชนิดเป้าหมาย")
        input(0, "20")
        click("บันทึก")
        waitFor("กำลังทำ")
        back()

        // Shoe stats picked up the run
        click("รองเท้า")
        waitFor("5")
        click("หน้าหลัก")
        rule.onNodeWithContentDescription("ตั้งค่า").performClick()
        waitFor("หน่วย")
    }
}
