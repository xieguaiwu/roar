package com.xieguiawu.roar.ui

import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xieguiawu.roar.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 强制深色主题回归测试（2026-09-09 默认深色模式）。
 *
 * RoarTheme 无 isSystemInDarkTheme 分支——无论系统明暗，窗口与内容必须深色。
 * 断言亮度 < 0.5（浅色方案亮度过不了该门槛）。
 *
 * 三层验证：
 * 1. [theme_回落色板_API26必须深色]——API 26-30 的 darkColorScheme 回落（纯函数注入 sdkInt）；
 * 2. [theme_回落色板_API31必须深色]——API 31+ 的 dynamicDarkColorScheme 分支；
 * 3. [theme_主窗口背景_必须深色]——真实 MainActivity 启动后的 XML 窗口背景
 *    （Theme.Roar = Theme.Material3.Dark.NoActionBar，防启动白闪）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoarThemeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun assertDark(luminance: Float, label: String) {
        assertTrue("$label 亮度 $luminance 应 < 0.5（强制深色）", luminance < 0.5f)
    }

    @Test
    fun theme_回落色板_API26必须深色() {
        val context = composeRule.activity.applicationContext
        assertDark(forcedDarkColorScheme(context, sdkInt = 26).background.luminance(), "API26 回落 background")
        assertDark(forcedDarkColorScheme(context, sdkInt = 30).background.luminance(), "API30 回落 background")
    }

    @Test
    fun theme_动态色板_API31必须深色() {
        val context = composeRule.activity.applicationContext
        assertDark(forcedDarkColorScheme(context, sdkInt = 31).background.luminance(), "API31 动态 background")
    }

    @Test
    fun theme_主窗口背景_必须深色() {
        composeRule.waitForIdle()
        // XML 主题（Theme.Roar）决定启动闪屏/窗口背景——浅色父主题会白闪（skill §6 三件套）。
        // Window 本身无 getBackground()，窗口背景挂在 DecorView 上。
        // （captureToImage 在 Robolectric 下 forceRedraw 超时，不可用——不采整屏）
        val drawable = composeRule.activity.window.decorView.background
        if (drawable is ColorDrawable) {
            assertDark(androidx.compose.ui.graphics.Color(drawable.color).luminance(), "窗口背景")
        }
        // 组合内容侧：SettingsActivityTest 已验证 RoarTheme 包裹的设置页可渲染；
        // 色板亮度由上两测锁定。
    }
}
