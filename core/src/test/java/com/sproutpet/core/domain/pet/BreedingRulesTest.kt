package com.sproutpet.core.domain.pet

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 稀有度遗传规则测试。
 *
 * 覆盖 docs/06-物种与稀有度设计.md §4 的三条硬约束：
 * 1. 永不降级超过 1 档
 * 2. 永不 100% 继承（除异兽 90%）
 * 3. 异兽只能由「史诗及以上」的父母产出
 */
class BreedingRulesTest {

    private val breeding = GrowthRules.BreedingRules

    // ─────────── 硬约束 1：永不降级超过 1 档 ───────────

    @Test
    fun `mythic parents never produce below legendary`() {
        val random = Random(42)
        repeat(20_000) {
            val child = breeding.offspringRarity(
                Rarity.MYTHIC, Rarity.MYTHIC,
                GrowthRules.BreedingRules.PairingKind.SAME_FAMILY,
                random
            )
            assertTrue(
                "神话×神话产出了 ${child.displayName}，跌了 ${Rarity.MYTHIC.id - child.id} 档",
                child.id >= Rarity.LEGENDARY.id
            )
        }
    }

    @Test
    fun `mutant parents never produce below mythic`() {
        val random = Random(7)
        repeat(20_000) {
            val child = breeding.offspringRarity(
                Rarity.MUTANT, Rarity.MUTANT,
                GrowthRules.BreedingRules.PairingKind.EXOTIC_PAIR,
                random
            )
            assertTrue("异兽×异兽产出了 ${child.displayName}", child.id >= Rarity.MYTHIC.id)
        }
    }

    // ─────────── 硬约束 2：永不 100% 继承 ───────────

    @Test
    fun `mythic parents do not always produce mythic`() {
        val random = Random(1)
        val results = mutableSetOf<Rarity>()
        repeat(5_000) {
            results += breeding.offspringRarity(
                Rarity.MYTHIC, Rarity.MYTHIC,
                GrowthRules.BreedingRules.PairingKind.SAME_FAMILY,
                random
            )
        }
        assertTrue("神话×神话应当能产出传说，避免通胀", Rarity.LEGENDARY in results)
        assertTrue("神话×神话应当能产出神话", Rarity.MYTHIC in results)
    }

    @Test
    fun `mythic pair produces legend about 5 percent of the time`() {
        val random = Random(99)
        var legend = 0
        val total = 100_000
        repeat(total) {
            if (breeding.offspringRarity(
                    Rarity.MYTHIC, Rarity.MYTHIC,
                    GrowthRules.BreedingRules.PairingKind.SAME_FAMILY,
                    random
                ) == Rarity.LEGENDARY
            ) legend++
        }
        val ratio = legend.toDouble() / total
        assertTrue("传说占比 $ratio 应接近 5%", ratio in 0.03..0.07)
    }

    // ─────────── 硬约束 3：异兽的获得门槛 ───────────

    @Test
    fun `common parents never produce mutant`() {
        val random = Random(5)
        repeat(20_000) {
            val child = breeding.offspringRarity(
                Rarity.COMMON, Rarity.COMMON,
                GrowthRules.BreedingRules.PairingKind.SAME_FAMILY,
                random
            )
            assertFalse("普通家庭不应产出异兽", child == Rarity.MUTANT)
        }
    }

    @Test
    fun `epic parents can very rarely produce mutant`() {
        val random = Random(11)
        var mutant = 0
        val total = 100_000
        repeat(total) {
            if (breeding.offspringRarity(
                    Rarity.EPIC, Rarity.EPIC,
                    GrowthRules.BreedingRules.PairingKind.SAME_FAMILY,
                    random
                ) == Rarity.MUTANT
            ) mutant++
        }
        assertEquals("史诗家庭产出异兽概率应约 2%", 0.02, mutant.toDouble() / total, 0.006)
    }

    @Test
    fun `common parents never produce above legendary`() {
        val random = Random(3)
        repeat(20_000) {
            val child = breeding.offspringRarity(
                Rarity.COMMON, Rarity.COMMON,
                GrowthRules.BreedingRules.PairingKind.SAME_FAMILY,
                random
            )
            assertTrue("普通家庭不该跳过太多档位", child.id <= Rarity.LEGENDARY.id)
        }
    }

    // ─────────── 分布中心取 max 而非 avg ───────────

    @Test
    fun `distribution centers on the higher parent`() {
        val random = Random(23)
        var atLeastMythic = 0
        val total = 20_000
        repeat(total) {
            // 一方神话、一方普通 —— avg 会被拖死，max 则保持高水平
            if (breeding.offspringRarity(
                    Rarity.MYTHIC, Rarity.COMMON,
                    GrowthRules.BreedingRules.PairingKind.CROSS_FAMILY,
                    random
                ).id >= Rarity.MYTHIC.id
            ) atLeastMythic++
        }
        val ratio = atLeastMythic.toDouble() / total
        assertTrue("神话×普通仍应大概率产出神话，实际 $ratio", ratio > 0.5)
    }

