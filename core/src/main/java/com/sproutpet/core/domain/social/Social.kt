package com.sproutpet.core.domain.social

/**
 * 家族：由多只宠物（同一用户或跨用户）组成的社交单位。
 *
 * 设计考量：
 * - 家族是「族谱」的容器，也是「家庭成员」页面的数据源
 * - 家族有等级与贡献度，激励用户长期投入
 */
data class Family(
    val id: Long,
    val name: String,
    /** 家族徽章资源标识 */
    val badgeRes: String,
    /** 族长用户 ID */
    val leaderId: Long,
    /** 家族等级 */
    val level: Int = 1,
    /** 家族总战力（成员战力之和） */
    val totalPower: Int = 0,
    /** 家族基金（金币池） */
    val funds: Int = 0,
    /** 创建时间 */
    val createdAt: Long = System.currentTimeMillis(),
    /** 家族宣言 */
    val motto: String = "",
    /** 成员上限 */
    val memberLimit: Int = 20,
    /** 是否公开招募 */
    val isPublic: Boolean = true
) {
    /** 家族等级所需经验（按等级递增） */
    fun expToNextLevel(): Int = level * 1000 + 500

    val levelProgressHint: String get() = "Lv.$level"
}

/**
 * 家族成员：用户与家族的关联关系。
 */
data class FamilyMember(
    val familyId: Long,
    val userId: Long,
    val userName: String,
    val userAvatar: String = "",
    /** 在家族中的职位 */
    val role: FamilyRole,
    /** 对家族的贡献值 */
    val contribution: Int = 0,
    /** 加入时间 */
    val joinedAt: Long = System.currentTimeMillis(),
    /** 该成员的主力宠物 ID */
    val mainPetId: Long? = null
)

/** 家族职位 */
enum class FamilyRole(val displayName: String, val level: Int) {
    LEADER("族长", 3),
    ELDER("长老", 2),
    MEMBER("成员", 1);

    val canManage: Boolean get() = this >= ELDER
}

/**
 * 配对生育关系（v2.0 起取代 v1.0 的「婚姻」）。
 *
 * ⚠️ 重要语义：这不是婚姻，是**生育搭档**。
 * - 双方各自保留自己的宠物，不存在归属转移
 * - 双方各获得一只后代
 * - 可随时解绑，再与其他宠物配对
 *
 * 取消婚姻的原因见 docs/00-产品需求文档PRD.md §11.1：
 * 若保留婚姻，只有一只女宠的用户在婚后会失去宠物。
 *
 * 设计为独立实体而非 Pet 上的一个字段，原因：
 * 1. 需要记录配对时间与后代数量
 * 2. 需要支持解绑/再配对的历史追溯
 * 3. 真人与 NPC 需要区分（NPC 免确认）
 */
data class Pairing(
    val id: Long,
    /** 宠物 A 的 ID（较小者） */
    val petIdA: Long,
    /** 宠物 B 的 ID（较大者） */
    val petIdB: Long,
    /** 宠物 A 的主人 */
    val ownerIdA: Long,
    /** 宠物 B 的主人 */
    val ownerIdB: Long,
    /** 配对时间 */
    val pairedAt: Long = System.currentTimeMillis(),
    /** 上次生育时间，用于冷却计算 */
    val lastBreedAt: Long = 0L,
    /** 已生育后代数量 */
    val offspringCount: Int = 0,
    /** 配对状态 */
    val status: PairingStatus = PairingStatus.PENDING,
    /**
     * 对方是否为 NPC。
     * NPC 配对**免确认**，创建即生效；真人配对需对方接受。
     */
    val partnerIsNpc: Boolean = false,

    /** 双方好感度（亲密值 0~100） */
    val affection: Int = 0,

    /** 邀请过期时间（真人配对未确认时 = `pairedAt + PairingRules.inviteTtlMs`；NPC 恒 0） */
    val expireAt: Long = 0L,

    /** 最后本地修改时间（同步用） */
    val updatedAt: Long = System.currentTimeMillis(),

    /** 是否已同步到云端 */
    val isSynced: Boolean = false
) {
    /** 搭档的另一方宠物 ID */
    fun partnerOf(petId: Long): Long = if (petId == petIdA) petIdB else petIdA

    /** 参与双方是否为同一主人（不允许自我繁殖） */
    val isSameOwner: Boolean get() = ownerIdA == ownerIdB

    /** 生育冷却是否结束 */
    fun canBreedNow(now: Long = System.currentTimeMillis()): Boolean =
        status == PairingStatus.PAIRED &&
                // NPC 伴侣后代上限更小（默认 3，真人为 5），引导用户去找真人配对。
                offspringCount < com.sproutpet.core.domain.pet.GrowthRules.PairingRules
                    .maxOffspringFor(partnerIsNpc) &&
                now - lastBreedAt >= com.sproutpet.core.domain.pet.GrowthRules.PairingRules.cooldownMs

    /** 本次配对的剩余后代名额 */
    val remainingOffspring: Int
        get() = (com.sproutpet.core.domain.pet.GrowthRules.PairingRules
            .maxOffspringFor(partnerIsNpc) - offspringCount).coerceAtLeast(0)

    /** 生育冷却剩余毫秒 */
    fun breedCooldownRemainMs(now: Long = System.currentTimeMillis()): Long {
        val rules = com.sproutpet.core.domain.pet.GrowthRules.PairingRules
        val ready = lastBreedAt + rules.cooldownMs
        return (ready - now).coerceAtLeast(0L)
    }
}

