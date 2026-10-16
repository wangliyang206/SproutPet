package com.sproutpet.core.domain.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 融合机制测试。
 *
 * 守住的是一条红线：**融合永远产不出灵兽**（ADR-017）。
 * 以及一条产品底线：**不可逆的误操作必须被 [FusionRules.check] 提前拦住**。
 *
 * @see docs/10-融合机制设计.md
 */
class FusionRulesTest {

    // ═══════════ 印记权重 ═══════════

    @Test
    fun `mark weight doubles with rarity`() {
        assertEquals(1, FusionRules.markWeightOf(Rarity.COMMON))
        assertEquals(2, FusionRules.markWeightOf(Rarity.RARE))
        assertEquals(4, FusionRules.markWeightOf(Rarity.EPIC))
        assertEquals(8, FusionRules.markWeightOf(Rarity.LEGENDARY))
        assertEquals(16, FusionRules.markWeightOf(Rarity.MYTHIC))
    }

    @Test
    fun `mutant can never be material`() {
        assertNull("灵兽不可作为融合材料", FusionRules.markWeightOf(Rarity.MUTANT))
        assertFalse(FusionRules.canBeMaterial(Rarity.MUTANT))
        assertTrue(FusionRules.canBeMaterial(Rarity.MYTHIC))
    }

    // ═══════════ 上漂阈值 ═══════════

    @Test
    fun `thresholds are 5 8 12 20`() {
        assertEquals(5, FusionRules.thresholdFor(Rarity.COMMON))
        assertEquals(8, FusionRules.thresholdFor(Rarity.RARE))
        assertEquals(12, FusionRules.thresholdFor(Rarity.EPIC))
        assertEquals(20, FusionRules.thresholdFor(Rarity.LEGENDARY))
    }

    @Test
    fun `mythic is the fusion ceiling`() {
        assertNull("神话之上没有可去的档位 —— 这正是融合产不出灵兽的实现方式",
            FusionRules.thresholdFor(Rarity.MYTHIC))
        assertNull(FusionRules.thresholdFor(Rarity.MUTANT))
        assertFalse(FusionRules.canBeTarget(Rarity.MYTHIC))
        assertFalse(FusionRules.canBeTarget(Rarity.MUTANT))
    }

    // ═══════════ 准入校验 ═══════════

    @Test
    fun `same species fusion is allowed`() {
        val target = pet(id = 1L, rarity = Rarity.RARE)
        val material = pet(id = 2L, rarity = Rarity.COMMON)

        assertTrue(FusionRules.check(target, material).allowed)
    }

    @Test
    fun `cross species fusion is rejected`() {
        val target = pet(id = 1L, species = PetSpecies.SNAIL)
        val material = pet(id = 2L, species = PetSpecies.KOI)

        val check = FusionRules.check(target, material)

        assertFalse(check.allowed)
        assertEquals(FusionRules.RejectReason.SPECIES_MISMATCH, check.rejectReason)
    }

    @Test
    fun `mutant is rejected on both sides`() {
        val mutant = pet(id = 1L, rarity = Rarity.MUTANT)
        val normal = pet(id = 2L, rarity = Rarity.MYTHIC)

        assertEquals(
            FusionRules.RejectReason.TARGET_IS_MUTANT,
            FusionRules.check(mutant, normal).rejectReason
        )
        assertEquals(
            FusionRules.RejectReason.MATERIAL_IS_MUTANT,
            FusionRules.check(normal, mutant).rejectReason
        )
    }

    @Test
    fun `mythic target is rejected instead of silently wasting the material`() {
        val target = pet(id = 1L, rarity = Rarity.MYTHIC)
        val material = pet(id = 2L, rarity = Rarity.MYTHIC)

        assertEquals(
            FusionRules.RejectReason.TARGET_AT_CAP,
            FusionRules.check(target, material).rejectReason
        )
    }

    @Test
    fun `a pet cannot fuse with itself`() {
        val target = pet(id = 1L)

        assertEquals(
            FusionRules.RejectReason.SAME_PET,
            FusionRules.check(target, target).rejectReason
        )
    }

    @Test
    fun `eggs cannot participate`() {
        val target = pet(id = 1L)
        val egg = pet(id = 2L, isHatched = false)

        assertEquals(
            FusionRules.RejectReason.TARGET_NOT_HATCHED,
            FusionRules.check(pet(id = 3L, isHatched = false), target).rejectReason
        )
        assertEquals(
            FusionRules.RejectReason.MATERIAL_NOT_HATCHED,
            FusionRules.check(target, egg).rejectReason
        )
    }

    @Test
    fun `ok check carries no reason`() {
        val check = FusionRules.check(pet(id = 1L), pet(id = 2L))
        assertTrue(check.allowed)
        assertNull(check.rejectReason)
    }

    // ═══════════ 结算 ═══════════

