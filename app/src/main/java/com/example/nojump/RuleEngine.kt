package com.example.nojump

/**
 * 规则引擎：根据当前前台应用 + 配置的 sources/targets，决定冻结或解冻。
 *
 * 核心状态：
 *   - inSource : 当前前台是否是已勾选"触发源头"的应用
 *   - wasInSource: 上一次 tick 的 inSource（用于检测"从源头切出来"）
 *   - unfreezingAt: 计划解冻的时间戳（Long.MAX_VALUE 表示无需解冻）
 *
 * 解冻触发条件（修复后不再脆弱）：
 *   只要 当前不在源头应用 并且 仍有被冻结的目标应用 在本引擎追踪范围内，
 *   就维护一个 unfreezingAt 倒计时。倒计时到期 = 全部解冻。
 *   不再依赖 wasInSource 的"切换瞬间"，避免 pollForeground 漏事件导致永远不解冻。
 */
object RuleEngine {

    private var selfPkg: String? = null
    @Volatile private var wasInSource = false
    @Volatile private var unfreezingAt = Long.MAX_VALUE

    fun configure(selfPackage: String) {
        selfPkg = selfPackage
    }

    fun onTick(current: String?, nowMs: Long) {
        val cur = current ?: ForegroundWatcher.lastForeground ?: return
        val core = selfPkg

        // 如果 cur 正好是 NoJump 自己：不冻结也不解冻，
        // 但仍然要更新 wasInSource = false，避免从源头跳进来配置界面卡死 wasInSource=true。
        if (core != null && cur == core) {
            wasInSource = false
            return
        }

        val sources = RuleStore.sourceSet
        val targets = RuleStore.targetSet
        val inSource = cur in sources

        if (inSource) {
            // —— 在源头应用里：立即冻结所有目标 ——
            // 取消解冻计划（防止刚进来就被解冻逻辑反杀）
            unfreezingAt = Long.MAX_VALUE
            for (t in targets) {
                if (core != null && t == core) continue
                if (!Freezer.isFrozen(t)) {
                    Freezer.freeze(t)
                }
            }
        } else {
            // —— 不在源头应用里：如果还有冻结的目标，启动倒计时解冻 ——
            // 关键修复：不管 wasInSource 是什么，只要已冻结集合非空，
            // 就保证有一个有效的 unfreezingAt 在"将来某个点"触发解冻。
            val hasFrozen = Freezer.frozenPackages().any { it in targets }
            if (hasFrozen && unfreezingAt == Long.MAX_VALUE) {
                // wasInSource=true 表示"刚从源头切出来"：用延迟 500ms 即可
                // 其他情况（解冻逻辑第一次运行、或之前的倒计时被重置过）：用默认 unfreezeDelayMs
                unfreezingAt = if (wasInSource) {
                    nowMs + 500L
                } else {
                    nowMs + RuleStore.unfreezeDelayMs
                }
            }
            if (nowMs >= unfreezingAt && unfreezingAt != Long.MAX_VALUE) {
                Freezer.unfreezeAll()
                unfreezingAt = Long.MAX_VALUE
            }
        }
        wasInSource = inSource
    }

    fun isInSourceOn(): Boolean = wasInSource
}
