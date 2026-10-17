package com.sproutpet.core.db.entity

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 用户数据访问接口。
 *
 * 本地单机阶段仅 1 条用户记录，所以这里提供「取第一条」的便捷方法。
 */
@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: UserEntity): Long

    @Update
    suspend fun update(user: UserEntity)

    @Query("SELECT * FROM user LIMIT 1")
    fun observeCurrent(): Flow<UserEntity?>

    @Query("SELECT * FROM user LIMIT 1")
    suspend fun queryCurrent(): UserEntity?

    @Query("SELECT * FROM user WHERE id = :id LIMIT 1")
    suspend fun queryById(id: Long): UserEntity?

    /** 更新保底计数 */
    @Query("UPDATE user SET pity_count = :count, updated_at = :now WHERE id = :id")
    suspend fun updatePityCount(id: Long, count: Int, now: Long = System.currentTimeMillis())

    /** 金币增减（delta 可为负，但最终值不低于 0 由调用方保证） */
    @Query("UPDATE user SET gold = MAX(gold + :delta, 0), updated_at = :now WHERE id = :id")
    suspend fun addGold(id: Long, delta: Int, now: Long = System.currentTimeMillis())

    @Query("UPDATE user SET gem = MAX(gem + :delta, 0), updated_at = :now WHERE id = :id")
    suspend fun addGem(id: Long, delta: Int, now: Long = System.currentTimeMillis())
}
