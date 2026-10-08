package com.gowaist.app

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gowaist.app.data.settings.SettingsRepository
import com.gowaist.app.data.settings.ThemeMode
import kotlinx.coroutines.runBlocking
import androidx.test.core.app.ActivityScenario
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Opens the detail and editor screens (in dark mode) to make sure each one composes. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreensRenderTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun launch() {
        // Skip onboarding and use the dark theme before the activity starts.
        runBlocking {
            SettingsRepository(ApplicationProvider.getApplicationContext()).update {
                it.copy(onboardingDone = true, themeMode = ThemeMode.DARK)
            }
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        rule.waitForIdle()
    }

    @After
    fun close() = scenario.close()

    private fun await(what: String, condition: () -> Boolean) {
        try {
            rule.waitUntil(10_000, condition)
        } catch (e: Throwable) {
            throw AssertionError("Not found: $what\n" + rule.onAllNodes(isRoot()).printToString(maxDepth = 40), e)
        }
    }

    private fun click(text: String) {
        await(text) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText(text).onFirst().performClick()
        rule.waitForIdle()
    }

    private fun waitFor(text: String) = await(text) { rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    private fun back() {
        rule.onAllNodes(hasContentDescription("ย้อนกลับ")).onFirst().performClick()
        rule.waitForIdle()
    }

    private fun scrollTo(text: String) {
        val lazy = rule.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty()
        rule.onAllNodes(if (lazy) hasScrollToIndexAction() else hasScrollAction()).onFirst().performScrollToNode(hasText(text, substring = true))
        rule.waitForIdle()
    }

    @Test
    fun editorsAndDetails() {
        waitFor("วันนี้")

        // Exercise detail + editor
        click("ฝึก")
        click("คลังท่า")
        waitFor("ครันช์")
        click("ครันช์")
        waitFor("วิธีทำ")
        rule.onAllNodes(hasContentDescription("แก้ไข")).onFirst().performClick()
        waitFor("ชื่อท่า (ไทย)")
        back()
        back()
        click("เพิ่มท่าเอง")
        waitFor("เพิ่มท่าใหม่")
        back()

        // Chain editor
        click("สายความก้าวหน้า")
        waitFor("สายวิดพื้น")
        rule.onAllNodes(hasContentDescription("แก้ไข")).onFirst().performClick()
        waitFor("แก้ไขสาย")
        back()

        // Template editor and interval timer setup
        click("เริ่มฝึก")
        waitFor("สร้าง Template")
        click("สร้าง Template")
        waitFor("Template ใหม่")
        back()
        click("ตัวจับเวลา EMOM / AMRAP / Tabata")
        waitFor("ทำ 20 วินาที พัก 10 วินาที")
        click("EMOM")
        waitFor("รวม")
        back()

        // Run detail via manual entry is covered elsewhere; open the import screen shell.
        click("วิ่ง")
        waitFor("ยังไม่มีผลวิ่ง")

        // Plan create + goals editor
        click("แผน")
        click("เลือกแผน")
        waitFor("สร้างแผนซ้อม")
        back()
        click("เป้าหมาย")
        rule.onNode(hasTestTag("fab_goal")).performClick()
        waitFor("ชนิดเป้าหมาย")
        click("ค้างท่าได้นาน")
        waitFor("ท่า:")
        back()

        // Settings and body metrics
        click("หน้าหลัก")
        rule.onAllNodes(hasContentDescription("เมนู")).onFirst().performClick()
        click("ตั้งค่า")
        waitFor("หน่วย")
        scrollTo("ลบข้อมูลทั้งหมด")
        click("ลบข้อมูลทั้งหมด")
        waitFor("แน่ใจไหม?")
        click("ยกเลิก")
        back()
        rule.onAllNodes(hasContentDescription("เมนู")).onFirst().performClick()
        click("Body Metrics")
        waitFor("ยังไม่มีข้อมูลร่างกาย")
    }
}
