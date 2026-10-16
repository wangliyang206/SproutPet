package com.sproutpet.core.domain.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 属性上限与派生关系（D-04 / G-10）校验。
 *
 * ## 为什么要守着"上限"
 *
 * `PetAttributes.MAX_ATTRIBUTE` 曾经是 `1000`，而游戏里的真实最高值是
 * **423**（60 级 × 灵兽 × 力量倾向的力量项）。这个 2.4 倍的差距看着无害，
 * 实际后果是：任何按文档"属性 0~1000"来设计的新玩法（进度条、转生、评分）
 * 都会**直接偏一个数量级** —— 而这种偏差在数值层面极难回头修，
 * 因为玩家已经按错的刻度建立了认知。
 *
 * 所以这里把"真实上界"算出来并钉住，让上限永远是**已知的量**而不是拍的数。
 */
class PetAttributesTest {

    @Test
    fun `the theoretical maximum attribute is well under the clamp`() {
        val maxLevel = GrowthRules.MAX_LEVEL
        val maxRarity = Rarity.entries.maxByOrNull { it.growthMultiplier }!!
        val maxWeight = AttributeTendency.entries
            .flatMap { tendency ->
                listOf(
                    tendency.staminaWeight, tendency.strengthWeight,
                    tendency.agilityWeight, tendency.wisdomWeight, tendency.charmWeight
                )
            }
            .max()

        // 与 ofLevel 内部完全同一条公式（base = 6 + level×2，再乘稀有度与倾向权重）
        val theoreticalMax = ((6 + maxLevel * 2) * maxRarity.growthMultiplier * maxWeight).toInt()

        assertTrue(
            "理论上界 $theoreticalMax 已经超过钳制值 ${PetAttributes.MAX_ATTRIBUTE}，" +
                "说明 MAX_ATTRIBUTE 该上调了（否则高等级宠的属性会被静默截断）",
            theoreticalMax <= PetAttributes.MAX_ATTRIBUTE
        )
        assertTrue(
            "MAX_ATTRIBUTE=${PetAttributes.MAX_ATTRIBUTE} 比理论上界 $theoreticalMax 大太多，" +
                "它会被文档当成设计目标来引用（历史上 1000 就是这么来的）",
            PetAttributes.MAX_ATTRIBUTE <= theoreticalMax * 2
        )
    }

    @Test
    fun `no attribute ever exceeds the clamp at any level or rarity`() {
        for (level in 0..GrowthRules.MAX_LEVEL) {
            for (rarity in Rarity.entries) {
                for (tendency in AttributeTendency.entries) {
                    val attrs = PetAttributes.ofLevel(level, rarity, tendency)
                    for (value in attrs.baseValues()) {
                        assertTrue(
                            "level=$level rarity=$rarity tendency=$tendency 产出 $value，" +
                                "超出钳制 ${PetAttributes.MAX_ATTRIBUTE}",
                            value in 1..PetAttributes.MAX_ATTRIBUTE
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `attribute growth actually moves with level and rarity`() {
        // 反向守门：确认 ofLevel 不是一个恒返回常数的假实现
        // （否则上面那两条"不超上限"的测试可以靠"全返回 1"通过）。
        val low = PetAttributes.ofLevel(1, Rarity.COMMON, AttributeTendency.BALANCED)
        val high = PetAttributes.ofLevel(GrowthRules.MAX_LEVEL, Rarity.MUTANT, AttributeTendency.BALANCED)

        assertTrue("等级/稀有度提升后属性必须变化", high.totalBase > low.totalBase)

        val epic = PetAttributes.ofLevel(30, Rarity.EPIC, AttributeTendency.BALANCED)
        val mythic = PetAttributes.ofLevel(30, Rarity.MYTHIC, AttributeTendency.BALANCED)
        assertTrue("同等级下更高稀有度应有更高属性", mythic.totalBase > epic.totalBase)
    }

    @Test
    fun `intimacy cap is a single source of truth and above the pairing gate`() {
        // G-10：MAX_INTIMACY 曾在 GrowthRules 与 PetAttributes 各定义一次，且都无人引用。
        // 现在只保留 GrowthRules 一份；这里同时钉住它与配对门槛的关系。
        assertTrue(
            "配对门槛 ${GrowthRules.PairingRules.requiredIntimacy} 必须低于亲密度上限 " +
                "${GrowthRules.MAX_INTIMACY}，否则永远配不了对",
            GrowthRules.PairingRules.requiredIntimacy < GrowthRules.MAX_INTIMACY
        )
    }

    @Test
    fun `power formula only depends on the five base attributes`() {
        val a = PetAttributes(stamina = 100, strength = 200, agility = 300, wisdom = 400, charm = 500)
        val b = PetAttributes(stamina = 100, strength = 200, agility = 300, wisdom = 400, charm = 500, intimacy = 100)

        assertEquals("亲密度不应进入战力（它是羁绊度量，不是战力）", a.power, b.power)
    }

    /** 五项基础属性（不含亲密度），便于统一遍历 */
    private fun PetAttributes.baseValues(): List<Int> =
        listOf(stamina, strength, agility, wisdom, charm)
}
