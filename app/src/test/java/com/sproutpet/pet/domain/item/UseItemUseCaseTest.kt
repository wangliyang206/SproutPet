package com.sproutpet.pet.domain.item

import com.sproutpet.core.domain.pet.Pet
import com.sproutpet.core.domain.pet.PetAttributes
import com.sproutpet.core.domain.pet.PetGenes
import com.sproutpet.core.domain.pet.PetSpecies
import com.sproutpet.core.domain.pet.PetStage
import com.sproutpet.core.domain.pet.Rarity
import com.sproutpet.pet.data.repository.ItemRepository
import com.sproutpet.pet.data.repository.PetRepository
import com.sproutpet.pet.data.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [UseItemUseCase] 测试（docs/04 §2.4 / §2.6）。
 */
class UseItemUseCaseTest {

    private val ownerId = 100L

    private fun aPet(id: Long = 1L): Pet = Pet(
        id = id, ownerId = ownerId, nickname = "测",
        species = PetSpecies.SNAIL, rarity = Rarity.COMMON, stage = PetStage.BABY,
        level = 1, exp = 0,
        attributes = PetAttributes(),
        genes = PetGenes(), bornAt = 0L, hatchAt = 0L, isHatched = true,
        spouseId = null, familyId = null, parentIdA = null, parentIdB = null, batchNo = "T"
    )

    @Test
    fun `道具不存在 → ItemNotFound`() = runTest {
        val itemRepo = mockk<ItemRepository>()
        val petRepo = mockk<PetRepository>()
        val userRepo = mockk<UserRepository>()
        val useCase = UseItemUseCase(itemRepo, petRepo, userRepo)

        val result = useCase.execute("no_such_item", ownerId, null)
        assertTrue(result is UseItemUseCase.Result.ItemNotFound)
    }

    @Test
    fun `库存不足 → NotEnough`() = runTest {
        val itemRepo = mockk<ItemRepository> {
            coEvery { availableCount(ownerId, "food_basic") } returns 0
        }
        val petRepo = mockk<PetRepository>()
        val userRepo = mockk<UserRepository>()
        val useCase = UseItemUseCase(itemRepo, petRepo, userRepo)

        val result = useCase.execute("food_basic", ownerId, 1L)
        assertTrue(result is UseItemUseCase.Result.NotEnough)
    }

    @Test
    fun `需要宠物但未传 petId → PetRequired`() = runTest {
        val itemRepo = mockk<ItemRepository> {
            coEvery { availableCount(ownerId, "food_basic") } returns 1
        }
        val petRepo = mockk<PetRepository>()
        val userRepo = mockk<UserRepository>()
        val useCase = UseItemUseCase(itemRepo, petRepo, userRepo)

        val result = useCase.execute("food_basic", ownerId, null)
        assertTrue(result is UseItemUseCase.Result.PetRequired)
    }

    @Test
    fun `宠物不存在 → PetNotFound`() = runTest {
        val itemRepo = mockk<ItemRepository> {
            coEvery { availableCount(ownerId, "food_basic") } returns 1
        }
        val petRepo = mockk<PetRepository> {
            coEvery { queryById(99L) } returns null
        }
        val userRepo = mockk<UserRepository>()
        val useCase = UseItemUseCase(itemRepo, petRepo, userRepo)

        val result = useCase.execute("food_basic", ownerId, 99L)
        assertTrue(result is UseItemUseCase.Result.PetNotFound)
    }

    @Test
    fun `使用食物成功 → 加经验并扣库存`() = runTest {
        val pet = aPet()
        val itemRepo = mockk<ItemRepository> {
            coEvery { availableCount(ownerId, "food_basic") } returns 1
            coEvery { consume(ownerId, "food_basic") } returns true
        }
        val petRepo = mockk<PetRepository> {
            coEvery { queryById(1L) } returns pet
            coEvery { save(any()) } returns 1L
        }
        val userRepo = mockk<UserRepository>()
        val useCase = UseItemUseCase(itemRepo, petRepo, userRepo)

        val result = useCase.execute("food_basic", ownerId, 1L)
        assertTrue(result is UseItemUseCase.Result.Success)
        val success = result as UseItemUseCase.Result.Success
        assertEquals(20, success.pet?.exp) // food_basic AddExp(20)
        assertEquals(3, success.pet?.attributes?.intimacy) // AddIntimacy(3)

        coVerify { itemRepo.consume(ownerId, "food_basic") }
        coVerify { petRepo.save(any()) }
    }
}
