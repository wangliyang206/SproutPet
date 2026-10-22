package com.sproutpet.core.domain.pet

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [EggPresentation] —— 2026-10-22 真机「大蛋慢慢消失，只剩背景」的回归测试。
 *
 * 那次故障的机制不是"蛋被删了"，而是**两个显隐判据各写各的**：
 * 聚焦层按 `egg != null && !hasAnyPet` 决定，角落蛋又按"聚焦层应该显示吗"决定。
 * 判据一分叉，就会出现「聚焦层没显示 + 角落蛋被隐藏」= 一个蛋都看不见。
 *
 * 这里把位置收敛成三态，并守两条不变量：
 * 1. 有蛋 ⇒ 一定有一个展示位（FOCUS 或 CORNER，永不落空）；
 * 2. FOCUS 与 CORNER 互斥（不会两颗蛋同屏）。
 */
class EggPresentationTest {

    private fun egg(id: Long = 1L): Pet = Pet(
        id = id, ownerId = 100L, nickname = "蛋$id",
        species = PetSpecies.SNAIL, rarity = Rarity.COMMON, stage = PetStage.EGG,
        level = 0, exp = 0,
        attributes = PetAttributes(),
        genes = PetGenes(),
        bornAt = 0L, hatchAt = 60_000L, isHatched = false,
        spouseId = null, familyId = null, parentIdA = null, parentIdB = null, batchNo = "B1",
        homeSlot = HomeSlots.NO_SLOT
    )

    @Test
    fun `first egg with no hatched pet goes to the focus stage`() {
        assertEquals(EggPresentation.FOCUS, EggPresentation.of(egg(), hasAnyHatchedPet = false))
    }

    @Test
    fun `an egg alongside hatched pets stays in the corner`() {
        // 十连出的第 2~10 颗蛋：已经有宠物了，不能再全屏压暗
        assertEquals(EggPresentation.CORNER, EggPresentation.of(egg(), hasAnyHatchedPet = true))
    }

    @Test
    fun `no egg means no stage`() {
        assertEquals(EggPresentation.NONE, EggPresentation.of(null, hasAnyHatchedPet = false))
        assertEquals(EggPresentation.NONE, EggPresentation.of(null, hasAnyHatchedPet = true))
    }

    @Test
    fun `an incubating egg always has exactly one place to show`() {
        // 不变量：有蛋必须有位置（这是"只剩背景"的直接反例）——
        // 三态里两个是"可见位置"，NONE 只属于「没有蛋」
        listOf(true, false).forEach { hasPet ->
            val stage = EggPresentation.of(egg(), hasAnyHatchedPet = hasPet)
            assertEquals(
                "有蛋时不允许落到 NONE（否则场景会一颗蛋都没有）",
                true,
                stage == EggPresentation.FOCUS || stage == EggPresentation.CORNER
            )
        }
        assertEquals(EggPresentation.NONE, EggPresentation.of(null, hasAnyHatchedPet = true))
    }

    // ═══════ 角落蛋兜底（2026-10-22「只剩背景」的回归） ═══════

    @Test
    fun `corner egg stays hidden while the focus stage is really showing`() {
        // 聚焦层显示时藏角落蛋 —— 否则会看到"两颗蛋"
        assertEquals(
            false,
            EggPresentation.cornerVisible(EggPresentation.FOCUS, focusActuallyShown = true)
        )
    }

    @Test
    fun `corner egg falls back to visible when the focus stage is not actually shown`() {
        // ⭐ 核心回归：聚焦层"应该显示"但实际没显示（动画被打断 / view 重建 / 落蛋失败）
        //    → 角落蛋必须兜底可见，绝不能两处都藏
        assertEquals(
            true,
            EggPresentation.cornerVisible(EggPresentation.FOCUS, focusActuallyShown = false)
        )
    }

    @Test
    fun `corner egg is always visible when it owns the stage`() {
        assertEquals(
            true,
            EggPresentation.cornerVisible(EggPresentation.CORNER, focusActuallyShown = false)
        )
        // 角落本来就是"可开箱"入口（没有蛋）—— 也得可见
        assertEquals(
            true,
            EggPresentation.cornerVisible(EggPresentation.NONE, focusActuallyShown = false)
        )
    }

    @Test
    fun `focus is exclusive to the very first egg`() {
        // 不变量②：FOCUS 只在「有蛋 + 一只已破壳的都没有」时成立 —— 互斥由 when 保证
        assertEquals(EggPresentation.FOCUS, EggPresentation.of(egg(), false))
        assertEquals(EggPresentation.CORNER, EggPresentation.of(egg(), true))
    }
}
