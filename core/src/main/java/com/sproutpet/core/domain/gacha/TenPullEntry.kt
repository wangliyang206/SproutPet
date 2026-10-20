package com.sproutpet.core.domain.gacha

/**
 * 十连入口的可见性判定（`docs/00 §2.1 入口` · `docs/14 §2.4`）。
 *
 * ## 为什么需要这个类
 *
 * 2026-10-20 真机反馈：「目前只有长按蛋才会弹出十连抽界面，用户是不知道可以长按的。」
 *
 * 十连在此之前**只有长按**一个入口 —— 而长按是「知道的人才做得出来的手势」：
 * 新用户点一下蛋就直接单抽了，十连在 UI 上等于不存在。
 * **凡是只靠手势的入口，必须有一个常驻可见的替代物**（见 `docs/05` 坑表）。
 *
 * 本类是那个替代物的**唯一判据**：徽章长什么样、要不要出现、点下去去哪，
 * 全部由这里算，UI 只负责画（A1 红线：规则不进 Fragment）。
 */
sealed interface TenPullEntry {

    /** 门票够十连：实心徽章「十连 ×N」，点开开箱台 */
    data class Ready(val tickets: Int) : TenPullEntry

    /** 门票不够：半透明徽章「十连 · 差N」，点进去仍是开箱台（那里有去商店补票） */
    data class Short(val missing: Int) : TenPullEntry

    /**
     * 首蛋期（还没有任何**已破壳**宠物）：入口不出现。
     *
     * 此刻唯一该被看见的动作是「开出第一颗蛋」，多摆一个"还差 10 张"的徽章只会让首屏变吵。
     * ⚠️ 口径必须是**已破壳**，不是"有蛋"—— 首蛋在孵化中时也不该露出十连入口
     * （旧实现把蛋算进 `hasAnyPet` 已经坑过一次，见 `docs/04` 附录 A 2026-10-20）。
     */
    data object Hidden : TenPullEntry

    companion object {

        /**
         * @param tickets 手上的盲盒门票数（`GachaRules.BOX_ITEM_ID` 可用总量）
         * @param hasAnyPet 是否已有**已破壳**宠物（`PetRepository.observeHatchedPetCount`）
         */
        fun of(tickets: Int, hasAnyPet: Boolean): TenPullEntry {
            if (!hasAnyPet) return Hidden
            val have = tickets.coerceAtLeast(0)
            return if (have >= GachaRules.TEN_PULL) {
                Ready(have)
            } else {
                Short(GachaRules.TEN_PULL - have)
            }
        }

        /**
         * 点蛋时是不是该改成「先开开箱台」。
         *
         * 手上有 10 张票（例如刚领到首只孵化奖励）时，「点一下蛋 = 直接开 1 个」是个陷阱：
         * 一次误触就把十连拆成 1 + 9，用户只会看到「还差 1 张」。
         * 所以够十连时点击先进开箱台让用户自己选；不够时保持"点一下就开"的爽快。
         */
        fun routesToSheet(tickets: Int): Boolean = tickets >= GachaRules.TEN_PULL
    }
}
