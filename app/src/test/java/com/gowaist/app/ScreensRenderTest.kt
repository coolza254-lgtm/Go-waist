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
        // Use the dark theme before the activity starts.
        runBlocking {
            SettingsRepository(ApplicationProvider.getApplicationContext()).update {
                it.copy(themeMode = ThemeMode.DARK)
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
    fun tabsAndEditorsRenderInDarkMode() {
        waitFor("Go waist")
        click("ประวัติ")
        waitFor("ยังไม่มีผลวิ่ง")
        click("พัฒนาการ")
        waitFor("VO2 Max")
        click("รองเท้า")
        click("เพิ่มรองเท้า")
        waitFor("ความทนทาน")
        back()
        click("หน้าหลัก")
        click("นำเข้าจาก Samsung Health")
        back()
    }
}
