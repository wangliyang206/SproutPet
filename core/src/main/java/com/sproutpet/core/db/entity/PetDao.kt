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

    /** 已破壳的宠物数（⭐ 不含蛋 —— 首蛋聚焦层的显隐依据，docs/14 §1.4） */
    @Query("SELECT COUNT(*) FROM pet WHERE owner_id = :ownerId AND is_hatched = 1 AND is_retired = 0")
    fun observeHatchedCount(ownerId: Long): Flow<Int>

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

    /** 用户的全部宠物（**一次性快照**，用于算家在册空槽 —— 槽位分配必须看到全量，否则会重复占号） */
    @Query("SELECT * FROM pet WHERE owner_id = :ownerId AND is_retired = 0")
    suspend fun queryByOwner(ownerId: Long): List<PetEntity>

    /**
     * 「院子」= 已破壳但**不在家**（`home_slot = -1`）的宠物。
     *
     * 这是 2026-10-20 崩溃的根因所在：十连开 10 只 + 原有 1 只 = 11 只，
     * 而「家」在册上限是 6（`HomeSlots.MAX_HOME_PETS`）。多出来的必须待在院子里，
     * 不能和在家的一起喂给 `ScenePetLayout.homeLayout`。
     */
    @Query(
        """
        SELECT * FROM pet
        WHERE owner_id = :ownerId AND is_hatched = 1 AND is_retired = 0 AND home_slot = -1
        ORDER BY power DESC
        """
    )
    fun observeYardPets(ownerId: Long): Flow<List<PetEntity>>

    /** 只改在家槽位（其余字段不动，避免整行 REPLACE 时用快照覆盖别处刚写入的改动） */
    @Query("UPDATE pet SET home_slot = :slot, updated_at = :now WHERE id = :id")
    suspend fun updateHomeSlot(id: Long, slot: Int, now: Long)

    /** 待同步记录（Phase 6 使用） */
    @Query("SELECT * FROM pet WHERE is_synced = 0 AND updated_at > :since")
    suspend fun queryPendingSync(since: Long): List<PetEntity>
}
