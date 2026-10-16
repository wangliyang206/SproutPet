package com.sproutpet.core.domain.pet

import kotlin.random.Random

/**
 * 成长规则：定义等级、经验、阶段、孵化、配对生育之间的数值关系。
 *
 * 这是全套玩法的「数值中枢」，所有界面都从这里取数，
 * 禁止在业务代码里散落魔法数字。
 *
 * 【可调参数】集中在此对象，便于策划调平衡。
 *
 * @see docs/06-物种与稀有度设计.md
 */
object GrowthRules {

    /** 等级上限 */
    const val MAX_LEVEL = 60

    /** 每个阶段覆盖的等级区间 [minLevel, maxLevel] */
    val stageLevelRange: Map<PetStage, IntRange> = mapOf(
        PetStage.EGG to 0..0,
        PetStage.HATCHING to 0..0,
        PetStage.BABY to 1..10,
        PetStage.JUVENILE to 11..25,
        PetStage.ADULT to 26..45,
        PetStage.ELDER to 46..60
    )

    /**
     * 从等级推算阶段。
     * 注意：蛋/破壳阶段由孵化流程单独控制，不由此函数决定。
     */
    fun stageOf(level: Int): PetStage {
        if (level <= 0) return PetStage.EGG
        return stageLevelRange.entries
            .firstOrNull { (stage, range) -> stage.isHatched && level in range }
            ?.key ?: PetStage.ELDER
    }

    /**
     * 升到下一级所需经验。
     * 采用递增曲线：基础值 + 等级平方系数，保证前期快、后期慢。
     */
    fun expToNextLevel(level: Int): Int {
        if (level >= MAX_LEVEL) return Int.MAX_VALUE
        return BASE_EXP + (level * level * EXP_QUADRATIC) + (level * EXP_LINEAR)
    }

    private const val BASE_EXP = 50
    private const val EXP_LINEAR = 30
    private const val EXP_QUADRATIC = 8

    // ───────────────────────── 孵化 ─────────────────────────

    /**
     * 各稀有度的基准孵化时长（毫秒）。
     *
     * ⚠️ v2.0 重大调整：从「24 小时起」压缩到**分钟级**。
     * 原因：首日必须让用户真正见到宠物，否则流失。长等待留给后续的蛋。
     * 详见 docs/00-产品需求文档PRD.md §2.2。
     */
    private val HATCH_DURATION_BY_RARITY: Map<Rarity, Long> = mapOf(
        Rarity.COMMON to 5 * 60 * 1000L,          // 5 分钟
        Rarity.RARE to 10 * 60 * 1000L,           // 10 分钟
        Rarity.EPIC to 20 * 60 * 1000L,           // 20 分钟
        Rarity.LEGENDARY to 40 * 60 * 1000L,      // 40 分钟
        Rarity.MYTHIC to 60 * 60 * 1000L,         // 1 小时
        Rarity.MUTANT to 90 * 60 * 1000L          // 1.5 小时（异兽，仅供展示）
    )

    /** 默认孵化时长（兼容无稀有度信息时的兜底） */
    const val BASE_HATCH_DURATION_MS = 5 * 60 * 1000L

    /**
     * 稀有度对孵化时长的影响。
     * 越稀有孵化越久，制造期待感 —— 但整体压制在分钟~小时级别。
     *
     * @param isFirstPet 是否为用户的**首只宠物**。首只宠物额外缩短时长，
     *                   保证当天就能玩到（首日体验优先）。
     */
    fun hatchDurationOf(rarity: Rarity, isFirstPet: Boolean = false): Long {
        val base = HATCH_DURATION_BY_RARITY[rarity] ?: BASE_HATCH_DURATION_MS
        return if (isFirstPet) (base * FIRST_PET_HATCH_RATIO).toLong() else base
    }

    /** 首只宠物的孵化时长倍率（更短，保证首日体验） */
    const val FIRST_PET_HATCH_RATIO = 0.5f

    /** 加速道具：单个道具减少的孵化时间（毫秒）—— 10 分钟 */
    const val SPEED_UP_ITEM_REDUCE_MS = 10 * 60 * 1000L

    /** 专注陪伴抵扣比例：陪伴时长 × 该比例 = 抵扣的孵化时间 */
    const val FOCUS_BOOST_RATIO = 0.5f

