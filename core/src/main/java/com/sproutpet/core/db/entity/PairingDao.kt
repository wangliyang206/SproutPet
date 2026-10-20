package com.sproutpet.core.db.entity

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 配对关系数据访问接口（docs/03 §3.6）。
 *
 * 约定与 [PetDao] 一致：查询返回 Flow 供 UI 响应式刷新；写操作用挂起函数。
 * 业务判断（冷却是否结束、名额还剩几个）**不在这里做** —— 领域对象
 * `Pairing.canBreedNow` / `remainingOffspring` 是唯一执法点。
 */
@Dao
interface PairingDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(pairing: PairingEntity): Long

    @Update
    suspend fun update(pairing: PairingEntity)

    /** 生效中的配对（UI：我的配对记录） */
    @Query("SELECT * FROM pairing WHERE status = 1 ORDER BY paired_at DESC")
    fun observeActive(): Flow<List<PairingEntity>>

    /** 某只宠物参与的全部有效配对（PENDING + PAIRED） */
    @Query(
        """
        SELECT * FROM pairing
        WHERE (pet_id_a = :petId OR pet_id_b = :petId)
          AND status IN (0, 1)
        ORDER BY paired_at DESC
        """
    )
    fun observeByPet(petId: Long): Flow<List<PairingEntity>>

    /** 某只宠物当前生效中的配对（最多一条 —— UNIQUE 约束 + 状态校验由仓库层保证） */
    @Query(
        """
        SELECT * FROM pairing
        WHERE (pet_id_a = :petId OR pet_id_b = :petId)
          AND status = 1
        ORDER BY paired_at DESC
        LIMIT 1
        """
    )
    suspend fun queryActiveByPet(petId: Long): PairingEntity?

    /** 待确认的真人邀请（对方视角展示用） */
    @Query("SELECT * FROM pairing WHERE status = 0 ORDER BY paired_at DESC")
    fun observePending(): Flow<List<PairingEntity>>

    /** 已过期邀请批量清理（`expire_at > 0 AND now`；仓库层在读取时顺带执行） */
    @Query("UPDATE pairing SET status = 3, updated_at = :now WHERE status = 0 AND expire_at > 0 AND expire_at < :now")
    suspend fun expireOverdue(now: Long): Int
}
