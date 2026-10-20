package com.sproutpet.core.domain.pet

import kotlin.math.abs
import kotlin.random.Random

/**
 * 宠物行为决策（docs/15 §十 · 2026-10-20 真机反馈「模型幼稚、宠物自己没戏演」）。
 *
 * ## 分层
 *
 * - **本文件（core，纯 Kotlin）**：只回答「下一拍做什么」—— 输入随机源与约束，输出动作；
 *   可纯 JVM 单测（`BehaviorDirectorTest`）。
 * - **app 层 `SceneFragment` 行为循环**：把动作翻译成视图动画（位移/朝向/哈欠），并负责
 *   打断规则（被拎起、睡觉、托盘收起重排）。
 *
 * ## 真机反馈的三条诉求与本层的关系
 *
 * | 诉求 | 本层负责 | 视图层负责 |
 * |------|---------|-----------|
 * | 宠物自己有动效（溜达/哈欠/睡觉） | 按概率挑 [WANDER]/[YAWN]/[REST] | 位移插值 + 朝向翻转 + 拉伸 |
 * | 多宠相互互动 | 有伙伴时按概率挑 [SOCIAL] | 走近 → 对视 → 双方小跳 |
 * | 3D 立体 | —（不涉及决策） | 影子 / 透视倾斜（Phase 3 再上真 3D） |
 */
enum class PetBehaviorAction {
    /** 原地休息（什么都不做，等下一拍） */
    REST,

    /** 左右张望（小角度摆头） */
    LOOK,

    /** 打哈欠（拉伸 + 台词气泡） */
    YAWN,

    /** 溜达到附近某点（家锚点附近游走，不出安全区） */
    WANDER,

    /** 凑近某只伙伴互动 */
    SOCIAL,
}

/** 一拍决策结果：做什么 + （WANDER 时）去哪 */
data class PetBehaviorDecision(
    val action: PetBehaviorAction,
    /** WANDER 目标（安全区像素坐标，x/y；其余动作无意义） */
    val targetX: Float = 0f,
    val targetY: Float = 0f,
) {
    val isWander: Boolean get() = action == PetBehaviorAction.WANDER
}

/**
 * 行为导演：状态机的「决策」半边（执行半边在 app 层）。
 *
 * 规则（全部可判定、已单测）：
 * 1. **睡觉时永远 REST** —— 睡着了不许溜达（睡着还在跑圈=恐怖片）；
 * 2. **没有伙伴时永不 SOCIAL**；
 * 3. WANDER 目标 = 家锚点 ± [WANDER_RADIUS] 内随机，**并夹回安全区**（不撞蛋、不压托盘）；
 * 4. 同种子同决策（确定性，便于回放调试）。
 */
object BehaviorDirector {

    /** 决策节拍：每 6~14 秒抽一次动作（太密变猴戏，太疏没生命感） */
    const val TICK_MIN_MS = 6_000L
    const val TICK_MAX_MS = 14_000L

    /** 溜达半径：距家锚点最大归一化偏移（× 安全区宽/高） */
    const val WANDER_RADIUS = 0.14f

    /** 概率带：SOCIAL 15% / WANDER 30% / YAWN 15% / LOOK 25% / REST 15%（无伙伴时重分） */
    const val P_SOCIAL = 0.15
    const val P_WANDER = 0.30
    const val P_YAWN = 0.15
    const val P_LOOK = 0.25

    /**
     * 决策一拍。
     *
     * @param sleeping 是否睡着（睡着 → 恒 REST）
     * @param companionCount 其他在场宠物数（0 → 永不 SOCIAL）
     * @param homeX/homeY 家锚点（本宠站位，安全区像素坐标）
     * @param minX/maxX/minY/maxY 安全区边界（目标会夹回界内）
     */
    fun decide(
        random: Random,
        sleeping: Boolean,
        companionCount: Int,
        homeX: Float,
        homeY: Float,
        minX: Float,
        maxX: Float,
        minY: Float,
        maxY: Float,
    ): PetBehaviorDecision {
        if (sleeping) return PetBehaviorDecision(PetBehaviorAction.REST)
        val roll = random.nextDouble()
        return when {
            companionCount > 0 && roll < P_SOCIAL ->
                PetBehaviorDecision(PetBehaviorAction.SOCIAL)
            roll < P_SOCIAL + P_WANDER -> {
                val tx = (homeX + jitter(random, WANDER_RADIUS) * (maxX - minX)).coerceIn(minX, maxX)
                val ty = (homeY + jitter(random, WANDER_RADIUS * 0.6f) * (maxY - minY)).coerceIn(minY, maxY)
                PetBehaviorDecision(PetBehaviorAction.WANDER, tx, ty)
            }
            roll < P_SOCIAL + P_WANDER + P_YAWN -> PetBehaviorDecision(PetBehaviorAction.YAWN)
            roll < P_SOCIAL + P_WANDER + P_YAWN + P_LOOK -> PetBehaviorDecision(PetBehaviorAction.LOOK)
            else -> PetBehaviorDecision(PetBehaviorAction.REST)
        }
    }

    /** 溜达目标是否确在家锚点附近且在安全区内（视图层二次校验也用它，测试用） */
    fun isWanderTargetValid(
        d: PetBehaviorDecision,
        homeX: Float, homeY: Float,
        minX: Float, maxX: Float, minY: Float, maxY: Float,
        spanX: Float, spanY: Float,
    ): Boolean {
        if (!d.isWander) return false
        val inBounds = d.targetX in minX..maxX && d.targetY in minY..maxY
        val nearHome = abs(d.targetX - homeX) <= WANDER_RADIUS * spanX + 1e-3f &&
            abs(d.targetY - homeY) <= WANDER_RADIUS * 0.6f * spanY + 1e-3f
        return inBounds && nearHome
    }

    private fun jitter(random: Random, radius: Float): Float =
        (random.nextDouble() * 2 - 1).toFloat() * radius
}
