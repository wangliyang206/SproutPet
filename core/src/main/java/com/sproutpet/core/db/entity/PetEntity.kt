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

    /**
     * 性别，对应 [com.sproutpet.core.domain.pet.PetGender.id]（0 公 / 1 母）。
     *
     * 配对生育的前置条件之一（一公一母）。**出生即定、永不改变**，
     * 所以必须落库 —— 否则重启后性别漂移，会出现"同一只宠时而能配时而不能配"。
     *
     * ⚠️ 见 [com.sproutpet.core.domain.pet.PetGender]：`id` 不可重排，
     * 重排会让存量宠物整体变性。
     */
    @ColumnInfo(name = "gender", defaultValue = "0")
    val gender: Int = 0,

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

    /**
     * ⚠️ **派生缓存列，不是独立真相源。**
     *
     * 真相源是 [stage]（`stage == 6` 即隐居）。本列由 `PetMapper` 从
     * `Pet.isRetired` 写入，存在的唯一理由是**让 DAO 能走索引过滤**
     * （`WHERE is_retired = 0` 比 `WHERE stage != 6` 更明确，也能建索引）。
     *
     * 它曾与 `stage` **各说各话且无同步逻辑**：DAO 过滤列、领域判断 stage，
     * 于是"后山小屋里的宠会不会出现在列表里"取决于从哪个入口问。
     * 现已收敛，并由 `PetMapperTest` 的往返测试守住"写入始终一致"。
     *
     * 📌 不能直接删列：`SqliteDatabase` 的 `DROP COLUMN` 需要 SQLite 3.35+，
     * 而 minSdk 24 上的系统 SQLite 版本更低。保留为缓存比冒险重建表更稳妥。
     */
    @ColumnInfo(name = "is_retired", defaultValue = "0")
    val isRetired: Boolean = false,

    /**
     * 灵血浓度（`0 ~ GrowthRules.ExoticBloodRules.MAX_BLOOD`）。
     *
     * 见 [com.sproutpet.core.domain.pet.Pet.exoticBlood] 与 ADR-023「灵血传承」。
     * 默认 `0` 与领域模型一致，因此历史数据天然等价于「无灵血」，迁移安全。
     *
     * ⚠️ 该列曾长期缺失（V1 → V2 补上）。缺失期间灵血一旦落库即归零，
     * 机制在真实使用中完全失效 —— 这是必须由迁移测试守住的点。
     */
    @ColumnInfo(name = "exotic_blood", defaultValue = "0")
    val exoticBlood: Int = 0,

    /**
     * 是否拥有「完整神兽形态层」（凤凰 / 麒麟 / 蛟龙 / 神龟）。
     *
     * 见 [com.sproutpet.core.domain.pet.Pet.hasFullDeityForm] 与 ADR-026。
     * 出生时由 [com.sproutpet.core.domain.pet.DeityRules.resolveAtBirth] 结算一次，
     * 此后**永不改变** —— 所以必须落库，否则重启后形态就丢了。
     *
     * 默认 `false` 与领域模型一致，因此对存量宠物语义等价于「没有神兽形态」，迁移安全。
     * （这也正确：V2 之前形态是由稀有度现算的，没有"个体级形态"这个概念。）
     */
    @ColumnInfo(name = "deity_form", defaultValue = "0")
    val hasFullDeityForm: Boolean = false,

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
