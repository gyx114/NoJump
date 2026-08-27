package com.example.nojump

object RuleEngine {

    private var selfPkg: String? = null
    @Volatile private var wasInSource = false
    @Volatile private var unfreezingAt = Long.MAX_VALUE

    fun configure(selfPackage: String) {
        selfPkg = selfPackage
    }

    fun onTick(current: String?, nowMs: Long) {
        val cur = current ?: return
        val core = selfPkg ?: return
        if (cur == core) return

        val sources = RuleStore.sourceSet
        val targets = RuleStore.targetSet
        val inSource = cur in sources

        if (inSource) {
            unfreezingAt = Long.MAX_VALUE
            for (t in targets) {
                if (t == core) continue
                if (!Freezer.isFrozen(t)) Freezer.freeze(t)
            }
        } else {
            if (wasInSource) {
                unfreezingAt = nowMs + RuleStore.unfreezeDelayMs
            }
            if (nowMs >= unfreezingAt) {
                Freezer.unfreezeAll()
                unfreezingAt = Long.MAX_VALUE
            }
        }
        wasInSource = inSource
    }

    fun isInSourceOn(): Boolean = wasInSource
}