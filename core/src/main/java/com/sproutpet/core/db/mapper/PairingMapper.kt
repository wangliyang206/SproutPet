package com.sproutpet.core.db.mapper

import com.sproutpet.core.db.entity.PairingEntity
import com.sproutpet.core.domain.social.Pairing
import com.sproutpet.core.domain.social.PairingStatus

/**
 * `PairingEntity` ↔ 领域对象 [Pairing] 转换器。
 *
 * ## 两条约定（与 `docs/03 §3.6` 一致）
 *
 * 1. **`petIdA` 恒存较小 ID** —— `UNIQUE(pet_id_a, pet_id_b)` 的去重与
 *    "同一对宠只建一档"都依赖这个顺序。转换时自动归一，调用方不必关心谁先谁后。
 * 2. **NPC 侧**：实体的 `partner_is_npc_a` 语义是「A 的搭档（即 B）是 NPC」。
 *    当前只存在「单 NPC 配对」（真人 × NPC），领域对象的 [Pairing.partnerIsNpc]
 *    以真人侧视角存取：真人侧若为 A，则 `partner_is_npc_a = true`、`partner_is_npc_b = false`。
 */
object PairingMapper {

    /** Entity → Domain */
    fun toDomain(entity: PairingEntity): Pairing = Pairing(
        id = entity.id,
        petIdA = entity.petIdA,
        petIdB = entity.petIdB,
        ownerIdA = entity.ownerIdA,
        ownerIdB = entity.ownerIdB,
        pairedAt = entity.pairedAt,
        lastBreedAt = entity.lastBreedAt,
        offspringCount = entity.offspringCount,
        status = PairingStatus.fromId(entity.status),
        partnerIsNpc = entity.partnerIsNpcA || entity.partnerIsNpcB,
        affection = entity.affection,
        expireAt = entity.expireAt,
        updatedAt = entity.updatedAt,
        isSynced = entity.isSynced
    )

    /** Domain → Entity（ID 归一：`petIdA` 恒存较小者，`ownerId` / NPC 标记跟随交换） */
    fun toEntity(pairing: Pairing, existing: PairingEntity? = null): PairingEntity {
        val aFirst = pairing.petIdA <= pairing.petIdB
        val petA = if (aFirst) pairing.petIdA else pairing.petIdB
        val petB = if (aFirst) pairing.petIdB else pairing.petIdA
        val ownerA = if (aFirst) pairing.ownerIdA else pairing.ownerIdB
        val ownerB = if (aFirst) pairing.ownerIdB else pairing.ownerIdA
        // NPC 标记跟随交换：真人侧的「搭档是 NPC」写进真人所在的那一列
        return PairingEntity(
            id = pairing.id,
            petIdA = petA,
            petIdB = petB,
            ownerIdA = ownerA,
            ownerIdB = ownerB,
            partnerIsNpcA = if (aFirst) pairing.partnerIsNpc else false,
            partnerIsNpcB = if (aFirst) false else pairing.partnerIsNpc,
            isSameOwner = pairing.isSameOwner,
            affection = pairing.affection,
            pairedAt = pairing.pairedAt,
            lastBreedAt = pairing.lastBreedAt,
            offspringCount = pairing.offspringCount,
            expireAt = pairing.expireAt,
            status = pairing.status.id,
            updatedAt = System.currentTimeMillis(),
            isSynced = false
        )
    }
}
