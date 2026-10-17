package com.sproutpet.pet.domain.interaction

import com.sproutpet.core.domain.pet.GrowthRules
import com.sproutpet.pet.data.local.InteractionStateStore
import com.sproutpet.pet.data.repository.ItemRepository
import com.sproutpet.pet.data.repository.PetRepository
import com.sproutpet.pet.domain.growth.AdvanceStageUseCase
import com.sproutpet.pet.domain.growth.GainExpUseCase
import com.sproutpet.pet.domain.growth.GainIntimacyUseCase
import com.sproutpet.pet.domain.growth.GrowAttributesUseCase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 互动执行器（docs/04 §2.3）：冷却/日限校验 → 结算 → 持久化。
 *
 * 六个具体互动 UseCase（抚摸/喂食/玩耍/训练/清洁/桌面）都委托到这里，
 * 避免六份重复的「查宠物→校验→结算→存库」模板代码。
 *
 * 每日经验上限（[GrowthRules.DAILY_EXP_CAP]）在此执法：若本次经验会超出上限，
 * 实际经验被钳制到剩余额度（亲密/属性仍正常结算，不浪费这次互动）。
 */
@Singleton
class InteractionExecutor @Inject constructor(
    private val petRepository: PetRepository,
    private val cooldownChecker: InteractionCooldownChecker,
    private val stateStore: InteractionStateStore,
    private val itemRepository: ItemRepository
) {

    /** 喂食消耗的食物道具 id */
    private val feedItemId = "food_basic"
    /** 玩耍消耗的玩具（优先毛线球，其次飞盘） */
    private val playItemCandidates = listOf("toy_yarn", "toy_frisbee")

    suspend fun execute(
        petId: Long,
        ownerId: Long,
        type: GrowthRules.InteractionType
    ): InteractionResult {
        // 1. 冷却 + 日限校验
        when (val check = cooldownChecker.check(ownerId, type)) {
            is InteractionCooldownChecker.CheckResult.OnCooldown ->
                return InteractionResult.OnCooldown(check.remainingMs)
            InteractionCooldownChecker.CheckResult.DailyLimitReached ->
                return InteractionResult.DailyLimitReached
            InteractionCooldownChecker.CheckResult.Allowed -> Unit
        }

        // 2. 道具消耗校验（喂食 / 玩耍）
        val consumedItemId = when (type) {
            GrowthRules.InteractionType.FEED -> {
                if (itemRepository.availableCount(ownerId, feedItemId) <= 0)
                    return InteractionResult.ItemNotEnough
                feedItemId
            }
            GrowthRules.InteractionType.PLAY -> {
                val toy = playItemCandidates.firstOrNull {
                    itemRepository.availableCount(ownerId, it) > 0
                } ?: return InteractionResult.ItemNotEnough
                toy
            }
            else -> null
        }

        // 3. 取宠物
        val pet = petRepository.queryById(petId) ?: return InteractionResult.PetNotFound

        // 4. 经验（受每日上限钳制）
        val dailyExp = stateStore.dailyExpGained(ownerId)
        val remainingCap = GrowthRules.DAILY_EXP_CAP - dailyExp
        val actualExp = type.exp.coerceAtMost(remainingCap.coerceAtLeast(0))

        val expResult = GainExpUseCase.execute(pet, actualExp)
        var current = expResult.pet
        current = GainIntimacyUseCase.execute(current, type.intimacy)
        current = GrowAttributesUseCase.execute(current, type)

        // 5. 扣减道具（玩具扣耐久，见 ItemRepository.consume）
        consumedItemId?.let { itemRepository.consume(ownerId, it) }

        // 6. 持久化
        petRepository.save(current)

        // 7. 记录冷却、日限计数、每日经验
        cooldownChecker.record(ownerId, type)
        if (actualExp > 0) stateStore.addDailyExp(ownerId, actualExp)

        return InteractionResult.Success(
            pet = current,
            expGained = actualExp,
            intimacyGained = type.intimacy,
            leveledUp = expResult.leveledUp,
            newLevel = expResult.newLevel,
            stageAdvanced = expResult.stageAdvanced
        )
    }
}
