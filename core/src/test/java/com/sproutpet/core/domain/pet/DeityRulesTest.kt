package com.sproutpet.core.domain.pet

import com.sproutpet.core.domain.pet.GrowthRules.BreedingRules.PairingKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 神兽形态规则测试（ADR-026）。
 *
 * 守住的是一条**结构不变量**：完整神兽形态层只能由「灵兽 × 同虫」取得，
 * 它不再是"稀有度到了就自动有"的赠品，也不能靠抽取/融合得到。
 *
 * @see docs/07-五虫体系设计.md §5.1
 */
class DeityRulesTest {

    // ═══════════ 完整形态层：三条件必须同时成立 ═══════════

    @Test
    fun `mutant from same family pairing gets the full deity form`() {
        assertTrue(
            DeityRules.hasFullDeityForm(
                Rarity.MUTANT,
                SpeciesFamily.YU,
                PairingKind.SAME_FAMILY
            )
        )
    }

    @Test
    fun `mutant from cross family pairing does NOT get the full deity form`() {
        // 这是 ADR-026 唯一的代价：灵兽会分两种长相。
        // 跨虫线的独占奖励是「异兽」，所以它不再拿到神兽形态 —— 两条路各有一块领地。
        assertFalse(
            DeityRules.hasFullDeityForm(
                Rarity.MUTANT,
                SpeciesFamily.YU,
                PairingKind.CROSS_FAMILY
            )
        )
    }

    @Test
    fun `non mutant never gets the full deity form no matter the pairing`() {
        val kinds = PairingKind.entries
        for (rarity in Rarity.entries.filter { it != Rarity.MUTANT }) {
            for (kind in kinds) {
                assertFalse(
                    "${rarity.displayName} 不该有完整神兽形态",
                    DeityRules.hasFullDeityForm(rarity, SpeciesFamily.YU, kind)
                )
            }
        }
    }

    @Test
    fun `drawn pet without lineage never gets the full deity form`() {
        // 抽取得到的宠物没有配对类型 —— 无论如何都不该有完整形态
        assertFalse(DeityRules.hasFullDeityForm(Rarity.MUTANT, SpeciesFamily.YU, null))
    }

    // ═══════════ 蠃虫与不入十类：结构上就没有形态 ═══════════

    @Test
    fun `luo worm has no deity form because the sage is not depicted`() {
        assertFalse("蠃虫之长是圣人，依 ADR-020 不设形", SpeciesFamily.LUO.hasDeityForm)
        assertNull(SpeciesFamily.LUO.deityId)
        assertFalse(
            DeityRules.hasFullDeityForm(
                Rarity.MUTANT,
                SpeciesFamily.LUO,
                PairingKind.SAME_FAMILY
            )
        )
    }

    @Test
    fun `uncategorized has no deity form`() {
        assertFalse(SpeciesFamily.UNCATEGORIZED.hasDeityForm)
        assertNull(SpeciesFamily.UNCATEGORIZED.deityId)
    }

    @Test
    fun `exactly four worms grant a deity form`() {
        // 五虫 5 个减去蠃虫 1 个 = 4 个（蛟龙 / 麒麟 / 凤凰 / 神龟）
        val granters = SpeciesFamily.FIVE_WORMS.filter { it.hasDeityForm }
        assertEquals(4, granters.size)
        assertEquals(
            listOf(SpeciesFamily.LIN, SpeciesFamily.MAO, SpeciesFamily.YU, SpeciesFamily.KUN),
            granters
        )
    }

    // ═══════════ 渲染通道编号 ═══════════

    @Test
    fun `deity ids are contiguous from zero to three`() {
        val ids = SpeciesFamily.entries.mapNotNull { it.deityId }.sorted()
        assertEquals(listOf(0, 1, 2, 3), ids)
    }

    @Test
    fun `deity id matches the classical pairing`() {
        assertEquals(0, SpeciesFamily.LIN.deityId)   // 鳞虫之长 蛟龙
        assertEquals(1, SpeciesFamily.MAO.deityId)   // 毛虫之长 麒麟
        assertEquals(2, SpeciesFamily.YU.deityId)    // 羽虫之长 凤凰
        assertEquals(3, SpeciesFamily.KUN.deityId)   // 昆虫之长 神龟
    }

    @Test
    fun `deity id is only exposed when the full form was obtained`() {
        val withForm = petWith(rarity = Rarity.MUTANT, family = SpeciesFamily.YU, deity = true)
        val withoutForm = petWith(rarity = Rarity.MUTANT, family = SpeciesFamily.YU, deity = false)

        assertEquals(2, withForm.deityId)
        assertNull(withoutForm.deityId)
    }

    // ═══════════ 特征层（第一层）与形态层（第二层）的区别 ═══════════

    @Test
    fun `legendary and above carry deity features`() {
        assertFalse(DeityRules.hasDeityFeatures(Rarity.EPIC, SpeciesFamily.YU))
        assertTrue(DeityRules.hasDeityFeatures(Rarity.LEGENDARY, SpeciesFamily.YU))
        assertTrue(DeityRules.hasDeityFeatures(Rarity.MYTHIC, SpeciesFamily.YU))
    }

    @Test
    fun `deity features never leak to luo or uncategorized`() {
        assertFalse(DeityRules.hasDeityFeatures(Rarity.LEGENDARY, SpeciesFamily.LUO))
        assertFalse(DeityRules.hasDeityFeatures(Rarity.LEGENDARY, SpeciesFamily.UNCATEGORIZED))
    }

    @Test
    fun `features and full form are independent layers`() {
        // 跨虫灵兽：有特征层，但没有完整形态层 —— 这就是"两层"的意义
        val crossFamilyMutant = petWith(
            rarity = Rarity.MUTANT,
            family = SpeciesFamily.YU,
            deity = false
        )
        assertTrue("灵兽属于高稀有，应当有神兽特征层", crossFamilyMutant.hasDeityFeatures)
        assertFalse("但它不是同虫线，没有完整形态层", crossFamilyMutant.hasFullDeityForm)
    }

    // ═══════════ 出生结算 ═══════════

    @Test
    fun `resolveAtBirth mirrors the rule for same family mutants`() {
        assertTrue(
            DeityRules.resolveAtBirth(
                Rarity.MUTANT,
                PetSpecies.SCHOLAR_CHICKEN,
                PairingKind.SAME_FAMILY
            )
        )
        assertFalse(
            DeityRules.resolveAtBirth(
                Rarity.MUTANT,
                PetSpecies.STAR_FOX, // 不入十类：即便同虫也无形态可给
                PairingKind.SAME_FAMILY
            )
        )
    }

    // ═══════════ 夹具 ═══════════

    private fun petWith(
        rarity: Rarity,
        family: SpeciesFamily,
        deity: Boolean
    ): Pet {
        val species = PetSpecies.entries.first { it.family == family }
        return Pet(
            id = 1L,
            ownerId = 1L,
            nickname = "test",
            species = species,
            rarity = rarity,
            stage = PetStage.ADULT,
            level = 30,
            exp = 0,
            attributes = PetAttributes.ofLevel(30, rarity, species.tendency),
            genes = PetGenes(),
            bornAt = 0L,
            hatchAt = 0L,
            isHatched = true,
            spouseId = null,
            familyId = null,
            parentIdA = null,
            parentIdB = null,
            batchNo = "T",
            hasFullDeityForm = deity
        )
    }
}
