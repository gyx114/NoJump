package com.example.nojump

/**
 * 冻结执行器：借 Shizuku 把目标应用禁用(pm disable-user)/恢复(pm enable)。
 *
 * "当前冻结了哪些"持久化在 RuleStore.frozenSet，这样即使杀后台/进程被回收
 * 丢了内存状态，依然能依据持久化账簿把被隐藏的应用统一恢复。
 */
object Freezer {

    fun isFrozen(pkg: String): Boolean = pkg in RuleStore.frozenSet

    fun frozenPackages(): Set<String> = RuleStore.frozenSet

    fun freeze(pkg: String): Boolean {
        val ok = ShizukuManager.runShell("pm", "disable-user", "--user", "0", pkg)
        if (ok) RuleStore.frozenSet = RuleStore.frozenSet + pkg
        return ok
    }

    fun unfreeze(pkg: String): Boolean {
        val ok = ShizukuManager.runShell("pm", "enable", pkg)
        if (ok) RuleStore.frozenSet = RuleStore.frozenSet - pkg
        return ok
    }

    fun unfreezeAll(): Boolean {
        var any = false
        for (pkg in RuleStore.frozenSet.toList()) {
            if (unfreeze(pkg)) any = true
        }
        return any
    }
}