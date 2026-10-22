package com.sproutpet.core.domain.daynight

import java.util.Calendar

/**
 * 昼夜判定 —— 全 App 唯一口径。
 *
 * 为什么必须集中：夜间不只是"换一张背景图"，它还决定
 * 场景背景（`bg_home_night`）、宠物困倦节奏、**冷启动首帧的 windowBackground**。
 * 冷启动那一帧若用了白天的浅色而场景是深夜，用户会先看到一次「白闪」再变黑 ——
 * 这正是 2026-10-22 真机反馈「第一次进入程序后会闪一下」的一半原因。
 * 两处各写一份 `hour >= 18` 迟早会漂移（改一处忘另一处 → 只闪夜间的用户）。
 *
 * 边界（与 `docs/02 §10` 一致）：
 * - **18:00 ~ 次日 06:00 视为夜晚**（含 18，不含 6）
 * - 06:00 ~ 18:00 为白天
 */
object DayNight {

    /** 入夜时刻（含） */
    const val NIGHT_START_HOUR = 18

    /** 天亮点（不含） */
    const val NIGHT_END_HOUR = 6

    /** 纯判定：给小时数（0..23）返回是否夜晚 —— 可纯 JVM 单测，不碰时钟 */
    fun isNight(hourOfDay: Int): Boolean {
        require(hourOfDay in 0..23) { "hourOfDay 必须在 0..23，收到 $hourOfDay" }
        return hourOfDay < NIGHT_END_HOUR || hourOfDay >= NIGHT_START_HOUR
    }

    /** 当前是否夜晚（真实时钟入口） */
    fun isNightNow(): Boolean = isNight(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
}
