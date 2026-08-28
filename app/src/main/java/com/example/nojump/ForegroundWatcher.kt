package com.example.nojump

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.view.accessibility.AccessibilityManager

/**
 * 前台应用监控器。
 *
 * 主判断：无障碍服务事件驱动写入（ForegroundAccessibilityService 一收到
 *         TYPE_WINDOW_STATE_CHANGED 就调用 [onForegroundChanged]），零延迟、无条件抖动。
 * 兜底  ：若无障碍服务被 vivo 等 ROM 杀掉（导致 accessibilityActive=false），
 *         退化为查一次 queryEvents（用固定 8 秒重叠窗，不信滑窗游标），尽力补偿。
 */
object ForegroundWatcher {

    private const val EVENTS_WINDOW_MS = 8_000L

    private lateinit var appContext: Context

    /** 无障碍服务是否在线。在线时优先信任其事件驱动的结果。 */
    @Volatile
    var accessibilityActive = false
        private set

    @Volatile
    var lastForeground: String? = null
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** 由无障碍服务在连接/断开时调用。 */
    fun onAccessibilityConnected(active: Boolean) {
        accessibilityActive = active
    }

    /** 由无障碍服务在窗口切换时调用，写入当前前台包名。 */
    fun onForegroundChanged(pkg: String) {
        lastForeground = pkg
    }

    /** 无障碍服务是否已在系统设置中开启。 */
    fun hasAccessibilityEnabled(): Boolean {
        val am = appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = am.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        val myPkg = appContext.packageName
        val myName = ForegroundAccessibilityService::class.java.name
        for (svc in enabled) {
            val si = svc.resolveInfo?.serviceInfo ?: continue
            if (si.packageName == myPkg && si.name == myName) {
                return true
            }
        }
        return false
    }

    /**
     * 返回当前前台包名，永不返回 null。
     * - 无障碍在线：直接返回内存中的最新前台（已实时、可靠）。
     * - 无障碍离线：用 queryEvents 固定 8 秒重叠窗捞一次作兜底。
     */
    fun pollForeground(): String {
        if (accessibilityActive) {
            return lastForeground ?: "android"
        }

        // —— 兜底：无障碍服务不可用时，用 UseStats 事件流临时代偿 ——
        val usm = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        var bestPkg: String? = null
        var bestTs = 0L
        try {
            val events: UsageEvents = usm.queryEvents(now - EVENTS_WINDOW_MS, now)
            val ev = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(ev)
                if (ev.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    if (ev.timeStamp > bestTs) {
                        bestTs = ev.timeStamp
                        bestPkg = ev.packageName
                    }
                }
            }
        } catch (_: Exception) {
        }
        if (bestPkg != null) {
            lastForeground = bestPkg
            return bestPkg
        }
        return lastForeground ?: "android"
    }

    // ---------- 以下保留供依赖方引用，避免破坏既有调用点；主流程不再依赖 ----------

    @Deprecated("改用无障碍服务检测前台", level = DeprecationLevel.HIDDEN)
    fun hasUsagePermission(): Boolean {
        val appOps = appContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}