    /** 单颗蛋可被专注陪伴抵扣的上限比例 */
    const val MAX_FOCUS_BOOST_RATIO = 0.5f

    /**
     * 互动行为提供的经验值。
     * 不同行为收益不同，且有每日上限（防刷）。
     */
    enum class InteractionType(
        val displayName: String,
        val exp: Int,
        val intimacy: Int,
        val cooldownMs: Long,
        val dailyLimit: Int
    ) {
        /** 抚摸：最基础、无消耗、收益低 */
        PET("抚摸", exp = 5, intimacy = 1, cooldownMs = 3_000L, dailyLimit = 50),

        /** 喂食：消耗食物道具，收益中 */
        FEED("喂食", exp = 20, intimacy = 3, cooldownMs = 60_000L, dailyLimit = 20),

        /** 玩耍：消耗玩具耐久，收益高 */
        PLAY("玩耍", exp = 35, intimacy = 5, cooldownMs = 120_000L, dailyLimit = 15),

        /** 训练：消耗体力，定向提升属性 */
        TRAIN("训练", exp = 50, intimacy = 2, cooldownMs = 300_000L, dailyLimit = 8),

        /** 清洁：维持状态，收益低但影响心情 */
        CLEAN("清洁", exp = 10, intimacy = 2, cooldownMs = 180_000L, dailyLimit = 10),

        /** 桌面互动：抱起、拖动、晃一晃 —— 桌面场景的轻量正反馈 */
        DESKTOP("桌面互动", exp = 2, intimacy = 1, cooldownMs = 30_000L, dailyLimit = 40)
    }

    /** 每日可得经验上限（防刷保护） */
    const val DAILY_EXP_CAP = 5_000

    /** 亲密度上限 */
    const val MAX_INTIMACY = 100

    // ───────────────────────── 配对生育 ─────────────────────────

    /**
     * 配对生育规则。
     *
     * v2.0 起以「配对生育」取代 v1.0 的「结婚」——
     * 双方各自保留宠物，各获得一只后代，互不影响归属。
     * 详见 docs/00-产品需求文档PRD.md §11.1。
     */
    object PairingRules {
        /** 最低阶段要求（成年） */
        val requiredStage = PetStage.ADULT

        /** 最低亲密度要求 */
        const val requiredIntimacy = 70

        /** 配对消耗金币 */
        const val costGold = 3_000

        /** 冷却时间（毫秒）：7 天 */
        const val cooldownMs = 7 * 24 * 60 * 60 * 1000L

        /** 每只宠物最多后代数量 */
        const val maxOffspring = 5

        /** 是否要求双方为不同主人（防止克隆式自我繁殖） */
        const val requireDifferentOwner = true

        /** 真人配对邀请的有效期（毫秒）：3 天 */
        const val inviteTtlMs = 3 * 24 * 60 * 60 * 1000L
    }

    /**
     * 遗传规则：后代稀有度与物种的推导。
     *
     * 核心设计（v2.0 修订）：
     * 1. **概率漂移，永不降级超过 1 档** —— 避免"神话爸爸生出普通儿子"的强挫败
     * 2. **永不 100% 继承**（除异兽 90%）—— 避免高稀有度通胀贬值
     * 3. **允许向上突变** —— 保留"普通家庭逆袭"的希望
     * 4. **分布中心取 max 而非 avg** —— 否则高手宠会被拖累，用户会避免与弱宠配对
     */
    object BreedingRules {

