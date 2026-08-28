package com.example.nojump

import android.content.Context
import android.content.SharedPreferences

object RuleStore {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext
                .getSharedPreferences("nojump", Context.MODE_PRIVATE)
        }
    }

    var sourceSet: Set<String>
        get() = prefs.getStringSet("sources", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("sources", HashSet(value)).apply()

    var targetSet: Set<String>
        get() = prefs.getStringSet("targets", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("targets", HashSet(value)).apply()

    var paused: Boolean
        get() = prefs.getBoolean("paused", false)
        set(value) = prefs.edit().putBoolean("paused", value).apply()

    /** 轮询间隔：越小拦截越快、越耗电。默认 400ms 兼顾响应与续航。 */
    var pollIntervalMs: Long
        get() = prefs.getLong("poll_ms", 400L)
        set(value) = prefs.edit().putLong("poll_ms", value).apply()

    var unfreezeDelayMs: Long
        get() = prefs.getLong("unfreeze_delay_ms", 5000L)
        set(value) = prefs.edit().putLong("unfreeze_delay_ms", value).apply()
}