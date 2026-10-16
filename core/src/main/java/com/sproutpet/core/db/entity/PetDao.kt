package com.sproutpet.core.db.entity

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 宠物数据访问接口。
 *
 * 约定：
 * - 查询返回 Flow 以支持响应式刷新（数据变化时 UI 自动更新）
 * - 挂起函数用于写操作，避免阻塞主线程
 * - 不在此层做业务判断，只做数据存取
 */
@Dao
interface PetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pet: PetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pets: List<PetEntity>): List<Long>

    @Update
    suspend fun update(pet: PetEntity)

    @Delete
    suspend fun delete(pet: PetEntity)

    @Query("SELECT * FROM pet WHERE id = :id LIMIT 1")
    suspend fun queryById(id: Long): PetEntity?

    @Query("SELECT * FROM pet WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<PetEntity?>

    /** 用户的全部宠物（按战力降序） */
    @Query("SELECT * FROM pet WHERE owner_id = :ownerId AND is_retired = 0 ORDER BY power DESC")
    fun observeByOwner(ownerId: Long): Flow<List<PetEntity>>

    /** 用户当前出战/主宠物（未孵化完的也返回，用于首页展示孵化进度） */
    @Query(
        """
        SELECT * FROM pet
        WHERE owner_id = :ownerId AND is_retired = 0
        ORDER BY is_hatched ASC, power DESC
        LIMIT 1
        """
    )
    fun observeMainPet(ownerId: Long): Flow<PetEntity?>

    /** 孵化中的蛋 */
    @Query("SELECT * FROM pet WHERE owner_id = :ownerId AND is_hatched = 0 AND is_retired = 0")
    fun observeIncubating(ownerId: Long): Flow<List<PetEntity>>

    /** 到点可破壳的宠物 */
    @Query("SELECT * FROM pet WHERE is_hatched = 0 AND hatch_at <= :now AND is_retired = 0")
    suspend fun queryReadyToHatch(now: Long): List<PetEntity>

    /** 族谱：查某只宠物的所有后代 */
    @Query("SELECT * FROM pet WHERE parent_id_a = :petId OR parent_id_b = :petId ORDER BY generation ASC")
    suspend fun queryChildren(petId: Long): List<PetEntity>

    /** 族谱：按世代取整棵树 */
    @Query("SELECT * FROM pet WHERE family_id = :familyId ORDER BY generation ASC, born_at ASC")
    suspend fun queryByFamily(familyId: Long): List<PetEntity>

    /** 排行榜：战力 Top N */
    @Query("SELECT * FROM pet WHERE is_hatched = 1 AND is_retired = 0 ORDER BY power DESC LIMIT :limit")
    suspend fun queryTopByPower(limit: Int): List<PetEntity>

    /** 排行榜：等级 Top N */
    @Query("SELECT * FROM pet WHERE is_hatched = 1 AND is_retired = 0 ORDER BY level DESC, exp DESC LIMIT :limit")
    suspend fun queryTopByLevel(limit: Int): List<PetEntity>

    /** 排行榜：亲密度 Top N */
    @Query("SELECT * FROM pet WHERE is_hatched = 1 AND is_retired = 0 ORDER BY attr_intimacy DESC LIMIT :limit")
    suspend fun queryTopByIntimacy(limit: Int): List<PetEntity>

    /** 某只宠物在战力榜中的排名（1-based） */
    @Query("SELECT COUNT(*) + 1 FROM pet WHERE is_hatched = 1 AND is_retired = 0 AND power > :power")
    suspend fun queryPowerRank(power: Int): Int

    @Query("SELECT COUNT(*) FROM pet WHERE owner_id = :ownerId AND is_retired = 0")
    suspend fun countByOwner(ownerId: Long): Int

    /** 待同步记录（Phase 6 使用） */
    @Query("SELECT * FROM pet WHERE is_synced = 0 AND updated_at > :since")
    suspend fun queryPendingSync(since: Long): List<PetEntity>
}
