package com.sproutpet.core.domain.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [HomeSlots] 的槽位不变量测试（`U-12` 三层数量链条 + `G-05` 三字段）。
 */
class HomeSlotsTest {

    private fun pet(
        id: Long = 1L,
        ownerId: Long = 100L,
        nickname: String = "小蜗",
        isMain: Boolean = false,
        homeSlot: Int = HomeSlots.NO_SLOT,
        desktopSlot: Int = HomeSlots.NO_SLOT,
        stage: PetStage = PetStage.BABY
    ): Pet = Pet(
        id = id, ownerId = ownerId, nickname = nickname,
        species = PetSpecies.SNAIL, rarity = Rarity.COMMON, stage = stage,
        level = 5, exp = 0,
        attributes = PetAttributes(), genes = PetGenes(),
        bornAt = 0L, hatchAt = 0L, isHatched = true,
        spouseId = null, familyId = null, parentIdA = null, parentIdB = null, batchNo = "B1",
        isMain = isMain, homeSlot = homeSlot, desktopSlot = desktopSlot
    )

    private val mainPet
        get() = pet(id = 1, nickname = "小豆芽", isMain = true, homeSlot = 0, desktopSlot = 0)

    @Test
    fun `a valid family passes with zero violations`() {
        val pets = listOf(
            mainPet,
            pet(id = 2, nickname = "瞌睡猫", homeSlot = 1, desktopSlot = 1),
            pet(id = 3, nickname = "慢慢", homeSlot = 2)          // 在家未上桌
        )
        assertEquals(emptyList<String>(), HomeSlots.validate(pets))
    }

    @Test
    fun `desktop is a subset of home`() {
        val bad = pet(id = 2, homeSlot = HomeSlots.NO_SLOT, desktopSlot = 1)
        assertTrue(HomeSlots.validate(listOf(mainPet, bad)).any { "不在家" in it })
    }

    @Test
    fun `exactly one main pet is required`() {
        assertTrue(HomeSlots.validate(listOf(pet(id = 2, homeSlot = 0))).any { "没有主宠" in it })
        val two = listOf(mainPet, pet(id = 3, nickname = "第二只", isMain = true, homeSlot = 1, desktopSlot = 0))
        assertTrue(HomeSlots.validate(two).any { "主宠" in it && "1 只" in it })
    }

    @Test
    fun `main pet always owns desktop slot zero`() {
        val fakeMain = pet(id = 1, isMain = true, homeSlot = 0, desktopSlot = 1)
        assertTrue(HomeSlots.validate(listOf(fakeMain)).any { "主宠桌面槽位" in it })

        val squatter = pet(id = 2, homeSlot = 1, desktopSlot = 0)   // 占 0 号但不是主宠
        assertTrue(HomeSlots.validate(listOf(mainPet, squatter)).any { "0 号但不是主宠" in it })
    }

    @Test
    fun `retired pets hold no slots`() {
        val retired = pet(id = 2, nickname = "长辈", homeSlot = 3, stage = PetStage.RETIRED)
        assertTrue(HomeSlots.validate(listOf(mainPet, retired)).any { "隐居" in it })
    }

    @Test
    fun `home slots cannot be shared and have a hard cap of six`() {
        val six = (1..6L).map { pet(id = it, homeSlot = (it - 1).toInt()) }
            .let { listOf(mainPet) + it }
        assertTrue(HomeSlots.validate(six).any { "上限" in it })

        val dup = listOf(mainPet, pet(id = 2, homeSlot = 0))
        assertTrue(HomeSlots.validate(dup).any { "家槽位 0" in it })
    }

    @Test
    fun `next free home slot returns null when full instead of erroring`() {
        val pets = (1L..6L).map { pet(id = it, homeSlot = (it - 1).toInt()) }
        assertNull(HomeSlots.nextFreeHomeSlot(pets))
        assertEquals(0, HomeSlots.nextFreeHomeSlot(emptyList()))
    }

    @Test
    fun `main pet always claims desktop slot zero`() {
        assertEquals(0, HomeSlots.nextFreeDesktopSlot(listOf(pet(id = 9, desktopSlot = 1)), isMain = true))
        val full = (0L..2L).map { pet(id = it, desktopSlot = it.toInt()) }
        assertNull(HomeSlots.nextFreeDesktopSlot(full, isMain = false))
        assertEquals(1, HomeSlots.nextFreeDesktopSlot(listOf(mainPet), isMain = false))
    }
}
