package com.sproutpet.core.db.mapper

import com.sproutpet.core.db.entity.InventoryItemEntity
import com.sproutpet.core.domain.item.InventoryItem

/**
 * InventoryItem Entity ↔ Domain 转换器。
 *
 * 注意：领域模型 [InventoryItem] 没有 `id`（它是背包里的一个道具实例），
 * 实体层的 `id` 仅供数据库定位。仓库层做增删改时需保留 id。
 */
object InventoryItemMapper {

    fun toDomain(entity: InventoryItemEntity): InventoryItem = InventoryItem(
        itemId = entity.itemId,
        count = entity.count,
        durability = entity.durability,
        acquiredAt = entity.acquiredAt,
        expireAt = entity.expireAt
    )

    fun toEntity(item: InventoryItem, ownerId: Long, existing: InventoryItemEntity? = null): InventoryItemEntity =
        InventoryItemEntity(
            id = existing?.id ?: 0L,
            ownerId = ownerId,
            itemId = item.itemId,
            count = item.count,
            durability = item.durability,
            acquiredAt = item.acquiredAt,
            expireAt = item.expireAt,
            updatedAt = System.currentTimeMillis(),
            isSynced = false
        )
}
