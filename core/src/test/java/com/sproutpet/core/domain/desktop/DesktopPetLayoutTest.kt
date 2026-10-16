package com.sproutpet.core.domain.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌宠屏幕布局与拖动手势测试。
 *
 * 这两组规则都是**真机实测踩坑**后补上的，见 `docs/99-桌宠技术验证操作单.md` §六：
 * 1. 宠物被拖出屏幕（找不回来）→ [DesktopPetLayout.clamp]
 * 2. 慢速拖动完全失效（单帧位移被丢弃）→ [DragGestureTracker]
 *
 * 测试里用的是真机上的真实数值：Mi 9 Pro 5G 屏宽 1080、宠物 302px、合法 x 上限 778。
 */
class DesktopPetLayoutTest {

    private companion object {
        const val PET = 302
        const val SCREEN_W = 1080
        const val SCREEN_H = 2340

        /** 真机探针里配置的拖动阈值 */
        const val SLOP = 6f
    }

    // ═══════════ 位置钳制 ═══════════

    @Test
    fun `position inside screen is untouched`() {
        val p = PetPoint(389, 1174)
        assertEquals(p, DesktopPetLayout.clamp(p, PET, SCREEN_W, SCREEN_H))
    }

    @Test
    fun `dragging past left edge is clamped to zero`() {
        // 真机日志里出现过 x = -215
        val clamped = DesktopPetLayout.clamp(PetPoint(-215, 632), PET, SCREEN_W, SCREEN_H)
        assertEquals(0, clamped.x)
        assertEquals(632, clamped.y)
    }

    @Test
    fun `dragging past right edge is clamped so the pet stays fully visible`() {
        // 真机日志里出现过 x = 960，而合法上限是 1080 - 302 = 778
        val clamped = DesktopPetLayout.clamp(PetPoint(960, 857), PET, SCREEN_W, SCREEN_H)
        assertEquals(SCREEN_W - PET, clamped.x)
        assertEquals(778, clamped.x)
    }

    @Test
    fun `dragging past top and bottom edges is clamped`() {
        assertEquals(0, DesktopPetLayout.clamp(PetPoint(100, -500), PET, SCREEN_W, SCREEN_H).y)
        assertEquals(
            SCREEN_H - PET,
            DesktopPetLayout.clamp(PetPoint(100, 99999), PET, SCREEN_W, SCREEN_H).y
        )
    }

    @Test
    fun `screen smaller than the pet collapses the range to zero instead of going negative`() {
        // 极端情况：分屏 / 小窗模式下可用区域可能比宠物还小
        val clamped = DesktopPetLayout.clamp(PetPoint(50, 50), PET, 200, 200)
        assertEquals(PetPoint(0, 0), clamped)
    }

    @Test
    fun `isInsideScreen reports whether clamping is needed`() {
        assertTrue(DesktopPetLayout.isInsideScreen(PetPoint(0, 0), PET, SCREEN_W, SCREEN_H))
        assertTrue(DesktopPetLayout.isInsideScreen(PetPoint(778, 2038), PET, SCREEN_W, SCREEN_H))
        assertFalse(DesktopPetLayout.isInsideScreen(PetPoint(-1, 0), PET, SCREEN_W, SCREEN_H))
        assertFalse(DesktopPetLayout.isInsideScreen(PetPoint(0, 2039), PET, SCREEN_W, SCREEN_H))
    }

    // ═══════════ 命中测试 ═══════════

    @Test
    fun `contains matches the pet rectangle`() {
        val p = PetPoint(100, 200)
        assertTrue(DesktopPetLayout.contains(p, PET, 100f, 200f))
        assertTrue(DesktopPetLayout.contains(p, PET, 250f, 350f))
        assertFalse(DesktopPetLayout.contains(p, PET, 99f, 200f))
        assertFalse(DesktopPetLayout.contains(p, PET, 403f, 200f))
    }

    @Test
    fun `tolerance widens the hit area for fingers`() {
        val p = PetPoint(100, 200)
        assertTrue(DesktopPetLayout.contains(p, PET, 95f, 200f, tolerance = 12f))
        assertFalse(DesktopPetLayout.contains(p, PET, 80f, 200f, tolerance = 12f))
    }

    // ═══════════ 触摸窗（比宠物大一圈） ═══════════

    @Test
    fun `touch window is larger than the pet by the margin on both sides`() {
        assertEquals(PET + 48, DesktopPetLayout.touchWindowSize(PET, marginPx = 24))
    }

