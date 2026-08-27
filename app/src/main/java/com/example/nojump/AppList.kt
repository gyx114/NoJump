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
            // 默认只保留有启动图标的应用，列表清爽；
            // includeSystem=true 时列出全部（含系统/预装），配合搜索框找应用
            if (!includeSystem && pm.getLaunchIntentForPackage(info.packageName) == null) continue
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