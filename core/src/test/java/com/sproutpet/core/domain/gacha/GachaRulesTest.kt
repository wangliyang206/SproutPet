package com.sproutpet.core.domain.gacha

import com.sproutpet.core.domain.economy.EconomyRules
import com.sproutpet.core.domain.incubation.BlindBoxEngine
import com.sproutpet.core.domain.item.ItemRegistry
import com.sproutpet.core.domain.item.ItemSource
import com.sproutpet.core.domain.pet.Rarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * 抽蛋（盲盒）规则校验（docs/09 `U-13` / `G-13`）。
 *
 * ## 这个类存在的意义
 *
 * 抽蛋的概率与保底**早就写好了**，但"这门生意怎么运转"整个缺失：
 * `blind_box` 道具声明了商店来源却**没有价格**；多连从未被设计；
 * 概率公示承诺了却没有一个数字可引用。
 *
 * 所以这里守三件事：
 * 1. **门票必须真的能买到**（回归测试：防止"商店有卖但没价"再次发生）；
 * 2. **十连兜底必须由引擎执行，且不得反向通胀**（保「稀有」几乎免费，保「史诗」会 +35%）；
 * 3. **公示条款不得被悄悄删短**（合规底线，删了就是少报）。
 */
class GachaRulesTest {

    // ═══════════ ① 门票：回归测试（G-13 的原始硬伤） ═══════════

    @Test
    fun `the blind box is actually purchasable`() {
        // 修复前：`sources` 里有 SHOP，但 shopPriceGold / shopPriceGem **双双是 null**。
        // 任何商店 UI 都会把这一项渲染成空白 —— 而且没人会发现，因为类型上它是合法的。
        val box = ItemRegistry.find(GachaRules.BOX_ITEM_ID)
        assertNotNull("盲盒道具必须存在于 ItemRegistry", box)

        assertNotNull("盲盒声明了商店来源，却仍然没有金币价（商店会渲染不出来）", box!!.shopPriceGold)
        assertNotNull("盲盒缺少钻石价", box.shopPriceGem)
        assertTrue("金币价必须为正", box.shopPriceGold!! > 0)
        assertTrue("钻石价必须为正", box.shopPriceGem!! > 0)

        // 价格只能有一处定义：道具表只是"引用"数值中枢，不得各写一份。
        assertEquals(GachaRules.GOLD_PRICE_PER_BOX, box.shopPriceGold!!)
        assertEquals(GachaRules.GEM_PRICE_PER_BOX, box.shopPriceGem!!)
    }

    @Test
    fun `the blind box is stackable and declares its real sources`() {
        val box = ItemRegistry.find(GachaRules.BOX_ITEM_ID)!!
        assertTrue("盲盒必须可堆叠 —— 否则「攒够十连再抽」无从谈起", box.stackable)
        assertTrue(
            "免费日供来自日常任务，来源里必须有 DAILY_TASK（否则玩家不知道抽数从哪来）",
            box.sources.contains(ItemSource.DAILY_TASK)
        )
        assertTrue(box.sources.contains(ItemSource.SHOP))
    }

    @Test
    fun `the gold price stays affordable within a single day`() {
        // 补票口的定位：当天收入就够补 1 抽。
        // 若哪天盲盒涨价或日收入被砍，这条会红 —— 提醒"补票要攒几天"是产品事故。
        assertTrue(
            "盲盒金币价需要 ${String.format("%.2f", EconomyRules.daysToAffordBlindBox)} 天才能攒够，补票口失效",
            EconomyRules.daysToAffordBlindBox < 1.0
        )
        // 但日限必须锁住"有钱就能无限抽"。
        assertEquals(1, GachaRules.MAX_GOLD_PURCHASE_PER_DAY)
    }

    // ═══════════ ② 免费节奏：一个月填满「家」 ═══════════

    @Test
    fun `free tickets fill the house in about a month`() {
        // 平衡锚点：每天 2 抽 × 30 天 ÷ 每只史诗+ 9.6 抽 ≈ 6.25 只。
        val perMonth = GachaRules.FREE_DRAWS_PER_MONTH / GachaRules.EXPECTED_DRAWS_TO_EPIC_PLUS

        assertTrue(
            "一个月免费只能拿到 ${String.format("%.2f", perMonth)} 只史诗+，填不满家在册 6 只 → 稀缺结构断裂",
            perMonth >= 6.0
        )
        assertTrue(
            "一个月免费能拿到 ${String.format("%.2f", perMonth)} 只史诗+，远超家在册 6 只 → 抽卡通胀，生育被架空",
            perMonth < 8.0
        )
    }

