package com.xieguiawu.roar.ime

import android.view.inputmethod.EditorInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * IME Compose 宿主回归测试（2026-09-09 P0 修复）。
 *
 * 修复前：RoarImeService.onCreateInputView 的 ComposeView 没有任何 ViewTree
 * LifecycleOwner / SavedStateRegistryOwner——compose-ui 1.7 的 AndroidComposeView
 * 在 onAttachedToWindow 强制要求两者，真机键盘首弹即抛
 * IllegalStateException（Robolectric 既有 56 个测试全部不启动 IME 服务，漏测）。
 *
 * 本测试直接启动服务并断言：
 * 1. ViewTree 两个 owner 已设置（=P0 回归防线，缺一即失败）；
 * 2. 生命周期映射：onCreate→CREATED / onStartInputView→RESUMED /
 *    onFinishInputView→CREATED。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RoarImeServiceTest {

    @Test
    fun ime_onCreateInputView_设置ViewTree两个Owner() {
        val service = Robolectric.setupService(RoarImeService::class.java)
        val view = service.onCreateInputView()

        assertNotNull(view)
        assertSame(service, view.findViewTreeLifecycleOwner())
        assertSame(service, view.findViewTreeSavedStateRegistryOwner())
    }

    @Test
    fun ime_onCreate后生命周期为CREATED() {
        val service = Robolectric.setupService(RoarImeService::class.java)
        assertEquals(Lifecycle.State.CREATED, service.lifecycle.currentState)
    }

    @Test
    fun ime_onStartInputView进入RESUMED_onFinishWindowHidden回到CREATED() {
        val service = Robolectric.setupService(RoarImeService::class.java)
        service.onStartInputView(EditorInfo(), false)
        assertEquals(Lifecycle.State.RESUMED, service.lifecycle.currentState)

        service.onFinishInputView(false)
        assertEquals(Lifecycle.State.STARTED, service.lifecycle.currentState)

        service.onWindowHidden()
        assertEquals(Lifecycle.State.CREATED, service.lifecycle.currentState)
    }
}
