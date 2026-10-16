package com.sproutpet.core.domain.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 五虫体系测试：确保《西游记》第五十八回如来原话的分类结构在代码中成立。
 *
 * > 「周天之内有五仙……有五虫：乃蠃、鳞、毛、羽、昆。」
 *
 * @see docs/06-物种与稀有度设计.md
 * @see docs/07-五虫体系设计.md
 */
class PetSpeciesTest {

    @Test
    fun `five worms match the original text order`() {
        // 如来原话顺序：蠃、鳞、毛、羽、昆
        assertEquals(
            listOf(
                SpeciesFamily.LUO,
                SpeciesFamily.LIN,
                SpeciesFamily.MAO,
                SpeciesFamily.YU,
                SpeciesFamily.KUN
            ),
            SpeciesFamily.FIVE_WORMS
        )
    }

    @Test
    fun `each worm has three species`() {
        SpeciesFamily.FIVE_WORMS.forEach { worm ->
            assertEquals("${worm.displayName} 应有 3 个物种", 3, PetSpecies.byFamily(worm).size)
        }
    }

    @Test
    fun `uncategorized has three species and none is drawable`() {
        val uncategorized = PetSpecies.byFamily(SpeciesFamily.UNCATEGORIZED)
        assertEquals(3, uncategorized.size)
        assertTrue("「不入十类之种」不应可从盲盒抽取", uncategorized.none { it.isDrawable })
    }

    @Test
    fun `there are eighteen species in total`() {
        // 五虫各 3 个（15）+ 不入十类 3 个 = 18
        assertEquals(18, PetSpecies.entries.size)
    }

    @Test
    fun `drawable pool is exactly the five worms`() {
        val pool = PetSpecies.drawableEntries()
        assertEquals(15, pool.size)
        assertTrue(pool.all { it.family.isWorm })
        assertTrue(pool.none { it.family == SpeciesFamily.UNCATEGORIZED })
    }

    @Test
    fun `every worm declares its deity`() {
        // 《大戴礼记·易本命》：羽虫之长凤凰、毛虫之长麒麟、鳞虫之长蛟龙、介虫之长神龟
        assertEquals("凤凰", SpeciesFamily.YU.deity)
        assertEquals("麒麟", SpeciesFamily.MAO.deity)
        assertEquals("蛟龙", SpeciesFamily.LIN.deity)
        assertEquals("神龟", SpeciesFamily.KUN.deity)
        assertEquals("圣人", SpeciesFamily.LUO.deity)
    }

    @Test
    fun `uncategorized has no deity`() {
        assertEquals("—", SpeciesFamily.UNCATEGORIZED.deity)
        assertFalse(SpeciesFamily.UNCATEGORIZED.isWorm)
        assertFalse(SpeciesFamily.UNCATEGORIZED.isDrawable)
    }