    // ─────────── 跨族加成 ───────────

    @Test
    fun `cross family gets better upward odds than same family`() {
        // 用大样本（20 万）确保统计差异不被随机噪声淹没
        fun sampleUpRatio(kind: GrowthRules.BreedingRules.PairingKind): Double {
            val random = Random(77)
            var up = 0
            val total = 200_000
            repeat(total) {
                if (breeding.offspringRarity(Rarity.EPIC, Rarity.EPIC, kind, random).id > Rarity.EPIC.id) up++
            }
            return up / total.toDouble()
        }
        val same = sampleUpRatio(GrowthRules.BreedingRules.PairingKind.SAME_FAMILY)
        val cross = sampleUpRatio(GrowthRules.BreedingRules.PairingKind.CROSS_FAMILY)
        assertTrue("跨族($cross) 应当比同族($same) 更容易向上突变", cross > same)
    }

    // ─────────── 配对类型判定 ───────────

    @Test
    fun `pairing kind is derived from species families`() {
        val kind = breeding::pairingKindOf
        assertEquals(
            GrowthRules.BreedingRules.PairingKind.SAME_FAMILY,
            kind(PetSpecies.SPROUT_CHICKEN, PetSpecies.VIGOR_CHICKEN)
        )
        assertEquals(
            GrowthRules.BreedingRules.PairingKind.CROSS_FAMILY,
            kind(PetSpecies.SPROUT_CHICKEN, PetSpecies.SLEEPY_CAT)
        )
        assertEquals(
            GrowthRules.BreedingRules.PairingKind.EXOTIC_MIX,
            kind(PetSpecies.STAR_FOX, PetSpecies.SPROUT_CHICKEN)
        )
        assertEquals(
            GrowthRules.BreedingRules.PairingKind.EXOTIC_PAIR,
            kind(PetSpecies.STAR_FOX, PetSpecies.MOON_RABBIT)
        )
    }

    @Test
    fun `cross family pairing can fail`() {
        val random = Random(31)
        var failed = 0
        repeat(20_000) {
            if (!breeding.isPairingSuccessful(
                    GrowthRules.BreedingRules.PairingKind.CROSS_FAMILY, random
                )
            ) failed++
        }
        assertEquals("跨族失败率应约 15%", 0.15, failed / 20_000.0, 0.02)
    }

    // ─────────── 物种遗传 ───────────

    @Test
    fun `same family offspring mostly inherits one of the parents`() {
        // 同族仍保留极低概率（1%）的异种惊喜，因此不能断言"必为父母之一"
        val random = Random(101)
        var fromParent = 0
        val total = 20_000
        repeat(total) {
            val child = breeding.offspringSpecies(
                PetSpecies.SPROUT_CHICKEN, PetSpecies.VIGOR_CHICKEN,
                GrowthRules.BreedingRules.PairingKind.SAME_FAMILY, random = random
            )
            if (child == PetSpecies.SPROUT_CHICKEN || child == PetSpecies.VIGOR_CHICKEN) fromParent++
        }
        val ratio = fromParent / total.toDouble()
        assertTrue("同族后代应绝大多数继承父母之一，实际 $ratio", ratio > 0.97)
    }

    @Test
    fun `cross family can produce an exotic species`() {
        val random = Random(202)
        var exotic = 0
        val total = 50_000
        repeat(total) {
            val child = breeding.offspringSpecies(
                PetSpecies.SPROUT_CHICKEN, PetSpecies.SLEEPY_CAT,
                GrowthRules.BreedingRules.PairingKind.CROSS_FAMILY, random = random
            )
            if (child.family == SpeciesFamily.UNCATEGORIZED) exotic++
        }
        val ratio = exotic / total.toDouble()
        assertEquals("跨族产出异种概率应约 4%", 0.04, ratio, 0.008)
        assertTrue("跨族应能产出异种", exotic > 0)
    }

    @Test
    fun `exotic pair greatly boosts exotic offspring`() {
        val random = Random(303)
        var exotic = 0
        val total = 20_000
        repeat(total) {
            val child = breeding.offspringSpecies(
                PetSpecies.STAR_FOX, PetSpecies.MOON_RABBIT,
                GrowthRules.BreedingRules.PairingKind.EXOTIC_PAIR, random = random
            )
            if (child.family == SpeciesFamily.UNCATEGORIZED) exotic++
        }
        val ratio = exotic / total.toDouble()
        assertTrue("异种×异种产出异种概率应显著高于跨族 4%，实际 $ratio", ratio > 0.2)
    }
}
