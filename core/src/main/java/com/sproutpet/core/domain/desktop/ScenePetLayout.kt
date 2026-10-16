package com.sproutpet.core.domain.desktop

/**
 * 多宠站位布局（`G-12`：此前「多只怎么站」完全缺位，`DesktopPetLayout` 只解决单只）。
 *
 * ## 覆盖两种规模（`U-12` 拍板的输入）
 *
 * - **家**：竖屏，最多 [HomeSlots 家在册 6][com.sproutpet.core.domain.pet.HomeSlots.MAX_HOME_PETS]
 *   只 —— 两排各 3（后排 3 只略高略小，前排 3 只略低略大），后排 z 序更低，形成景深
 * - **手机桌面**：最多 [HomeSlots 桌面 3][com.sproutpet.core.domain.pet.HomeSlots.MAX_DESKTOP_PETS]
 *   只 —— 底部一排横向分布，避开顶部通知栏与底部手势条
 *
 * ## 设计约束
 *
 * 1. **纯几何**：输入输出全是归一化坐标（0f~1f），不依赖具体屏幕尺寸 / Android 类 —— 可纯 JVM 单测
 * 2. **确定性**：同 count 永远同布局（站位抖动由调用方在返回值上叠加，布局本身不随机 ——
 *    保证「重启后大家还站在原地」，这是陪伴感的一部分）
 * 3. **不重叠**：任意两槽位的最小间距由 [minSpacing] 保证（含后排因缩小产生的视觉补偿）
 * 4. **防呆**：count 超上限直接 `require` 抛出 —— 上限是 [com.sproutpet.core.domain.pet.HomeSlots]
 *    的职责，这里只负责「给定了 N 只怎么站」
 *
 * @see com.sproutpet.core.domain.pet.HomeSlots
 */
object ScenePetLayout {

    /** 一个站位：归一化坐标 + 渲染 z 序（大者在前） */
    data class Slot(
        val index: Int,
        /** 归一化 x（0=最左，1=最右），指宠物**脚底中心** */
        val x: Float,
        /** 归一化 y（0=最上，1=最下），指宠物**脚底** */
        val y: Float,
        /** 视觉缩放（后排 < 1，形成景深） */
        val scale: Float,
        /** z 序：越大越靠前（后手臂挡前手臂的规则由渲染层按此排序） */
        val z: Int
    )

    /** 家场景：后排 3 + 前排 3 */
    const val HOME_ROWS = 2

    /**
     * 「家」场景布局：两排各 3。
     *
     * 填充顺序 = **先前排后后排、每排从左到右**（第 1 只永远在最前面正中偏左 ——
     * 用户的第一只宠应该是最显眼的）。
     */
    fun homeLayout(count: Int): List<Slot> {
        require(count in 0..6) { "家的在册上限是 6（HomeSlots.MAX_HOME_PETS），收到 $count" }
        if (count == 0) return emptyList()

        // 前排 y=0.78 scale=1.00；后排 y=0.62 scale=0.86
        val frontX = listOf(0.22f, 0.50f, 0.78f)
        val backX = listOf(0.32f, 0.60f, 0.13f)   // 后排错开，避免与前排完全同列
        val slots = mutableListOf<Slot>()
        var z = 10
        repeat(minOf(count, 3)) { i ->
            slots += Slot(slots.size, frontX[i], 0.78f, 1.00f, z + 10)
        }
        if (count > 3) {
            repeat(count - 3) { i ->
                slots += Slot(slots.size, backX[i], 0.62f, 0.86f, z)
            }
        }
        return slots
    }

    /**
     * 「手机桌面」布局：底部一排，最多 3 只。
     *
     * 1 只 → 居中偏右（不挡主内容，像「坐在角落」）；
     * 2 只 → 左右分布；
     * 3 只 → 均匀一排。y 固定在 0.86（贴近屏幕底缘，留出桌面图标区）。
     */
    fun desktopLayout(count: Int): List<Slot> {
        require(count in 0..3) { "桌面上限是 3（HomeSlots.MAX_DESKTOP_PETS），收到 $count" }
        return when (count) {
            0 -> emptyList()
            1 -> listOf(Slot(0, 0.68f, 0.86f, 1.00f, 10))
            2 -> listOf(
                Slot(0, 0.34f, 0.86f, 1.00f, 11),
                Slot(1, 0.72f, 0.86f, 1.00f, 10)
            )
            else -> listOf(
                Slot(0, 0.22f, 0.86f, 1.00f, 12),
                Slot(1, 0.52f, 0.86f, 1.00f, 11),
                Slot(2, 0.82f, 0.86f, 1.00f, 10)
            )
        }
    }

    /**
     * 任意两槽位的归一化最小间距（按 x 差 + y 差的欧氏距离）。
     *
     * 考虑后排 scale 0.86 的视觉补偿：后排宠更小，但其**脚底**位置仍不得与前排重叠。
     */
    const val minSpacing: Float = 0.16f

    /** 校验一组布局不重叠（自检用，也被单测引用） */
    fun hasOverlap(slots: List<Slot>): Boolean =
        slots.indices.any { i -> (i + 1 until slots.size).any { j ->
            val a = slots[i]; val b = slots[j]
            val dx = a.x - b.x; val dy = (a.y - b.y) * 2f   // 竖屏 y 更紧凑，加权
            dx * dx + dy * dy < minSpacing * minSpacing
        }}
}
