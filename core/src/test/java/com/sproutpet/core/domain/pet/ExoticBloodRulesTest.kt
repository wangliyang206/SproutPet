package com.sproutpet.core.domain.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 灵血传承测试。
 *
 * 守住四个设计意图：
 * 1. 灵兽血统**不会"一次断代就归零"** —— 这是引入灵血的唯一理由
 * 2. 灵血**也不会永久继承** —— 否则灵兽通胀，违背 ADR-017 的初衷
 * 3. 浓度越高越容易觉醒，且**严格单调**
 * 4. 灵血**不参与属性计算** —— 守住 ADR-021
 *
 * @see GrowthRules.ExoticBloodRules
 * @see docs/06-物种与稀有度设计.md §2.4
 */
class ExoticBloodRulesTest {

    private val rules = GrowthRules.ExoticBloodRules

    // ───────────────────────── 浓度推导 ─────────────────────────

    @Test
    fun `carrier blood takes the higher of the two parents`() {
        assertEquals(3, rules.carrierBloodOf(3, 0))
        assertEquals(3, rules.carrierBloodOf(0, 3))
        assertEquals(2, rules.carrierBloodOf(2, 1))
        assertEquals(1, rules.carrierBloodOf(1, 0))
        assertEquals(0, rules.carrierBloodOf(0, 0))
    }

    @Test
    fun `carrier blood is clamped to the max`() {
        assertEquals(rules.MAX_BLOOD, rules.carrierBloodOf(99, 99))
        assertEquals(rules.MAX_BLOOD, rules.carrierBloodOf(4, 1))
    }

    @Test
    fun `exotic beast offspring always carries full blood`() {
        assertEquals(rules.MAX_BLOOD, rules.inheritedBlood(carrierBlood = 1, offspringIsExoticBeast = true))
        assertEquals(rules.MAX_BLOOD, rules.inheritedBlood(carrierBlood = 3, offspringIsExoticBeast = true))
        assertEquals(rules.MAX_BLOOD, rules.inheritedBlood(carrierBlood = 0, offspringIsExoticBeast = true))
    }

    @Test
    fun `blood decays one level per generation when offspring is not an exotic beast`() {
        assertEquals(2, rules.inheritedBlood(carrierBlood = 3, offspringIsExoticBeast = false))
        assertEquals(1, rules.inheritedBlood(carrierBlood = 2, offspringIsExoticBeast = false))
        assertEquals(0, rules.inheritedBlood(carrierBlood = 1, offspringIsExoticBeast = false))
        assertEquals(0, rules.inheritedBlood(carrierBlood = 0, offspringIsExoticBeast = false))
    }

    @Test
    fun `blood line dies out within three generations`() {
        var blood = rules.MAX_BLOOD
        var generations = 0
        while (blood > 0 && generations < 10) {
            blood = rules.inheritedBlood(blood, offspringIsExoticBeast = false)
            generations++
        }
        assertEquals("灵血应在 3 代内归零", rules.MAX_BLOOD, generations)
        assertEquals(0, blood)
    }

    // ───────────────────────── 觉醒概率 ─────────────────────────

    @Test
    fun `awaken rate is zero without blood`() {
        assertEquals(0f, rules.awakenRateOf(0), 0f)
    }

    @Test
    fun `awaken rate increases strictly with blood level`() {
        val rates = (0..rules.MAX_BLOOD).map { rules.awakenRateOf(it) }
        rates.zipWithNext().forEach { (lower, higher) ->
            assertTrue("觉醒率必须随浓度严格递增，但出现 $lower → $higher", higher > lower)
        }
    }

    @Test
    fun `awaken rate tolerates out of range blood`() {
        assertEquals(0f, rules.awakenRateOf(-5), 0f)
        assertEquals(rules.awakenRateOf(rules.MAX_BLOOD), rules.awakenRateOf(99), 0f)
    }

    @Test
    fun `no awakening when blood is zero`() {
        repeat(2_000) { seed ->
            assertFalse("无异血不应觉醒", rules.shouldAwaken(0, Random(seed)))
        }
    }

    @Test
    fun `awakening rate at full blood is about fifteen percent`() {
        var hits = 0
        val samples = 20_000
        repeat(samples) { seed ->
            if (rules.shouldAwaken(rules.MAX_BLOOD, Random(seed))) hits++
        }
        assertEquals("满灵血觉醒率应约 15%", 0.15, hits.toDouble() / samples, 0.02)
    }

    // ───────────────────── 与完整推导的整合 ─────────────────────

