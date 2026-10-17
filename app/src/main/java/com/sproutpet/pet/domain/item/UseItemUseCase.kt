package com.sproutpet.pet.domain.item

import com.sproutpet.core.domain.item.ItemEffectApplier
import com.sproutpet.core.domain.item.ItemRegistry
import com.sproutpet.core.domain.pet.Pet
import com.sproutpet.core.domain.pet.Rarity
import com.sproutpet.pet.data.repository.ItemRepository
import com.sproutpet.pet.data.repository.PetRepository
import com.sproutpet.pet.data.repository.UserRepository
import com.sproutpet.pet.domain.growth.AdvanceStageUseCase
import com.sproutpet.pet.domain.growth.LevelUpPetUseCase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 道具使用用例（docs/04 §2.4）：按 `ItemEffect` 分派执行。
 *
 * 效果执行本体是 core 的 [ItemEffectApplier]（纯函数），本用例负责：
 * 1. 校验库存是否足够
 * 2. 调用效果执行层
 * 3. 对 AddExp 道具触发升级（LevelUp + AdvanceStage）
 * 4. 持久化宠物 / 钱包 / 扣减库存
 */
@Singleton
class UseItemUseCase @Inject constructor(
    private val itemRepository: ItemRepository,
    private val petRepository: PetRepository,
    private val userRepository: UserRepository
) {

    sealed interface Result {
        data class Success(
            val pet: Pet?,
            val goldDelta: Int,
            val hatchMsReduced: Long,
            val guaranteedRarity: Rarity?
        ) : Result

        /** 道具定义不存在 */
        object ItemNotFound : Result

        /** 库存不足 */
        object NotEnough : Result

        /** 效果需要作用于宠物，但未传 petId */
        object PetRequired : Result

        /** 宠物不存在 */
        object PetNotFound : Result
    }

    /**
     * @param itemId 道具定义 ID
     * @param ownerId 所属用户
     * @param petId 目标宠物 ID；不需要宠物的道具（如 AddGold）传 null
     */
    suspend fun execute(itemId: String, ownerId: Long, petId: Long?): Result {
        val definition = ItemRegistry.find(itemId) ?: return Result.ItemNotFound

        // 1. 库存校验
        if (itemRepository.availableCount(ownerId, itemId) <= 0) return Result.NotEnough

        // 2. 目标宠物（需要个体的效果必须传）
        val needsPet = definition.effects.any {
            it is com.sproutpet.core.domain.item.ItemEffect.ReduceHatchTime ||
                it is com.sproutpet.core.domain.item.ItemEffect.AddExp ||
                it is com.sproutpet.core.domain.item.ItemEffect.AddIntimacy ||
                it is com.sproutpet.core.domain.item.ItemEffect.AddAttribute ||
                it is com.sproutpet.core.domain.item.ItemEffect.AdvanceStage
        }
        val pet: Pet? = if (needsPet) {
            val id = petId ?: return Result.PetRequired
            petRepository.queryById(id) ?: return Result.PetNotFound
        } else {
            petId?.let { petRepository.queryById(it) }
        }

        // 3. 执行效果
        val outcome = ItemEffectApplier.apply(definition.effects, pet)

        // 4. AddExp 道具：经验溢出后触发升级 + 阶段推进
        var resultPet = outcome.pet
        if (resultPet != null && definition.effects.any { it is com.sproutpet.core.domain.item.ItemEffect.AddExp }) {
            val leveled = LevelUpPetUseCase.execute(resultPet)
            resultPet = AdvanceStageUseCase.execute(leveled.pet)
        }

        // 5. 扣减库存
        itemRepository.consume(ownerId, itemId)

        // 6. 持久化
        resultPet?.let { petRepository.save(it) }
        if (outcome.walletGoldDelta != 0) {
            userRepository.addGold(outcome.walletGoldDelta)
        }

        return Result.Success(
            pet = resultPet,
            goldDelta = outcome.walletGoldDelta,
            hatchMsReduced = outcome.hatchMsReduced,
            guaranteedRarity = outcome.guaranteedRarity
        )
    }
}
