package com.example.nojump

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

object AppList {

    data class AppInfo(
        val pkg: String,
        val label: String,
        val icon: Drawable?,
    )

    fun all(context: Context, includeSystem: Boolean = false): List<AppInfo> {
        val pm = context.packageManager
        val result = mutableListOf<AppInfo>()
        for (info in pm.getInstalledApplications(0)) {
            if (info.packageName == context.packageName) continue
            if (!includeSystem) {
                val launchable = pm.getLaunchIntentForPackage(info.packageName) != null
                // 被冻结(disable-user)的应用 launch intent 会返回 null，
                // 但需保留在列表中以便取消目标勾选，故用 enabled 兜底
                val enabled = runCatching { info.enabled }.getOrDefault(true)
                if (!launchable && enabled) continue
            }
            val label = runCatching { pm.getApplicationLabel(info).toString() }
                .getOrDefault(info.packageName)
            val icon = runCatching { info.loadIcon(pm) }.getOrNull()
            result += AppInfo(info.packageName, label, icon)
        }
        return result.sortedBy { it.label.lowercase() }
    }

    fun isLauncher(pkg: String, context: Context): Boolean {
        val pm = context.packageManager
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return runCatching {
            pm.resolveActivity(home, 0)?.activityInfo?.packageName == pkg
        }.getOrDefault(false)
    }
}