    @Test
    fun `offspring blood follows the child beast status`() {
        repeat(2_000) { seed ->
            val result = GrowthRules.BreedingRules.resolveOffspringRarity(
                parentA = Rarity.MYTHIC, bloodA = rules.MAX_BLOOD,
                parentB = Rarity.MYTHIC, bloodB = rules.MAX_BLOOD,
                random = Random(seed)
            )
            if (result.rarity == Rarity.MUTANT) {
                assertEquals(rules.MAX_BLOOD, result.exoticBlood)
            } else {
                assertEquals(rules.MAX_BLOOD - 1, result.exoticBlood)
            }
        }
    }

    @Test
    fun `awakening always produces an exotic beast with full blood`() {
        var awakenings = 0
        repeat(3_000) { seed ->
            val result = GrowthRules.BreedingRules.resolveOffspringRarity(
                parentA = Rarity.MYTHIC, bloodA = rules.MAX_BLOOD,
                parentB = Rarity.MYTHIC, bloodB = rules.MAX_BLOOD,
                random = Random(seed)
            )
            if (result.awakened) {
                awakenings++
                assertEquals(Rarity.MUTANT, result.rarity)
                assertEquals(rules.MAX_BLOOD, result.exoticBlood)
            }
        }
        assertTrue("满灵血配对应当出现觉醒样本", awakenings > 0)
    }

    @Test
    fun `parents without blood can never awaken`() {
        repeat(1_000) { seed ->
            val result = GrowthRules.BreedingRules.resolveOffspringRarity(
                parentA = Rarity.COMMON, bloodA = 0,
                parentB = Rarity.COMMON, bloodB = 0,
                random = Random(seed)
            )
            assertFalse("无异血不可能觉醒", result.awakened)
            assertEquals(0, result.exoticBlood)
        }
    }

    @Test
    fun `blood gives a second chance when the line would otherwise break`() {
        // 神话 × 神话 本来只有 10% 出灵兽；满灵血应显著抬高这个比例
        fun beastRatio(blood: Int): Double {
            var hits = 0
            val samples = 20_000
            repeat(samples) { seed ->
                val result = GrowthRules.BreedingRules.resolveOffspringRarity(
                    parentA = Rarity.MYTHIC, bloodA = blood,
                    parentB = Rarity.MYTHIC, bloodB = blood,
                    random = Random(seed)
                )
                if (result.rarity == Rarity.MUTANT) hits++
            }
            return hits.toDouble() / samples
        }

        val withoutBlood = beastRatio(0)
        val withFullBlood = beastRatio(rules.MAX_BLOOD)

        assertEquals("无灵血时应接近分布表的 10%", 0.10, withoutBlood, 0.015)
        assertTrue(
            "满灵血应显著提高灵兽产出（实测 $withoutBlood → $withFullBlood）",
            withFullBlood > withoutBlood + 0.05
        )
    }

    // ─────────────────── ADR-021 红线守护 ───────────────────

    @Test
    fun `blood never affects attributes`() {
        val plain = exoticBeastSample().copy(rarity = Rarity.EPIC, exoticBlood = 0)
        val carrier = plain.copy(exoticBlood = rules.MAX_BLOOD)
        assertEquals(plain.attributes, carrier.attributes)
        assertEquals(plain.power, carrier.power)
    }

    @Test
    fun `pet exposes beast and blood helpers consistently`() {
        val beast = exoticBeastSample()
        assertTrue(beast.isExoticBeast)
        assertTrue(beast.carriesExoticBlood)

        val plain = beast.copy(rarity = Rarity.COMMON, exoticBlood = 0)
        assertFalse(plain.isExoticBeast)
        assertFalse(plain.carriesExoticBlood)

        val silentCarrier = beast.copy(rarity = Rarity.MYTHIC, exoticBlood = 2)
        assertFalse(silentCarrier.isExoticBeast)
        assertTrue(silentCarrier.carriesExoticBlood)
    }

    private fun exoticBeastSample(): Pet = Pet(
        id = 1L,
        ownerId = 1L,
        nickname = "测试灵兽",
        species = PetSpecies.SLEEPY_CAT,
        rarity = Rarity.MUTANT,
        stage = PetStage.ADULT,
        level = 30,
        exp = 0,
        attributes = PetAttributes.ofLevel(30, Rarity.MUTANT, PetSpecies.SLEEPY_CAT.tendency),
        genes = PetGenes(),
        bornAt = 0L,
        hatchAt = 0L,
        isHatched = true,
        spouseId = null,
        familyId = null,
        parentIdA = null,
        parentIdB = null,
        batchNo = "test",
        exoticBlood = rules.MAX_BLOOD
    )
}
