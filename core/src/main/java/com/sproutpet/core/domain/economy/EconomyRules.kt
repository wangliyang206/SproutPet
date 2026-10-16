package com.sproutpet.core.domain.economy

import com.sproutpet.core.domain.pet.GrowthRules

/**
 * 经济系统数值中枢（金币 / 钻石的产出与回收）。
 *
 * ## 为什么单独一个对象
 *
 * 与 [GrowthRules] 同一条约定：**所有可调数值集中在此，禁止在业务代码里散落魔法数字**。
 *
 * ## 它解决的问题（docs/09 G-02）
 *
 * 修复前，**消耗侧写得清清楚楚、产出侧一片空白**：
 * 配对 3,000 金币、融合 200 金币、商店道具 50~3,000 金币都定好了，
 * 而 PRD §6.1 只给了一句"任务、离线收益、售卖"。
 * 结果是：**核心长线玩法（配对生育）的前置条件是 3,000 金币，
 * 而"玩家怎么赚到 3,000 金币"没有任何定义** —— 做配对 UI 那天必然卡住。
 *
 * ## 平衡锚点（这是本文件所有数字的推导依据）
 *
 * 唯一必须满足的关系是：
 *
 * ```
 * 每日金币收入 ≥ 配对花费 / 生育冷却
 * ```
 *
 * 即"一个冷却周期内攒得起一次配对费"。当前取值：
 * - 每日上限 = 签到+任务 710 + 离线 300 = **1,010 金币/天**
 * - 配对 3,000 → **2.97 天**，而生育冷却 **3 天** → 恰好赶得上
 *
 * 这个锚点由 [EconomyRulesTest] 守住。若哪天有人调了配对价格或冷却，
 * 而没同步调这里，测试会立刻变红。
 *
 * @see docs/00-产品需求文档PRD.md §6
 */
object EconomyRules {

    // ═══════════════════════ 每日签到 ═══════════════════════

    /** 每日签到金币 */
    const val SIGN_IN_GOLD = 100

    /**
     * 连续签到第 N 天的额外奖励（7 天一轮）。
     *
     * 第 7 天给一颗钻石，把"签到"与硬货币挂钩，给一个每日回来的理由。
     * 数组下标 0 对应第 1 天，长度即轮次长度。
     */
    val SIGN_IN_BONUS_GOLD: IntArray = intArrayOf(0, 0, 20, 0, 0, 0, 100)

    /** 连续签到满一轮（7 天）奖励的钻石数 */
    const val SIGN_IN_BONUS_GEM = 1

    /** 签到轮次长度（天） */
    const val SIGN_IN_CYCLE_DAYS = 7

    // ═══════════════════════ 日常任务 ═══════════════════════

    /**
     * 日常任务。
     *
     * ## 设计约束：任务经验 + 互动经验 = 每日经验上限（精确触顶）
     *
     * 全部任务的经验合计 [TOTAL_DAILY_EXP] = 735，与互动打满的 5,265 相加
     * **正好等于** `GrowthRules.DAILY_EXP_CAP`（6,000）。
     *
     * 这不是巧合，是**刻意标定的**：
     * 修复前日上限 5,000 是个"够不着的空配置"（互动打满仅 1,755），
     * 任何人看到它都会误判真实节奏。现在"打满一切可刷来源"正好触顶，
     * 上限才真正承担了它的职责 —— **拦住脚本刷经验**。
     *
     * ⚠️ 因此**新加任务或改经验值，必须同步调整 [GrowthRules.DAILY_EXP_CAP]**，
     * 否则这个等式会悄悄失效（[EconomyRulesTest] 会报错）。
     *
     * @param gold 完成奖励金币
     * @param exp 完成奖励经验（计入每日经验上限）
     * @param target 完成条件的人类可读描述
     */
    enum class DailyTask(
        val displayName: String,
        val gold: Int,
        val exp: Int,
        val target: String
    ) {
        /** 签到：零门槛，保证"每天回来就有"的确定性收益 */
        SIGN_IN("每日签到", gold = SIGN_IN_GOLD, exp = 0, target = "登录游戏"),

        INTERACT("互动达人", gold = 80, exp = 100, target = "完成 10 次互动"),

        FEED("大厨时间", gold = 60, exp = 90, target = "喂食 5 次"),

        PLAY("玩伴时光", gold = 100, exp = 120, target = "玩耍 3 次"),

        TRAIN("健身教练", gold = 120, exp = 150, target = "训练 2 次"),

        CLEAN("干干净净", gold = 60, exp = 60, target = "清洁 3 次"),

        DESKTOP("桌面陪伴", gold = 90, exp = 90, target = "桌面互动 20 次"),

        /**
         * 串门：唯一的**社交向**日常任务。
         *
         * 有它，日常任务才不只是"多喂两次饭"，而是在每天推一次社交曝光 ——
         * 而配对生育是本作的核心长线玩法，需要玩家先认识人。
         */
        VISIT("串门拜访", gold = 100, exp = 125, target = "访问好友 1 次（NPC 亦可）");

        companion object {
            /** 每日任务数量 */
            val COUNT: Int get() = entries.size
        }
    }

    /** 全部日常任务的金币合计（不含签到加成） */
    val dailyGoldFromTasks: Int
        get() = DailyTask.entries.sumOf { it.gold }

    /** 全部日常任务的经验合计（不含互动） */
    val dailyExpFromTasks: Int
        get() = DailyTask.entries.sumOf { it.exp }

    /** 签到 + 全部日常任务的金币合计（7 天一轮里第 N 天还有 [SIGN_IN_BONUS_GOLD] 加成） */
    val dailyGoldFromTasksWithSignIn: Int
        get() = dailyGoldFromTasks