/** 配对状态 */
enum class PairingStatus(val id: Int, val displayName: String) {
    /** 待对方确认（仅真人配对会出现） */
    PENDING(0, "待确认"),

    /** 配对生效中 */
    PAIRED(1, "搭档中"),

    /** 已解绑 */
    RELEASED(2, "已解绑"),

    /** 已过期（邀请未被接受） */
    EXPIRED(3, "已过期");

    /** 是否为有效配对 */
    val isActive: Boolean get() = this == PAIRED

    /** 是否处于等待确认 */
    val isWaiting: Boolean get() = this == PENDING;

    companion object {
        /** 从持久化值还原（`docs/03 §3.6`：0/1/2/3）。未知值按已解绑处理，不抛错。 */
        fun fromId(id: Int): PairingStatus =
            entries.firstOrNull { it.id == id } ?: RELEASED
    }
}

/**
 * 族谱节点：用于构建族谱树。
 *
 * 由 Pet 数据 + 父母关系动态计算得出，不单独建表（避免数据冗余不一致）。
 */
data class GenealogyNode(
    val petId: Long,
    val nickname: String,
    val species: com.sproutpet.core.domain.pet.PetSpecies,
    val rarity: com.sproutpet.core.domain.pet.Rarity,
    val stage: com.sproutpet.core.domain.pet.PetStage,
    val generation: Int,
    val parentIdA: Long?,
    val parentIdB: Long?,
    val children: List<GenealogyNode> = emptyList(),
    /** 配对搭档宠物 ID（非婚姻关系，仅表示"生育搭档"） */
    val partnerId: Long? = null
) {
    /** 是否为根节点（无父母记录） */
    val isRoot: Boolean get() = parentIdA == null && parentIdB == null

    /** 后代总数（递归） */
    val descendantCount: Int
        get() = children.size + children.sumOf { it.descendantCount }
}

/**
 * 排行榜条目。
 */
data class RankingEntry(
    val rank: Int,
    val userId: Long,
    val userName: String,
    val userAvatar: String = "",
    /** 代表宠物 */
    val petId: Long,
    val petNickname: String,
    val species: com.sproutpet.core.domain.pet.PetSpecies,
    val rarity: com.sproutpet.core.domain.pet.Rarity,
    val stage: com.sproutpet.core.domain.pet.PetStage,
    /** 排序依据的分数（战力/等级/亲密度，按榜单类型不同） */
    val score: Int,
    /** 是否为自己 */
    val isSelf: Boolean = false
)

/** 排行榜类型 */
enum class RankingType(val displayName: String, val description: String) {
    /** 战力榜：按综合战力排序 */
    POWER("战力榜", "综合实力最强的宠物"),
    /** 等级榜：按等级排序 */
    LEVEL("等级榜", "成长最快的宠物"),
    /** 亲密度榜：按亲密度排序 */
    INTIMACY("亲密榜", "与主人羁绊最深"),
    /** 家族榜：按家族总战力排序（Phase 10 可选功能） */
    FAMILY("家族榜", "最强大的家族"),
    /** 繁育榜：按后代数量排序 */
    BREEDING("繁育榜", "血脉最兴盛的宠物"),
    /** 世代榜：按家族最大世代数排序 */
    GENERATION("世代榜", "血脉延续最久");

    /** 是否为家族维度 */
    val isFamilyScope: Boolean get() = this == FAMILY
}
