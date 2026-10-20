package com.sproutpet.pet.data.repository

import com.sproutpet.core.db.entity.PairingDao
import com.sproutpet.core.db.entity.PetDao
import com.sproutpet.core.db.mapper.PetMapper
import com.sproutpet.core.domain.pet.FusionRules
import com.sproutpet.core.domain.pet.GrowthRules
import com.sproutpet.core.domain.pet.HomeSlots
import com.sproutpet.core.domain.pet.Pet
import com.sproutpet.core.domain.pet.PetStage
import com.sproutpet.core.domain.social.PairingStatus
import com.sproutpet.pet.data.repository.FuseFailure.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 融合仓库（design-v2 屏 26 · docs/00 §2.6.1 · docs/10-融合机制设计）。
 *
 * 「把重复的那只喂给要留下的那只」：目标变强（印记 → 稀有度上漂），副宠**入后山**。
 *
 * ## 规则与编排的分工（A5 红线）
 *
 * 能不能融（[FusionRules.check]）、融完什么样（[FusionRules.resolve]）是 core 的**纯函数**；
 * 本层只做四件编排事：扣金币、写两只宠、解除受影响的配对、组织拒绝原因。
 * **任何数值都不在这里发明** —— 金币单价读 [FusionRules.costGold]。
 */
interface FusionRepository {

    /** 可参与融合的宠物：本主人的、已破壳、未隐居（融合候选列表 + 目标/材料都从这取） */
    fun observeFusiblePets(ownerId: Long): Flow<List<Pet>>

    /**
     * 执行一次融合。
     *
     * @return 成功时返回 [FuseSuccess]；任何一步不满足都**整体不做**（整笔不扣），
     *         返回具体拒绝原因 —— 不给"扣了钱没效果"或"喂了一半"留活口。
     */
    suspend fun fuse(ownerId: Long, targetId: Long, materialId: Long): FuseOutcome
}

/** 融合成功结果 */
data class FuseSuccess(
    /** 融合后的目标宠（新稀有度 / 新印记 / 新经验，已持久化） */
    val target: Pet,
    /** 入后山后的副宠（stage = RETIRED，已持久化） */
    val material: Pet,
    /** 纯函数结算结果（drifted = 是否上漂，用于播揭晓动画） */
    val fusion: FusionRules.FusionResult,
    /** 目标是否因经验升级 */
    val leveledUp: Boolean,
    /** 升级后的等级（未升级为 null） */
    val newLevel: Int?
) {
    /** 本次融合给目标的经验（= 副宠等级 × 50，读 [FusionRules.expGainedPerMaterialLevel]） */
    val expGained: Int get() = fusion.expGained
}

/** 融合拒绝原因（规则拒绝沿用 [FusionRules.RejectReason]；编排层另有两条） */
sealed class FuseFailure {
    /** 领域规则拒绝（同物种/灵兽/封顶/未破壳…文案直接读 reason.message） */
    data class RuleRejected(val reason: FusionRules.RejectReason) : FuseFailure()

    /**
     * 副宠是**主宠** —— 主宠不能被喂掉。
     *
     * 依据 `U-12`：主宠是「在任何地方都优先表达的那只」，把它送进后山等于
     * **静默降级主宠**，而「想换主宠必须先回答谁是新的主角」。
     * 这是编排层防护（不进 [FusionRules]，领域规则不感知槽位概念）。
     */
    data object MaterialIsMain : FuseFailure()

    /** 金币不足（[need] = 还差多少） */
    data class NotEnoughGold(val need: Int) : FuseFailure()

    /** 宠物不存在或不属于该主人（防御：UI 层传错 id） */
    data object PetNotFound : FuseFailure()
}

/** 融合结果：成功或整体失败 */
sealed class FuseOutcome {
    data class Ok(val success: FuseSuccess) : FuseOutcome()
    data class Rejected(val failure: FuseFailure) : FuseOutcome()
}

