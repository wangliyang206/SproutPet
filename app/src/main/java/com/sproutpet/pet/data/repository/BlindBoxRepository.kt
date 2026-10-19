package com.sproutpet.pet.data.repository

import com.sproutpet.core.db.entity.PetDao
import com.sproutpet.core.db.mapper.PetMapper
import com.sproutpet.core.domain.gacha.GachaRules
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
     * 首只宠物传 `isFirstPet = true`（孵化时长**恒 1 分钟**，ADR-013 首日必出宠物）。
     */
    suspend fun openBox(ownerId: Long): OpenBoxOutcome

    /**
     * ⭐ 十连：一次抽 [count] 只，**共用一条出壳队列**落库（docs/14 §二 · `W-14-2`）。
     *
     * 与连续调用 10 次 [openBox] 的**本质区别**：
     * - 只有一个 `batchNo`（一次开箱的 10 颗蛋是同一批）；
     * - `hatchAt` 由 [com.sproutpet.core.domain.gacha.GachaRules.queueHatchOffsetMs]
     *   按**稀有度降序后的位次**给出 —— 第 1 颗 T+60s、每 20s 一颗、总长 ≤ 4 分钟；
     * - 不按每颗蛋自己的稀有度算 CD（那正是「一只一只孵化」的旧观感）。
     */
    suspend fun openMany(ownerId: Long, count: Int = GachaRules.TEN_PULL): TenPullOutcome
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

/**
 * 十连结果（十连结果页 / 出壳队列屏的数据源）。
 *
 * [eggs] 与 [results]**同序且已按稀有度降序** —— 最值钱的那只在最前，
 * 对应 `design-v2/ 31 屏` 里「第 1 颗高亮」的画法。
 */
data class TenPullOutcome(
    /** 引擎抽取结果（保持引擎原始顺序，供概率核对） */
    val results: List<BlindBoxResult>,
    /** 已落库的 10 颗蛋（按稀有度降序，`hatchAt` 已按队列时序写好） */
    val eggs: List<Pet>,
    /** 是否触发了「十连保稀有及以上」兜底 */
    val floorApplied: Boolean,
    /** 本次开箱后，距保底还差几抽 */
    val pityRemaining: Int
) {
    /** 一次开箱的批次号（10 颗蛋共用，用于「全部出壳」一键结算） */
    val batchNo: String get() = eggs.firstOrNull()?.batchNo.orEmpty()
}

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

    override suspend fun openMany(ownerId: Long, count: Int): TenPullOutcome =
        withContext(Dispatchers.IO) {
            val user = userRepository.current()
                ?: userRepository.getOrCreate(ownerId)
            val multi = BlindBoxEngine.drawMany(count = count, pityCount = user.pityCount)
            userRepository.updatePityCount(multi.newPityCount)

            val isFirstPet = petDao.countByOwner(ownerId) == 0
            val batchNo = UUID.randomUUID().toString()

            // ⭐ 出壳顺序 = 稀有度降序：第 1 颗就是整批最值钱的（docs/14 §2.2）。
            // 顺序是免费的体验杠杆 —— 若神话排最后，用户要盯满 4 分钟，前面 9 颗都成了障碍物。
            val ordered = multi.results.sortedByDescending { it.rarity.id }
            val eggs = ordered.mapIndexed { index, result ->
                val egg = BlindBoxEngine.createEgg(
                    ownerId = ownerId,
                    result = result,
                    batchNo = batchNo,
                    isFirstPet = isFirstPet,
                    queueIndex = index
                )
                egg.copy(id = petDao.insert(PetMapper.toEntity(egg)))
            }

            TenPullOutcome(
                results = multi.results,
                eggs = eggs,
                floorApplied = multi.floorApplied,
                pityRemaining = BlindBoxEngine.remainingToPity(multi.newPityCount)
            )
        }
}
