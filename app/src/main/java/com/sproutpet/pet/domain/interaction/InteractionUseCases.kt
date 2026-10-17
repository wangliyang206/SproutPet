package com.sproutpet.pet.domain.interaction

import com.sproutpet.core.domain.pet.GrowthRules
import javax.inject.Inject

/**
 * 抚摸用例（docs/04 §2.3）：冷却 3s，日限 50，+15 经验 +1 亲密度。
 */
class PetPetUseCase @Inject constructor(
    private val executor: InteractionExecutor
) {
    suspend fun execute(petId: Long, ownerId: Long): InteractionResult =
        executor.execute(petId, ownerId, GrowthRules.InteractionType.PET)
}

/**
 * 喂食用例（docs/04 §2.3）：消耗食物道具，冷却 60s，日限 20，+60 经验 +3 亲密度。
 */
class FeedPetUseCase @Inject constructor(
    private val executor: InteractionExecutor
) {
    suspend fun execute(petId: Long, ownerId: Long): InteractionResult =
        executor.execute(petId, ownerId, GrowthRules.InteractionType.FEED)
}

/**
 * 玩耍用例（docs/04 §2.3）：消耗玩具耐久，冷却 120s，日限 15，+105 经验 +5 亲密度 +1 敏捷。
 */
class PlayPetUseCase @Inject constructor(
    private val executor: InteractionExecutor
) {
    suspend fun execute(petId: Long, ownerId: Long): InteractionResult =
        executor.execute(petId, ownerId, GrowthRules.InteractionType.PLAY)
}

/**
 * 训练用例（docs/04 §2.3）：定向提升力量，冷却 300s，日限 8，+150 经验 +2 亲密度 +2 力量 +1 敏捷。
 */
class TrainPetUseCase @Inject constructor(
    private val executor: InteractionExecutor
) {
    suspend fun execute(petId: Long, ownerId: Long): InteractionResult =
        executor.execute(petId, ownerId, GrowthRules.InteractionType.TRAIN)
}

/**
 * 清洁用例（docs/04 §2.3）：冷却 180s，日限 10，+30 经验 +2 亲密度。
 */
class CleanPetUseCase @Inject constructor(
    private val executor: InteractionExecutor
) {
    suspend fun execute(petId: Long, ownerId: Long): InteractionResult =
        executor.execute(petId, ownerId, GrowthRules.InteractionType.CLEAN)
}

/**
 * 桌面互动用例（docs/04 §2.3）：抱/拖/晃，冷却 30s，日限 40，+6 经验 +1 亲密度。
 */
class DesktopInteractUseCase @Inject constructor(
    private val executor: InteractionExecutor
) {
    suspend fun execute(petId: Long, ownerId: Long): InteractionResult =
        executor.execute(petId, ownerId, GrowthRules.InteractionType.DESKTOP)
}
