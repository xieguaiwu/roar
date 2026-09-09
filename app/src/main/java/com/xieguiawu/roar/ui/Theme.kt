package com.xieguiawu.roar.ui

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * 强制深色取色（纯函数，可单测）：API 31+ 用 Material You 动态深色
 * （dynamicDarkColorScheme，壁纸驱动深色调色板），API 26–30 回落 Material3
 * 默认深色（darkColorScheme）。**无条件返回深色方案**。
 */
/**
 * 强制深色取色（纯函数，可单测）：API 31+ 用 Material You 动态深色
 * （dynamicDarkColorScheme，壁纸驱动深色调色板），API 26–30 回落 Material3
 * 默认深色（darkColorScheme）。**无条件返回深色方案**。
 *
 * 注意： sdkInt 是可注入参数（测试用）；lint 无法追踪参数值，
 * 因此动态色分支的字面 SDK_INT 守卫必须保留（不可只看 sdkInt）。
 */
fun forcedDarkColorScheme(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): ColorScheme =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && sdkInt >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        darkColorScheme()
    }

/**
 * Roar 全局主题：**强制深色**（产品决定——键盘与设置页无视系统明暗设置，恒为深色）。
 *
 * - 无 `isSystemInDarkTheme()` 分支、无 light 配色——刻意不提供浅色路径；
 * - 与 `res/values/themes.xml` 的 `Theme.Roar`（parent=Theme.Material3.Dark.NoActionBar）
 *   配套：XML 主题管窗口背景（防启动白闪），本组合管 Compose 内容。
 *
 * 强制深色三件套参见 android-development skill §6：XML 父主题与 Compose 主题必须同改，
 * 只改一侧会出现启动白闪或浅色窗口背景。
 */
@Composable
fun RoarTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = forcedDarkColorScheme(LocalContext.current), content = content)
}
