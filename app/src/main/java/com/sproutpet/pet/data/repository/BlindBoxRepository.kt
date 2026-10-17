package com.sproutpet.pet.data.repository

import com.sproutpet.core.db.entity.PetDao
import com.sproutpet.core.db.mapper.PetMapper
import com.sproutpet.core.domain.incubation.BlindBoxEngine
import com.sproutpet.core.domain.incubation.BlindBoxResult
import com.sproutpet.core.domain.pet.Pet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 盲盒仓库（docs/04 §1.4 / §2.1）：调 `BlindBoxEngine` 开箱，并把结果落库为「蛋」。
 *
 * 保底计数 Phase 2 起存 `user.pity_count`（[UserRepository]）；引擎是唯一执法点，
 * 本层只负责传参（`pityCount` / `isFirstPet`）与持久化。
 */
interface BlindBoxRepository {

    /** 距史诗保底还差几抽（`BlindBoxEngine.remainingToPity`） */
    fun observePityRemaining(): Flow<Int>

    /**
     * 开一次盲盒：抽取 → 更新保底计数 → 创建蛋并入本地库。
     *
     * 首只宠物传 `isFirstPet = true`（孵化时长减半，ADR-013 首日必出宠物）。
     */
    suspend fun openBox(ownerId: Long): OpenBoxOutcome
}

/** 开箱结果（揭晓模态卡的数据源） */
data class OpenBoxOutcome(
    /** 引擎抽取结果（物种/稀有度/基因/保底标记） */
    val result: BlindBoxResult,
    /** 已落库的蛋（id 已分配） */
    val egg: Pet,
    /** 本次开箱后，距保底还差几抽 */
    val pityRemaining: Int
)

@Singleton
class BlindBoxRepositoryImpl @Inject constructor(
    private val petDao: PetDao,
    private val userRepository: UserRepository
) : BlindBoxRepository {

    override fun observePityRemaining(): Flow<Int> =
        userRepository.observeCurrent().map { user ->
            BlindBoxEngine.remainingToPity(user?.pityCount ?: 0)
        }

    override suspend fun openBox(ownerId: Long): OpenBoxOutcome = withContext(Dispatchers.IO) {
        val user = userRepository.current()
            ?: userRepository.getOrCreate(ownerId)
        val outcome = BlindBoxEngine.draw(pityCount = user.pityCount)
        userRepository.updatePityCount(outcome.newPityCount)

        val isFirstPet = petDao.countByOwner(ownerId) == 0
        val egg = BlindBoxEngine.createEgg(
            ownerId = ownerId,
            result = outcome.result,
            batchNo = UUID.randomUUID().toString(),
            isFirstPet = isFirstPet
        )
        val newId = petDao.insert(PetMapper.toEntity(egg))

        OpenBoxOutcome(
            result = outcome.result,
            egg = egg.copy(id = newId),
            pityRemaining = BlindBoxEngine.remainingToPity(outcome.newPityCount)
        )
    }
}
