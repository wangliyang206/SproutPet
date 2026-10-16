package com.sproutpet.core.domain.desktop

import kotlin.math.hypot

/**
 * 桌宠在屏幕上的整数坐标（**左上角**，单位像素）。
 */
data class PetPoint(val x: Int, val y: Int)

/**
 * 一次拖动要施加的位移（像素）。
 */
data class PetDelta(val dx: Float, val dy: Float)

/**
 * 桌宠的屏幕布局规则。
 *
 * ## 为什么放在 core 而不是 app
 *
 * 这些都是**纯几何运算**，不依赖 `WindowManager` / `View`，因此可以和业务逻辑一样
 * 做单元测试。而桌宠一旦上线，这两件事最容易出 bug 且最难在真机上复现：
 *
 * 1. 宠物被拖出屏幕（找不回来）
 * 2. 拖动手势判定错误（慢拖不动）
 *
 * 所以把它们从平台代码里剥出来，用测试钉死。
 *
 * @see docs/02-宠物渲染方案.md §3.5 桌宠窗口架构
 */
object DesktopPetLayout {

    /**
     * 把宠物位置**钳制在屏幕可视范围内**，保证宠物完整可见。
     *
     * ⚠️ **这是实测踩坑得来的**（见 `docs/99-桌宠技术验证操作单.md` §六）：
     * 真机上曾把宠物拖到 `x = -215`（左边缘外 215px）和 `x = 960`
     * （屏宽 1080、宠物 302 时合法上限是 778）。
     * 宠物一旦跑出屏幕，用户**再也没有办法把它拖回来**，只能重启服务。
     *
     * 屏幕比宠物还小（极端小窗模式 / 分屏）时全部归零，不产生负的可用区间。
     */
    fun clamp(
        position: PetPoint,
        petSize: Int,
        screenWidth: Int,
        screenHeight: Int
    ): PetPoint {
        val maxX = (screenWidth - petSize).coerceAtLeast(0)
        val maxY = (screenHeight - petSize).coerceAtLeast(0)
        return PetPoint(
            x = position.x.coerceIn(0, maxX),
            y = position.y.coerceIn(0, maxY)
        )
    }

    /** 位置是否已经合法（在屏幕内），用于调试断言 */
    fun isInsideScreen(
        position: PetPoint,
        petSize: Int,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean = clamp(position, petSize, screenWidth, screenHeight) == position

    /**
     * 命中测试：屏幕坐标是否落在宠物矩形内。
     *
     * @param tolerance 容差（像素），给手指一点余量，让"点点宠物"更好按中
     */
    fun contains(
        position: PetPoint,
        petSize: Int,
        x: Float,
        y: Float,
        tolerance: Float = 0f
    ): Boolean =
        x >= position.x - tolerance && x <= position.x + petSize + tolerance &&
            y >= position.y - tolerance && y <= position.y + petSize + tolerance

    /**
     * 触摸窗的实际边长 = 宠物边长 + 两侧余量。
     *
     * ## 为什么需要余量
     *
     * 宠物被 [clamp] 顶到屏幕边缘后，**手指还能继续往屏幕外滑**。
     * 如果触摸窗和宠物一样大，手指会立刻滑出窗口 → 收到 `ACTION_CANCEL`
     * → 被系统中断拖动，手感是"拖到边上会突然松手"。
     *
     * 四周各留一圈余量，手指滑到屏幕边缘时仍落在窗内，拖动可以平滑地停在边缘。
     */
    fun touchWindowSize(petSize: Int, marginPx: Int): Int = petSize + marginPx * 2

    /** 触摸窗左上角坐标（以宠物为中心向外扩 [marginPx]） */
    fun touchWindowOrigin(position: PetPoint, marginPx: Int): PetPoint =
        PetPoint(position.x - marginPx, position.y - marginPx)

    /**
     * 触摸窗是否应该接收某个屏幕坐标。
     *
     * 触摸窗比宠物大一圈，所以窗口内、宠物外的区域也会拿到事件 ——
     * 那部分应当**忽略**（既不拖动宠物，也不算点击）。
     */
    fun isInTouchWindow(
        position: PetPoint,
        petSize: Int,
        marginPx: Int,
        x: Float,
        y: Float
    ): Boolean = contains(
        position = touchWindowOrigin(position, marginPx),
        petSize = touchWindowSize(petSize, marginPx),
        x = x,
        y = y
    )
}

/**
 * 拖动手势判定器。
 *
 * ## 为什么不能用"单帧位移阈值"
 *
 * 探针版本的做法是：每次 `ACTION_MOVE` 都拿相邻两次事件的位移和阈值比，
 * 不够阈值就**整段丢弃**。阈值 6px 在 60Hz 触摸流下约等于 **360px/秒**：
 *
 * - 拖得快 → 每帧位移大 → 正常
 * - **拖得慢（< 360px/秒）→ 每帧位移都不足 6px → 每一次位移都被丢掉 → 宠物纹丝不动**
 *
 * 而松手时因为从未进入拖动状态，还会被判定成"点击"。真机上"轻拖不动"就是这么来的。
 *
 * ## 正确做法
 *
 * 分两段：
 * 1. **是否开始拖动** —— 用**累计**位移（从按下点算起）与 touch slop 比较，只看一次
 * 2. **拖动中的跟手** —— 一旦开始，按相邻两次事件的位移施加，不做阈值过滤
 *
 * @param touchSlopPx 触发拖动所需的最小累计位移。生产代码传
 *   `ViewConfiguration.get(context).scaledTouchSlop`
 *   （⚠️ 注意是 `ViewConfiguration` 的实例方法 `scaledTouchSlop`，**不是**已废弃的静态
 *   `ViewConfiguration.getScaledTouchSlop()`）。
 */
class DragGestureTracker(private val touchSlopPx: Float) {

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f

    private var pressed = false

    /** 是否已进入拖动状态 */
    var isDragging: Boolean = false
        private set

    fun onDown(rawX: Float, rawY: Float) {
        downX = rawX
        downY = rawY
        lastX = rawX
        lastY = rawY
        pressed = true
        isDragging = false
    }

    /**
     * 处理一次移动。
     *
     * @return 需要施加的位移；`null` 表示本次不产生位移
     *   （未按下，或累计位移还没越过 touch slop）
     */
    fun onMove(rawX: Float, rawY: Float): PetDelta? {
        if (!pressed) return null

        if (!isDragging) {
            if (hypot(rawX - downX, rawY - downY) < touchSlopPx) return null
            // 刚越过 slop：从本次位置开始跟手，避免"起手跳一下"
            isDragging = true
            lastX = rawX
            lastY = rawY
            return null
        }

        val dx = rawX - lastX
        val dy = rawY - lastY
        lastX = rawX
        lastY = rawY
        return PetDelta(dx, dy)
    }

    /**
     * 处理抬手。
     *
     * @return `true` 表示这是一次**点击**（按下后从未进入拖动），
     *   调用方应据此触发"摸一下宠物"之类的互动
     */
    fun onUp(): Boolean {
        val wasTap = pressed && !isDragging
        pressed = false
        isDragging = false
        return wasTap
    }

    /** 事件被系统抢走（例如手指滑出触摸窗）。不算点击。 */
    fun onCancel() {
        pressed = false
        isDragging = false
    }
}
