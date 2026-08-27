package com.xieguiawu.roar.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xieguiawu.roar.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric 冒烟测试（android-development skill §2：必须存在 onNodeWithText 断言且通过，
 * 防占位代码上线）。模型未下载的测试环境下 [modelReady] 恒为 false，
 * 因此「模型未就绪」文案可稳定断言。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsActivityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun app_startsAndShowsTitle() {
        composeRule.onNodeWithText("Roar").assertIsDisplayed()
        composeRule.onNodeWithText("粵語（廣州話）").assertIsDisplayed()
    }

    @Test
    fun settings_未下载模型时显示模型未就绪() {
        composeRule.onNodeWithText("模型未就绪").assertIsDisplayed()
    }

    @Test
    fun settings_显示应用说明与启用指引() {
        composeRule.onNodeWithText("Roar：說方言，出正字").assertIsDisplayed()
        composeRule
            .onNodeWithText("启用指引：系统设置 → 语言与输入法 → 启用 Roar，并设为当前输入法。")
            .assertIsDisplayed()
    }
}
