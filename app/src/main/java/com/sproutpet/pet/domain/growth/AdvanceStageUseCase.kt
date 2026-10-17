package com.sproutpet.pet.domain.growth

import com.sproutpet.core.domain.pet.GrowthRules
import com.sproutpet.core.domain.pet.Pet

/**
 * 阶段推进用例（docs/04 §2.2）：等级跨区间时切阶段。
 *
 * 纯函数。阶段-等级映射来自 [GrowthRules.stageLevelRange]，规则唯一真相源在 core。
 * 隐居阶段（RETIRED）不由本用例推进——那是用户主动行为（入后山）。
 */
object AdvanceStageUseCase {

    /**
     * 根据当前等级推进阶段。
     *
     * @return 推进后的宠物；阶段未变则原样返回
     */
    fun execute(pet: Pet): Pet {
        val targetStage = GrowthRules.stageOf(pet.level)
        // 不回退到蛋/破壳阶段（那由孵化流程控制）
        if (targetStage == pet.stage) return pet
        if (pet.stage == com.sproutpet.core.domain.pet.PetStage.EGG ||
            pet.stage == com.sproutpet.core.domain.pet.PetStage.HATCHING
        ) return pet
        return pet.copy(stage = targetStage)
    }
}
