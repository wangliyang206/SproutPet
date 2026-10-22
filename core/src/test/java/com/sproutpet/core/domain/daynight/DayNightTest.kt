package com.sproutpet.core.domain.daynight

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 昼夜边界（`docs/02 §10`：18:00 ~ 次日 06:00 为夜）。
 *
 * 这些用例存在的理由：冷启动首帧背景与场景背景必须用**同一个**判据，
 * 否则夜间启动会白闪一下（2026-10-22 真机反馈）。
 */
class DayNightTest {

    @Test
    fun `18点到23点都是夜晚`() {
        (18..23).forEach { assertTrue("$it 点应为夜晚", DayNight.isNight(it)) }
    }

    @Test
    fun `0点到5点是夜晚`() {
        (0..5).forEach { assertTrue("$it 点应为夜晚", DayNight.isNight(it)) }
    }

    @Test
    fun `6点到17点是白天`() {
        (6..17).forEach { assertFalse("$it 点应为白天", DayNight.isNight(it)) }
    }

    @Test
    fun `边界 18 属于夜晚 6 属于白天`() {
        assertTrue("入夜时刻含 18", DayNight.isNight(DayNight.NIGHT_START_HOUR))
        assertFalse("天亮时刻不含 6", DayNight.isNight(DayNight.NIGHT_END_HOUR))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `非法小时数必须报错而不是静默当成白天`() {
        DayNight.isNight(24)
    }
}