    @Test
    fun `free tickets come from companionship not from money`() {
        // 定位守卫：免费票必须同时挂在"签到"与"任务全清"上。
        // 若哪天 ALL_TASKS_DRAW 被删成一个纯登录奖励，抽蛋就与宠物脱钩了。
        assertTrue("签到必须给票", GachaRules.SIGN_IN_DRAW >= 1)
        assertTrue("任务全清必须给票（抽蛋应当是照顾宠物的副产品）", GachaRules.ALL_TASKS_DRAW >= 1)
        assertEquals(
            GachaRules.SIGN_IN_DRAW + GachaRules.ALL_TASKS_DRAW,
            GachaRules.FREE_DRAWS_PER_DAY
        )
    }

    // ═══════════ ③ 十连：兜底必须由引擎执行 ═══════════

    @Test
    fun `a ten pull never comes back all common`() {
        // 「十连全普通」概率 1.21% —— 是最伤体验的那一档，兜底就是消掉它。
        val rnd = Random(20261016)
        var pity = 0
        repeat(2_000) {
            val batch = BlindBoxEngine.drawMany(count = GachaRules.TEN_PULL, pityCount = pity, random = rnd)
            pity = batch.newPityCount
            assertTrue(
                "十连里一只稀有及以上都没有（兜底没生效）",
                batch.results.any { it.rarity.id >= GachaRules.TEN_PULL_FLOOR.id }
            )
        }
    }

    @Test
    fun `the floor only lifts to rare and only the last one`() {
        val rnd = Random(7)
        var pity = 0
        var floorSeen = 0
        repeat(2_000) {
            val batch = BlindBoxEngine.drawMany(count = GachaRules.TEN_PULL, pityCount = pity, random = rnd)
            pity = batch.newPityCount
            val floored = batch.results.filter { it.flooredByBatch }
            if (batch.floorApplied) {
                floorSeen++
                assertEquals("兜底只能提升最后一只", 1, floored.size)
                assertEquals(
                    "兜底只需抬到「稀有」，抬更高就是白送（会把史诗率抬到 13.47%）",
                    GachaRules.TEN_PULL_FLOOR,
                    floored.single().rarity
                )
            } else {
                assertTrue("没触发兜底却标记了提升", floored.isEmpty())
            }
        }
        assertTrue("2000 批十连里一次兜底都没触发，测试没覆盖到该分支", floorSeen > 0)
    }

    @Test
    fun `the ten pull floor does not inflate the epic rate`() {
        // 这是本节最该被看见的约束：保「稀有」几乎免费（+0.34%），
        // 保「史诗」会把史诗+ 产出率从 9.97% 抬到 13.47%（+35%），
        // 直接稀释 docs/00 §4.1 的三层稀缺结构。这条测试就是拦住那个改动。
        //
        // ⚠️ 长期连续抽（保底一直累计）的均衡产出率就是**综合概率 10.42%**，
        // 而不是单抽的 9.968% —— 这正是"含保底的综合概率"必须公示的原因。
        // 所以这里以综合概率为基准，给 ±1 个百分点：
        // 偷偷保史诗会把它推到 ~14.1%（+3.7pp），必红。
        val draws = 40_000
        val rnd = Random(99)
        var pity = 0
        var epicPlus = 0
        var done = 0
        while (done < draws) {
            val batch = BlindBoxEngine.drawMany(count = GachaRules.TEN_PULL, pityCount = pity, random = rnd)
            pity = batch.newPityCount
            epicPlus += batch.results.count { it.rarity.isHighRarity }
            done += batch.results.size
        }
        val rate = epicPlus.toDouble() / done
        val expected = GachaRules.COMPREHENSIVE_EPIC_PLUS_RATE.toDouble()

        assertTrue(
            "史诗+ 产出率被抬到 ${String.format("%.4f", rate)}（综合概率 ${String.format("%.4f", expected)}）" +
                " —— 十连兜底偷偷保了史诗档",
            rate < expected + 0.01
        )
        assertTrue(
            "史诗+ 产出率异常偏低：${String.format("%.4f", rate)} —— 保底可能没生效",
            rate > expected - 0.01
        )
    }

    // ═══════════ ④ 保底：第 30 抽必须精确触发 ═══════════

