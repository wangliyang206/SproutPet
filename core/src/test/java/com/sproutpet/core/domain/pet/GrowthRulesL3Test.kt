package com.sproutpet.core.domain.pet

import com.sproutpet.core.domain.item.ToyRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L3 模型规则测试：`G-03` 世代计算 · `G-06` 属性消费者 · `G-08` 玩具耐久。
 */
class GrowthRulesL3Test {

    private fun pet(generation: Int): Pet = Pet(
        id = generation.toLong(), ownerId = 100L, nickname = "G$generation",
        species = PetSpecies.SNAIL, rarity = Rarity.COMMON, stage = PetStage.ADULT,
        level = 26, exp = 0, attributes = PetAttributes(), genes = PetGenes(),
        bornAt = 0L, hatchAt = 0L, isHatched = true,
        spouseId = null, familyId = null, parentIdA = null, parentIdB = null, batchNo = "B1",
        generation = generation
    )

    // ── G-03 世代 ──

    @Test
    fun `offspring generation is max of parents plus one`() {
        assertEquals(6, GrowthRules.GenerationRules.offspringGeneration(pet(2), pet(5)))
        assertEquals(6, GrowthRules.GenerationRules.offspringGeneration(pet(5), pet(2)))
    }

    @Test
    fun `first generation pets breed second generation offspring`() {
        assertEquals(2, GrowthRules.GenerationRules.offspringGeneration(pet(1), pet(1)))
    }

    @Test
    fun `first generation constant is one`() {
        assertEquals(1, GrowthRules.GenerationRules.FIRST_GENERATION)
    }

    // ── G-06 属性消费者 ──

    private fun attrs(level: Int, rarity: Rarity = Rarity.COMMON) =
        PetAttributes.ofLevel(level, rarity, AttributeTendency.BALANCED)

    @Test
    fun `level up adds the level delta on top of existing bonuses`() {
        // 起点：Lv.5 基准 + 互动攒下的 +5 力量（道具/互动加成必须保留）
        val current = attrs(5).copy(strength = attrs(5).strength + 5)
        val grown = GrowthRules.AttributeRules.onLevelUp(5, 10, Rarity.COMMON, AttributeTendency.BALANCED, current)

        val expectedDelta = attrs(10).strength - attrs(5).strength
        assertEquals(attrs(5).strength + 5 + expectedDelta, grown.strength)
        // 整算会覆盖互动加成 —— 差量方案就是为了防这个
        assertTrue("升级不得吞掉既有加成", grown.strength > attrs(10).strength)
    }

    @Test
    fun `level up keeps intimacy untouched`() {
        val current = attrs(5).copy(intimacy = 70)
        val grown = GrowthRules.AttributeRules.onLevelUp(5, 9, Rarity.COMMON, AttributeTendency.BALANCED, current)
        assertEquals(70, grown.intimacy)
    }

    @Test
    fun `same level is a no-op`() {
        val current = attrs(5)
        assertEquals(current, GrowthRules.AttributeRules.onLevelUp(5, 5, Rarity.COMMON, AttributeTendency.BALANCED, current))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `level cannot go backwards`() {
        GrowthRules.AttributeRules.onLevelUp(10, 5, Rarity.COMMON, AttributeTendency.BALANCED, attrs(10))
    }

    @Test
    fun `only train and play grant attributes`() {
        val train = GrowthRules.AttributeRules.interactionGain(GrowthRules.InteractionType.TRAIN)
        val play = GrowthRules.AttributeRules.interactionGain(GrowthRules.InteractionType.PLAY)
        val none = GrowthRules.AttributeRules.interactionGain(GrowthRules.InteractionType.PET)

        assertEquals(2, train.strength)
        assertEquals(1, play.agility)
        assertEquals(PetAttributes.ZERO, none)
        assertTrue("wisdom 与 charm 不得因互动增长（U-06：纯展示）", train.wisdom == 0 && train.charm == 0)
    }

    @Test
    fun `attribute growth clamps at max`() {
        val capped = PetAttributes(strength = PetAttributes.MAX_ATTRIBUTE).plus(
            PetAttributes(strength = 50, agility = 0, stamina = 0, wisdom = 0, charm = 0)
        )
        assertEquals(PetAttributes.MAX_ATTRIBUTE, capped.strength)
    }

    // ── G-08 玩具耐久 ──

    @Test
    fun `toy wears one per play`() {
        assertEquals(19, ToyRules.wear(20))
        assertEquals(0, ToyRules.wear(1))    // 用完这一下正好归零（保留道具、不可再用）
        assertEquals(0, ToyRules.wear(0))    // 已耗尽的玩具不再负数
        assertNull(ToyRules.wear(null))      // 非玩具没有耐久概念
    }
}
