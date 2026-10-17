package com.sproutpet.pet.domain.interaction

import com.sproutpet.core.domain.pet.GrowthRules
import com.sproutpet.pet.data.local.InteractionStateStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 互动冷却与日限校验器（docs/04 §2.3）。
 *
 * 统一管理每种互动的冷却（[GrowthRules.InteractionType.cooldownMs]）与
 * 每日上限（[GrowthRules.InteractionType.dailyLimit]）。
 */
@Singleton
class InteractionCooldownChecker @Inject constructor(
    private val store: InteractionStateStore
) {

    /** 校验结果 */
    sealed interface CheckResult {
        object Allowed : CheckResult
        data class OnCooldown(val remainingMs: Long) : CheckResult
        object DailyLimitReached : CheckResult
    }

    /**
     * 校验某互动是否可执行。
     *
     * 先查日限（已达上限直接拒绝），再查冷却。
     */
    suspend fun check(ownerId: Long, type: GrowthRules.InteractionType): CheckResult {
        val daily = store.dailyCount(ownerId, type.name)
        if (daily >= type.dailyLimit) return CheckResult.DailyLimitReached

        val last = store.lastInteractionAt(ownerId, type.name)
        val elapsed = System.currentTimeMillis() - last
        val remaining = type.cooldownMs - elapsed
        if (remaining > 0) return CheckResult.OnCooldown(remaining)

        return CheckResult.Allowed
    }

    /** 记录一次互动执行（更新时间戳 + 日限计数） */
    suspend fun record(ownerId: Long, type: GrowthRules.InteractionType) {
        val now = System.currentTimeMillis()
        store.setLastInteractionAt(ownerId, type.name, now)
        store.incrementDailyCount(ownerId, type.name)
    }
}
