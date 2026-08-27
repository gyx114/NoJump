package com.example.nojump

object Freezer {

    private val frozenByUs = mutableSetOf<String>()

    fun isFrozen(pkg: String): Boolean = pkg in frozenByUs

    fun frozenPackages(): Set<String> = frozenByUs.toSet()

    fun freeze(pkg: String): Boolean {
        val ok = ShizukuManager.runShell("pm", "disable-user", "--user", "0", pkg)
        if (ok) frozenByUs.add(pkg)
        return ok
    }

    fun unfreeze(pkg: String): Boolean {
        val ok = ShizukuManager.runShell("pm", "enable", pkg)
        if (ok) frozenByUs.remove(pkg)
        return ok
    }

    fun unfreezeAll(): Boolean {
        var any = false
        for (pkg in frozenByUs.toList()) {
            if (unfreeze(pkg)) any = true
        }
        return any
    }
}