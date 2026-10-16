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
        Rarity.MUTANT to 90 * 60 * 1000L          // 1.5 小时（灵兽）
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

    /** 专注陪伴抵扣比例：陪伴时长 × 该比例 = 抵扣的孵化时间 */
    const val FOCUS_BOOST_RATIO = 0.5f

    /**
     * 单颗蛋可被专注陪伴抵扣的**上限比例**（50%）。
     *
     * ⚠️ 执法点在 `IncubationEngine.focusBoostMs(focusMs, remainingMs)` ——
     * 该重载是唯一的执行入口。此前这个常量**没有任何读取方**，
     * 属于"承诺写进 PRD、代码里没人执行"的典型（见 docs/09 G-09）。
     */
    const val MAX_FOCUS_BOOST_RATIO = 0.5f

    /**
     * 互动行为提供的经验值。
     * 不同行为收益不同，且有每日上限（防刷）。
     *
     * ⚠️ **v2.1 调参（ADR-024 / 08 §六 方案 B）**：全部 `exp` 上调为原来的 **3 倍**。
     *
     * 原因是测算暴露的硬矛盾：升到成年（26 级）需累计 **55,250** 经验，
     * 而旧数值下"把全部互动打满"每天只能拿到 **1,755** 经验
     * （`5×50 + 20×20 + 35×15 + 50×8 + 10×10 + 2×40`），
     * 折算 **31.5 天/代**，把灵兽目标推到 117~159 天 —— 远超 ADR-024 的 60 天上限。
     *
     * 上调后实际上限为 **5,265/天**，成熟期降到 **10.5 天/代**。
     * 注意：`exp` 只影响**升级速度**，**不影响亲密度**（`intimacy` 未调整）——
     * 亲密度是"陪伴深度"的度量，不该被等级调速带着一起变水。
     *
     * 📌 任何改动此处的行为都必须重跑 `tools/acquisition_sim.py` 并同步 docs/08（ADR-024）。
     */
    enum class InteractionType(
        val displayName: String,
        val exp: Int,
        val intimacy: Int,
        val cooldownMs: Long,
        val dailyLimit: Int
    ) {
        /** 抚摸：最基础、无消耗、收益低 */
        PET("抚摸", exp = 15, intimacy = 1, cooldownMs = 3_000L, dailyLimit = 50),

        /** 喂食：消耗食物道具，收益中 */
        FEED("喂食", exp = 60, intimacy = 3, cooldownMs = 60_000L, dailyLimit = 20),

        /** 玩耍：消耗玩具耐久，收益高 */
        PLAY("玩耍", exp = 105, intimacy = 5, cooldownMs = 120_000L, dailyLimit = 15),

        /** 训练：消耗体力，定向提升属性 */
        TRAIN("训练", exp = 150, intimacy = 2, cooldownMs = 300_000L, dailyLimit = 8),

        /** 清洁：维持状态，收益低但影响心情 */
        CLEAN("清洁", exp = 30, intimacy = 2, cooldownMs = 180_000L, dailyLimit = 10),

        /** 桌面互动：抱起、拖动、晃一晃 —— 桌面场景的轻量正反馈 */
        DESKTOP("桌面互动", exp = 6, intimacy = 1, cooldownMs = 30_000L, dailyLimit = 40)
    }

    /**
     * 每日可得经验上限（防刷保护）。
     *
     * ⚠️ **v2.1 从 `5_000` 上调到 `6_000`**：旧值是个**够不着的空配置** ——
     * 全部互动打满只有 1,755 经验/天，上限形同虚设，也让策划误判真实节奏。
     *
     * 调参后这个数字被**精确标定**成了"打满一切可刷来源"的总和：
     *
     * ```
     * 互动 5,265（[dailyExpFromInteractions]）+ 日常任务 735（EconomyRules.dailyExpFromTasks）= 6,000
     * ```
     *
     * 也就是说上限现在**真的会拦到人** —— 它终于开始履行"防脚本刷经验"的职责，
     * 而不再是一个没人够得到的装饰。这条等式的守护测试在 `EconomyRulesTest`。
     *
     * 📌 不变量：
     * 1. `DAILY_EXP_CAP <= dailyExpFromInteractions + 日常任务经验`（否则又变空配置）
     * 2. 改动任一侧都必须同步另一侧，否则 [com.sproutpet.core.domain.economy.EconomyRulesTest] 变红
     *
     * 📌 离线收益**不占这个额度**，它只给金币 —— 见 `EconomyRules` 的说明
     * （若离线也给经验，最优策略会变成"关掉 App"，与陪伴定位冲突）。
     */
    const val DAILY_EXP_CAP = 6_000

    /**
     * 全部互动类型"打满每日上限"可获得的经验总和。
     *
     * 这个值是节奏测算的基准（见 `tools/acquisition_sim.py`），
     * 也是"日上限是否形同虚设"的判据。
     */
    val dailyExpFromInteractions: Int
        get() = InteractionType.entries.sumOf { it.exp * it.dailyLimit }

    /**
     * 亲密度上限（0~100）。
     *
     * 📌 **唯一真相源就在这里**（数值中枢）。此前 `PetAttributes` 里也定义过一份，
     * 两处均无人引用 —— 已删除重复定义，避免改一处忘一处。
     * 配对门槛 [PairingRules.requiredIntimacy] = 70 必须小于本值。
     */
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
        /** 最低亲密度要求 */
        const val requiredIntimacy = 70

        /** 配对消耗金币 */
        const val costGold = 3_000

        /**
         * 冷却时间（毫秒）。
         *
         * ⚠️ **v2.1 从 7 天下调到 3 天**（ADR-024 / 08 §六 方案 B）：
         * 单代周期 ≈ `max(成熟天数, 4×冷却)`，旧值 7 天时冷却（28 天/代）
         * 远大于成熟期，成为节奏的唯一瓶颈。降到 3 天后
         * 冷却 12 天 ≈ 成熟 10.5 天，两者终于平衡。
         */
        const val cooldownMs = 3 * 24 * 60 * 60 * 1000L

        /** 每只宠物最多后代数量（搭档为**真人**时） */
        const val maxOffspring = 5

        /**
         * 每只宠物最多后代数量（搭档为 **NPC** 时）。
         *
         * NPC 存在的意义是"用户量不足时兜底"，**不能反过来比真人配对更划算** ——
         * 否则社交引导失效，玩家会一直配 NPC。
         * 因此 NPC 上限压到 3 次，让"找真人配"有明确的数值优势（多 2 个后代名额）。
         *
         * @see maxOffspringFor
         */
        const val maxNpcOffspring = 3

        /** 按搭档类型取后代名额上限 */
        fun maxOffspringFor(partnerIsNpc: Boolean): Int =
            if (partnerIsNpc) maxNpcOffspring else maxOffspring

        /** 是否要求双方为不同主人（防止克隆式自我繁殖） */
        const val requireDifferentOwner = true

        /**
         * 是否要求「一公一母」。
         *
         * 设为 `false` 会让 [gendersCompatible] 恒为 `true`，配对前置条件
         * 自动退化为"只看成年 + 亲密度"。
         *
         * ⚠️ 保留这个开关是为了让规则**可读、可追溯**（改一处即可全局生效），
         * 而不是散落在业务代码里各写各的 —— 性别校验曾**完全缺失**，
         * 见 docs/09 G-01，就是因为规则没有集中入口。见 docs/00 §5.2。
         */
        const val requireDifferentGender = true

        /** 双方性别能否配对（受 [requireDifferentGender] 开关控制） */
        fun gendersCompatible(a: PetGender, b: PetGender): Boolean =
            !requireDifferentGender || a != b

        /** 真人配对邀请的有效期（毫秒）：3 天 */
        const val inviteTtlMs = 3 * 24 * 60 * 60 * 1000L
    }

    /**
     * 遗传规则：后代稀有度与物种的推导。
     *
     * 核心设计（v2.0 修订）：
     * 1. **概率漂移，永不降级超过 1 档** —— 避免"神话爸爸生出普通儿子"的强挫败
     * 2. **永不 100% 继承**（灵兽为 90%）—— 避免高稀有度通胀贬值
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
            // 史诗 → 开始有极小概率摸到灵兽
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
            // 灵兽 → 基本稳定传承（10% 回落到神话，此时灵血开始起作用）
            Rarity.MUTANT to mapOf(
                Rarity.MYTHIC to 0.10f,
                Rarity.MUTANT to 0.90f
            )
        )

        /**
         * 同虫配对（如 羽虫 × 羽虫）：向上档位的权重加成。
         *
         * ⚠️ 数值语义：这是对"高于分布中心的那几档"的**权重倍率**。
         * 必须满足 同虫 < 跨虫 < 含异兽 < 异兽互配，才能真正体现
         * "跨虫比同虫更容易出好东西"（鼓励社交）的设计意图。
         *
         * 📌 本文件中「异兽」一律指**物种轴**上的「不入十类之种」（见 [SpeciesFamily.UNCATEGORIZED]），
         * 与**稀有度轴**的「灵兽」（[Rarity.MUTANT]）是两回事，请勿混读。
         *
         * ⚠️ 另有一条**已知无效路径**：当基准档已经是 [Rarity.MUTANT]（灵兽）时，
         * 分布表里没有更高档位，倍率**无处施加**，加成完全失效。
         * 这不是 bug，是"顶档无加成"的自然结果。
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

        /**
         * 配对类型：决定稀有度分布加成、成功率与「不入十类之种」产出概率。
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
         * 后代稀有度的**完整推导**（含灵血觉醒与冷启动保底）。
         *
         * 三步顺序很重要，**不可调换**：
         * 1. **概率漂移** → 得到基础稀有度（[offspringRarity]）
         * 2. **灵血觉醒** → 隔代返祖，把非灵兽提升为灵兽（[ExoticBloodRules.shouldAwaken]）
         * 3. **冷启动保底** → 长期不出灵兽时的系统性补偿（[MutantPityRules.shouldTrigger]）
         *
         * 前两步都是"概率语言"，第三步是"系统怜悯"，所以放在最后 ——
         * 只有在前两步都没给出灵兽时才轮到它，且它**只会提升、绝不降级**。
         *
         * @param bloodA 父方灵血浓度（见 [Pet.exoticBlood]）
         * @param bloodB 母方灵血浓度
         * @param pityFailStreak 该账号「连续 N 次史诗及以上配对未出灵兽」的累计次数；
         *        持久化在账号级状态里（Phase 6 随云端同步），无状态时传 `0` 即等价于关闭保底
         * @return 后代稀有度 + 后代灵血浓度 + 本次触发原因
         */
        fun resolveOffspringRarity(
            parentA: Rarity,
            bloodA: Int,
            parentB: Rarity,
            bloodB: Int,
            kind: PairingKind = PairingKind.SAME_FAMILY,
            pityFailStreak: Int = 0,
            random: Random = Random.Default
        ): OffspringRarityResult {
            val carrierBlood = ExoticBloodRules.carrierBloodOf(bloodA, bloodB)
            val rolled = offspringRarity(parentA, parentB, kind, random)

            val awakened = rolled != Rarity.MUTANT &&
                    ExoticBloodRules.shouldAwaken(carrierBlood, random)
            val afterAwaken = if (awakened) Rarity.MUTANT else rolled

            val pityTriggered = afterAwaken != Rarity.MUTANT &&
                    MutantPityRules.shouldTrigger(pityFailStreak, random)
            val rarity = if (pityTriggered) Rarity.MUTANT else afterAwaken

            return OffspringRarityResult(
                rarity = rarity,
                exoticBlood = ExoticBloodRules.inheritedBlood(
                    carrierBlood = carrierBlood,
                    offspringIsExoticBeast = rarity == Rarity.MUTANT
                ),
                awakened = awakened,
                pityTriggered = pityTriggered
            )
        }

        /**
         * 后代稀有度推导结果。
         *
         * @param rarity 后代最终稀有度
         * @param exoticBlood 后代携带的灵血浓度
         * @param awakened 是否由「灵血觉醒」产生（用于播返祖专属动画与文案）
         * @param pityTriggered 是否由「冷启动保底」产生（用于播另一种文案，并重置计数器）
         */
        data class OffspringRarityResult(
            val rarity: Rarity,
            val exoticBlood: Int,
            val awakened: Boolean,
            val pityTriggered: Boolean = false
        ) {
            /** 是否由任一"补偿机制"产生（觉醒或保底）—— 用于播额外出场特效 */
            val compensated: Boolean get() = awakened || pityTriggered
        }

        /**
         * 后代物种推导。
         *
         * 只有两种归宿，**没有中间态**：
         * 1. 按 [PairingKind.exoticSpeciesRate] 判定，命中则产出「不入十类之种」
         * 2. 否则**等概率**继承父母之一（各 50%）
         *
         * 📌 这里**没有**"混血新表现"这第三种结果 —— 物种要么是五虫之一，要么是不入十类。
         * 外观层面的混合由 [PetGenes.inherit] 负责，属于**基因轴**，与本函数无关。
         * （历史上曾有 `CROSS_FAMILY_INHERIT_SPECIES_RATE` 常量承诺过"70% 继承 / 30% 混血表现"，
         * 但该行为从未实现，常量已于 v1.2 删除。）
         *
         * @param exoticPool 可产出的物种池（默认取全部不可抽取物种）
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

    /**
     * 灵血传承规则。
     *
     * ## 为什么需要它
     *
     * 灵兽（[Rarity.MUTANT]）只能靠生育获得，而生育有 10% 概率"回落"到神话档
     * （见 [BreedingRules.RARITY_DISTRIBUTION] 中 `MUTANT` 那一行）。
     * 没有灵血机制时，**一次回落就等于血统永久归零** ——
     * 玩家辛苦养出的灵兽血脉会在孙辈彻底消失，"灵兽血脉"也就无法成为可经营的家族资产。
     *
     * ## 它做什么
     *
     * 给非灵兽后代留下一个**隐性印记**（[Pet.exoticBlood]）：
     * - 灵兽本身 = 满值（[MAX_BLOOD]）
     * - 非灵兽后代逐代**折减**（[inheritedBlood]），直至归零
     * - 浓度越高，下一代"觉醒"为灵兽的概率越大（[awakenRateOf]）
     *
     * 于是血统有了**韧性**：不会一次断代就归零，但也绝不会永久继承。
     *
     * ## 它不做什么（红线）
     *
     * ⚠️ 灵血**只影响觉醒概率，绝不参与属性计算** —— 守住 ADR-021
     * （血统不制造数值差异，避免出现"劣等血统"）。
     *
     * @see docs/06-物种与稀有度设计.md §2.4
     */
    object ExoticBloodRules {

        /** 灵血浓度上限 */
        const val MAX_BLOOD = 3

        /**
         * 各浓度对应的「觉醒」概率。
         *
         * 只在**后代最终不是灵兽**时才判定；命中则直接跃升为灵兽。
         * 注意浓度 3 意味着父母至少有一方是灵兽，此时觉醒只是对那 10% 回落概率的补偿。
         */
        private val AWAKEN_RATE_BY_BLOOD: Map<Int, Float> = mapOf(
            0 to 0.00f,
            1 to 0.03f,
            2 to 0.08f,
            3 to 0.15f
        )

        /** 取某浓度的觉醒概率（越界按 0 处理，不抛异常） */
        fun awakenRateOf(blood: Int): Float =
            AWAKEN_RATE_BY_BLOOD[blood.coerceIn(0, MAX_BLOOD)] ?: 0f

        /**
         * 父母双方的灵血浓度 → 传给后代的**携带浓度**。
         *
         * 取双方**较高**者：血统不因"与普通人配对"而被稀释。
         */
        fun carrierBloodOf(bloodA: Int, bloodB: Int): Int =
            maxOf(bloodA, bloodB).coerceIn(0, MAX_BLOOD)

        /**
         * 推导后代最终携带的灵血浓度。
         *
         * @param carrierBlood 父母的携带浓度（见 [carrierBloodOf]）
         * @param offspringIsExoticBeast 后代最终是否为灵兽
         *
         * - 后代是灵兽 → 满值（血统"回归"）
         * - 否则 → 折减一级（隐性基因的自然衰减）
         */
        fun inheritedBlood(carrierBlood: Int, offspringIsExoticBeast: Boolean): Int =
            if (offspringIsExoticBeast) MAX_BLOOD
            else (carrierBlood - 1).coerceAtLeast(0)

        /**
         * 觉醒判定：后代本不是灵兽，是否因灵血而"觉醒"为灵兽。
         *
         * @return `true` 表示应把稀有度提升为 [Rarity.MUTANT]
         */
        fun shouldAwaken(carrierBlood: Int, random: Random = Random.Default): Boolean {
            val rate = awakenRateOf(carrierBlood)
            return rate > 0f && random.nextFloat() < rate
        }
    }

    /**
     * 灵兽冷启动保底规则（S15）。
     *
     * ## 为什么需要它
     *
     * [ExoticBloodRules]（灵血传承）对**冷启动完全无效** ——
     * 它的觉醒率由血统浓度决定，而"还没有过灵兽"的玩家浓度恒为 `0`，觉醒率也是 `0`。
     * 也就是说，灵血解决的是"**血统别断**"，而**不是**"第一次怎么拿到"。
     *
     * 于是顶稀有玩家最可能在这里流失：反复配对、每次都符合条件，却看不到任何进度感。
     * 现实测算中灵兽期望需要 20 次以上配对，一个不透明的低概率长跑就是劝退器。
     *
     * ## 它做什么
     *
     * 用**可见的计数器**把"运气"变成"进度条"：
     * - 只看「**父母最高档 ≥ 史诗**」的配对（低档配对不算 —— 保底是奖励"认真配对"）
     * - 从第 [PITY_THRESHOLD] 次仍未出灵兽开始，每次额外 **+2%** 灵兽概率
     * - 封顶 [MAX_PITY_RATE]（40%），出灵兽后计数器**归零**
     *
     * 与抽卡保底共用同一套心智：**"距离灵兽保底还差 N 次"** 是可见的。
     *
     * ## 它不是什么
     *
     * - ❌ 不是"必然获得"：封顶 25% 仍然是概率，只是把长尾拉平
     * - ❌ 不影响属性（守住 [ExoticBloodRules] 同款红线，ADR-021）
     * - ❌ 不是抽取／融合通道：灵兽依然**只能**由生育产生（ADR-017）
     *
     * ## 参数是**实测出来的**，不是拍脑袋定的
     *
     * 初版取「30 次 / 每次 +1% / 封顶 25%」。但 `tools/acquisition_sim.py` 的实测显示：
     * 命中灵兽那一刻，"史诗及以上配对"的连击数中位数只有 **10 次（单线）/ 16 次（三线）**，
     * 也就是说 30 这个门槛**只对最倒霉的 5%~20% 玩家生效**，几乎等于没配。
     * 调成「**20 次 / 每次 +2% / 封顶 40%**」后 p99 从 54 次降到 46 次（单线）、
     * 71 次降到 66 次（三线），长尾明显被削平，而中位数不变（目标没有被廉价化）。
     *
     * 📌 这个教训本身值得记住：**保底阈值必须对着"实际连击分布"去设**，
     * 直觉估的阈值很容易落在一个永远不会被触发的位置（与 `DAILY_EXP_CAP` 那个坑同源）。
     *
     * ## 状态放在哪
     *
     * [failStreak] 是**账号级**状态，不属于任何一只宠物。
     * 本期先在 `core` 提供**纯函数**（无副作用、可单测），
     * 持久化随 Phase 6 的账号体系落地（见 docs/03）。
     *
     * @see docs/08-获取路径与周期测算.md §九 S15
     */
    object MutantPityRules {

        /** 开始补偿的门槛：连续这么多次"史诗及以上"配对未出灵兽即开始加概率 */
        const val PITY_THRESHOLD = 20

        /** 每次未出灵兽的额外概率增量（2%） */
        const val PITY_STEP = 0.02f

        /** 额外概率上限（40%）——封顶后仍不是必出 */
        const val MAX_PITY_RATE = 0.40f

        /**
         * 当前连败次数折算出的**额外**灵兽概率。
         *
         * 未达门槛返回 `0f`；达到后线性递增并封顶。
         */
        fun pityBonusOf(failStreak: Int): Float {
            if (failStreak < PITY_THRESHOLD) return 0f
            val steps = failStreak - PITY_THRESHOLD + 1
            return (steps * PITY_STEP).coerceAtMost(MAX_PITY_RATE)
        }

        /**
         * 是否由保底促成本次灵兽。
         *
         * @return `true` 表示应把稀有度提升为 [Rarity.MUTANT]
         */
        fun shouldTrigger(failStreak: Int, random: Random = Random.Default): Boolean {
            val bonus = pityBonusOf(failStreak)
            return bonus > 0f && random.nextFloat() < bonus
        }

        /**
         * 本次配对结束后，计数器应变成多少。
         *
         * 规则：
         * - 出了灵兽 → **归零**（无论靠运气、觉醒还是保底）
         * - 父母最高档**不到史诗** → **保持不变**（这次配对不算数，别惩罚玩家）
         * - 其余 → `+1`
         */
        fun nextStreak(current: Int, parentTopRarity: Rarity, offspringIsMutant: Boolean): Int =
            when {
                offspringIsMutant -> 0
                !parentTopRarity.isHighRarity -> current.coerceAtLeast(0)
                else -> current.coerceAtLeast(0) + 1
            }

        /** 距离开始获得保底补偿还差几次（已过门槛则为 0）—— 用于 UI 展示"还差 N 次" */
        fun remainingToThreshold(failStreak: Int): Int =
            (PITY_THRESHOLD - failStreak).coerceAtLeast(0)
    }
}