    @Test
    fun `species ids are unique`() {
        val ids = PetSpecies.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `legacy family names map to the five worms`() {
        // v1.0 / v2.0 早期的类群名需要能安全降级到五虫
        assertEquals(SpeciesFamily.YU, SpeciesFamily.fromName("BIRD"))
        assertEquals(SpeciesFamily.YU, SpeciesFamily.fromName("禽类"))
        assertEquals(SpeciesFamily.MAO, SpeciesFamily.fromName("MAMMAL"))
        assertEquals(SpeciesFamily.MAO, SpeciesFamily.fromName("OVINE"))
        assertEquals(SpeciesFamily.MAO, SpeciesFamily.fromName("FELINE"))
        assertEquals(SpeciesFamily.MAO, SpeciesFamily.fromName("猫科"))
        assertEquals(SpeciesFamily.UNCATEGORIZED, SpeciesFamily.fromName("EXOTIC"))
        assertEquals(SpeciesFamily.LUO, SpeciesFamily.fromName("LUO"))
    }

    @Test
    fun `every species declares a personality keyword`() {
        assertTrue(PetSpecies.entries.all { it.personality.isNotBlank() })
    }

    @Test
    fun `every species declares a definition for the family`() {
        assertTrue(SpeciesFamily.entries.all { it.definition.isNotBlank() })
    }

    @Test
    fun `family enum contains exactly six entries`() {
        // 五虫 + 不入十类 = 6。历史上曾误加 BIRD/MAMMAL 等"幽灵别名"污染枚举，
        // 这条断言防止再次发生。
        assertEquals(6, SpeciesFamily.entries.size)
    }

    @Test
    fun `FIVE_WORMS is a subset of entries and has five items`() {
        assertEquals(5, SpeciesFamily.FIVE_WORMS.size)
        assertTrue(SpeciesFamily.FIVE_WORMS.all { it in SpeciesFamily.entries })
        assertTrue(SpeciesFamily.UNCATEGORIZED !in SpeciesFamily.FIVE_WORMS)
    }

    // ═══════════ ADR-020：蠃虫不设神兽形象 ═══════════

    @Test
    fun `luo has no deity form while other worms do`() {
        // 《大戴礼记·易本命》「倮虫三百六十，而圣人为之长」—— 蠃虫之长是人，
        // 把"人"做成可穿戴可攀比的外观皮属结构性文化风险，故不设形象。
        assertFalse("蠃虫不得有神兽形态", SpeciesFamily.LUO.hasDeityForm)
        assertTrue(SpeciesFamily.LIN.hasDeityForm)
        assertTrue(SpeciesFamily.MAO.hasDeityForm)
        assertTrue(SpeciesFamily.YU.hasDeityForm)
        assertTrue(SpeciesFamily.KUN.hasDeityForm)
    }

    @Test
    fun `uncategorized has no deity form either`() {
        assertFalse(SpeciesFamily.UNCATEGORIZED.hasDeityForm)
    }

    @Test
    fun `luo deity name stays true to the classic text`() {
        // 不做形象 ≠ 抹掉记载。deity 字段仍忠于原文，供图鉴文案使用。
        assertEquals("圣人", SpeciesFamily.LUO.deity)
    }

    @Test
    fun `only four worms can present a deity form`() {
        val withDeity = SpeciesFamily.FIVE_WORMS.filter { it.hasDeityForm }
        assertEquals(4, withDeity.size)
    }

    // ═══════════ ADR-021：五虫不影响数值 ═══════════

    @Test
    fun `attribute tendency is not determined by family`() {
        // 若「同虫 → 倾向模式相同」，则类群被间接编码进了数值，违反 ADR-021。
        // 断言：不存在任何一个类群，其所有物种的倾向完全一致。
        SpeciesFamily.FIVE_WORMS.forEach { family ->
            val tendencies = PetSpecies.byFamily(family).map { it.tendency }.distinct()
            assertTrue(
                "类群 ${family.displayName} 的物种属性倾向过于单一（$tendencies），" +
                    "会把类群间接编码进数值，违反 ADR-021",
                tendencies.size > 1
            )
        }
    }

    @Test
    fun `every attribute tendency appears in more than one family`() {
        // 四种倾向都应跨类群分布，避免"某倾向 = 某虫"的隐式绑定。
        AttributeTendency.entries.forEach { tendency ->
            val families = PetSpecies.entries.filter { it.tendency == tendency }
                .map { it.family }.distinct()
            assertTrue(
                "属性倾向 ${tendency.displayName} 只出现在 $families，应跨类群分布",
                families.size > 1
            )
        }
    }

    @Test
    fun `species distribution across the five worms is balanced`() {
        SpeciesFamily.FIVE_WORMS.forEach { family ->
            assertEquals(
                "${family.displayName} 应有 3 个物种",
                3,
                PetSpecies.byFamily(family).size
            )
        }
    }
}

/**
 * 稀有度体系测试：6 档、异兽不可抽取。
 */
class RarityTest {

    @Test
    fun `there are six rarity tiers`() {
        assertEquals(6, Rarity.entries.size)
        assertEquals(Rarity.MUTANT, Rarity.entries.last())
    }

    @Test
    fun `mutant is not drawable`() {
        assertFalse(Rarity.MUTANT.isDrawable)
        assertTrue(Rarity.drawableEntries().none { it == Rarity.MUTANT })
    }

    @Test
    fun `drawable weight total excludes mutant`() {
        val expected = 1000 + 400 + 120 + 30 + 5
        assertEquals(expected, Rarity.totalWeight())
    }

    @Test
    fun `growth multiplier increases monotonically`() {
        val multipliers = Rarity.entries.map { it.growthMultiplier }
        assertEquals(multipliers.sorted(), multipliers)
    }

    @Test
    fun `rarity ids are contiguous from zero`() {
        Rarity.entries.forEachIndexed { index, rarity ->
            assertEquals(index, rarity.id)
        }
    }
}

/**
 * 孵化时长测试：v2.0 起必须为分钟级（首日体验铁律）。
 */
class HatchDurationTest {

    @Test
    fun `common egg hatches in minutes not hours`() {
        val duration = GrowthRules.hatchDurationOf(Rarity.COMMON)
        assertTrue("普通蛋应 ≤ 10 分钟，实际 ${duration / 60000} 分钟", duration <= 10 * 60 * 1000L)
    }

    @Test
    fun `first pet hatches faster than a normal one`() {
        val normal = GrowthRules.hatchDurationOf(Rarity.EPIC, isFirstPet = false)
        val first = GrowthRules.hatchDurationOf(Rarity.EPIC, isFirstPet = true)
        assertTrue("首只宠物应更快破壳", first < normal)
    }

    @Test
    fun `higher rarity takes longer`() {
        val common = GrowthRules.hatchDurationOf(Rarity.COMMON)
        val mythic = GrowthRules.hatchDurationOf(Rarity.MYTHIC)
        assertTrue(mythic > common)
    }
}