    // ═══════════════════════ 离线收益 ═══════════════════════

    /**
     * 离线收益：金币/小时。
     *
     * ## 为什么离线**只给金币、不给经验**（这是一条刻意的设计约束）
     *
     * 本产品是「治愈陪伴」，核心价值是**在线互动的过程**。
     * 如果离线也给经验，就会出现一个荒谬的反向激励：
     * **关掉 App 比开着 App 升级更快**（因为在线要手动点、离线躺着就有）。
     *
     * 所以离线的定位是"宠物自己在家照顾自己"的**照看津贴**，
     * 只给软货币，不给成长 —— 让金币成为"回来花"的理由，而不是"别回来"的理由。
     *
     * @see offlineGold
     */
    const val OFFLINE_GOLD_PER_HOUR = 25

    /** 离线收益的时长上限（小时）。超过部分不计 —— 见 docs/00 §2.4 的"12h 上限" */
    const val OFFLINE_CAP_HOURS = 12

    /** 离线收益的时长上限（毫秒） */
    const val OFFLINE_CAP_MS = OFFLINE_CAP_HOURS * 60L * 60L * 1000L

    /**
     * 计算离线收益金币。
     *
     * 公式：`floor(min(elapsedMs, 上限) / 1 小时) × [OFFLINE_GOLD_PER_HOUR]`
     *
     * 刻意**向下取整到整小时**：避免玩家为了"卡最后 59 分钟"而精确掐表上线，
     * 把陪伴变成打卡任务。
     *
     * @param elapsedMs 距上次结算的时长
     * @return 应发放的金币（负数/零时长返回 0）
     */
    fun offlineGold(elapsedMs: Long): Int {
        if (elapsedMs <= 0L) return 0
        val cappedMs = elapsedMs.coerceAtMost(OFFLINE_CAP_MS)
        val hours = cappedMs / (60L * 60L * 1000L)
        return (hours * OFFLINE_GOLD_PER_HOUR).toInt()
    }

    /** 离线收益的理论日上限（按 12 小时封顶计） */
    const val MAX_OFFLINE_GOLD_PER_DAY = OFFLINE_CAP_HOURS * OFFLINE_GOLD_PER_HOUR

    // ═══════════════════════ 售卖 / 回收 ═══════════════════════

    /**
     * 道具回收价 = 商店价 × 该比例（向下取整）。
     *
     * 定在 50% 而不是 100%：否则"买入 → 卖出"就是无损套利，
     * 商店会退化成提款机；低于 50% 又会让误买的玩家感觉被惩罚。
     *
     * ⚠️ **宠物本身不可售卖**（PRD §6.1 的"售卖"仅指道具回收）。
     * 原因有两个，任一都足够：
     * 1. 与「治愈陪伴」定位冲突 —— 把陪伴对象标价卖掉会摧毁情感叙事；
     * 2. 族谱需要保留血缘记录，卖宠会让后代失去父母节点。
     *
     * 重复宠物的正确去处是**融合**（见 [com.sproutpet.core.domain.pet.FusionRules]），
     * 那是"投入换成长"，不是"变现"。
     */
    const val SELL_REFUND_RATE = 0.5f

    /**
     * 道具回收价。
     *
     * @param shopPriceGold 商店售价；`null` 表示不可购买，此时不可回收（返回 `null`）
     * @return 回收可得金币
     */
    fun sellPriceOf(shopPriceGold: Int?): Int? {
        if (shopPriceGold == null || shopPriceGold <= 0) return null
        return (shopPriceGold * SELL_REFUND_RATE).toInt()
    }

    // ═══════════════════════ 钻石 ═══════════════════════

    /**
     * 钻石的**非付费**获取途径。
     *
     * 定位是"珍贵但可获得"：让不付费玩家也能摸到高级道具，
     * 避免付费墙直接把核心体验切开（PRD §6.3「所有道具均可通过任务/活动获得」）。
     *
     * 注：单笔数值由**成就表 / 运营配置**决定，此处只定义**节奏上限**，
     * 防止某次活动一次性放量把硬货币体系冲垮。
     */
    object GemRules {

        /** 非付费途径每日钻石上限（签到 + 成就 + 活动合计） */
        const val MAX_FREE_GEM_PER_DAY = 10

        /** 成就一次性奖励的钻石上限（单个成就） */
        const val MAX_ACHIEVEMENT_GEM = 50
    }

    // ═══════════════════════ 平衡校验 ═══════════════════════

    /**
     * 每日金币收入上限（签到 + 全部任务 + 满额离线）。
     *
     * 这是"玩家一天最多能赚多少金币"的**唯一权威口径**，
     * 任何"配对要攒几天"的结论都必须用它推导，不要另算一遍。
     */
    val maxGoldPerDay: Int
        get() = dailyGoldFromTasksWithSignIn + MAX_OFFLINE_GOLD_PER_DAY

    /**
     * 攒够一次配对费需要几天。
     *
     * 设计锚点：它应当 ≈ `PairingRules.cooldownMs` 折算的天数，
     * 这样"冷却结束"与"钱够了"同时到位，玩家不会卡在"条件都满足但没钱"。
     */
    val daysToAffordPairing: Double
        get() = GrowthRules.PairingRules.costGold.toDouble() / maxGoldPerDay

    /**
     * 攒够一次融合费需要几天。
     *
     * 融合是日常小循环（200 金币），应当**当天就能做**，
     * 否则"重复宠物的去处"会被拖延，背包很快堆满。
     */
    val daysToAffordFusion: Double
        get() = com.sproutpet.core.domain.pet.FusionRules.costGold.toDouble() / maxGoldPerDay
}
