package com.sproutpet.core.domain.pet

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 行为导演决策规则单测（docs/15 §十 · 2026-10-20 真机反馈「宠物自己没戏演」）。
 *
 * 守的四条硬规则：
 * 1. 睡觉 → 恒 REST（睡着不许溜达）
 * 2. 没伙伴 → 永不 SOCIAL
 * 3. WANDER 目标必须在家锚点附近且夹在安全区内
 * 4. 同种子同决策（确定性）
 */
class BehaviorDirectorTest {

    private fun decideMany(
        seed: Long,
        times: Int = 400,
        sleeping: Boolean = false,
        companionCount: Int = 0,
        homeX: Float = 100f, homeY: Float = 100f,
        minX: Float = 0f, maxX: Float = 200f,
        minY: Float = 0f, maxY: Float = 200f,
    ): List<PetBehaviorDecision> {
        val rng = Random(seed)
        return (0 until times).map {
            BehaviorDirector.decide(
                rng, sleeping, companionCount,
                homeX, homeY, minX, maxX, minY, maxY
            )
        }
    }

    @Test
    fun `睡觉时永远 REST`() {
        val actions = decideMany(seed = 7, sleeping = true).map { it.action }.toSet()
        assertEquals(setOf(PetBehaviorAction.REST), actions)
    }

    @Test
    fun `没有伙伴时永不 SOCIAL`() {
        val actions = decideMany(seed = 42, companionCount = 0).map { it.action }
        assertTrue("companionCount=0 出现了 SOCIAL", PetBehaviorAction.SOCIAL !in actions)
    }

    @Test
    fun `有伙伴时可能出现 SOCIAL`() {
        val actions = decideMany(seed = 42, companionCount = 2, times = 600).map { it.action }
        assertTrue("600 拍里一次社交都没有，概率带写错了", PetBehaviorAction.SOCIAL in actions)
    }

    @Test
    fun `溜达目标在家锚点附近且在安全区内`() {
        val decisions = decideMany(seed = 99, times = 800).filter { it.isWander }
        assertTrue("一次 WANDER 都没有", decisions.isNotEmpty())
        decisions.forEach { d ->
            assertTrue(
                "越界/跑远的溜达目标：$d",
                BehaviorDirector.isWanderTargetValid(
                    d,
                    homeX = 100f, homeY = 100f,
                    minX = 0f, maxX = 200f, minY = 0f, maxY = 200f,
                    spanX = 200f, spanY = 200f
                )
            )
        }
    }

    @Test
    fun `目标被夹回安全区而不越界`() {
        // 家锚点贴着左边界，向左溜达的目标必须被夹回 minX
        val decisions = decideMany(
            seed = 5, times = 600,
            homeX = 2f, homeY = 100f,
            minX = 0f, maxX = 200f, minY = 0f, maxY = 200f
        ).filter { it.isWander }
        decisions.forEach { assertTrue("目标越出左边界：$it", it.targetX >= 0f) }
    }

    @Test
    fun `同种子同决策`() {
        val a = decideMany(seed = 123, times = 100)
        val b = decideMany(seed = 123, times = 100)
        assertEquals(a, b)
    }

    @Test
    fun `非睡眠时四种基础动作都会出现`() {
        val actions = decideMany(seed = 2026, times = 1200).map { it.action }.toSet()
        listOf(
            PetBehaviorAction.WANDER,
            PetBehaviorAction.YAWN,
            PetBehaviorAction.LOOK,
            PetBehaviorAction.REST
        ).forEach { action ->
            assertTrue("概率带上 $action 一次都没出现", action in actions)
        }
    }
}
