package com.example.nojump

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * 无障碍前台侦测服务。
 *
 * 为什么用它替代 UseStats 轮询：
 *   轮询是"每 400ms 猜一次前台是谁"，有采样间隔 + ROM 上报延迟 + 聚合写入延迟，
 *   导致"时灵时不灵"。无障碍服务是**事件驱动**的：系统每切换一个 Activity，
 *   立刻通过 TYPE_WINDOW_STATE_CHANGED 回调，包名精确、零延迟、无 ROM 差异。
 *
 * 本服务只做一件事：把当前前台包名写入 ForegroundWatcher。
 * 冻结/解冻仍由 Shizuku 负责，本服务不读取屏幕内容、不拦截输入。
 */
class ForegroundAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        ForegroundWatcher.onAccessibilityConnected(true)
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        ForegroundWatcher.onAccessibilityConnected(false)
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                val pkg = event.packageName?.toString()
                if (pkg != null && pkg.isNotEmpty()) {
                    ForegroundWatcher.onForegroundChanged(pkg)
                }
            }
            else -> Unit // 其余事件（内容变化等）不关心
        }
    }

    override fun onInterrupt() {
        // 无需处理
    }
}