@Singleton
class FusionRepositoryImpl @Inject constructor(
    private val petDao: PetDao,
    private val pairingDao: PairingDao,
    private val userRepository: UserRepository
) : FusionRepository {

    override fun observeFusiblePets(ownerId: Long): Flow<List<Pet>> =
        petDao.observeByOwner(ownerId).map { entities ->
            PetMapper.toDomainList(entities).filter { it.isHatched }
        }

    override suspend fun fuse(ownerId: Long, targetId: Long, materialId: Long): FuseOutcome =
        withContext(Dispatchers.IO) {
            val target = petDao.queryById(targetId)?.let(PetMapper::toDomain)
            val material = petDao.queryById(materialId)?.let(PetMapper::toDomain)
            if (target == null || material == null ||
                target.ownerId != ownerId || material.ownerId != ownerId
            ) {
                return@withContext FuseOutcome.Rejected(PetNotFound)
            }

            // ① 领域规则（越早拒绝越省事）
            val check = FusionRules.check(target, material)
            if (!check.allowed) {
                return@withContext FuseOutcome.Rejected(RuleRejected(check.rejectReason!!))
            }

            // ② 编排层防护：主宠不能被喂掉（U-12，见 FuseFailure.MaterialIsMain）
            if (material.isMain) {
                return@withContext FuseOutcome.Rejected(MaterialIsMain)
            }

            // ③ 金币：整笔校验在前，消费在后 —— 失败路径不产生半扣
            val gold = userRepository.current()?.gold ?: 0
            val cost = FusionRules.costGold
            if (gold < cost) {
                return@withContext FuseOutcome.Rejected(NotEnoughGold(need = cost - gold))
            }

            // ④ 结算（纯函数：加印记 → 上漂 → 经验）
            val fusion = FusionRules.resolve(target, target.fusionMarks, material)

            // ⑤ 目标：稀有度上漂 + 印记 + 经验升级（连跳，与 ApplyInteractionUseCase 同款循环）
            var newExp = target.exp + fusion.expGained
            var newLevel = target.level
            var leveledUp = false
            while (newLevel < GrowthRules.MAX_LEVEL) {
                val need = GrowthRules.expToNextLevel(newLevel)
                if (need == Int.MAX_VALUE || newExp < need) break
                newExp -= need
                newLevel++
                leveledUp = true
            }
            if (newLevel >= GrowthRules.MAX_LEVEL) {
                newLevel = GrowthRules.MAX_LEVEL
                newExp = 0
            }
            val newAttributes = if (leveledUp) {
                GrowthRules.AttributeRules.onLevelUp(
                    oldLevel = target.level,
                    newLevel = newLevel,
                    rarity = fusion.targetRarity,
                    tendency = target.species.tendency,
                    current = target.attributes
                )
            } else {
                target.attributes
            }
            val newTarget = target.copy(
                rarity = fusion.targetRarity,
                fusionMarks = fusion.targetMarks,
                level = newLevel,
                exp = newExp,
                attributes = newAttributes
                // 📌 上漂不重算属性面板：docs/10 §六 —— 融合的即时反馈是经验，
                //    稀有度变化只影响外观与后续成长曲线，不给"变强就白嫖属性"。
            )

            // ⑥ 副宠入后山：不消失（族谱/图鉴保留），但退出桌面与配对，且不可取回
            val retiredMaterial = material.copy(
                stage = PetStage.RETIRED,
                homeSlot = HomeSlots.NO_SLOT,
                desktopSlot = HomeSlots.NO_SLOT,
                // 解除与任何人的配对搭档关系；对方指向它的 spouseId 一并清空
                spouseId = null
            )
            val reconciledTarget = if (newTarget.spouseId == material.id) {
                newTarget.copy(spouseId = null)
            } else {
                newTarget
            }

            // ⑦ 解除受影响的配对记录（status = RELEASED，宠物归属不变）
            val activePairing = pairingDao.queryActiveByPet(material.id)
            if (activePairing != null) {
                pairingDao.update(
                    activePairing.copy(
                        status = PairingStatus.RELEASED.id,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }

            // ⑧ 落库：先写两只宠，再扣金币（都成功才动钱包）
            petDao.insert(PetMapper.toEntity(retiredMaterial))
            petDao.insert(PetMapper.toEntity(reconciledTarget))
            userRepository.addGold(-cost)

            FuseOutcome.Ok(
                FuseSuccess(
                    target = reconciledTarget,
                    material = retiredMaterial,
                    fusion = fusion,
                    leveledUp = leveledUp,
                    newLevel = if (leveledUp) newLevel else null
                )
            )
        }
}
