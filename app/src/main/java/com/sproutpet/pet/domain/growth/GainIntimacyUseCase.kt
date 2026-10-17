package com.sproutpet.pet.domain.growth

import com.sproutpet.core.domain.pet.Pet

/**
 * 亲密度用例（docs/04 §2.2）：增加亲密度，封顶 100。
 *
 * 纯函数。上限真相源 = [com.sproutpet.core.domain.pet.GrowthRules.MAX_INTIMACY]。
 */
object GainIntimacyUseCase {

    fun execute(pet: Pet, amount: Int): Pet {
        if (amount <= 0) return pet
        val capped = (pet.attributes.intimacy + amount)
            .coerceAtMost(com.sproutpet.core.domain.pet.GrowthRules.MAX_INTIMACY)
        return pet.copy(attributes = pet.attributes.copy(intimacy = capped))
    }
}
