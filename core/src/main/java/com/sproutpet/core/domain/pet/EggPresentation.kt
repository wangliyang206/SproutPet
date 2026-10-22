package com.sproutpet.core.domain.pet

/**
 * 蛋应该出现在场景的哪个位置（docs/14 §1.4 / §1.5）。
 *
 * ## 为什么要有这个类型
 *
 * 蛋有两个展示位：盖在场景上的**首蛋聚焦层**（180dp、压暗全场）与右下角**角落蛋**（72dp）。
 * 早期实现里，两处的显隐判据是**分开写的**：
 *
 * ```
 * 聚焦层：egg != null && !hasAnyPet
 * 角落蛋：if (聚焦层应该显示) GONE else VISIBLE
 * ```
 *
 * 判据一旦漂移（例如聚焦层因动画失败没能显示，而角落蛋已经按「聚焦层应该显示」被隐藏），
 * 结果就是**一个蛋都看不见** —— 2026-10-22 真机反馈「明显看到一个很大的蛋慢慢的消失了，
 * 除了背景什么都没有了」正是这一类。
 *
 * 收敛成一个三态后，UI 只做映射，不变量由单测守住：
 * **任一时刻，屏幕上可见的蛋最多一个；只要有蛋，就必有一个可见。**
 *
 * ⚠️ 判据里的 `hasAnyHatchedPet` 是**已破壳**口径（蛋不算宠物），
 * 与 `PetDao.observeHatchedCount`（`is_hatched = 1`）严格一致。
 * 写成「有没有 pet 记录」会让首蛋一落库就退出聚焦层（蛋本身就是一行 pet）。
 */
enum class EggPresentation {

    /** 首蛋孵化期：场景压暗、大蛋居中 + 倒计时（首日唯一的主角） */
    FOCUS,

    /** 已有宠物，或第 2 颗起的蛋：右下角小蛋（不抢场景） */
    CORNER,

    /** 没有孵化中的蛋：角落是「可开箱」入口，不是蛋在孵化 */
    NONE;

    companion object {
        /**
         * @param egg 孵化中的蛋（null = 没有）
         * @param hasAnyHatchedPet 是否已有**已破壳**宠物
         */
        fun of(egg: Pet?, hasAnyHatchedPet: Boolean): EggPresentation = when {
            egg == null -> NONE
            hasAnyHatchedPet -> CORNER
            else -> FOCUS
        }

        /**
         * 角落蛋是否可见。
         *
         * 这条判据不是"聚焦层该不该显示"，而是"**聚焦层此刻到底显示了没有**"
         * —— 两者的差别就是 2026-10-22 那次「一颗蛋都看不见」：
         * 聚焦层因为动画/时序没显示出来时，角落蛋必须兜底可见，
         * 否则用户面对的是一屏什么都不能点的背景。
         *
         * @param stage [of] 的结果
         * @param focusActuallyShown 聚焦层此刻是否真的可见（不是"应该可见"）
         */
        fun cornerVisible(stage: EggPresentation, focusActuallyShown: Boolean): Boolean =
            stage != FOCUS || !focusActuallyShown
    }
}
