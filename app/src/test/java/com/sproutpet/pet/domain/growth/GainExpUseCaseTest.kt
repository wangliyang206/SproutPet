package com.sproutpet.pet.domain.growth

import com.sproutpet.core.domain.pet.GrowthRules
import com.sproutpet.core.domain.pet.Pet
import com.sproutpet.core.domain.pet.PetAttributes
import com.sproutpet.core.domain.pet.PetGenes
import com.sproutpet.core.domain.pet.PetSpecies
import com.sproutpet.core.domain.pet.PetStage
import com.sproutpet.core.domain.pet.Rarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [GainExpUseCase] 测试（docs/04 §2.2 / §2.6）。
 */
class GainExpUseCaseTest {

    private fun basePet(level: Int = 1, exp: Int = 0): Pet = Pet(
        id = 1, ownerId = 100, nickname = "测",
        species = PetSpecies.SNAIL, rarity = Rarity.COMMON, stage = PetStage.BABY,
        level = level, exp = exp,
        attributes = PetAttributes(),
        genes = PetGenes(), bornAt = 0L, hatchAt = 0L, isHatched = true,
        spouseId = null, familyId = null, parentIdA = null, parentIdB = null, batchNo = "T"
    )

    @Test
    fun `非正数经验不结算`() {
        val pet = basePet()
        val result = GainExpUseCase.execute(pet, 0)
        assertEquals(pet, result.pet)
        assertEquals(false, result.leveledUp)
    }

    @Test
    fun `加经验后未达升级阈值`() {
        val result = GainExpUseCase.execute(basePet(level = 1, exp = 0), 10)
        assertEquals(1, result.pet.level)
        assertEquals(10, result.pet.exp)
        assertEquals(false, result.leveledUp)
    }

    @Test
    fun `经验足够时触发升级并记录 newLevel`() {
        val need = GrowthRules.expToNextLevel(1)
        val result = GainExpUseCase.execute(basePet(level = 1, exp = 0), need + 5)
        assertTrue(result.leveledUp)
        assertEquals(2, result.newLevel)
        assertEquals(5, result.pet.exp)
    }
}
