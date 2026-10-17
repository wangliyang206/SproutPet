package com.sproutpet.core.db.entity

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 背包数据访问接口。
 */
@Dao
interface InventoryItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: InventoryItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<InventoryItemEntity>): List<Long>

    @Update
    suspend fun update(item: InventoryItemEntity)

    @Query("DELETE FROM inventory_item WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 某用户的全部背包（响应式） */
    @Query("SELECT * FROM inventory_item WHERE owner_id = :ownerId ORDER BY acquired_at DESC")
    fun observeByOwner(ownerId: Long): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_item WHERE owner_id = :ownerId ORDER BY acquired_at DESC")
    suspend fun queryByOwner(ownerId: Long): List<InventoryItemEntity>

    /** 某道具的全部条目（可堆叠道具可能只有一条，玩具可能有多条） */
    @Query("SELECT * FROM inventory_item WHERE owner_id = :ownerId AND item_id = :itemId")
    suspend fun queryByItem(ownerId: Long, itemId: String): List<InventoryItemEntity>

    /** 增减某条记录的数量；返回影响行数 */
    @Query("UPDATE inventory_item SET count = MAX(count + :delta, 0), updated_at = :now WHERE id = :id")
    suspend fun adjustCount(id: Long, delta: Int, now: Long = System.currentTimeMillis()): Int

    /** 更新某条记录的耐久（玩具） */
    @Query("UPDATE inventory_item SET durability = :durability, updated_at = :now WHERE id = :id")
    suspend fun updateDurability(id: Long, durability: Int?, now: Long = System.currentTimeMillis()): Int
}
