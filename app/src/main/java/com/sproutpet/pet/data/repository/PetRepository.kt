package com.sproutpet.pet.data.repository

import com.sproutpet.core.db.entity.PetDao
import com.sproutpet.core.db.mapper.PetMapper
import com.sproutpet.core.domain.pet.GrowthRules
import com.sproutpet.core.domain.pet.Pet
import com.sproutpet.pet.domain.interaction.ApplyInteractionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 宠物仓库（docs/04 §1.5）：观察 + 保存 + 互动结算。
 *
 * 规则运算全部委托 core 引擎与 [ApplyInteractionUseCase]，本层只做存取与编排（A5 红线）。
 */
interface PetRepository {

    /** 家在册的已破壳宠物（场景站位层的数据源） */
    fun observeHomePets(ownerId: Long): Flow<List<Pet>>

    /** 该主人是否已有任何宠物（含蛋），用于新用户引导显隐 */
    fun observePetCount(ownerId: Long): Flow<Int>

    suspend fun queryById(id: Long): Pet?

    /** 新增或更新（存在即更新） */
    suspend fun save(pet: Pet): Long

    /** 破壳起名（强制命名是唯一改名入口） */
    suspend fun rename(petId: Long, name: String)

    /**
     * 结算一次互动并持久化。
     *
     * @return 结算结果；宠物不存在时返回 null（调用方按无操作处理）
     */
    suspend fun applyInteraction(petId: Long, type: GrowthRules.InteractionType): ApplyInteractionUseCase.Result?
}

@Singleton
class PetRepositoryImpl @Inject constructor(
    private val petDao: PetDao
) : PetRepository {

    override fun observeHomePets(ownerId: Long): Flow<List<Pet>> =
        petDao.observeByOwner(ownerId).map { entities ->
            PetMapper.toDomainList(entities).filter { it.isHatched && !it.isRetired }
        }

    override fun observePetCount(ownerId: Long): Flow<Int> =
        petDao.observeByOwner(ownerId).map { it.size }

    override suspend fun queryById(id: Long): Pet? = withContext(Dispatchers.IO) {
        petDao.queryById(id)?.let(PetMapper::toDomain)
    }

    override suspend fun save(pet: Pet): Long = withContext(Dispatchers.IO) {
        petDao.insert(PetMapper.toEntity(pet))
    }

    override suspend fun rename(petId: Long, name: String) {
        withContext(Dispatchers.IO) {
            petDao.queryById(petId)?.let { entity ->
                petDao.update(entity.copy(nickname = name))
            }
        }
    }

    override suspend fun applyInteraction(
        petId: Long,
        type: GrowthRules.InteractionType
    ): ApplyInteractionUseCase.Result? = withContext(Dispatchers.IO) {
        val pet = petDao.queryById(petId)?.let(PetMapper::toDomain) ?: return@withContext null
        val result = ApplyInteractionUseCase.execute(pet, type)
        petDao.insert(PetMapper.toEntity(result.pet))
        result
    }
}