    @Test
    fun `marks accumulate without drifting below the threshold`() {
        val target = pet(id = 1L, rarity = Rarity.RARE)   // 阈值 8
        val material = pet(id = 2L, rarity = Rarity.RARE) // 权重 2

        val result = FusionRules.resolve(target, targetMarks = 3, material = material)

        assertEquals(Rarity.RARE, result.targetRarity)
        assertEquals(5, result.targetMarks)
        assertFalse(result.drifted)
    }

    @Test
    fun `crossing the threshold drifts up one tier and keeps the overflow`() {
        val target = pet(id = 1L, rarity = Rarity.RARE)      // 阈值 8
        val material = pet(id = 2L, rarity = Rarity.EPIC)    // 权重 4

        val result = FusionRules.resolve(target, targetMarks = 6, material = material)

        assertEquals(Rarity.EPIC, result.targetRarity)
        assertEquals("溢出印记必须带到下一档，玩家的投入不能白费", 2, result.targetMarks)
        assertTrue(result.drifted)
    }

    @Test
    fun `a rich material can drift two tiers at once`() {
        val target = pet(id = 1L, rarity = Rarity.COMMON)     // 阈值 5
        val material = pet(id = 2L, rarity = Rarity.MYTHIC)   // 权重 16

        val result = FusionRules.resolve(target, targetMarks = 0, material = material)

        // 16 - 5 = 11 → 11 >= 8 → 再升一档，剩 3
        assertEquals(Rarity.EPIC, result.targetRarity)
        assertEquals(3, result.targetMarks)
        assertTrue(result.drifted)
    }

    @Test
    fun `fusion can never reach the mutant tier`() {
        val target = pet(id = 1L, rarity = Rarity.LEGENDARY)  // 阈值 20
        val material = pet(id = 2L, rarity = Rarity.MYTHIC)   // 权重 16

        val result = FusionRules.resolve(target, targetMarks = 10, material = material)

        assertEquals("只能封顶到神话，绝不越界成灵兽", Rarity.MYTHIC, result.targetRarity)
        assertEquals(0, result.overflowWasted)
    }

    @Test
    fun `no input combination ever produces a mutant`() {
        // 穷举所有 (目标稀有度, 已存印记, 材料稀有度)，断言产出永不为灵兽。
        // 这是 ADR-017 红线在融合通道上的守门测试。
        // 注意：灵兽作为目标已被 check 拒绝，所以这里跳过它（resolve 的前提是 check 已通过）。
        for (targetRarity in Rarity.entries.filter { it != Rarity.MUTANT }) {
            for (marks in 0..40) {
                for (materialRarity in Rarity.entries) {
                    val result = FusionRules.resolve(
                        pet(id = 1L, rarity = targetRarity),
                        targetMarks = marks,
                        material = pet(id = 2L, rarity = materialRarity)
                    )
                    assertFalse(
                        "融合(${targetRarity.displayName}, marks=$marks, " +
                            "材料=${materialRarity.displayName}) 不该产出灵兽",
                        result.targetRarity == Rarity.MUTANT
                    )
                    assertTrue("稀有度不能倒退", result.targetRarity.id >= targetRarity.id)
                    assertTrue("印记数必须非负", result.targetMarks >= 0)
                }
            }
        }
    }

    @Test
    fun `material level converts into exp for the target`() {
        val target = pet(id = 1L, rarity = Rarity.RARE)
        val material = pet(id = 2L, rarity = Rarity.COMMON, level = 30)

        val result = FusionRules.resolve(target, targetMarks = 0, material = material)

        assertEquals(30 * FusionRules.expGainedPerMaterialLevel, result.expGained)
    }

    @Test
    fun `resolve does not mutate its inputs`() {
        val target = pet(id = 1L, rarity = Rarity.RARE)
        val material = pet(id = 2L, rarity = Rarity.EPIC)

        FusionRules.resolve(target, targetMarks = 7, material = material)

        assertEquals("纯函数不得改动入参", Rarity.RARE, target.rarity)
        assertEquals(Rarity.EPIC, material.rarity)
    }

    // ═══════════ 夹具 ═══════════

    private fun pet(
        id: Long,
        species: PetSpecies = PetSpecies.SPROUT_CHICKEN,
        rarity: Rarity = Rarity.COMMON,
        level: Int = 10,
        isHatched: Boolean = true
    ): Pet = Pet(
        id = id,
        ownerId = 1L,
        nickname = "p$id",
        species = species,
        rarity = rarity,
        stage = PetStage.ADULT,
        level = level,
        exp = 0,
        attributes = PetAttributes.ofLevel(level, rarity, species.tendency),
        genes = PetGenes(),
        bornAt = 0L,
        hatchAt = 0L,
        isHatched = isHatched,
        spouseId = null,
        familyId = null,
        parentIdA = null,
        parentIdB = null,
        batchNo = "T"
    )
}
