package com.example.nojump

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuManager {

    val isReady: Boolean
        get() = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    val isGranted: Boolean
        get() = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

    fun runShell(vararg cmd: String): Boolean {
        if (!isReady || !isGranted) return false
        return runCatching {
            val proc = Shizuku.newProcess(cmd, null, null) ?: return false
            proc.waitFor() == 0
        }.getOrDefault(false)
    }
}