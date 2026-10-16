package com.sproutpet.core.domain.incubation

import com.sproutpet.core.domain.pet.GrowthRules
import com.sproutpet.core.domain.pet.PetSpecies
import com.sproutpet.core.domain.pet.Rarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 盲盒抽取的安全边界测试。
 *
 * 重点守住 **ADR-017**：灵兽（[Rarity.MUTANT]）只能靠遗传获得，**绝不可抽取**。
 * 抽取入口是"灵兽不可抽"这条规则的唯一薄弱面 —— 任何新增的
 * "保底 / 指定稀有度"能力都必须在这里被拦下。
 */
class BlindBoxEngineTest {

    // ─────────────────── 常规抽取不得越界 ───────────────────

    @Test
    fun `drawing never yields a non drawable rarity`() {
        repeat(5_000) {
            val outcome = BlindBoxEngine.draw()
            assertTrue(
                "抽到了不可抽取的稀有度：${outcome.result.rarity.displayName}",
                outcome.result.rarity.isDrawable
            )
        }
    }

    @Test
    fun `drawing never yields a non drawable species`() {
        repeat(5_000) {
            val outcome = BlindBoxEngine.draw()
            assertTrue(
                "抽到了不可抽取的物种：${outcome.result.species.displayName}",
                outcome.result.species.isDrawable
            )
        }
    }

    @Test
    fun `a pool of exotic species only falls back to the drawable pool`() {
        val exoticOnly = PetSpecies.entries.filter { !it.isDrawable }
        assertTrue("前置条件：应当存在不可抽取物种", exoticOnly.isNotEmpty())
        repeat(200) {
            val outcome = BlindBoxEngine.draw(pool = exoticOnly)
            assertTrue(outcome.result.species.isDrawable)
        }
    }

    // ──────────── 保底道具不得绕开 ADR-017 ────────────

    @Test
    fun `guaranteeing an exotic beast is capped at the highest drawable tier`() {
        repeat(2_000) {
            val outcome = BlindBoxEngine.draw(guaranteedRarity = Rarity.MUTANT)
            assertFalse(
                "保底道具绕开了 ADR-017，抽出了灵兽",
                outcome.result.rarity == Rarity.MUTANT
            )
        }
    }

    @Test
    fun `guaranteeing an exotic beast still gives the capped tier`() {
        repeat(500) {
            val outcome = BlindBoxEngine.draw(guaranteedRarity = Rarity.MUTANT)
            assertEquals(
                "被钳制后应当保底到最高可抽档",
                Rarity.maxDrawable,
                outcome.result.rarity
            )
        }
    }

    @Test
    fun `a legitimate guarantee still acts as a floor`() {
        repeat(500) {
            val outcome = BlindBoxEngine.draw(guaranteedRarity = Rarity.EPIC)
            assertTrue(outcome.result.rarity.id >= Rarity.EPIC.id)
        }
    }

    @Test
    fun `pity never escalates beyond the highest drawable tier`() {
        repeat(500) {
            val outcome = BlindBoxEngine.draw(pityCount = BlindBoxEngine.PITY_THRESHOLD)
            assertTrue(outcome.result.rarity.isHighRarity)
            assertTrue(outcome.result.rarity.isDrawable)
        }
    }

    // ─────────────────── 权重表自洽 ───────────────────

    @Test
    fun `total weight excludes the non drawable tier`() {
        assertEquals(1555, Rarity.totalWeight())
    }

    @Test
    fun `max drawable tier is mythic`() {
        assertEquals(Rarity.MYTHIC, Rarity.maxDrawable)
    }

    @Test
    fun `exotic beast tier carries zero weight`() {
        assertEquals(0, Rarity.MUTANT.baseWeight)
        assertFalse(Rarity.MUTANT.isDrawable)
    }

    // ─────────────────── 专注陪伴的抵扣上限 ───────────────────
    //
    // `GrowthRules.MAX_FOCUS_BOOST_RATIO`（"每颗蛋最多抵扣 50%"）此前**没有任何读取方** ——
    // PRD §2.2 承诺的上限在代码里根本不存在。这组测试守住它的唯一执法点。

    @Test
    fun `focus boost never exceeds half of the remaining hatch time`() {
        val oneHour = 60L * 60L * 1000L
        val remaining = 10 * oneHour

        // 陪了 20 小时（远超单颗蛋的剩余时间），最多也只能抵掉一半
        val boosted = IncubationEngine.focusBoostMs(20 * oneHour, remaining)

        assertEquals(
            "专注陪伴没有抵扣上限，玩家可以把整颗蛋直接抵完",
            (remaining * GrowthRules.MAX_FOCUS_BOOST_RATIO).toLong(),
            boosted
        )
    }

    @Test
    fun `focus boost below the cap is applied in full`() {
        val oneHour = 60L * 60L * 1000L
        val remaining = 10 * oneHour
        val focus = 2 * oneHour  // 2h × 50% = 1h，远低于 5h 的上限

        assertEquals(oneHour, IncubationEngine.focusBoostMs(focus, remaining))
    }

    @Test
    fun `focus boost is zero when there is nothing to boost`() {
        val oneHour = 60L * 60L * 1000L
        assertEquals(0L, IncubationEngine.focusBoostMs(0L, oneHour))
        assertEquals(0L, IncubationEngine.focusBoostMs(oneHour, 0L))
        assertEquals(0L, IncubationEngine.focusBoostMs(-oneHour, oneHour))
    }

    @Test
    fun `the focus boost ratio has exactly one definition`() {
        // G-10：本文件曾私有一个同值的 `private const FOCUS_BOOST_RATE`，
        // 与 GrowthRules.FOCUS_BOOST_RATIO 构成"同一数值两处定义"。
        // 现在只剩数值中枢那一份，这里用行为断言把重复定义钉死：
        // 抵扣量必须严格等于 GrowthRules 的比例 —— 若有人再复制一份并改掉它，这条会红。
        val focus = 4L * 60L * 60L * 1000L
        assertEquals(
            (focus * GrowthRules.FOCUS_BOOST_RATIO).toLong(),
            IncubationEngine.focusBoostMs(focus, Long.MAX_VALUE / 2)
        )
    }
}
