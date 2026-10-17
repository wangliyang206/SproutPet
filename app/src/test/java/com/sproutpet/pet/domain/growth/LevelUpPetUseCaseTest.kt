package com.sproutpet.pet.domain.growth

import com.sproutpet.core.domain.pet.GrowthRules
import com.sproutpet.core.domain.pet.Pet
import com.sproutpet.core.domain.pet.PetAttributes
import com.sproutpet.core.domain.pet.PetGenes
import com.sproutpet.core.domain.pet.PetSpecies
import com.sproutpet.core.domain.pet.PetStage
import com.sproutpet.core.domain.pet.Rarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * [LevelUpPetUseCase] 连跳升级测试（docs/04 §2.2 / §2.6）。
 */
class LevelUpPetUseCaseTest {

    private fun basePet(level: Int = 1, exp: Int = 0): Pet = Pet(
        id = 1, ownerId = 100, nickname = "测",
        species = PetSpecies.SNAIL, rarity = Rarity.COMMON, stage = PetStage.BABY,
        level = level, exp = exp,
        attributes = PetAttributes(),
        genes = PetGenes(), bornAt = 0L, hatchAt = 0L, isHatched = true,
        spouseId = null, familyId = null, parentIdA = null, parentIdB = null, batchNo = "T"
    )

    @Test
    fun `经验不足时不升级`() {
        val pet = basePet(level = 1, exp = 10)
        val result = LevelUpPetUseCase.execute(pet)
        assertEquals(1, result.newLevel)
        assertEquals(1, result.oldLevel)
        assertEquals(pet.attributes, result.pet.attributes)
    }

    @Test
    fun `刚好够升一级`() {
        val need = GrowthRules.expToNextLevel(1)
        val result = LevelUpPetUseCase.execute(basePet(level = 1, exp = need))
        assertEquals(2, result.newLevel)
        assertEquals(0, result.pet.exp)
    }

    @Test
    fun `经验溢出可连跳多级`() {
        // 1 级经验设为「升 1→2 + 2→3」之和，应连跳到 3 级且无剩余
        val need1 = GrowthRules.expToNextLevel(1)
        val need2 = GrowthRules.expToNextLevel(2)
        val result = LevelUpPetUseCase.execute(basePet(level = 1, exp = need1 + need2))
        assertEquals(3, result.newLevel)
        assertEquals(0, result.pet.exp)
    }

    @Test
    fun `升级后属性发生差量成长`() {
        val need = GrowthRules.expToNextLevel(1)
        val pet = basePet(level = 1, exp = need)
        val result = LevelUpPetUseCase.execute(pet)
        assertNotEquals(pet.attributes, result.pet.attributes)
    }

    @Test
    fun `达到满级后不再升级且经验清零`() {
        val result = LevelUpPetUseCase.execute(basePet(level = GrowthRules.MAX_LEVEL, exp = 999))
        assertEquals(GrowthRules.MAX_LEVEL, result.newLevel)
        assertEquals(0, result.pet.exp)
    }
}