    @Test
    fun `the pity fires on exactly the thirtieth draw`() {
        // 判定式是 pityCount + 1 >= PITY_THRESHOLD → pityCount = 29（第 30 抽）触发。
        // 文档曾写"连续 30 次未出 → 第 31 次必出"，比代码晚 1 抽（X-16）。
        val before = BlindBoxEngine.draw(pityCount = BlindBoxEngine.PITY_THRESHOLD - 2)
        assertFalse("第 29 抽不该触发保底", before.result.isLucky)

        val at = BlindBoxEngine.draw(pityCount = BlindBoxEngine.PITY_THRESHOLD - 1)
        assertTrue("第 30 抽必须触发保底", at.result.isLucky)
        assertTrue("保底给出的档位必须至少是史诗", at.result.rarity.isHighRarity)
    }

    @Test
    fun `remaining to pity counts down to the guarantee`() {
        assertEquals(GachaRules.PITY_THRESHOLD, GachaRules.remainingToPity(0))
        assertEquals(12, GachaRules.remainingToPity(18))
        assertEquals(1, GachaRules.remainingToPity(BlindBoxEngine.PITY_THRESHOLD - 1))
        assertEquals("已触发过保底时不该返回负数", 0, GachaRules.remainingToPity(BlindBoxEngine.PITY_THRESHOLD))
        // 与灵兽保底的同款能力对称 —— 这条不对称曾经存在过（G-13 ②）。
        assertEquals(GachaRules.PITY_THRESHOLD, BlindBoxEngine.remainingToPity(0))
    }

    @Test
    fun `the pity counter resets on a high rarity and advances otherwise`() {
        repeat(3_000) {
            val pity = Random.nextInt(0, 5)
            val outcome = BlindBoxEngine.draw(pityCount = pity)
            val expected = if (outcome.result.rarity.isHighRarity) 0 else pity + 1
            assertEquals(expected, outcome.newPityCount)
        }
    }

    @Test
    fun `a triggered pity always resets the counter`() {
        repeat(200) {
            val outcome = BlindBoxEngine.draw(pityCount = BlindBoxEngine.PITY_THRESHOLD - 1)
            assertEquals("保底给出高稀有后计数必须归零", 0, outcome.newPityCount)
        }
    }

    // ═══════════ ⑤ 多连：批内计数传递 + 越界拒绝 ═══════════

    @Test
    fun `a batch carries the pity counter across its draws`() {
        val batch = BlindBoxEngine.drawMany(count = GachaRules.TEN_PULL, pityCount = 5, random = Random(3))

        // 用同一颗种子手工重放十次单抽：批内计数必须等于"逐抽链式传递"的结果。
        // 若哪天有人把十连实现成"每抽都从 0 开始"，这条会立刻变红。
        val replay = Random(3)
        var pity = 5
        repeat(GachaRules.TEN_PULL) {
            pity = BlindBoxEngine.draw(pityCount = pity, random = replay).newPityCount
        }
        assertEquals("批内保底计数没有逐抽传递（十连被当成了保底重置点）", pity, batch.newPityCount)
        assertEquals(GachaRules.TEN_PULL, batch.results.size)
    }

    @Test
    fun `a batch longer than a ten pull is rejected`() {
        // 只做十连。显式失败优于静默支持 50 连（那会变成 15,000 金币 = 15 天日收入）。
        try {
            BlindBoxEngine.drawMany(count = GachaRules.MAX_BATCH + 1)
            throw AssertionError("超过十连的批次未被拒绝")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("最多连抽"))
        }
    }

    @Test
    fun `a single draw gets no batch floor`() {
        repeat(500) {
            val batch = BlindBoxEngine.drawMany(count = 1, random = Random(it))
            assertFalse("单抽不该有「最低保障」", batch.floorApplied)
            assertEquals(1, batch.results.size)
        }
    }

    // ═══════════ ⑥ 概率公示：合规底线不得被删短 ═══════════

    @Test
    fun `the disclosed rate is the comprehensive one`() {
        // 上架要披露的是含保底的综合概率，不是基础概率 —— 写基础概率属于少报。
        assertTrue(
            "综合概率必须高于基础概率（含保底）",
            GachaRules.COMPREHENSIVE_EPIC_PLUS_RATE > GachaRules.BASE_EPIC_PLUS_RATE
        )
    }

    @Test
    fun `the disclosure states the two counterintuitive facts`() {
        val text = GachaRules.Disclosure.MUST_STATE.joinToString(" | ")
        assertTrue("公示必须写明保底只到「史诗」", text.contains("史诗"))
        assertTrue(
            "公示必须写明「传说 / 神话没有保底通道」—— 否则玩家会以为抽多了总会出神话（期望 320 抽）",
            text.contains("传说") && text.contains("神话") && text.contains("没有保底")
        )
        assertTrue("公示必须写明灵兽不可抽（ADR-017）", text.contains("灵兽"))
    }
}
