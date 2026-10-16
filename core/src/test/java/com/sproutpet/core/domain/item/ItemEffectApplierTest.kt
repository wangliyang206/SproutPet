package com.sproutpet.core.domain.item

import com.sproutpet.core.domain.pet.Pet
import com.sproutpet.core.domain.pet.PetAttributes
import com.sproutpet.core.domain.pet.PetGenes
import com.sproutpet.core.domain.pet.PetSpecies
import com.sproutpet.core.domain.pet.PetStage
import com.sproutpet.core.domain.pet.Rarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ItemEffectApplier] 的八种效果执行测试（`G-07`：此前八种效果全部无消费者）。
 */
class ItemEffectApplierTest {

    private val bornAt = 1_000_000L
    private val hatchAt = 2_000_000L
    private val now = 1_500_000L

    private fun pet(): Pet = Pet(
        id = 1, ownerId = 100, nickname = "小蜗",
        species = PetSpecies.SNAIL, rarity = Rarity.COMMON, stage = PetStage.BABY,
        level = 5, exp = 100,
        attributes = PetAttributes(stamina = 10, strength = 20, agility = 30, wisdom = 40, charm = 50, intimacy = 60),
        genes = PetGenes(), bornAt = bornAt, hatchAt = hatchAt, isHatched = false,
        spouseId = null, familyId = null, parentIdA = null, parentIdB = null, batchNo = "B1"
    )

    @Test
    fun `reduce hatch time shortens but never before birth`() {
        val out = ItemEffectApplier.apply(listOf(ItemEffect.ReduceHatchTime(300_000L)), pet(), now)
        assertEquals(hatchAt - 300_000L, out.pet!!.hatchAt)
        assertEquals(300_000L, out.hatchMsReduced)

        // 超额缩短被钳到出生时刻 —— 蛋的"孵化年龄"不能变负
        val clamped = ItemEffectApplier.apply(listOf(ItemEffect.ReduceHatchTime(9_000_000L)), pet(), now)
        assertEquals(bornAt, clamped.pet!!.hatchAt)
        assertEquals(hatchAt - bornAt, clamped.hatchMsReduced)
    }

    @Test
    fun `add exp and intimacy clamp correctly`() {
        val out = ItemEffectApplier.apply(
            listOf(ItemEffect.AddExp(200), ItemEffect.AddIntimacy(999)), pet(), now)
        assertEquals(300, out.pet!!.exp)
        assertEquals(100, out.pet!!.attributes.intimacy)   // 上限 100
    }

    @Test
    fun `add attribute clamps at max`() {
        val big = pet().copy(attributes = pet().attributes.copy(strength = PetAttributes.MAX_ATTRIBUTE))
        val out = ItemEffectApplier.apply(listOf(ItemEffect.AddAttribute(AttributeType.STRENGTH, 5)), big, now)
        assertEquals(PetAttributes.MAX_ATTRIBUTE, out.pet!!.attributes.strength)
    }

    @Test
    fun `advance stage moves the lifecycle pointer`() {
        val out = ItemEffectApplier.apply(listOf(ItemEffect.AdvanceStage(PetStage.JUVENILE)), pet(), now)
        assertEquals(PetStage.JUVENILE, out.pet!!.stage)
    }

    @Test
    fun `wallet affection and guarantee effects do not touch the pet`() {
        val p = pet()
        val out = ItemEffectApplier.apply(
            listOf(ItemEffect.AddGold(300), ItemEffect.AddAffection(10),
                ItemEffect.GuaranteeRarity(Rarity.EPIC)), p, now)

        assertEquals(p, out.pet)                       // 宠物原样返回
        assertEquals(300, out.walletGoldDelta)
        assertEquals(10, out.affectionDelta)
        assertEquals(Rarity.EPIC, out.guaranteedRarity)
    }

    @Test
    fun `all effects compose in one pass`() {
        val out = ItemEffectApplier.apply(
            listOf(
                ItemEffect.AddExp(50), ItemEffect.AddIntimacy(5),
                ItemEffect.AddAttribute(AttributeType.AGILITY, 2), ItemEffect.AddGold(-100)
            ), pet(), now)

        assertEquals(150, out.pet!!.exp)
        assertEquals(65, out.pet!!.attributes.intimacy)
        assertEquals(32, out.pet!!.attributes.agility)
        assertEquals(-100, out.walletGoldDelta)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `pet dependent effect without a pet fails fast instead of silently doing nothing`() {
        ItemEffectApplier.apply(listOf(ItemEffect.AddExp(20)), pet = null, now = now)
    }

    @Test
    fun `registry now contains toys with durability semantics`() {
        // G-08：TOY 分类不再为空，且玩具不可堆叠（有独立耐久）
        val toys = ItemRegistry.all.filter { it.category == ItemCategory.TOY }
        assertTrue("TOY 分类至少要有毛线球与飞盘", toys.size >= 2)
        assertTrue(toys.all { !it.stackable && !it.consumable })
        assertTrue(toys.all { it.shopPriceGold != null })
    }
}
