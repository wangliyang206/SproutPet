package com.sproutpet.pet.domain.interaction

import com.sproutpet.core.domain.pet.GrowthRules
import com.sproutpet.core.domain.pet.Pet
import com.sproutpet.core.domain.pet.PetAttributes
import com.sproutpet.core.domain.pet.PetGenes
import com.sproutpet.core.domain.pet.PetSpecies
import com.sproutpet.core.domain.pet.PetStage
import com.sproutpet.core.domain.pet.Rarity
import com.sproutpet.pet.data.local.InteractionStateStore
import com.sproutpet.pet.data.repository.ItemRepository
import com.sproutpet.pet.data.repository.PetRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 喂食链路测试（docs/04 §2.3 / §2.6）：冷却、日限、道具不足、成功结算。
 *
 * 喂食最终走 [InteractionExecutor]，本测试用真实 executor + mock 依赖覆盖 FEED 全部分支。
 */
class FeedPetUseCaseTest {

    private val ownerId = 100L
    private val petId = 1L
    private val type = GrowthRules.InteractionType.FEED

    private fun aPet(): Pet = Pet(
        id = petId, ownerId = ownerId, nickname = "测",
        species = PetSpecies.SNAIL, rarity = Rarity.COMMON, stage = PetStage.BABY,
        level = 1, exp = 0, attributes = PetAttributes(),
        genes = PetGenes(), bornAt = 0L, hatchAt = 0L, isHatched = true,
        spouseId = null, familyId = null, parentIdA = null, parentIdB = null, batchNo = "T"
    )

    private data class Harness(val executor: InteractionExecutor, val itemRepo: ItemRepository)

    private fun executor(
        pet: Pet = aPet(),
        dailyCount: Int = 0,
        lastAt: Long = 0L,
        foodCount: Int = 1,
        dailyExp: Int = 0
    ): Harness {
        val checker = mockk<InteractionCooldownChecker> {
            coEvery { check(ownerId, type) } returns
                when {
                    dailyCount >= type.dailyLimit -> InteractionCooldownChecker.CheckResult.DailyLimitReached
                    System.currentTimeMillis() - lastAt < type.cooldownMs ->
                        InteractionCooldownChecker.CheckResult.OnCooldown(type.cooldownMs)
                    else -> InteractionCooldownChecker.CheckResult.Allowed
                }
            coEvery { record(ownerId, type) } returns Unit
        }
        val store = mockk<InteractionStateStore> {
            coEvery { dailyExpGained(ownerId) } returns dailyExp
            coEvery { addDailyExp(ownerId, any()) } returns Unit
        }
        val petRepo = mockk<PetRepository> {
            coEvery { queryById(petId) } returns pet
            coEvery { save(any()) } returns 1L
        }
        val itemRepo = mockk<ItemRepository> {
            coEvery { availableCount(ownerId, "food_basic") } returns foodCount
            coEvery { consume(ownerId, "food_basic") } returns true
        }
        return Harness(InteractionExecutor(petRepo, checker, store, itemRepo), itemRepo)
    }

    @Test
    fun `食物不足 → ItemNotEnough`() = runTest {
        val useCase = FeedPetUseCase(executor(foodCount = 0).executor)
        val result = useCase.execute(petId, ownerId)
        assertTrue(result is InteractionResult.ItemNotEnough)
    }

    @Test
    fun `日限已满 → DailyLimitReached`() = runTest {
        val useCase = FeedPetUseCase(executor(dailyCount = type.dailyLimit).executor)
        val result = useCase.execute(petId, ownerId)
        assertTrue(result is InteractionResult.DailyLimitReached)
    }

    @Test
    fun `冷却中 → OnCooldown`() = runTest {
        val useCase = FeedPetUseCase(executor(lastAt = System.currentTimeMillis()).executor)
        val result = useCase.execute(petId, ownerId)
        assertTrue(result is InteractionResult.OnCooldown)
    }

    @Test
    fun `喂食成功 → 加经验亲密并扣食物`() = runTest {
        val h = executor()
        val useCase = FeedPetUseCase(h.executor)
        val result = useCase.execute(petId, ownerId)
        assertTrue(result is InteractionResult.Success)
        val success = result as InteractionResult.Success
        assertEquals(type.exp, success.expGained)
        assertEquals(type.intimacy, success.intimacyGained)
        coVerify { h.itemRepo.consume(ownerId, "food_basic") }
    }
}
