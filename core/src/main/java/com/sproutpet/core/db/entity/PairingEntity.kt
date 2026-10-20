package com.sproutpet.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 配对生育关系（docs/03 §3.6 冻结结构，`marriage` → `pairing` 语义修正版）。
 *
 * ## 三条语义红线（ADR-008 / ADR-011）
 *
 * 1. **这不是婚姻，是「生育搭档」** —— 双方各自保留宠物、各自获得一只后代，
 *    宠物归属**永不变更**；解绑即 `status = RELEASED`，没有"离异/丧偶"状态。
 * 2. **真人配对照走 `PENDING` 需对方确认；NPC 配对直接落 `PAIRED` 免确认**
 *    （`partner_is_npc_a/b` 区分）—— 查询待处理邀请只需 `WHERE status = 0`。
 * 3. `UNIQUE(pet_id_a, pet_id_b)` 防止同一对宠物重复建档。
 *
 * ## 与领域模型 [com.sproutpet.core.domain.social.Pairing] 的分工
 *
 * Entity 只负责持久化；冷却是否结束、名额还剩几个这类**业务判断**
 * 全部走领域对象（`canBreedNow` / `remainingOffspring`），不在 SQL 里散写。
 */
@Entity(
    tableName = "pairing",
    indices = [
        Index(value = ["pet_id_a", "pet_id_b"], unique = true),
        Index(value = ["pet_id_a"]),
        Index(value = ["pet_id_b"]),
        Index(value = ["status"])
    ]
)
data class PairingEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    /** 宠物 A（存较小 ID，保证唯一约束与顺序无关） */
    @ColumnInfo(name = "pet_id_a")
    val petIdA: Long,

    /** 宠物 B */
    @ColumnInfo(name = "pet_id_b")
    val petIdB: Long,

    /** A 的主人（支持跨用户配对） */
    @ColumnInfo(name = "owner_id_a")
    val ownerIdA: Long,

    /** B 的主人 */
    @ColumnInfo(name = "owner_id_b")
    val ownerIdB: Long,

    /** A 的搭档是否为 NPC（`0/1`） */
    @ColumnInfo(name = "partner_is_npc_a", defaultValue = "0")
    val partnerIsNpcA: Boolean = false,

    /** B 的搭档是否为 NPC（`0/1`） */
    @ColumnInfo(name = "partner_is_npc_b", defaultValue = "0")
    val partnerIsNpcB: Boolean = false,

    /** 是否同一主人的两只宠物自配对（`0/1`；正常配对恒为 0 —— 见 requireDifferentOwner） */
    @ColumnInfo(name = "is_same_owner", defaultValue = "0")
    val isSameOwner: Boolean = false,

    /** 双方好感度（亲密值 `0~100`） */
    @ColumnInfo(name = "affection", defaultValue = "0")
    val affection: Int = 0,

    /** 配对生效时间 */
    @ColumnInfo(name = "paired_at")
    val pairedAt: Long,

    /** 上次生育时间（0 = 尚未生育；冷却计算见 PairingRules.cooldownMs） */
    @ColumnInfo(name = "last_breed_at", defaultValue = "0")
    val lastBreedAt: Long = 0L,

    /** 已生育数量（上限：真人搭档 5 / NPC 搭档 3，S16） */
    @ColumnInfo(name = "offspring_count", defaultValue = "0")
    val offspringCount: Int = 0,

    /** 邀请过期时间（真人配对未确认时 = `paired_at + 3 天`；NPC 恒 0） */
    @ColumnInfo(name = "expire_at", defaultValue = "0")
    val expireAt: Long = 0L,

    /** 配对状态，对应 [com.sproutpet.core.domain.social.PairingStatus.id] */
    @ColumnInfo(name = "status", defaultValue = "0")
    val status: Int = 0,

    /** 最后本地修改时间 */
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    /** 是否已同步到云端 */
    @ColumnInfo(name = "is_synced", defaultValue = "0")
    val isSynced: Boolean = false
)