    @Test
    fun `touch window origin is expanded outward from the pet`() {
        assertEquals(PetPoint(76, 176), DesktopPetLayout.touchWindowOrigin(PetPoint(100, 200), 24))
    }

    @Test
    fun `touches inside the touch window but outside the pet are still in the window`() {
        val p = PetPoint(100, 200)
        // 宠物外、但落在余量圈里 —— 窗口会拿到这个事件
        assertTrue(DesktopPetLayout.isInTouchWindow(p, PET, 24, 90f, 200f))
        // 宠物内 —— 应认定为点到了宠物
        assertTrue(DesktopPetLayout.contains(p, PET, 150f, 250f))
        // 完全在窗口之外
        assertFalse(DesktopPetLayout.isInTouchWindow(p, PET, 24, 60f, 200f))
    }

    // ═══════════ 拖动手势 ═══════════

    @Test
    fun `move without a preceding down is ignored`() {
        val tracker = DragGestureTracker(SLOP)
        assertNull(tracker.onMove(50f, 50f))
    }

    @Test
    fun `a stationary press never enters dragging`() {
        val tracker = DragGestureTracker(SLOP)
        tracker.onDown(300f, 400f)
        assertNull(tracker.onMove(301f, 400f))
        assertFalse(tracker.isDragging)
    }

    @Test
    fun `a decisive drag produces deltas and counts as a drag not a tap`() {
        val tracker = DragGestureTracker(SLOP)
        tracker.onDown(300f, 400f)

        // 起手这一帧只负责越过 slop，不产生位移（避免起手跳一下）
        assertNull(tracker.onMove(340f, 400f))
        assertTrue(tracker.isDragging)

        val delta = tracker.onMove(380f, 430f)
        assertNotNull(delta)
        assertEquals(40f, delta!!.dx, 0.001f)
        assertEquals(30f, delta.dy, 0.001f)

        assertFalse("拖动结束后不应被判定为点击", tracker.onUp())
    }

    /**
     * ⭐ 核心回归测试：慢速拖动必须照样跟手。
     *
     * 探针版本按"相邻两帧位移 ≥ 6px"判定，慢拖时每帧位移不足 6px，
     * 会被**整段丢弃**，宠物纹丝不动。这里模拟一次慢拖：
     * 每帧只移动 2px，共 100 帧（累计 200px）。
     *
     * 正确行为：累计位移越过 slop 后开始跟手，总位移应等于剩余的全部移动量。
     */
    @Test
    fun `slow dragging still moves the pet`() {
        val tracker = DragGestureTracker(SLOP)
        tracker.onDown(0f, 500f)

        var totalDx = 0f
        var steps = 0
        var x = 0f
        repeat(100) {
            x += 2f
            tracker.onMove(x, 500f)?.let { totalDx += it.dx }
            steps++
        }

        assertEquals(100, steps)
        assertTrue("慢拖必须进入拖动状态", tracker.isDragging)

        // 前几帧用于越过 slop（6px / 每帧 2px ⇒ 第 3 帧进入拖动，且那一帧不产生位移），
        // 之后每一帧的位移都应被施加，不能被丢弃。
        // 累计 200px，起手消耗 ≤ slop(6px)，所以至少应跟手上 194px。
        assertTrue(
            "慢拖总位移应接近全程 200px，实际 $totalDx",
            totalDx >= 190f
        )
    }

    @Test
    fun `a press followed by a release without movement is a tap`() {
        val tracker = DragGestureTracker(SLOP)
        tracker.onDown(300f, 400f)
        tracker.onMove(302f, 401f)
        assertTrue("没越过 slop，应判定为点击", tracker.onUp())
    }

    @Test
    fun `a cancelled gesture is neither a tap nor a drag`() {
        val tracker = DragGestureTracker(SLOP)
        tracker.onDown(300f, 400f)
        tracker.onMove(400f, 400f)
        tracker.onCancel()

        assertFalse(tracker.isDragging)
        assertFalse("被系统抢走后不应再算点击", tracker.onUp())
    }

    @Test
    fun `after release a new move is ignored until the next down`() {
        val tracker = DragGestureTracker(SLOP)
        tracker.onDown(0f, 0f)
        tracker.onMove(100f, 0f)
        tracker.onUp()

        assertNull(tracker.onMove(200f, 0f))
    }
}
