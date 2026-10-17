package com.sproutpet.pet.domain.interaction

import com.sproutpet.core.domain.pet.Pet

/**
 * 一次互动的执行结果。
 */
sealed interface InteractionResult {
    /** 互动成功结算 */
    data class Success(
        val pet: Pet,
        val expGained: Int,
        val intimacyGained: Int,
        val leveledUp: Boolean,
        val newLevel: Int?,
        val stageAdvanced: Boolean
    ) : InteractionResult

    /** 仍在冷却中 */
    data class OnCooldown(val remainingMs: Long) : InteractionResult

    /** 今日次数已达上限 */
    object DailyLimitReached : InteractionResult

    /** 宠物不存在 */
    object PetNotFound : InteractionResult

    /** 需要消耗道具但库存不足（如喂食缺食物、玩耍缺玩具） */
    object ItemNotEnough : InteractionResult
}
