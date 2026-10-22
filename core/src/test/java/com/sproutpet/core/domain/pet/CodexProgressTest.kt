package com.sproutpet.core.domain.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CodexProgress] —— 图鉴三个维度的判据。
 *
 * 起因（2026-10-20 真机）：「左侧图鉴显示 已收集 1/18，实际已经有 7 只了」。
 * 那个 `1` 是抽屉里写死的常量；这里守的是**真正的计数口径**，
 * 免得下次又从某个 UI 里冒出一个手写的数字。
 */
class CodexProgressTest {

    private fun pet(
        id: Long,
        species: PetSpecies,
        isHatched: Boolean = true,
        hasFullDeityForm: Boolean = false,
        isRetired: Boolean = false
    ): Pet = Pet(
        id = id, ownerId = 100L, nickname = "宠$id",
        species = species, rarity = Rarity.COMMON,
        stage = if (isRetired) PetStage.ELDER else PetStage.ADULT,
        level = 1, exp = 0,
        attributes = PetAttributes(),
        genes = PetGenes(),
        bornAt = 0L, hatchAt = 0L, isHatched = isHatched,
        spouseId = null, familyId = null, parentIdA = null, parentIdB = null, batchNo = "B1",
        hasFullDeityForm = hasFullDeityForm
    )

    // ═══════ 分母（这些数字不能随手改，改了图鉴的"还差几个"全错） ═══════

    @Test
    fun `totals are 18 species, 6 families, 4 deity slots`() {
        val p = CodexProgress.EMPTY
        assertEquals(18, p.speciesTotal)   // 五虫 15 + 不入十类 3
        assertEquals(6, p.familiesTotal)   // 蠃鳞毛羽昆 + 不入十类
        // ⚠️ 不是 5 —— 蠃虫之神是「人」，按 ADR-020 不设形象
        assertEquals(4, p.deitiesTotal)
        assertEquals(0, p.speciesSeen)
        assertEquals(0, p.deitiesSeen)
    }

    // ═══════ 「见」= 已破壳 ═══════

    @Test
    fun `eggs do not count as seen`() {
        val list = listOf(
            pet(1, PetSpecies.SNAIL, isHatched = true),
            pet(2, PetSpecies.KOI, isHatched = false)   // 还是蛋
        )
        val p = CodexProgress.of(list)
        assertEquals(1, p.speciesSeen)
        assertTrue(p.isCollected(PetSpecies.SNAIL))
        assertFalse(p.isCollected(PetSpecies.KOI))
    }

    @Test
    fun `same species raised many times still counts once`() {
        // 「7 只宠物」不等于「7 种」—— 这正是用户以为图鉴坏了的来源
        val list = (1L..7L).map { pet(it, PetSpecies.SPROUT_CHICKEN) }
        val p = CodexProgress.of(list)
        assertEquals(1, p.speciesSeen)
        assertEquals(1, p.familiesSeen)   // 全在羽虫
    }

    @Test
    fun `retired pets still count`() {
        val list = listOf(pet(1, PetSpecies.SNAIL, isRetired = true))
        assertEquals(1, CodexProgress.of(list).speciesSeen)
    }

    // ═══════ 类群 ═══════

    @Test
    fun `families seen is per-family not per-pet`() {
        val list = listOf(
            pet(1, PetSpecies.SNAIL),        // 蠃虫
            pet(2, PetSpecies.BUBBLE_FROG),  // 蠃虫
            pet(3, PetSpecies.KOI),          // 鳞虫
            pet(4, PetSpecies.STAR_FOX)      // 不入十类
        )
        val p = CodexProgress.of(list)
        assertEquals(4, p.speciesSeen)           // 四个不同物种
        assertEquals(3, p.familiesSeen)          // 但只走了蠃 / 鳞 / 不入十类三个类群
        assertEquals(2, p.speciesSeenIn(SpeciesFamily.LUO))
        assertEquals(3, p.speciesTotalIn(SpeciesFamily.LUO))
    }

    // ═══════ 神兽位 ═══════

    @Test
    fun `deity slot counts only full deity forms`() {
        val list = listOf(
            pet(1, PetSpecies.LITTLE_DRAGON, hasFullDeityForm = true),   // 蛟龙
            pet(2, PetSpecies.KOI, hasFullDeityForm = false)             // 普通锦鲤
        )
        val p = CodexProgress.of(list)
        assertTrue(p.hasDeity(SpeciesFamily.LIN))
        assertEquals(1, p.deitiesSeen)
    }

    @Test
    fun `luo never has a deity slot even with the flag set`() {
        // 脏数据 / 未来若有人给蠃虫写了 deityForm=true，图鉴也不能亮这一格：
        // 「圣人」按典籍不设形（ADR-020 反向红线）
        val list = listOf(pet(1, PetSpecies.SNAIL, hasFullDeityForm = true))
        val p = CodexProgress.of(list)
        assertFalse(p.familyHasDeitySlot(SpeciesFamily.LUO))
        assertFalse(p.hasDeity(SpeciesFamily.LUO))
        assertEquals(0, p.deitiesSeen)   // 分母 4 里它本来就不占位
    }

    @Test
    fun `uncategorized family has neither deity nor slot`() {
        assertFalse(SpeciesFamily.UNCATEGORIZED.hasDeityForm)
        assertFalse(CodexProgress.EMPTY.hasDeity(SpeciesFamily.UNCATEGORIZED))
        assertTrue(CodexProgress.EMPTY.familyHasDeitySlot(SpeciesFamily.LIN))
    }

    @Test
    fun `empty roster is empty progress`() {
        assertEquals(CodexProgress.EMPTY, CodexProgress.of(emptyList()))
        assertEquals(
            CodexProgress.EMPTY,
            CodexProgress.of(listOf(pet(1, PetSpecies.SNAIL, isHatched = false)))
        )
    }
}
