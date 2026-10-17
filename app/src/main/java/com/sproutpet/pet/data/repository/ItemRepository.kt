package com.sproutpet.pet.data.repository

import com.sproutpet.core.db.entity.InventoryItemDao
import com.sproutpet.core.db.mapper.InventoryItemMapper
import com.sproutpet.core.domain.item.InventoryItem
import com.sproutpet.core.domain.item.ItemDefinition
import com.sproutpet.core.domain.item.ItemRegistry
import com.sproutpet.core.domain.item.ToyRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 道具/背包仓库（docs/04 §2.4）：增删查改。
 *
 * 道具定义（静态配置）来自 core 的 [ItemRegistry]；背包实例持久化在 `inventory_item` 表。
 * 规则（堆叠 / 耐久消耗）由仓库层按 [ItemDefinition.stackable] 与 [ToyRules] 编排。
 */
interface ItemRepository {

    /** 观察某用户的全部背包条目 */
    fun observeInventory(ownerId: Long): Flow<List<InventoryItem>>

    /** 一次性读取全部背包 */
    suspend fun queryInventory(ownerId: Long): List<InventoryItem>

    /**
     * 获得道具（入库）。
     *
     * - 可堆叠道具：合并到已有同 itemId 记录的 count
     * - 不可堆叠道具（玩具）：新建独立记录，耐久取道具初始耐久（见 [initialDurability]）
     *
     * @return 新增/更新后的条目数
     */
    suspend fun acquire(ownerId: Long, itemId: String, count: Int = 1): Int

    /**
     * 消耗一个道具。
     *
     * - 可堆叠：count - 1，归零则删除记录
     * - 玩具：耐久 - 1（[ToyRules.WEAR_PER_PLAY]），归零保留记录但不可用
     *
     * @return 消耗是否成功（道具不足 / 不存在返回 false）
     */
    suspend fun consume(ownerId: Long, itemId: String): Boolean

    /** 某道具当前可用总量（可堆叠 = count 之和；玩具 = 可用条数） */
    suspend fun availableCount(ownerId: Long, itemId: String): Int
}

@Singleton
class ItemRepositoryImpl @Inject constructor(
    private val inventoryDao: InventoryItemDao
) : ItemRepository {

    override fun observeInventory(ownerId: Long): Flow<List<InventoryItem>> =
        inventoryDao.observeByOwner(ownerId).map { entities ->
            entities.map(InventoryItemMapper::toDomain)
        }

    override suspend fun queryInventory(ownerId: Long): List<InventoryItem> =
        withContext(Dispatchers.IO) {
            inventoryDao.queryByOwner(ownerId).map(InventoryItemMapper::toDomain)
        }

    override suspend fun acquire(ownerId: Long, itemId: String, count: Int): Int =
        withContext(Dispatchers.IO) {
            val definition = ItemRegistry.find(itemId) ?: return@withContext 0
            if (definition.stackable) {
                val existing = inventoryDao.queryByItem(ownerId, itemId).firstOrNull()
                if (existing != null) {
                    inventoryDao.adjustCount(existing.id, count)
                    1
                } else {
                    val entity = InventoryItemMapper.toEntity(
                        InventoryItem(itemId = itemId, count = count),
                        ownerId
                    )
                    inventoryDao.insert(entity)
                    1
                }
            } else {
                // 不可堆叠（玩具）：每条独立记录，带初始耐久
                val durability = initialDurability(definition)
                val entities = List(count) {
                    InventoryItemMapper.toEntity(
                        InventoryItem(itemId = itemId, count = 1, durability = durability),
                        ownerId
                    )
                }
                inventoryDao.insertAll(entities).size
            }
        }

    override suspend fun consume(ownerId: Long, itemId: String): Boolean =
        withContext(Dispatchers.IO) {
            val definition = ItemRegistry.find(itemId) ?: return@withContext false
            val records = inventoryDao.queryByItem(ownerId, itemId)
            if (records.isEmpty()) return@withContext false

            if (definition.stackable) {
                // 取 count > 0 的一条减 1
                val target = records.firstOrNull { it.count > 0 } ?: return@withContext false
                val affected = inventoryDao.adjustCount(target.id, -1)
                if (affected > 0 && target.count - 1 <= 0) {
                    inventoryDao.deleteById(target.id)
                }
                true
            } else {
                // 玩具：取耐久 > 0 的一条扣耐久
                val target = records.firstOrNull { (it.durability ?: 0) > 0 } ?: return@withContext false
                val newDurability = ToyRules.wear(target.durability)
                inventoryDao.updateDurability(target.id, newDurability)
                true
            }
        }

    override suspend fun availableCount(ownerId: Long, itemId: String): Int =
        withContext(Dispatchers.IO) {
            val definition = ItemRegistry.find(itemId) ?: return@withContext 0
            val records = inventoryDao.queryByItem(ownerId, itemId)
            if (definition.stackable) {
                records.sumOf { it.count }
            } else {
                records.count { (it.durability ?: 0) > 0 }
            }
        }

    /** 玩具初始耐久：由道具定义决定；当前毛线球 20 / 飞盘 30（见 ItemRegistry）。 */
    private fun initialDurability(definition: ItemDefinition): Int = when (definition.id) {
        "toy_yarn" -> 20
        "toy_frisbee" -> 30
        else -> 1
    }
}
