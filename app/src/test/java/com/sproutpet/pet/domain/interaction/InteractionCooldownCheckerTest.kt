package com.sproutpet.pet.domain.interaction

import com.sproutpet.core.domain.pet.GrowthRules
import com.sproutpet.pet.data.local.InteractionStateStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [InteractionCooldownChecker] 冷却与日限测试（docs/04 §2.3 / §2.6）。
 */
class InteractionCooldownCheckerTest {

    private val ownerId = 100L
    private val type = GrowthRules.InteractionType.PET // 冷却 3s，日限 50

    @Test
    fun `首次互动且日限未满 → 允许`() = runTest {
        val store = mockk<InteractionStateStore>()
        coEvery { store.dailyCount(ownerId, type.name) } returns 0
        coEvery { store.lastInteractionAt(ownerId, type.name) } returns 0L
        val checker = InteractionCooldownChecker(store)

        val result = checker.check(ownerId, type)
        assertTrue(result is InteractionCooldownChecker.CheckResult.Allowed)
    }

    @Test
    fun `冷却未结束 → OnCooldown`() = runTest {
        val store = mockk<InteractionStateStore>()
        coEvery { store.dailyCount(ownerId, type.name) } returns 0
        // 1 秒前刚互动过，冷却 3 秒 → 剩约 2 秒
        coEvery { store.lastInteractionAt(ownerId, type.name) } returns
            System.currentTimeMillis() - 1_000L
        val checker = InteractionCooldownChecker(store)

        val result = checker.check(ownerId, type)
        assertTrue(result is InteractionCooldownChecker.CheckResult.OnCooldown)
        val remaining = (result as InteractionCooldownChecker.CheckResult.OnCooldown).remainingMs
        assertTrue(remaining in 1_000L..3_000L)
    }

    @Test
    fun `日限已满 → DailyLimitReached（优先于冷却）`() = runTest {
        val store = mockk<InteractionStateStore>()
        coEvery { store.dailyCount(ownerId, type.name) } returns type.dailyLimit
        coEvery { store.lastInteractionAt(ownerId, type.name) } returns 0L
        val checker = InteractionCooldownChecker(store)

        val result = checker.check(ownerId, type)
        assertTrue(result is InteractionCooldownChecker.CheckResult.DailyLimitReached)
    }

    @Test
    fun `record 会写入时间戳并累加日计数`() = runTest {
        val store = mockk<InteractionStateStore>(relaxed = true)
        val checker = InteractionCooldownChecker(store)

        checker.record(ownerId, type)

        coVerify { store.setLastInteractionAt(eq(ownerId), eq(type.name), any()) }
        coVerify { store.incrementDailyCount(eq(ownerId), eq(type.name)) }
    }
}
