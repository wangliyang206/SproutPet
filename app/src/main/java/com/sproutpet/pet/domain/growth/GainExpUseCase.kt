package com.sproutpet.pet.domain.growth

import com.sproutpet.core.domain.pet.Pet

/**
 * 经验获取用例（docs/04 §2.2）：统一入口，叠加经验并触发升级/阶段推进。
 *
 * 纯函数，**不**处理每日上限 —— 每日上限的拦截由调用方
 * （互动 UseCase / InteractionCooldownChecker）在调用前完成，
 * 因为"今天已经得了多少经验"是账号级状态，纯函数层不持有。
 *
 * 内部编排：加经验 → [LevelUpPetUseCase] 连跳升级 → [AdvanceStageUseCase] 切阶段。
 */
object GainExpUseCase {

    /** 结算结果 */
    data class Result(
        val pet: Pet,
        val expGained: Int,
        val leveledUp: Boolean,
        val newLevel: Int?,
        val stageAdvanced: Boolean
    )

    fun execute(pet: Pet, expToAdd: Int): Result {
        if (expToAdd <= 0) return Result(pet, 0, false, null, false)
        val withExp = pet.copy(exp = pet.exp + expToAdd)
        val levelResult = LevelUpPetUseCase.execute(withExp)
        val stageResult = AdvanceStageUseCase.execute(levelResult.pet)
        return Result(
            pet = stageResult,
            expGained = expToAdd,
            leveledUp = levelResult.oldLevel != levelResult.newLevel,
            newLevel = if (levelResult.oldLevel != levelResult.newLevel) levelResult.newLevel else null,
            stageAdvanced = stageResult.stage != pet.stage
        )
    }
}
