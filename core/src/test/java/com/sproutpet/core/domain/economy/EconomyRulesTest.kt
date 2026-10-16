package com.sproutpet.core.domain.economy

import com.sproutpet.core.domain.pet.GrowthRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 经济系统（G-02）校验。
 *
 * ## 这个测试类存在的意义
 *
 * 经济数值本身不需要"验证正确性"（那是策划权衡），但**它们之间的约束必须被守住**。
 * 修复前的状况正是约束缺失的后果：消耗侧的价格都定好了（配对 3,000 / 融合 200），
 * 产出侧一个数字都没有 —— 没有人发现"3,000 金币从哪来"这个问题，
 * 因为**没有任何地方把收入与支出写在同一个等式里**。
 *
 * 所以这里的测试几乎全是**关系断言**（A 应等于 B、A 应落在 B 附近），
 * 而不是"断言某个数字等于 710"这种把测试变成镜像的写法（那种测试改数字时形同虚设）。
 *
 * 唯一的例外是那一组"设计总量"断言 —— 它们的作用是：**数值一旦被改动，
 * 必须有人主动确认改动是故意的**（改了就要来改这个数字，改动就留下了痕迹）。
 */
class EconomyRulesTest {

    // ═════════ ⚠️ 最核心的一条：每日经验上限必须"够得着" ═════════

    @Test
    fun `tasks plus interactions exactly fill the daily exp cap`() {
        // 这是整个经济数值里唯一一条**硬约束**。
        //
        // 修复前的坑：日上限 5,000，而互动打满只有 1,755 ——
        // 上限谁也够不到，是个"空配置"，任何人看它都会误判真实节奏
        // （同样的坑在 DAILY_EXP_CAP 上踩过一次，见 GrowthRules 的注释）。
        //
        // 现在的标定：互动 5,265 + 任务 735 = 正好 6,000 = DAILY_EXP_CAP。
        // "打满一切可刷来源"精确触顶 —— 上限才真正开始干活（拦脚本）。
        //
        // 若这条变红：要么改了任务的 exp，要么改了 DAILY_EXP_CAP，要么加了新互动。
        // 三种情况都需要**同时**调整另一侧，不能只改一边。
        val total = GrowthRules.dailyExpFromInteractions + EconomyRules.dailyExpFromTasks

        assertEquals(
            "互动经验 + 日常任务经验必须正好等于每日经验上限（当前差 ${GrowthRules.DAILY_EXP_CAP - total} 点）：" +
                "上限要么够不着（空配置）、要么被穿透",
            GrowthRules.DAILY_EXP_CAP,
            total
        )
    }

    @Test
    fun `the daily exp cap is not a null config`() {
        // 反面守门：上限必须 <= 可得总量（否则形同虚设）。
        val obtainable =
            GrowthRules.dailyExpFromInteractions + EconomyRules.dailyExpFromTasks
        assertTrue(
            "每日上限 ${GrowthRules.DAILY_EXP_CAP} 高于可得总量 $obtainable，配置形同虚设",
            GrowthRules.DAILY_EXP_CAP <= obtainable
        )
    }

    // ═════════ 收入侧 ═════════

    @Test
    fun `every daily task pays gold`() {
        for (task in EconomyRules.DailyTask.entries) {
            assertTrue(
                "日常任务「${task.displayName}」不发金币，玩家没有回来的理由",
                task.gold > 0
            )
        }
    }

    @Test
    fun `daily task gold matches the designed total`() {
        // 100 + 80 + 60 + 100 + 120 + 60 + 90 + 100
        assertEquals(710, EconomyRules.dailyGoldFromTasks)
    }

    @Test
    fun `daily task exp matches the designed total`() {
        // 0 + 100 + 90 + 120 + 150 + 60 + 90 + 125
        assertEquals(735, EconomyRules.dailyExpFromTasks)
    }

    @Test
    fun `max gold per day is tasks plus a full offline window`() {
        assertEquals(710, EconomyRules.dailyGoldFromTasksWithSignIn)
        assertEquals(300, EconomyRules.MAX_OFFLINE_GOLD_PER_DAY)
        assertEquals(1_010, EconomyRules.maxGoldPerDay)
    }

    @Test
    fun `sign in bonus covers exactly one cycle`() {
        assertEquals(
            EconomyRules.SIGN_IN_CYCLE_DAYS,
            EconomyRules.SIGN_IN_BONUS_GOLD.size
        )
        assertTrue(
            "签到满一轮应当给一点硬货币，否则签到缺少长线理由",
            EconomyRules.SIGN_IN_BONUS_GEM > 0
        )
    }

