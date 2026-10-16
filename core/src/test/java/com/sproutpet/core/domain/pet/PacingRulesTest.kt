package com.sproutpet.core.domain.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 节奏与配对容量测试（ADR-024 / 08 §六 方案 B）。
 *
 * ## 为什么这些看似"只是常量"的东西值得单独立测试
 *
 * 因为它们曾经**互相矛盾**：
 * 配置里写着日经验上限 5,000，而"全部互动打满"实际只有 1,755 ——
 * 上限是个够不着的空配置，于是策划算节奏时高估了进度、低估了成熟天数，
 * 把灵兽目标算成 117~159 天。**数字之间的一致性必须由测试守住**，
 * 否则下次调参还会踩同一个坑。
 *
 * 这正是 ADR-024 的要求：任何影响获取成本的改动都必须可复算、可验证。
 */
class PacingRulesTest {

    /** 单代周期 = max(成熟天数, (后代上限-1) × 冷却)，两者并行 */
    private val maturityDays: Double
        get() {
            val adultLevel = GrowthRules.stageLevelRange.getValue(PetStage.ADULT).first
            val need = (0 until adultLevel).sumOf { GrowthRules.expToNextLevel(it) }
            return need.toDouble() / GrowthRules.dailyExpFromInteractions
        }

    private val perGenerationDays: Double
        get() = maxOf(
            maturityDays,
            cooldownSpanDays
        )

    /** 把 5 个后代名额用完所需的冷却跨度（天） */
    private val cooldownSpanDays: Double
        get() = (GrowthRules.PairingRules.maxOffspring - 1) *
            (GrowthRules.PairingRules.cooldownMs.toDouble() / DAY_MS)

    // ═══════════ 经验配置必须自洽 ═══════════

    @Test
    fun `daily cap is actually reachable`() {
        assertTrue(
            "日经验上限 ${GrowthRules.DAILY_EXP_CAP} 必须 >= 互动实际上限 " +
                "${GrowthRules.dailyExpFromInteractions}，否则它就是个空配置",
            GrowthRules.DAILY_EXP_CAP >= GrowthRules.dailyExpFromInteractions
        )
    }

    @Test
    fun `daily exp from interactions is the tripled value`() {
        // 旧值 1_755（= 5×50 + 20×20 + 35×15 + 50×8 + 10×10 + 2×40），方案 B 上调为 3 倍
        assertEquals(1_755 * 3, GrowthRules.dailyExpFromInteractions)
    }

    @Test
    fun `interaction exp was tripled but intimacy was not`() {
        // 亲密度是"陪伴深度"的度量，不该被等级调速带着一起变水
        assertEquals(50, GrowthRules.InteractionType.PET.dailyLimit)
        assertEquals(1, GrowthRules.InteractionType.PET.intimacy)
        assertEquals(15, GrowthRules.InteractionType.PET.exp)
    }

    // ═══════════ 节奏必须落在 ADR-024 的预算内 ═══════════

    @Test
    fun `maturity is about ten and a half days`() {
        assertTrue(
            "成熟期应降到 11 天以内（现状为 ${"%.2f".format(maturityDays)} 天）",
            maturityDays <= 11.0
        )
        assertTrue("也不能快到失去成长感", maturityDays >= 8.0)
    }

    @Test
    fun `cool down is three days`() {
        assertEquals(3.0, GrowthRules.PairingRules.cooldownMs / DAY_MS.toDouble(), 1e-9)
    }

    @Test
    fun `maturity and cooldown are finally balanced`() {
        // 调参前冷却（28 天/代）远大于成熟期，是节奏的唯一瓶颈；
        // 现在两者同一量级，说明瓶颈不再是单一的配对冷却。
        val ratio = cooldownSpanDays / maturityDays

        assertTrue("冷却跨度与成熟期应当同量级，当前比值为 $ratio", ratio in 0.8..1.6)
    }

    @Test
    fun `one generation cycle fits in twelve days`() {
        assertTrue(
            "单代周期应为 ${"%.2f".format(perGenerationDays)} 天",
            perGenerationDays <= 12.0
        )
    }

    /**
     * 端到端墙钟天数估算：
     * `(代数-1) × 单代周期 + 成熟天数 + 冷却跨度`
     *
     * 代数取自 `tools/acquisition_sim.py` 的实测均值（20 万次蒙特卡洛），
     * 不是估的 —— 这些常量改了就必须重跑脚本（ADR-024）。
     */
    private fun wallClockDays(generations: Double): Double =
        (generations - 1) * perGenerationDays + maturityDays +
            (GrowthRules.PairingRules.maxOffspring - 1) *
            (GrowthRules.PairingRules.cooldownMs / DAY_MS)

    @Test
    fun `mid term target fits in forty five days`() {
        // 异兽（跨虫 3 线并行的实测代数 2.2）
        val days = wallClockDays(generations = 2.2)

        assertTrue(
            "异兽约 ${"%.0f".format(days)} 天，必须落在 ADR-024 的中期目标预算（45 天）内",
            days <= 45.0
        )
    }

    @Test
    fun `the ultimate target fits in seventy days`() {
        // 首只灵兽 = 终极目标。同虫单线是最慢的一条路（实测代数 4.5）
        val slowest = wallClockDays(generations = 4.5)
        // 桌面 3 只并行的实测代数 2.8 —— 这是产品预期的常态
        val typical = wallClockDays(generations = 2.8)

        assertTrue(
            "最慢路径约 ${"%.0f".format(slowest)} 天，必须落在 ADR-024 的终极目标预算（70 天）内",
            slowest <= 70.0
        )
        assertTrue(
            "常态路径约 ${"%.0f".format(typical)} 天，应当明显快于最慢路径",
            typical < slowest
        )
    }

    @Test
    fun `same family is only marginally slower than cross family`() {
        // 同虫（4.5 代）比跨虫（4.1 代）慢约 8%。
        // 这是 ADR-026 的代价，但差距必须**小** ——
        // 否则"同虫"会重新变成没人走的路，S12 就等于没解决。
        val same = wallClockDays(generations = 4.5)
        val cross = wallClockDays(generations = 4.1)
        val gap = (same - cross) / cross

        assertTrue(
            "同虫只应比跨虫慢一点点（当前 ${"%.0f".format(gap * 100)}%），不能慢到没人愿意走",
            gap < 0.25
        )
    }

    // ═══════════ NPC 配对容量（S16） ═══════════

    @Test
    fun `npc partners get a smaller offspring budget than real players`() {
        val npc = GrowthRules.PairingRules.maxOffspringFor(partnerIsNpc = true)
        val real = GrowthRules.PairingRules.maxOffspringFor(partnerIsNpc = false)

        assertEquals(3, npc)
        assertEquals(5, real)
        assertTrue("真人配对必须明显更划算，否则社交引导失效", npc < real)
    }

    @Test
    fun `npc budget is a fallback not an upgrade`() {
        assertTrue(
            "NPC 存在的意义是用户量不足时兜底，不能比真人更划算",
            GrowthRules.PairingRules.maxNpcOffspring < GrowthRules.PairingRules.maxOffspring
        )
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
