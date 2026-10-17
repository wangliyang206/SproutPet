package com.sproutpet.pet.data.repository

import com.sproutpet.core.db.entity.PetDao
import com.sproutpet.core.db.mapper.PetMapper
import com.sproutpet.core.domain.incubation.BlindBoxEngine
import com.sproutpet.core.domain.incubation.IncubationEngine
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
            val hatched = BlindBoxEngine.hatch(egg, now)
            petDao.insert(PetMapper.toEntity(hatched))
            hatched
        }
}
