package com.sproutpet.core.domain.pet

import com.sproutpet.core.domain.pet.GrowthRules.MutantPityRules
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 灵兽冷启动保底测试（S15）。
 *
 * 保底只做一件事：把"看不见的运气"变成"看得见的进度"。
 * 它**不能**改变灵兽的获取通道（仍然只能生育，ADR-017），也不能影响属性（ADR-021）。
 *
 * @see docs/08-获取路径与周期测算.md §九 S15
 */
class MutantPityRulesTest {

    // ═══════════ 概率曲线 ═══════════

    @Test
    fun `no bonus before the threshold`() {
        for (streak in 0 until MutantPityRules.PITY_THRESHOLD) {
            assertEquals("连败 $streak 次不该有补偿", 0f, MutantPityRules.pityBonusOf(streak), 0f)
        }
    }

    @Test
    fun `bonus starts at two percent on the threshold and ramps`() {
        val t = MutantPityRules.PITY_THRESHOLD

        assertEquals(0.02f, MutantPityRules.pityBonusOf(t), 1e-6f)
        assertEquals(0.04f, MutantPityRules.pityBonusOf(t + 1), 1e-6f)
        assertEquals(0.20f, MutantPityRules.pityBonusOf(t + 9), 1e-6f)
    }

    @Test
    fun `bonus is capped at forty percent`() {
        val cap = MutantPityRules.MAX_PITY_RATE

        assertEquals("40% 在连败 39 次时达到", cap, MutantPityRules.pityBonusOf(39), 1e-6f)
        assertEquals(cap, MutantPityRules.pityBonusOf(40), 1e-6f)
        assertEquals(cap, MutantPityRules.pityBonusOf(9_999), 1e-6f)
    }

    @Test
    fun `bonus is monotonic`() {
        var prev = 0f
        for (streak in 0..200) {
            val cur = MutantPityRules.pityBonusOf(streak)
            assertTrue("补偿概率不能回落", cur >= prev)
            prev = cur
        }
    }

    @Test
    fun `pity is never a guarantee`() {
        assertTrue(
            "封顶后仍是概率，不能变成必出",
            MutantPityRules.MAX_PITY_RATE < 1f
        )
    }

    // ═══════════ 触发判定 ═══════════

    @Test
    fun `does not trigger below the threshold even with a zero roll`() {
        assertFalse(MutantPityRules.shouldTrigger(19, FixedRandom(0f)))
        assertFalse(MutantPityRules.shouldTrigger(0, FixedRandom(0f)))
    }

    @Test
    fun `triggers at the threshold when the roll is under the bonus`() {
        assertTrue(MutantPityRules.shouldTrigger(20, FixedRandom(0.01f)))
    }

    @Test
    fun `does not trigger when the roll exceeds the bonus`() {
        assertFalse(MutantPityRules.shouldTrigger(20, FixedRandom(0.5f)))
        assertFalse(MutantPityRules.shouldTrigger(39, FixedRandom(0.41f)))
        assertTrue(MutantPityRules.shouldTrigger(39, FixedRandom(0.39f)))
    }

    // ═══════════ 计数器推进 ═══════════

    @Test
    fun `getting a mutant resets the counter`() {
        assertEquals(0, MutantPityRules.nextStreak(29, Rarity.MYTHIC, offspringIsMutant = true))
        assertEquals(0, MutantPityRules.nextStreak(99, Rarity.MYTHIC, offspringIsMutant = true))
    }

    @Test
    fun `low rarity pairing does not count against the player`() {
        val unchanged = MutantPityRules.nextStreak(
            current = 12,
            parentTopRarity = Rarity.RARE,   // 不到史诗
            offspringIsMutant = false
        )
        assertEquals("廉价配对不该被计入，否则保底会变成对玩家的惩罚", 12, unchanged)
    }

    @Test
    fun `high rarity pairing advances the counter`() {
        assertEquals(
            13,
            MutantPityRules.nextStreak(12, Rarity.EPIC, offspringIsMutant = false)
        )
        assertEquals(
            1,
            MutantPityRules.nextStreak(0, Rarity.LEGENDARY, offspringIsMutant = false)
        )
    }

    @Test
    fun `ui progress helper counts down to the threshold`() {
        assertEquals(20, MutantPityRules.remainingToThreshold(0))
        assertEquals(1, MutantPityRules.remainingToThreshold(19))
        assertEquals(0, MutantPityRules.remainingToThreshold(20))
        assertEquals(0, MutantPityRules.remainingToThreshold(100))
    }

    // ═══════════ 与遗传主流程的集成 ═══════════

    @Test
    fun `pity can produce a mutant when randomness fails`() {
        // 连败 100 次、随机数恒为 0（即"运气差到底"）—— 保底必须兜住
        val result = GrowthRules.BreedingRules.resolveOffspringRarity(
            parentA = Rarity.MYTHIC,
            bloodA = 0,
            parentB = Rarity.MYTHIC,
            bloodB = 0,
            pityFailStreak = 100,
            random = FixedRandom(0f)
        )

        assertEquals(Rarity.MUTANT, result.rarity)
        assertTrue(result.pityTriggered)
        assertFalse("保底与灵血觉醒是两条路径，不该同时命中", result.awakened)
        assertTrue(result.compensated)
    }

    @Test
    fun `without pity streak the same roll produces no mutant`() {
        val result = GrowthRules.BreedingRules.resolveOffspringRarity(
            parentA = Rarity.MYTHIC,
            bloodA = 0,
            parentB = Rarity.MYTHIC,
            bloodB = 0,
            pityFailStreak = 0,
            random = FixedRandom(0f)
        )

        assertFalse(result.pityTriggered)
        assertFalse(result.compensated)
        assertTrue("灵兽仍只能靠概率漂移或觉醒产生", result.rarity != Rarity.MUTANT)
    }

    @Test
    fun `pity triggered mutant carries full blood like any other mutant`() {
        val result = GrowthRules.BreedingRules.resolveOffspringRarity(
            parentA = Rarity.EPIC,
            bloodA = 0,
            parentB = Rarity.EPIC,
            bloodB = 0,
            pityFailStreak = 100,
            random = FixedRandom(0f)
        )

        assertEquals(Rarity.MUTANT, result.rarity)
        assertEquals(GrowthRules.ExoticBloodRules.MAX_BLOOD, result.exoticBlood)
    }

    // ═══════════ 夹具 ═══════════

    /** 固定返回同一个浮点数的随机源，让概率判定变成确定性的。 */
    private class FixedRandom(private val value: Float) : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextFloat(): Float = value
    }
}
