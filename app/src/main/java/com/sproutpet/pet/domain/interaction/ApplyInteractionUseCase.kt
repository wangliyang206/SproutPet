package com.sproutpet.pet.domain.interaction

import com.sproutpet.core.domain.pet.GrowthRules
import com.sproutpet.core.domain.pet.Pet

/**
 * 互动结算（纯 Kotlin，无 Android 依赖 —— A4 红线）。
 *
 * Phase 1 做「表现层闭环 + 基础持久化」：加经验/亲密度、升级用
 * `GrowthRules.expToNextLevel` 与 `AttributeRules.onLevelUp` 差量叠加；
 * 冷却与日限的严格校验属 Phase 2 的 UseCase，此处不拦。
 */
object ApplyInteractionUseCase {

    /** 结算结果 */
    data class Result(
        /** 结算后的宠物（待持久化） */
        val pet: Pet,
        /** 本次获得经验 */
        val expGained: Int,
        /** 本次获得亲密度 */
        val intimacyGained: Int,
        /** 是否升级 */
        val leveledUp: Boolean,
        /** 升级后的等级（未升级为 null） */
        val newLevel: Int? = null
    )

    /**
     * 结算一次互动。
     *
     * 升级可连跳（经验溢出继续往上升），属性按差量叠加避免覆盖互动副产物，
     * 阶段推进（幼崽→少年…）由 Phase 2 的成长 UseCase 统一处理。
     */
    fun execute(pet: Pet, type: GrowthRules.InteractionType): Result {
        var newExp = pet.exp + type.exp
        var newLevel = pet.level
        var leveledUp = false
        // 连跳升级：经验可跨级
        while (newLevel < GrowthRules.MAX_LEVEL) {
            val need = GrowthRules.expToNextLevel(newLevel)
            if (need == Int.MAX_VALUE || newExp < need) break
            newExp -= need
            newLevel++
            leveledUp = true
        }
        if (newLevel >= GrowthRules.MAX_LEVEL) {
            newLevel = GrowthRules.MAX_LEVEL
            newExp = 0
        }
        val newAttributes = if (leveledUp) {
            GrowthRules.AttributeRules.onLevelUp(
                oldLevel = pet.level,
                newLevel = newLevel,
                rarity = pet.rarity,
                tendency = pet.species.tendency,
                current = pet.attributes
            )
        } else {
            pet.attributes
        }
        // 互动副产物属性（训练/玩耍），见 GrowthRules.AttributeRules.interactionGain
        val withGain = newAttributes + GrowthRules.AttributeRules.interactionGain(type)
        val newIntimacy = (pet.attributes.intimacy + type.intimacy)
            .coerceAtMost(GrowthRules.MAX_INTIMACY)

        return Result(
            pet = pet.copy(
                level = newLevel,
                exp = newExp,
                attributes = withGain.copy(intimacy = newIntimacy)
            ),
            expGained = type.exp,
            intimacyGained = type.intimacy,
            leveledUp = leveledUp,
            newLevel = if (leveledUp) newLevel else null
        )
    }
}
