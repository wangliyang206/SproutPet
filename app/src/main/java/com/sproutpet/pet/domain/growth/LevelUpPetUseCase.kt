package com.sproutpet.pet.domain.growth

import com.sproutpet.core.domain.pet.GrowthRules
import com.sproutpet.core.domain.pet.Pet

/**
 * 升级用例（docs/04 §2.2）：经验溢出递进 + 属性差量成长。
 *
 * 纯函数，无 Android 依赖（A4 红线）。属性按 `GrowthRules.AttributeRules.onLevelUp`
 * 做差量叠加，**不覆盖**互动/道具已加上的值（G-06 消费约定）。
 */
object LevelUpPetUseCase {

    /** 升级结果 */
    data class Result(
        /** 升级后的宠物 */
        val pet: Pet,
        /** 从多少级升到多少级；未升级则 old==new */
        val oldLevel: Int,
        val newLevel: Int
    )

    /**
     * 在当前经验基础上，把能升的级都升掉（连跳）。
     *
     * @param pet 当前宠物（level/exp/attributes 为现状）
     * @return 升级结果（属性已按差量叠加）
     */
    fun execute(pet: Pet): Result {
        var level = pet.level
        var exp = pet.exp
        var leveledUp = false
        while (level < GrowthRules.MAX_LEVEL) {
            val need = GrowthRules.expToNextLevel(level)
            if (need == Int.MAX_VALUE || exp < need) break
            exp -= need
            level++
            leveledUp = true
        }
        if (level >= GrowthRules.MAX_LEVEL) {
            level = GrowthRules.MAX_LEVEL
            exp = 0
        }
        val attributes = if (leveledUp) {
            GrowthRules.AttributeRules.onLevelUp(
                oldLevel = pet.level,
                newLevel = level,
                rarity = pet.rarity,
                tendency = pet.species.tendency,
                current = pet.attributes
            )
        } else {
            pet.attributes
        }
        return Result(
            pet = pet.copy(level = level, exp = exp, attributes = attributes),
            oldLevel = pet.level,
            newLevel = level
        )
    }
}
