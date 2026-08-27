package com.example.nojump

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process

object ForegroundWatcher {

    private lateinit var appContext: Context
    @Volatile private var lastQueryTime = 0L

    @Volatile var lastForeground: String? = null
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        lastQueryTime = System.currentTimeMillis() - 3000
        lastForeground = null
    }

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

    fun pollForeground(): String? {
        val usm = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        var seen: String? = null
        try {
            val events = usm.queryEvents(lastQueryTime, now)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    seen = event.packageName
                }
            }
        } catch (_: Exception) {
            return lastForeground
        }
        lastQueryTime = now
        if (seen != null) lastForeground = seen
        return lastForeground
    }
}