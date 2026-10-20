package com.sproutpet.core.domain.social

import com.sproutpet.core.domain.pet.GrowthRules
import com.sproutpet.core.domain.pet.Pet
import com.sproutpet.core.domain.pet.PetAttributes
import com.sproutpet.core.domain.pet.PetGender
import com.sproutpet.core.domain.pet.PetGenes
import com.sproutpet.core.domain.pet.PetSpecies
import com.sproutpet.core.domain.pet.PetStage
import com.sproutpet.core.domain.pet.Rarity
import com.sproutpet.core.domain.pet.SpeciesFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * [NpcCandidateRules] 测试 —— `W-16-1` 拍板口径（2026-10-19）逐条守住。
 *
 * 这是数值真相源类测试：改任何一个常量（候选数 / 抖动 / 名池语义）都会在这里变红，
 * 防止"拍板口径是一套、代码悄悄是另一套"。
 */
class NpcCandidateRulesTest {

    @Test
    fun `候选一次 3 只`() {
        assertEquals(3, NpcCandidateRules.CANDIDATE_COUNT)
        assertEquals(3, NpcCandidateRules.candidatesFor(myPet()).size)
    }

    @Test
    fun `物种池是 15 种五虫 - 异兽不进池`() {
        val worms = PetSpecies.entries.filter { it.family.isWorm }
        assertEquals(15, NpcCandidateRules.SPECIES_POOL.size)
        assertEquals(worms.toSet(), NpcCandidateRules.SPECIES_POOL.toSet())
        assertTrue(NpcCandidateRules.SPECIES_POOL.none { it.family == SpeciesFamily.UNCATEGORIZED })
    }

    @Test
    fun `稀有度不设字段 - 性别永远取反`() {
        val candidates = NpcCandidateRules.candidatesFor(myPet(gender = PetGender.MALE))
        // NpcCandidate 没有稀有度字段：编译期就保证了"稀有度恒等我方"只能由仓库层执行，
        // 这里守住的是性别口径
        candidates.forEach { assertEquals(PetGender.FEMALE, it.gender) }

        val flipped = NpcCandidateRules.candidatesFor(myPet(gender = PetGender.FEMALE))
        flipped.forEach { assertEquals(PetGender.MALE, it.gender) }
    }

    @Test
    fun `等级在我方 ±3 内且不越界`() {
        repeat(50) { seed ->
            val candidates = NpcCandidateRules.candidatesFor(myPet(level = 30), Random(seed))
            candidates.forEach {
                assertTrue("等级 ${it.level} 超出 27..33", it.level in 27..33)
            }
        }
        // 边界：低等级宠的候选不能 < 1
        NpcCandidateRules.candidatesFor(myPet(level = 1)).forEach {
            assertTrue(it.level >= 1)
        }
        // 边界：满级宠的候选不能 > MAX_LEVEL
        NpcCandidateRules.candidatesFor(myPet(level = GrowthRules.MAX_LEVEL)).forEach {
            assertTrue(it.level <= GrowthRules.MAX_LEVEL)
        }
    }

    @Test
    fun `名字来自名池且确定性随机可复现`() {
        val a = NpcCandidateRules.candidatesFor(myPet(), Random(42))
        val b = NpcCandidateRules.candidatesFor(myPet(), Random(42))
        assertEquals(a, b)  // 同种子同候选（UI「换一批」= 换种子）
        a.forEach { assertTrue(it.nickname in NpcCandidateRules.NAME_POOL) }
    }

    @Test
    fun `sameFamily 标记与物种类群一致`() {
        val candidates = NpcCandidateRules.candidatesFor(myPet(species = PetSpecies.SPROUT_CHICKEN))
        candidates.forEach {
            assertEquals(
                it.species.family == PetSpecies.SPROUT_CHICKEN.family,
                it.sameFamily
            )
        }
    }

    private fun myPet(
        species: PetSpecies = PetSpecies.SPROUT_CHICKEN,
        gender: PetGender = PetGender.MALE,
        level: Int = 30
    ): Pet =
        Pet(
            id = 1L, ownerId = 100L, nickname = "小豆芽",
            species = species,
            rarity = Rarity.EPIC, stage = PetStage.ADULT,
            level = level, exp = 0,
            attributes = PetAttributes(10, 10, 10, 10, 10, 80),
            genes = PetGenes.random(),
            bornAt = 0L, hatchAt = 0L, isHatched = true,
            spouseId = null, familyId = null, parentIdA = null, parentIdB = null,
            batchNo = "B-TEST",
            gender = gender
        )
}
