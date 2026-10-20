package com.sproutpet.core.domain.gacha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 十连入口可见性（`docs/00 §2.1 入口` · `docs/14 §2.4`）。
 *
 * 守两件事：
 * 1. **首蛋期不出现徽章**（口径是"已破壳"，不是"有蛋"）；
 * 2. **够十连时点蛋不直接单抽**（否则一次误触把十连拆成 1 + 9）。
 */
class TenPullEntryTest {

    @Test
    fun `首蛋期即使门票够十连也不展示入口`() {
        assertEquals(TenPullEntry.Hidden, TenPullEntry.of(tickets = 10, hasAnyPet = false))
        assertEquals(TenPullEntry.Hidden, TenPullEntry.of(tickets = 99, hasAnyPet = false))
    }

    @Test
    fun `门票够十连时是实心 Ready`() {
        assertEquals(TenPullEntry.Ready(10), TenPullEntry.of(tickets = 10, hasAnyPet = true))
        assertEquals(TenPullEntry.Ready(23), TenPullEntry.of(tickets = 23, hasAnyPet = true))
    }

    @Test
    fun `门票不够时报还差几张`() {
        assertEquals(TenPullEntry.Short(10), TenPullEntry.of(tickets = 0, hasAnyPet = true))
        assertEquals(TenPullEntry.Short(1), TenPullEntry.of(tickets = 9, hasAnyPet = true))
    }

    @Test
    fun `负数门票按 0 处理（不出现"差 12 张"这种鬼话）`() {
        assertEquals(TenPullEntry.Short(10), TenPullEntry.of(tickets = -2, hasAnyPet = true))
    }

    @Test
    fun `够十连时点蛋先进开箱台`() {
        assertTrue(TenPullEntry.routesToSheet(GachaRules.TEN_PULL))
        assertTrue(TenPullEntry.routesToSheet(GachaRules.TEN_PULL + 5))
        assertFalse(TenPullEntry.routesToSheet(GachaRules.TEN_PULL - 1))
        assertFalse(TenPullEntry.routesToSheet(0))
    }
}