    // ═════════ 离线收益 ═════════

    @Test
    fun `offline income is zero when nothing has elapsed`() {
        assertEquals(0, EconomyRules.offlineGold(0))
        assertEquals(0, EconomyRules.offlineGold(-1_000))
    }

    @Test
    fun `offline income floors to whole hours`() {
        val oneHour = 60L * 60L * 1000L
        assertEquals(0, EconomyRules.offlineGold(oneHour - 1))
        assertEquals(EconomyRules.OFFLINE_GOLD_PER_HOUR.toLong(), EconomyRules.offlineGold(oneHour).toLong())
        assertEquals(
            EconomyRules.OFFLINE_GOLD_PER_HOUR.toLong(),
            EconomyRules.offlineGold(oneHour * 2 - 1).toLong()
        )
    }

    @Test
    fun `offline income stops growing after the cap`() {
        val cap = EconomyRules.OFFLINE_CAP_MS
        val atCap = EconomyRules.offlineGold(cap)
        assertEquals(EconomyRules.MAX_OFFLINE_GOLD_PER_DAY, atCap)

        // 一周不上线，也只能拿到 12 小时的量。
        assertEquals(
            "离线收益未封顶，长时间不上线的收益会失控",
            atCap,
            EconomyRules.offlineGold(cap * 14)
        )
    }

    @Test
    fun `offline income alone can never fund a pairing`() {
        // 单靠离线收益必须攒不起配对费 —— 否则玩家最优策略是"别打开 App"，
        // 与「治愈陪伴」的定位直接冲突。
        assertTrue(
            "离线日收益 ${EconomyRules.MAX_OFFLINE_GOLD_PER_DAY} 已经够接近配对费了",
            EconomyRules.MAX_OFFLINE_GOLD_PER_DAY * 4 < GrowthRules.PairingRules.costGold
        )
    }

    // ═════════ 消耗侧锚点：配对费要"一个冷却周期内攒得起" ═════════

    @Test
    fun `pairing cost is affordable within about one breeding cooldown`() {
        val cooldownDays = GrowthRules.PairingRules.cooldownMs.toDouble() / 86_400_000.0
        val days = EconomyRules.daysToAffordPairing

        assertTrue(
            "攒配对费需要 $days 天，超过冷却 $cooldownDays 天 —— " +
                "玩家会遇到「条件全满足但没钱」，卡死在核心玩法门口",
            days <= cooldownDays
        )
        assertTrue(
            "攒配对费只要 $days 天，远快于冷却 $cooldownDays 天 —— " +
                "金币会失去意义，配对变成无成本动作",
            days >= cooldownDays * 0.8
        )
    }

    @Test
    fun `fusion is affordable on day one`() {
        assertTrue(
            "融合费要攒 ${EconomyRules.daysToAffordFusion} 天，重复宠物的去处被拖延了",
            EconomyRules.daysToAffordFusion < 1.0
        )
    }

    // ═════════ 道具回收 ═════════

    @Test
    fun `selling refunds half of the shop price`() {
        assertEquals(25, EconomyRules.sellPriceOf(50))
        assertEquals(100, EconomyRules.sellPriceOf(200))
        assertEquals(75, EconomyRules.sellPriceOf(151)) // 向下取整
    }

    @Test
    fun `selling can never be an arbitrage`() {
        // 回收价必须严格低于售价，否则"买入 → 卖出"就是无损套利，商店变提款机。
        for (price in listOf(50, 100, 150, 200, 500, 3_000)) {
            val refund = EconomyRules.sellPriceOf(price)!!
            assertTrue("售价 $price 的回收价 $refund 不够低", refund < price)
        }
    }

    @Test
    fun `items that cannot be bought cannot be sold`() {
        assertNull(EconomyRules.sellPriceOf(null))
        assertNull(EconomyRules.sellPriceOf(0))
        assertNull(EconomyRules.sellPriceOf(-100))
    }

    // ═════════ 钻石 ═════════

    @Test
    fun `free gems are rate limited`() {
        assertTrue(EconomyRules.GemRules.MAX_FREE_GEM_PER_DAY > 0)
        assertTrue(
            "单次成就钻石 ${EconomyRules.GemRules.MAX_ACHIEVEMENT_GEM} " +
                "不应超过每日免费上限的十倍，否则一次成就就把硬货币体系冲垮",
            EconomyRules.GemRules.MAX_ACHIEVEMENT_GEM <=
                EconomyRules.GemRules.MAX_FREE_GEM_PER_DAY * 10
        )
    }
}
