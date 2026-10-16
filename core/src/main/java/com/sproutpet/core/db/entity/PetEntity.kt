package com.sproutpet.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 宠物数据库实体。
 *
 * 与领域模型 [com.sproutpet.core.domain.pet.Pet] 的分工：
 * - Entity：只负责持久化，字段类型受 Room 约束（所以用 Int/String 表示枚举）
 * - Domain：承载业务行为（如 canPair / hatchProgress 等计算属性）
 * - Mapper：负责两者互转（见 [com.sproutpet.core.db.mapper.PetMapper]）
 *
 * 为什么不直接用 Entity 做业务对象：避免数据库结构变化渗透到业务层。
 */
@Entity(
    tableName = "pet",
    indices = [
        Index(value = ["owner_id"]),
        Index(value = ["family_id"]),
        Index(value = ["spouse_id"]),
        Index(value = ["parent_id_a"]),
        Index(value = ["parent_id_b"]),
        Index(value = ["power"], orders = [Index.Order.DESC]),
        Index(value = ["is_hatched", "owner_id"])
    ]
)
data class PetEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "owner_id")
    val ownerId: Long,

    @ColumnInfo(name = "nickname")
    val nickname: String,

    /** 种族 ID，对应 [com.sproutpet.core.domain.pet.PetSpecies.id] */
    @ColumnInfo(name = "species_id")
    val speciesId: String,

    /** 稀有度等级值，对应 [com.sproutpet.core.domain.pet.Rarity.id] */
    @ColumnInfo(name = "rarity")
    val rarity: Int,

    /** 生命阶段，对应 [com.sproutpet.core.domain.pet.PetStage.id] */
    @ColumnInfo(name = "stage")
    val stage: Int,

    @ColumnInfo(name = "level")
    val level: Int,

    @ColumnInfo(name = "exp")
    val exp: Int,

    // ===== 属性面板 =====
    @ColumnInfo(name = "attr_stamina")
    val stamina: Int,
    @ColumnInfo(name = "attr_strength")
    val strength: Int,
    @ColumnInfo(name = "attr_agility")
    val agility: Int,
    @ColumnInfo(name = "attr_wisdom")
    val wisdom: Int,
    @ColumnInfo(name = "attr_charm")
    val charm: Int,
    @ColumnInfo(name = "attr_intimacy")
    val intimacy: Int,

    /** 战力快照：冗余字段，用于排行榜排序避免实时计算 */
    @ColumnInfo(name = "power")
    val power: Int,

    // ===== 基因 =====
    @ColumnInfo(name = "gene_body_size")
    val geneBodySize: Float,
    @ColumnInfo(name = "gene_hue_shift")
    val geneHueShift: Float,
    @ColumnInfo(name = "gene_pattern_density")
    val genePatternDensity: Float,
    @ColumnInfo(name = "gene_eye_size")
    val geneEyeSize: Float,
    @ColumnInfo(name = "gene_tail_length")
    val geneTailLength: Float,
    @ColumnInfo(name = "gene_liveliness")
    val geneLiveliness: Float,

    // ===== 时间与关系 =====
    @ColumnInfo(name = "born_at")
    val bornAt: Long,

    @ColumnInfo(name = "hatch_at")
    val hatchAt: Long,

    @ColumnInfo(name = "is_hatched")
    val isHatched: Boolean,

    /**
     * 配对搭档宠物 ID（非婚姻关系）。
     * 列名保留为 `spouse_id` 以兼容 v1.0 的历史数据，语义已改为「生育搭档」。
     */
    @ColumnInfo(name = "spouse_id")
    val spouseId: Long? = null,

    @ColumnInfo(name = "family_id")
    val familyId: Long? = null,

    @ColumnInfo(name = "parent_id_a")
    val parentIdA: Long? = null,

    @ColumnInfo(name = "parent_id_b")
    val parentIdB: Long? = null,

    /** 世代数：根为 1，后代递增。加速族谱查询 */
    @ColumnInfo(name = "generation")
    val generation: Int = 1,

    @ColumnInfo(name = "batch_no")
    val batchNo: String,

    @ColumnInfo(name = "is_retired")
    val isRetired: Boolean = false,

    // ===== 同步字段（Phase 6 云端使用） =====
    /** 服务端 ID，未同步时为 null */
    @ColumnInfo(name = "remote_id")
    val remoteId: String? = null,

    /** 最后本地修改时间，用于同步冲突解决 */
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    /** 是否已同步到云端 */
    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = false
)
