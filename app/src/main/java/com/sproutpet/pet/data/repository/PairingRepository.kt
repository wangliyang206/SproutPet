package com.sproutpet.pet.data.repository

import com.sproutpet.core.db.entity.PairingDao
import com.sproutpet.core.db.entity.PairingEntity
import com.sproutpet.core.db.entity.PetDao
import com.sproutpet.core.db.mapper.PairingMapper
import com.sproutpet.core.db.mapper.PetMapper
import com.sproutpet.core.domain.pet.DeityRules
import com.sproutpet.core.domain.pet.GrowthRules
import com.sproutpet.core.domain.pet.Pet
import com.sproutpet.core.domain.pet.PetAttributes
import com.sproutpet.core.domain.pet.PetGender
import com.sproutpet.core.domain.pet.PetGenes
import com.sproutpet.core.domain.pet.PetSpecies
import com.sproutpet.core.domain.pet.PetStage
import com.sproutpet.core.domain.pet.Rarity
import com.sproutpet.core.domain.social.NpcCandidateRules
import com.sproutpet.core.domain.social.Pairing
import com.sproutpet.core.domain.social.PairingStatus
import com.sproutpet.pet.data.repository.PairingFailure.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** NPC 宠的系统托管 ownerId（不等于任何真实用户；`observeByOwner` 按 owner 过滤，NPC 不会混进用户列表） */
const val SYSTEM_NPC_OWNER_ID = -1L

/** NPC 宠的批次号（追溯来源用） */
const val NPC_BATCH_NO = "NPC"

/**
 * 配对生育仓库（design-v2 屏 24/25 · docs/00 §2.5 · docs/08 S16）。
 *
 * ## 规则与编排的分工（A5 红线，与 [FusionRepository] 同款）
 *
 * 能不能配（`PairingRules` 七条前置）、能不能生（`Pairing.canBreedNow`：
 * 冷却 3 天 + 名额）、生什么样（`BreedingRules.resolveOffspringRarity` /
 * `offspringSpecies` / `PetGenes.inherit` / `DeityRules.resolveAtBirth`）
 * 全部是 core 的**纯函数**；本层只做编排：扣金币、落 NPC 宠、建配对、写后代。
 * **任何数值都不在这里发明**。
 */
interface PairingRepository {

    /** 可参与配对的宠物：成年 + 亲密度 ≥ 70（`PairingRules.requiredIntimacy`） */
    fun observePairablePets(ownerId: Long): Flow<List<Pet>>

    /** 生成 NPC 候选（`W-16-1` 拍板口径，见 [NpcCandidateRules]；不落库） */
    suspend fun npcCandidates(myPet: Pet, seed: Long? = null): List<NpcCandidateRules.NpcCandidate>

    /** 取某只宠当前生效中的配对（含领域对象，供 UI 算冷却/名额） */
    suspend fun activePairingOf(petId: Long): Pairing?

    /** 按 id 查宠物（UI 展示搭档信息用；不区分 owner —— NPC 宠 ownerId = -1 也能查到） */
    suspend fun queryPetById(petId: Long): Pet?

    /**
     * 与 NPC 候选立即配对（免确认，`ADR-011`）。
     *
     * 整笔不扣：前置（可配 / 无在档配对 / 金币）任一不满足即整体失败。
     */
    suspend fun startNpcPairing(ownerId: Long, petId: Long, candidate: NpcCandidateRules.NpcCandidate): PairingStartOutcome

    /**
     * 生育一次（双方各得一只 —— 单机 NPC 场景下**我方得一只**，NPC 侧不生成个体）。
     *
     * 冷却与名额由 `Pairing.canBreedNow` 守住；出生上下文字段
     * （`gender` 随机 / `generation` / `exoticBlood` / `hasFullDeityForm`）
     * 在本方法内**一次算全**后落库 —— 不许"先生下来再补"。
     */
    suspend fun breed(ownerId: Long, pairingId: Long, now: Long): BreedOutcome

    /** 解除配对（生育搭档关系，宠物归属永不变更） */
    suspend fun releasePairing(pairingId: Long)
}

/** 配对发起结果 */
sealed class PairingStartOutcome {
    /** 成功：返回建好的配对（含 NPC 宠 id，供 25 屏演出） */
    data class Ok(val pairing: Pairing, val npcPetId: Long, val kind: GrowthRules.BreedingRules.PairingKind) : PairingStartOutcome()

    /** 失败原因（UI 直接可读） */
    data class Rejected(val failure: PairingFailure) : PairingStartOutcome()
}

/** 生育结果 */
sealed class BreedOutcome {
    /** 成功：返回新生后代（已落库，进「院子」待命） */
    data class Ok(val offspring: Pet) : BreedOutcome()

    data class Rejected(val failure: PairingFailure) : BreedOutcome()
}

/** 配对/生育失败原因（`FusionRules.RejectReason` 不适用，这里是配对自己的语义） */
sealed class PairingFailure {
    /** 前置不满足：成年 + 亲密度 ≥ 70 */
    data object PetNotPairable : PairingFailure()

