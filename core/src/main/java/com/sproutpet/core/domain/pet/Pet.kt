package com.sproutpet.core.domain.pet

/**
 * 宠物领域模型（业务层核心对象，与数据库实体解耦）。
 *
 * 说明：这是纯业务对象，不依赖 Android / Room / 网络框架，
 * 因此可以直接在单元测试中构造与验证。
 */
data class Pet(
    val id: Long,

    /** 主人用户 ID */
    val ownerId: Long,

    /** 昵称（用户可自定义） */
    val nickname: String,

    /** 种族 */
    val species: PetSpecies,

    /** 稀有度 */
    val rarity: Rarity,

    /** 当前生命阶段 */
    val stage: PetStage,

    /** 当前等级 */
    val level: Int,

    /** 当前经验 */
    val exp: Int,

    /** 属性面板 */
    val attributes: PetAttributes,

    /**
     * 个体特征值（0~1 的浮点数组，用于外观微调与遗传）。
     * 例如：体型、色相偏移、花纹密度。让每只宠物"长得不一样"。
     */
    val genes: PetGenes,

    /** 出生时间戳（毫秒） */
    val bornAt: Long,

    /** 孵化完成时间戳（毫秒），未破壳时为未来的预计时间 */
    val hatchAt: Long,

    /** 是否已破壳 */
    val isHatched: Boolean,

    /** 搭档宠物 ID（配对生育搭档），null 表示暂无 */
    val spouseId: Long?,

    /** 所属家族 ID，null 表示未加入 */
    val familyId: Long?,

    /** 父母宠物 ID（用于族谱） */
    val parentIdA: Long?,
    val parentIdB: Long?,

    /** 盲盒批次编号（用于追溯来源） */
    val batchNo: String,

    /** 是否已放生/离世（保留数据用于族谱） */
    val isRetired: Boolean = false
) {
    /** 升级所需经验 */
    val expToNext: Int get() = GrowthRules.expToNextLevel(level)

    /** 升级进度 0f~1f */
    val expProgress: Float
        get() = if (expToNext == Int.MAX_VALUE) 1f
        else (exp.toFloat() / expToNext).coerceIn(0f, 1f)

    /**
     * 能否参与配对生育。
     *
     * v2.0 起以「配对生育」取代「结婚」：只要满足成年 + 亲密度门槛即可参与，
     * 不要求"单身"（配对的双方各自保留宠物，不存在归属转移）。
     * 详见 docs/00-产品需求文档PRD.md §11.1。
     */
    val canPair: Boolean
        get() = stage.canPair &&
                attributes.intimacy >= GrowthRules.PairingRules.requiredIntimacy

    /** 能否生育（需已有配对搭档） */
    val canBreed: Boolean get() = stage.canPair && spouseId != null

    /** 孵化剩余毫秒（未破壳时有效） */
    fun hatchRemainMs(now: Long = System.currentTimeMillis()): Long =
        (hatchAt - now).coerceAtLeast(0L)

    /** 孵化进度 0f~1f */
    fun hatchProgress(now: Long = System.currentTimeMillis()): Float {
        val total = (hatchAt - bornAt).coerceAtLeast(1L)
        val elapsed = (now - bornAt).coerceAtLeast(0L)
        return (elapsed.toFloat() / total).coerceIn(0f, 1f)
    }

    /** 战力（排行榜用） */
    val power: Int get() = attributes.power
}

/**
 * 宠物基因：一组 0~1 的浮点特征。
 *
 * 作用：
 * 1. 外观微调（同一只种族也长得不同）
 * 2. 遗传（后代基因 = 父母基因混合 + 随机扰动），实现"长得像爸妈"
 */
data class PetGenes(
    /** 体型：0=小巧，1=壮硕 */
    val bodySize: Float = 0.5f,
    /** 色相偏移：0~1 映射到色环偏移 */
    val hueShift: Float = 0.5f,
    /** 花纹密度 */
    val patternDensity: Float = 0.5f,
    /** 眼睛大小 */
    val eyeSize: Float = 0.5f,
    /** 尾巴长度 */
    val tailLength: Float = 0.5f,
    /** 性格活泼度：影响待机行为频率 */
    val liveliness: Float = 0.5f
) {
    companion object {
        /** 随机生成初始基因（用于盲盒开蛋） */
        fun random(): PetGenes = PetGenes(
            bodySize = randomUnit(),
            hueShift = randomUnit(),
            patternDensity = randomUnit(),
            eyeSize = randomUnit(),
            tailLength = randomUnit(),
            liveliness = randomUnit()
        )

        /**
         * 遗传：混合父母基因 + 随机扰动。
         *
         * @param a 父方基因
         * @param b 母方基因
         * @param mutationRate 变异概率 0~1，越大越不像父母
         */
        fun inherit(a: PetGenes, b: PetGenes, mutationRate: Float = 0.15f): PetGenes {
            fun mix(x: Float, y: Float): Float {
                val base = (x + y) / 2f
                val mutation = (randomUnit() - 0.5f) * mutationRate * 2f
                return (base + mutation).coerceIn(0f, 1f)
            }
            return PetGenes(
                bodySize = mix(a.bodySize, b.bodySize),
                hueShift = mix(a.hueShift, b.hueShift),
                patternDensity = mix(a.patternDensity, b.patternDensity),
                eyeSize = mix(a.eyeSize, b.eyeSize),
                tailLength = mix(a.tailLength, b.tailLength),
                liveliness = mix(a.liveliness, b.liveliness)
            )
        }

        private fun randomUnit(): Float = (0..1000).random() / 1000f
    }
}
