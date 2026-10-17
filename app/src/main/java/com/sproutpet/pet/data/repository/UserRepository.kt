package com.sproutpet.pet.data.repository

import com.sproutpet.core.db.entity.UserDao
import com.sproutpet.core.db.mapper.UserMapper
import com.sproutpet.core.domain.user.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用户仓库（docs/04 §2.1）：本地账号、保底计数、金币钻石。
 *
 * Phase 2 起保底计数从 DataStore 迁移到 `user.pity_count`（账号级真相源）。
 * ownerId 仍由 [com.sproutpet.pet.data.local.OwnerLocalDataSource] 生成并作为 user.id，
 * 保证 pet.owner_id 与 user.id 对齐。
 */
interface UserRepository {

    /** 当前用户（响应式） */
    fun observeCurrent(): Flow<User?>

    /** 取当前用户（一次性），不存在返回 null */
    suspend fun current(): User?

    /** 取当前用户，不存在则以 [ownerId] 为 id 创建一条 */
    suspend fun getOrCreate(ownerId: Long): User

    /** 更新盲盒保底计数 */
    suspend fun updatePityCount(count: Int)

    /** 金币增减（delta 可为负，DB 层钳制不低于 0） */
    suspend fun addGold(delta: Int)

    /** 钻石增减 */
    suspend fun addGem(delta: Int)
}

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userDao: UserDao
) : UserRepository {

    override fun observeCurrent(): Flow<User?> =
        userDao.observeCurrent().map { it?.let(UserMapper::toDomain) }

    override suspend fun current(): User? = withContext(Dispatchers.IO) {
        userDao.queryCurrent()?.let(UserMapper::toDomain)
    }

    override suspend fun getOrCreate(ownerId: Long): User = withContext(Dispatchers.IO) {
        val existing = userDao.queryCurrent()
        if (existing != null) return@withContext UserMapper.toDomain(existing)
        val now = System.currentTimeMillis()
        val user = User(
            id = ownerId,
            nickname = DEFAULT_NICKNAME,
            createdAt = now,
            lastLoginAt = now
        )
        val id = userDao.insert(UserMapper.toEntity(user))
        // insert 返回的 id 可能与传入的 ownerId 不同（Room autoGenerate），
        // 用实际落库的 id 回写领域对象，保证后续 pet.ownerId 一致。
        UserMapper.toDomain(userDao.queryById(id)!!)
    }

    override suspend fun updatePityCount(count: Int) {
        withContext(Dispatchers.IO) {
            val user = userDao.queryCurrent() ?: return@withContext
            userDao.updatePityCount(user.id, count)
        }
    }

    override suspend fun addGold(delta: Int) {
        withContext(Dispatchers.IO) {
            val user = userDao.queryCurrent() ?: return@withContext
            userDao.addGold(user.id, delta)
        }
    }

    override suspend fun addGem(delta: Int) {
        withContext(Dispatchers.IO) {
            val user = userDao.queryCurrent() ?: return@withContext
            userDao.addGem(user.id, delta)
        }
    }

    companion object {
        private const val DEFAULT_NICKNAME = "萌芽主人"
    }
}