        /**
         * 后代稀有度概率分布表。
         *
         * key = 父母中较高的稀有度；value = 档位到概率的映射（概率和应为 1.0）。
         * 这是**唯一**的稀有度遗传配置来源，修改此处即可调平衡。
         */
        val RARITY_DISTRIBUTION: Map<Rarity, Map<Rarity, Float>> = mapOf(
            // 普通 × 普通 → 大部分还是普通，但有 1% 摸到传说（逆袭的希望）
            Rarity.COMMON to mapOf(
                Rarity.COMMON to 0.60f,
                Rarity.RARE to 0.33f,
                Rarity.EPIC to 0.06f,
                Rarity.LEGENDARY to 0.01f
            ),
            // 稀有
            Rarity.RARE to mapOf(
                Rarity.RARE to 0.50f,
                Rarity.EPIC to 0.35f,
                Rarity.LEGENDARY to 0.13f,
                Rarity.MYTHIC to 0.02f
            ),
            // 史诗 → 开始有极小概率摸到异兽
            Rarity.EPIC to mapOf(
                Rarity.EPIC to 0.45f,
                Rarity.LEGENDARY to 0.38f,
                Rarity.MYTHIC to 0.15f,
                Rarity.MUTANT to 0.02f
            ),
            // 传说
            Rarity.LEGENDARY to mapOf(
                Rarity.LEGENDARY to 0.45f,
                Rarity.MYTHIC to 0.45f,
                Rarity.MUTANT to 0.10f
            ),
            // 神话 → 不会必出神话（否则通胀），且有小概率回落到传说
            Rarity.MYTHIC to mapOf(
                Rarity.LEGENDARY to 0.05f,
                Rarity.MYTHIC to 0.85f,
                Rarity.MUTANT to 0.10f
            ),
            // 异兽 → 基本稳定传承
            Rarity.MUTANT to mapOf(
                Rarity.MYTHIC to 0.10f,
                Rarity.MUTANT to 0.90f
            )
        )

        /**
         * 同虫配对（如 羽虫 × 羽虫）：向上档位的权重加成。
         *
         * ⚠️ 数值语义：这是对"高于分布中心的那几档"的**权重倍率**。
         * 必须满足 同虫 < 跨虫 < 含异兽 < 异兽对，才能真正体现
         * "跨虫比同虫更容易出好东西"（鼓励社交）的设计意图。
         */
        const val SAME_FAMILY_UPWARD_BONUS = 1.0f

        /** 跨虫配对（如 羽虫 × 毛虫）：向上档位的权重加成（**高于同虫**，鼓励与他人配对） */
        const val CROSS_FAMILY_UPWARD_BONUS = 1.35f

        /** 参与方含「不入十类之种」：向上档位权重加成 */
        const val EXOTIC_MIX_UPWARD_BONUS = 1.8f

        /** 双方均为「不入十类之种」：向上档位权重加成 */
        const val EXOTIC_PAIR_UPWARD_BONUS = 2.0f

        /** 同虫配对成功概率 */
        const val SAME_FAMILY_SUCCESS_RATE = 1.0f

        /** 跨虫配对成功概率 */
        const val CROSS_FAMILY_SUCCESS_RATE = 0.85f

        /** 含异兽配对成功概率 */
        const val EXOTIC_MIX_SUCCESS_RATE = 0.9f

        /** 跨虫混血产出「不入十类之种」的概率 */
        const val CROSS_FAMILY_EXOTIC_SPECIES_RATE = 0.04f

        /** 含异兽配对产出「不入十类之种」的概率 */
        const val EXOTIC_MIX_EXOTIC_SPECIES_RATE = 0.12f

        /** 异兽 × 异兽产出「不入十类之种」的概率 */
        const val EXOTIC_PAIR_EXOTIC_SPECIES_RATE = 0.40f

        /** 异兽 × 异兽产出「异兽稀有度」的概率 */
        const val EXOTIC_PAIR_MUTANT_RARITY_RATE = 0.20f

        /** 跨虫混血时，后代继承父母之一物种的概率（其余概率产出混血表现） */
        const val CROSS_FAMILY_INHERIT_SPECIES_RATE = 0.7f

        /**
         * 配对类型：决定稀有度分布加成、成功率与异兽产出概率。
         *
         * 命名沿用「同虫 / 跨虫」的五虫体系语义（见 [SpeciesFamily]）。
         */
        enum class PairingKind {
            /** 同虫（如 羽虫 × 羽虫） */
            SAME_FAMILY,

            /** 跨虫（如 羽虫 × 毛虫） */
            CROSS_FAMILY,

            /** 一方为「不入十类之种」 */
            EXOTIC_MIX,

            /** 双方均为「不入十类之种」 */
            EXOTIC_PAIR;

            /** 向上档位的权重加成 */
            val upwardBonus: Float
                get() = when (this) {
                    SAME_FAMILY -> SAME_FAMILY_UPWARD_BONUS
                    CROSS_FAMILY -> CROSS_FAMILY_UPWARD_BONUS
                    EXOTIC_MIX -> EXOTIC_MIX_UPWARD_BONUS
                    EXOTIC_PAIR -> EXOTIC_PAIR_UPWARD_BONUS
                }

