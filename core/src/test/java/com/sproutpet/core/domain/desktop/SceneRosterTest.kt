package com.sproutpet.core.domain.desktop

import com.sproutpet.core.domain.pet.HomeSlots
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
 * [SceneRoster] —— 2026-10-20 崩溃（`家的在册上限是 6，收到 11`）的回归测试。
 *
 * 崩溃链条：首只孵化奖励送 10 抽 → 十连开出 10 只 → 加上原有的 1 只 = 11 只
 * → `ScenePetLayout.homeLayout(11)` 抛 `IllegalArgumentException` → 主线程崩。
 *
 * 这里守的是**上游**：名册永远不给出 >6 的"在场景"列表。
 */
class SceneRosterTest {

    private fun pet(
        id: Long,
        homeSlot: Int = HomeSlots.NO_SLOT,
        isHatched: Boolean = true,
        stage: PetStage = PetStage.BABY,
        powerAttr: Int = 0
    ): Pet = Pet(
        id = id, ownerId = 100L, nickname = "宠$id",
        species = PetSpecies.SNAIL, rarity = Rarity.COMMON, stage = stage,
        level = 1, exp = 0,
        attributes = PetAttributes(stamina = powerAttr, strength = powerAttr, agility = powerAttr),
        genes = PetGenes(),
        bornAt = 0L, hatchAt = 0L, isHatched = isHatched,
        spouseId = null, familyId = null, parentIdA = null, parentIdB = null, batchNo = "B1",
        homeSlot = homeSlot
    )

    // ═══════ 崩溃现场回归 ═══════

    @Test
    fun `ten pull plus the first pet never produces more than six on stage`() {
        // 原有 1 只（在家 0 号）+ 十连 10 只（都没抢到槽位，全是 -1）
        val all = listOf(pet(1, homeSlot = 0)) + (2L..11L).map { pet(it, homeSlot = HomeSlots.NO_SLOT) }
        val onStage = SceneRoster.onStage(all)

        assertEquals(11, all.size)
        assertEquals(1, onStage.size)                        // 只有真正在家的那只上台
        assertTrue(onStage.size <= HomeSlots.MAX_HOME_PETS)

        // 关键：喂给布局不再抛
        ScenePetLayout.homeLayout(onStage.size)
    }

    @Test
    fun `dirty data with nine pets marked at home is clamped to six`() {
        // 模拟历史脏数据：9 只都写着 home_slot >= 0（迁移前多个 0 号位的情况）
        val dirty = (1L..9L).map { pet(it, homeSlot = ((it - 1) % 9).toInt()) }
        val onStage = SceneRoster.onStage(dirty)

        assertEquals(HomeSlots.MAX_HOME_PETS, onStage.size)
        ScenePetLayout.homeLayout(onStage.size)              // 不抛
    }

    @Test
    fun `on stage and yard never share a pet`() {
        val all = (1L..9L).map { pet(it, homeSlot = if (it <= 6) (it - 1).toInt() else HomeSlots.NO_SLOT) }
        val stageIds = SceneRoster.onStage(all).map { it.id }.toSet()
        val yardIds = SceneRoster.yard(all).map { it.id }.toSet()

        assertTrue("名册必须互斥", stageIds.intersect(yardIds).isEmpty())
        assertEquals(all.map { it.id }.toSet(), stageIds + yardIds)   // 且不重不漏
    }

    // ═══════ 名册口径 ═══════

    @Test
    fun `eggs belong to neither list`() {
        val egg = pet(1, homeSlot = 0, isHatched = false, stage = PetStage.EGG)
        assertEquals(emptyList<Long>(), SceneRoster.onStage(listOf(egg)).map { it.id })
        assertEquals(emptyList<Long>(), SceneRoster.yard(listOf(egg)).map { it.id })
    }

    @Test
    fun `retired pets stay out of both lists even if they hold a slot`() {
        // 隐居宠不该占位（HomeSlots.validate 不变量⑥），但真脏了也不能画出幽灵
        val retired = pet(1, homeSlot = 0, stage = PetStage.RETIRED)
        val waiting = pet(2, homeSlot = HomeSlots.NO_SLOT, stage = PetStage.RETIRED)
        assertEquals(emptyList<Long>(), SceneRoster.onStage(listOf(retired)).map { it.id })
        assertEquals(emptyList<Long>(), SceneRoster.yard(listOf(waiting)).map { it.id })
    }

    @Test
    fun `stage order follows home slot so positions survive a restart`() {
        val shuffled = listOf(pet(3, homeSlot = 2), pet(1, homeSlot = 0), pet(2, homeSlot = 1))
        assertEquals(listOf(1L, 2L, 3L), SceneRoster.onStage(shuffled).map { it.id })
    }

    @Test
    fun `yard lists the strongest first`() {
        val yard = listOf(pet(1, powerAttr = 5), pet(2, powerAttr = 99), pet(3, powerAttr = 40))
        assertEquals(listOf(2L, 3L, 1L), SceneRoster.yard(yard).map { it.id })
    }
}
