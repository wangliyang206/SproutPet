package com.sproutpet.core.domain.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ScenePetLayout] 的多宠站位测试（`G-12`）。
 */
class ScenePetLayoutTest {

    @Test
    fun `home fits up to six in two rows of three`() {
        assertEquals(6, ScenePetLayout.homeLayout(6).size)
        assertEquals(1, ScenePetLayout.homeLayout(1).size)
        assertEquals(0, ScenePetLayout.homeLayout(0).size)
    }

    // ⚠️ 必须用块体：表达式体会把返回值变成 List，JUnit 要求 @Test 方法为 void
    @Test(expected = IllegalArgumentException::class)
    fun `home rejects seven pets`() {
        ScenePetLayout.homeLayout(7)
    }

    @Test
    fun `front row renders above back row in z order`() {
        val slots = ScenePetLayout.homeLayout(6)
        val front = slots.filter { it.y > 0.7f }
        val back = slots.filter { it.y <= 0.7f }
        assertEquals(3, front.size)
        assertEquals(3, back.size)
        assertTrue("前排 z 序必须整体高于后排", front.minOf { it.z } > back.maxOf { it.z })
        assertTrue("后排应略小以形成景深", back.all { it.scale < 1f } && front.all { it.scale == 1f })
    }

    @Test
    fun `no two home slots overlap`() {
        (1..6).forEach { n ->
            assertFalse("homeLayout($n) 出现重叠", ScenePetLayout.hasOverlap(ScenePetLayout.homeLayout(n)))
        }
    }

    @Test
    fun `desktop fits at most three in one row`() {
        assertEquals(3, ScenePetLayout.desktopLayout(3).size)
        assertEquals(1, ScenePetLayout.desktopLayout(1).size)
        assertFalse(ScenePetLayout.hasOverlap(ScenePetLayout.desktopLayout(3)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `desktop rejects four pets`() {
        ScenePetLayout.desktopLayout(4)
    }

    @Test
    fun `layout is deterministic - same count always same slots`() {
        // 「重启后大家还站在原地」是陪伴感的一部分 —— 布局本身绝不随机
        assertEquals(ScenePetLayout.homeLayout(5), ScenePetLayout.homeLayout(5))
        assertEquals(ScenePetLayout.desktopLayout(3), ScenePetLayout.desktopLayout(3))
    }

    @Test
    fun `all slots stay inside the screen bounds`() {
        (ScenePetLayout.homeLayout(6) + ScenePetLayout.desktopLayout(3)).forEach {
            assertTrue("x 越界: ${it.x}", it.x in 0f..1f)
            assertTrue("y 越界: ${it.y}", it.y in 0f..1f)
        }
    }
}
