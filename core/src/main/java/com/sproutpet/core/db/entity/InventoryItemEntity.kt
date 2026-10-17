package com.sproutpet.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 背包表实体（docs/03 §3.3）。
 *
 * 📌 不设 `UNIQUE(owner_id, item_id)`：玩具有独立耐久（不可堆叠），
 * 同一道具可能有多条不同耐久的记录。堆叠逻辑由仓库层在「可堆叠道具」上做合并。
 */
@Entity(
    tableName = "inventory_item",
    indices = [
        Index(value = ["owner_id", "item_id"]),
        Index(value = ["item_id"])
    ]
)
data class InventoryItemEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "owner_id")
    val ownerId: Long,

    /** 道具定义 ID，对应 [com.sproutpet.core.domain.item.ItemDefinition.id] */
    @ColumnInfo(name = "item_id")
    val itemId: String,

    @ColumnInfo(name = "count", defaultValue = "0")
    val count: Int = 0,

    /** 耐久（玩具类），null 表示无耐久概念 */
    @ColumnInfo(name = "durability")
    val durability: Int? = null,

    @ColumnInfo(name = "acquired_at")
    val acquiredAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "expire_at")
    val expireAt: Long? = null,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "is_synced", defaultValue = "0")
    val isSynced: Boolean = false
)
