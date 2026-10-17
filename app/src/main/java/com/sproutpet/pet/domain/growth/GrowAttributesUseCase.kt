package com.sproutpet.pet.domain.growth

import com.sproutpet.core.domain.pet.GrowthRules
import com.sproutpet.core.domain.pet.Pet

/**
 * 属性成长用例（docs/04 §2.2）：互动带来的属性副产物。
 *
 * 纯函数。只有训练（力量为主）与玩耍（敏捷）给属性，其余互动不给 ——
 * 克制是刻意的：互动主收益是经验与亲密度，属性只是副产物（见 GrowthRules.AttributeRules）。
 */
object GrowAttributesUseCase {

    /**
     * 把一次互动的属性副产物叠到宠物上。
     *
     * @return 叠加后的宠物
     */
    fun execute(pet: Pet, type: GrowthRules.InteractionType): Pet {
        val gain = GrowthRules.AttributeRules.interactionGain(type)
        if (gain == com.sproutpet.core.domain.pet.PetAttributes.ZERO) return pet
        return pet.copy(attributes = pet.attributes + gain)
    }
}
