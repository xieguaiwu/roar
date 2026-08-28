package com.xieguiawu.roar.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xieguiawu.roar.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric 冒烟测试（android-development skill §2：必须存在 onNodeWithText 断言且通过，
 * 防占位代码上线）。模型未下载的测试环境下模型状态恒为「模型未就绪」，
 * 因此可稳定断言下载按钮与未就绪文案；方言下拉默认粤语可稳定断言。
 *
 * 2026-08-28 修复下载链路后新增：下载按钮存在性 + 方言下拉交互。
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
        // 方言选择按钮（默认粤语，带下拉箭头后缀）
        composeRule.onNodeWithText("粵語（廣州話） ▾").assertIsDisplayed()
    }

    @Test
    fun settings_未下载模型时显示模型未就绪与下载按钮() {
        composeRule.onNodeWithText("模型未就绪").assertIsDisplayed()
        // 下载链路入口（P0 修复：此前无任何入口，模型永远无法就绪）
        composeRule.onNodeWithText("下载模型").assertIsDisplayed()
    }

    @Test
    fun settings_显示应用说明与启用指引() {
        composeRule.onNodeWithText("Roar：說方言，出正字").assertIsDisplayed()
        composeRule
            .onNodeWithText("启用指引：系统设置 → 语言与输入法 → 启用 Roar，并设为当前输入法。")
            .assertIsDisplayed()
    }

    @Test
    fun settings_方言下拉可展开并显示候选() {
        // 点击方言按钮展开菜单（默认粤语）
        composeRule.onNodeWithText("粵語（廣州話） ▾").performClick()
        // 菜单内显示粤语选项（当前仅注册一个方言，未来新增方言后此测试同步扩展）
        composeRule.onNodeWithText("粵語（廣州話）").assertIsDisplayed()
    }
}