    /** 该宠已在配对中（生育搭档不互斥多个，但同一时刻只认一段 PAIRED） */
    data object AlreadyPaired : PairingFailure()

    /** 金币不足 */
    data class NotEnoughGold(val need: Int) : PairingFailure()

    /** 冷却未到（`remainMs` 给 UI 倒计时） */
    data class Cooldown(val remainMs: Long) : PairingFailure()

    /** 后代名额已用完（真人 5 / NPC 3，S16） */
    data object NoOffspringQuota : PairingFailure()

    /** 配对记录不存在或已失效 */
    data object PairingNotFound : PairingFailure()

    /** 宠物不存在或不属于该主人 */
    data object PetNotFound : PairingFailure()
}

@Singleton
class PairingRepositoryImpl @Inject constructor(
    private val petDao: PetDao,
    private val pairingDao: PairingDao,
    private val userRepository: UserRepository
) : PairingRepository {

    override fun observePairablePets(ownerId: Long): Flow<List<Pet>> =
        petDao.observeByOwner(ownerId).map { entities ->
            PetMapper.toDomainList(entities).filter { it.canPair }
        }

    override suspend fun npcCandidates(
        myPet: Pet,
        seed: Long?
    ): List<NpcCandidateRules.NpcCandidate> = withContext(Dispatchers.IO) {
        NpcCandidateRules.candidatesFor(myPet, seed?.let { kotlin.random.Random(it) } ?: kotlin.random.Random.Default)
    }

    override suspend fun activePairingOf(petId: Long): Pairing? = withContext(Dispatchers.IO) {
        pairingDao.queryActiveByPet(petId)?.let(PairingMapper::toDomain)
    }

    override suspend fun queryPetById(petId: Long): Pet? = withContext(Dispatchers.IO) {
        petDao.queryById(petId)?.let(PetMapper::toDomain)
    }

    override suspend fun startNpcPairing(
        ownerId: Long,
        petId: Long,
        candidate: NpcCandidateRules.NpcCandidate
    ): PairingStartOutcome = withContext(Dispatchers.IO) {
        val myPet = petDao.queryById(petId)?.let(PetMapper::toDomain)
        if (myPet == null || myPet.ownerId != ownerId) {
            return@withContext PairingStartOutcome.Rejected(PetNotFound)
        }
        // 七条前置中本层能查的：成年 + 亲密度（性别由 NPC 取反兜住；不同主人由 NPC ownerId 满足）
        if (!myPet.canPair) return@withContext PairingStartOutcome.Rejected(PetNotPairable)
        if (pairingDao.queryActiveByPet(petId) != null) {
            return@withContext PairingStartOutcome.Rejected(AlreadyPaired)
        }

        val cost = GrowthRules.PairingRules.costGold
        val gold = userRepository.current()?.gold ?: 0
        if (gold < cost) return@withContext PairingStartOutcome.Rejected(NotEnoughGold(cost - gold))

        val now = System.currentTimeMillis()

        // NPC 宠：出生上下文一次算全（性别=候选取反值；无父母 → 世代 1）
        val npcPet = Pet(
            id = 0L,
            ownerId = SYSTEM_NPC_OWNER_ID,
            nickname = candidate.nickname,
            species = candidate.species,
            rarity = myPet.rarity,                       // W-16-1 第 3 条：与我方同档
            stage = PetStage.ADULT,                      // NPC 恒成年（可配对是它的存在意义）
            level = candidate.level,
            exp = 0,
            attributes = PetAttributes.ofLevel(
                candidate.level, myPet.rarity, candidate.species.tendency
            ).copy(intimacy = GrowthRules.PairingRules.requiredIntimacy),
            genes = PetGenes.random(),
            bornAt = now,
            hatchAt = now,
            isHatched = true,
            spouseId = null,                             // 建档后由下方互指补上
            familyId = null,
            parentIdA = null,
            parentIdB = null,
            batchNo = NPC_BATCH_NO,
            gender = candidate.gender
        )
        val npcId = petDao.insert(PetMapper.toEntity(npcPet))

        // 配对：petIdA 恒存较小 ID（PairingMapper.toEntity 会归一，这里直接按大小给）
        val aFirst = petId <= npcId
        val pairing = Pairing(
            id = 0L,
            petIdA = if (aFirst) petId else npcId,
            petIdB = if (aFirst) npcId else petId,
            ownerIdA = if (aFirst) ownerId else SYSTEM_NPC_OWNER_ID,
            ownerIdB = if (aFirst) SYSTEM_NPC_OWNER_ID else ownerId,
            pairedAt = now,
            status = PairingStatus.PAIRED,               // NPC 免确认（ADR-011）
            partnerIsNpc = true,
            affection = myPet.attributes.intimacy
        )
        val pairingId = pairingDao.insert(PairingMapper.toEntity(pairing))

        // 双方互指搭档
        petDao.insert(PetMapper.toEntity(myPet.copy(spouseId = npcId)))
        petDao.insert(
            PetMapper.toEntity(npcPet.copy(id = npcId, spouseId = petId))
        )
        userRepository.addGold(-cost)

        PairingStartOutcome.Ok(
            pairing = pairing.copy(id = pairingId),
            npcPetId = npcId,
            kind = GrowthRules.BreedingRules.pairingKindOf(myPet.species, candidate.species)
        )
    }

    override suspend fun breed(ownerId: Long, pairingId: Long, now: Long): BreedOutcome =
        withContext(Dispatchers.IO) {
            val target = queryPairingById(pairingId)
            if (target == null || target.status != PairingStatus.PAIRED.id) {
                return@withContext BreedOutcome.Rejected(PairingNotFound)
            }
            val pairing = PairingMapper.toDomain(target)
            if (!pairing.canBreedNow(now)) {
                val cooldown = pairing.breedCooldownRemainMs(now)
                if (cooldown > 0L) return@withContext BreedOutcome.Rejected(Cooldown(cooldown))
                return@withContext BreedOutcome.Rejected(NoOffspringQuota)
            }

            // 区分我方 / NPC 侧
            val myPetId = if (target.ownerIdA == ownerId) target.petIdA else target.petIdB
            val npcPetId = pairing.partnerOf(myPetId)
            val myPet = petDao.queryById(myPetId)?.let(PetMapper::toDomain)
                ?: return@withContext BreedOutcome.Rejected(PetNotFound)
            val npcPet = petDao.queryById(npcPetId)?.let(PetMapper::toDomain)
                ?: return@withContext BreedOutcome.Rejected(PetNotFound)

            val kind = GrowthRules.BreedingRules.pairingKindOf(myPet.species, npcPet.species)
            val random = kotlin.random.Random.Default

            // 后代三件套：稀有度（概率漂移 + 灵血觉醒 + 冷启动保底）→ 物种 → 世代
            val rarityResult = GrowthRules.BreedingRules.resolveOffspringRarity(
                parentA = myPet.rarity, bloodA = myPet.exoticBlood,
                parentB = npcPet.rarity, bloodB = npcPet.exoticBlood,
                kind = kind, random = random
            )
            val species = GrowthRules.BreedingRules.offspringSpecies(
                myPet.species, npcPet.species, kind, random = random
            )
            val generation = GrowthRules.GenerationRules.offspringGeneration(myPet, npcPet)

            val offspring = Pet(
                id = 0L,
                ownerId = ownerId,
                nickname = species.displayName,
                species = species,
                rarity = rarityResult.rarity,
                stage = PetStage.BABY,
                level = 1,
                exp = 0,
                attributes = PetAttributes.ofLevel(1, rarityResult.rarity, species.tendency),
                genes = PetGenes.inherit(myPet.genes, npcPet.genes),
                bornAt = now,
                hatchAt = now,               // 出生即已破壳（生育不做蛋）
                isHatched = true,
                spouseId = null,
                familyId = null,
                parentIdA = myPet.id,
                parentIdB = npcPet.id,
                batchNo = NPC_BATCH_NO,
                exoticBlood = rarityResult.exoticBlood,
                // ⚠️ 出生上下文字段一次算全（常驻铁律）：性别随机、世代、灵血、神兽形态
                hasFullDeityForm = DeityRules.resolveAtBirth(rarityResult.rarity, species, kind),
                gender = PetGender.random(random),
                generation = generation,
                // 落点：进「院子」待命（14 §2.3 —— 后代不自动占家/桌面）
                isMain = false,
                homeSlot = -1,
                desktopSlot = -1
            )
            val offspringId = petDao.insert(PetMapper.toEntity(offspring))

            pairingDao.update(
                target.copy(
                    offspringCount = pairing.offspringCount + 1,
                    lastBreedAt = now,
                    updatedAt = now
                )
            )

            BreedOutcome.Ok(offspring.copy(id = offspringId))
        }

    override suspend fun releasePairing(pairingId: Long) = withContext(Dispatchers.IO) {
        val target = queryPairingById(pairingId) ?: return@withContext
        val pairing = PairingMapper.toDomain(target)
        // 双方解除互指
        listOfNotNull(
            petDao.queryById(target.petIdA),
            petDao.queryById(target.petIdB)
        ).forEach { entity ->
            val pet = PetMapper.toDomain(entity)
            if (pet.spouseId == target.petIdA || pet.spouseId == target.petIdB) {
                petDao.insert(PetMapper.toEntity(pet.copy(spouseId = null)))
            }
        }
        pairingDao.update(
            target.copy(status = PairingStatus.RELEASED.id, updatedAt = System.currentTimeMillis())
        )
        Unit
    }

    private suspend fun queryPairingById(id: Long): PairingEntity? {
        // MVP：单机配对量极小；Phase 6 云端化时补 @Query("... WHERE id = :id")
        return pairingDao.observeActive().first().firstOrNull { it.id == id }
    }
}