            /** 配对成功概率 */
            val successRate: Float
                get() = when (this) {
                    SAME_FAMILY -> SAME_FAMILY_SUCCESS_RATE
                    CROSS_FAMILY -> CROSS_FAMILY_SUCCESS_RATE
                    EXOTIC_MIX -> EXOTIC_MIX_SUCCESS_RATE
                    EXOTIC_PAIR -> 1.0f
                }

            /** 产出「不入十类之种」物种的概率 */
            val exoticSpeciesRate: Float
                get() = when (this) {
                    SAME_FAMILY -> 0.01f
                    CROSS_FAMILY -> CROSS_FAMILY_EXOTIC_SPECIES_RATE
                    EXOTIC_MIX -> EXOTIC_MIX_EXOTIC_SPECIES_RATE
                    EXOTIC_PAIR -> EXOTIC_PAIR_EXOTIC_SPECIES_RATE
                }
        }

        /** 根据父母物种判定配对类型 */
        fun pairingKindOf(speciesA: PetSpecies, speciesB: PetSpecies): PairingKind {
            val aExotic = speciesA.family == SpeciesFamily.UNCATEGORIZED
            val bExotic = speciesB.family == SpeciesFamily.UNCATEGORIZED
            return when {
                aExotic && bExotic -> PairingKind.EXOTIC_PAIR
                aExotic || bExotic -> PairingKind.EXOTIC_MIX
                speciesA.family == speciesB.family -> PairingKind.SAME_FAMILY
                else -> PairingKind.CROSS_FAMILY
            }
        }

        /**
         * 后代稀有度推导。
         *
         * 以父母中**较高的稀有度**为分布中心，按 [RARITY_DISTRIBUTION] 抽样，
         * 并根据配对类型对「向上档位」施加权重加成。
         *
         * @param parentA 父方稀有度
         * @param parentB 母方稀有度
         * @param kind 配对类型，决定向上加成
         * @param random 随机源，便于测试注入
         * @return 后代稀有度（永不比 [maxOf] 低超过 1 档）
         */
        fun offspringRarity(
            parentA: Rarity,
            parentB: Rarity,
            kind: PairingKind = PairingKind.SAME_FAMILY,
            random: Random = Random.Default
        ): Rarity {
            val base = maxOf(parentA.id, parentB.id)
            val baseRarity = Rarity.fromId(base)
            val distribution = RARITY_DISTRIBUTION[baseRarity]
                ?: return baseRarity

            // 对高于 base 的档位施加加成，再归一化抽样
            val weighted = distribution.mapValues { (rarity, weight) ->
                if (rarity.id > base) weight * kind.upwardBonus else weight
            }
            val total = weighted.values.sum()
            if (total <= 0f) return baseRarity

            var roll = random.nextFloat() * total
            for ((rarity, weight) in weighted) {
                roll -= weight
                if (roll < 0f) return rarity
            }
            // 浮点兜底：返回分布中心
            return baseRarity
        }

        /**
         * 后代物种推导。
         *
         * - 同族：继承父母之一（各 50%）
         * - 跨族/含异种：按概率产出异种，否则继承父母之一
         *
         * @param exoticPool 可产出的异种物种池（默认取全部不可抽取物种）
         */
        fun offspringSpecies(
            parentA: PetSpecies,
            parentB: PetSpecies,
            kind: PairingKind = pairingKindOf(parentA, parentB),
            exoticPool: List<PetSpecies> = PetSpecies.entries.filter { !it.isDrawable },
            random: Random = Random.Default
        ): PetSpecies {
            // 先判定是否触发异种
            if (exoticPool.isNotEmpty() && random.nextFloat() < kind.exoticSpeciesRate) {
                return exoticPool[random.nextInt(exoticPool.size)]
            }
            // 否则继承父母之一
            return if (random.nextBoolean()) parentA else parentB
        }

        /** 配对是否成功（跨族有失败概率） */
        fun isPairingSuccessful(kind: PairingKind, random: Random = Random.Default): Boolean =
            random.nextFloat() < kind.successRate
    }
}
