package com.sproutpet.pet.data.repository

import com.sproutpet.core.db.entity.PetDao
import com.sproutpet.core.db.mapper.PetMapper
import com.sproutpet.core.domain.incubation.BlindBoxEngine
import com.sproutpet.core.domain.incubation.IncubationEngine
import com.sproutpet.core.domain.pet.HomeSlots
import com.sproutpet.core.domain.pet.Pet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 孵化仓库（docs/04 §1.4）：计时、加速、破壳。
 *
 * 规则本体在 core（`IncubationEngine` / `BlindBoxEngine.hatch`），本层只做编排与持久化。
 */
interface IncubationRepository {

    /** 当前孵化中的蛋（最多展示一颗，Phase 1 口径） */
    fun observeIncubatingEgg(ownerId: Long): Flow<Pet?>

    /**
     * 专注陪伴加速：按 `GrowthRules.FOCUS_BOOST_RATIO` 抵扣，
     * 并受 `MAX_FOCUS_BOOST_RATIO` 单蛋上限约束（执法点在引擎）。
     *
     * @param focusMs 本次陪伴时长（毫秒）
     * @return 加速后的蛋；蛋不存在返回 null
     */
    suspend fun applyFocusBoost(eggId: Long, focusMs: Long): Pet?

    /**
     * 到点破壳：把蛋转成幼崽并落库。
     *
     * @return 破壳后的宠物；未到点或不存在返回 null
     */
    suspend fun hatchIfReady(eggId: Long, now: Long = System.currentTimeMillis()): Pet?

    /**
     * 立即破壳（不看计时）：十连「全部出壳」用（docs/14 §2.2 · 零成本）。
     *
     * 与 [hatchIfReady] 的差别只有"不检查时间" —— 仍然走 `BlindBoxEngine.hatch`
     * 的同一套属性/阶段初始化，**不是**绕过规则直接改 stage。
     *
     * @return 破壳后的宠物；蛋不存在或已破壳返回 null
     */
    suspend fun forceHatch(eggId: Long, now: Long = System.currentTimeMillis()): Pet?
}

@Singleton
class IncubationRepositoryImpl @Inject constructor(
    private val petDao: PetDao
) : IncubationRepository {

    override fun observeIncubatingEgg(ownerId: Long): Flow<Pet?> =
        petDao.observeIncubating(ownerId).map { entities ->
            entities.firstOrNull()?.let(PetMapper::toDomain)
        }

    override suspend fun applyFocusBoost(eggId: Long, focusMs: Long): Pet? =
        withContext(Dispatchers.IO) {
            val entity = petDao.queryById(eggId) ?: return@withContext null
            val egg = PetMapper.toDomain(entity)
            if (egg.isHatched) return@withContext null
            val now = System.currentTimeMillis()
            val boost = IncubationEngine.focusBoostMs(focusMs, egg.hatchRemainMs(now))
            val newHatchAt = IncubationEngine.applySpeedUp(egg.hatchAt, boost, now)
            val updated = egg.copy(hatchAt = newHatchAt)
            petDao.insert(PetMapper.toEntity(updated))
            updated
        }

    override suspend fun hatchIfReady(eggId: Long, now: Long): Pet? =
        withContext(Dispatchers.IO) {
            val entity = petDao.queryById(eggId) ?: return@withContext null
            val egg = PetMapper.toDomain(entity)
            if (egg.isHatched || egg.hatchRemainMs(now) > 0L) return@withContext null
            val hatched = assignHomeSlotIfPossible(BlindBoxEngine.hatch(egg, now))
            petDao.insert(PetMapper.toEntity(hatched))
            hatched
        }

    override suspend fun forceHatch(eggId: Long, now: Long): Pet? =
        withContext(Dispatchers.IO) {
            val entity = petDao.queryById(eggId) ?: return@withContext null
            val egg = PetMapper.toDomain(entity)
            if (egg.isHatched) return@withContext null
            val hatched = assignHomeSlotIfPossible(BlindBoxEngine.hatch(egg, now))
            petDao.insert(PetMapper.toEntity(hatched))
            hatched
        }

    /**
     * 破壳即「入册」：家有空槽就占一个，满了就留在**院子**（`home_slot = -1`）。
     *
     * 为什么要在这里做：`BlindBoxEngine.createEgg` 造出来的蛋 `home_slot` 一律是
     * `NO_SLOT`（引擎是纯函数，看不到"家里还有几个位子"）。如果破壳时不补，
     * 抽到的宠物就永远不会 `atHome` —— 而场景只渲染 `atHome` 的宠物（三层链条），
     * 结果就是「抽到了但看不见」。
     *
     * ⚠️ 队列（十连）按出壳顺序调用，先到先得 —— 而队列本身是**稀有度降序**，
     * 所以最好的那几只优先入册，这个顺序恰好是对的。
     */
    private suspend fun assignHomeSlotIfPossible(pet: Pet): Pet {
        if (HomeSlots.isAtHome(pet)) return pet
        val peers = petDao.queryByOwner(pet.ownerId).map(PetMapper::toDomain)
        val slot = HomeSlots.nextFreeHomeSlot(peers) ?: return pet
        return pet.copy(homeSlot = slot)
    }
}
