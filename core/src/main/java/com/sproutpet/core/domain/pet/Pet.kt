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

    /**
     * 灵血浓度 `0 ~ GrowthRules.ExoticBloodRules.MAX_BLOOD`。
     *
     * 「灵兽」（[Rarity.MUTANT]）血统的**隐性印记**：
     * - 灵兽本身为满值（[GrowthRules.ExoticBloodRules.MAX_BLOOD]）
     * - 它的非灵兽后代逐代**折减**，最终归零
     * - 浓度越高，下一代"觉醒"为灵兽的概率越大
     *
     * ⚠️ 灵血**只影响觉醒概率，不影响属性** —— 守住 ADR-021
     * （血统不制造数值差异，避免"劣等血统"）。
     *
     * 注意：灵血属于**稀有度轴**，与物种轴上的「异兽」（不入十类之种）无关。
     *
     * @see GrowthRules.ExoticBloodRules
     * @see docs/06-物种与稀有度设计.md §2.4
     */
    val exoticBlood: Int = 0,

    /**
     * 是否拥有**完整神兽形态层**（凤凰 / 麒麟 / 蛟龙 / 神龟）。
     *
     * 这是**出生时结算一次、此后永不改变的个体特征**（见 [DeityRules.resolveAtBirth]）——
     * 所以它必须是持久化字段，而不能在渲染时现算：
     * 判定需要"诞生时的配对类型"这个信息，而宠物自身已经推不出来了。
     *
     * 取得条件（三者同时成立，ADR-026）：
     * 1. 个体是灵兽（[Rarity.MUTANT]）
     * 2. 物种属五虫之一，且该虫有神兽形态（蠃虫无、不入十类无）
     * 3. 诞生自**同虫配对**
     *
     * ⚠️ 它是**外观字段，不参与任何数值计算** —— 与 [exoticBlood] 同一条红线（ADR-021）。
     *
     * @see DeityRules
     * @see docs/07-五虫体系设计.md §5.1
     */
    val hasFullDeityForm: Boolean = false,

    /**
     * 性别（公 / 母）。
     *
     * **出生即定、永不改变**的个体特征：盲盒开蛋时随机分配，
     * 生育后代时同样随机分配。它是配对生育的前置条件之一（一公一母）。
     *
     * ⚠️ 不参与任何数值计算 —— 只决定"能否配对"与族谱的展示位（ADR-021）。
     *
     * 默认值 [PetGender.MALE] 是为了让历史数据与默认构造有确定语义；
     * **新宠一律用 [PetGender.random] 显式分配**，不要依赖这个默认值。
     *
     * @see PetGender
     * @see canPairWith
     */
    val gender: PetGender = PetGender.MALE,

    /**
     * 世代数：初代 1，后代 = max(父, 母) + 1。
     *
     * ⚠️ **`G-03` 的修复点**：此前该值**只存在数据库里、领域模型没有**，
     * `PetMapper.toEntity` 默认永远写 1 —— 族谱排序、世代榜拿到的全是恒定 1。
     * 现在它是领域模型的正式成员，计算规则见
     * [GrowthRules.GenerationRules.offspringGeneration]。
     *
     * 必须持久化：它由"出生上下文"（父母的世代）决定，宠物自身推不出来。
     */
    val generation: Int = 1,

    /**
     * 是否主宠 —— 「**在任何地方都优先表达的那只**」（`U-12` 拍板）。
     *
     * 家与桌面共用同一属性（家也有搭话气泡，多只同时想说话必须有优先级）。
     * **恒定占桌面 0 号槽位**（[HomeSlots.MAIN_DESKTOP_SLOT]）；想换主宠，
     * 必须先回答「谁是新的主角」，不允许静默降级。
     *
     * 全局必须**恰好一只** —— 由 [HomeSlots.validate] 守住。
     */
    val isMain: Boolean = false,

    /**
     * 在「家」的站位槽位（`0..5`）；[HomeSlots.NO_SLOT]（-1）= 待命（不在家）。
     *
     * 三层链条「拥有不限 ⊃ 家在册 6 ⊃ 桌面 3」的中间层（`U-12`）。
     * 「放出 / 收起」改变本字段；隐居宠不占槽位。
     */
    val homeSlot: Int = HomeSlots.NO_SLOT,

    /**
     * 手机桌面的槽位（`0..2`）；[HomeSlots.NO_SLOT] = 未上桌。
     *
     * 含包约束：**桌面 ⊆ 在家** —— 桌上的宠必须先在册。由 [HomeSlots.validate] 守住。
     */
    val desktopSlot: Int = HomeSlots.NO_SLOT,

    /**
     * 融合印记累计数（**当前档位内**，上漂后溢出自动带到新档）。
     *
     * 它由 [FusionRules.resolve] 结算、**必须持久化在宠物身上** ——
     * 印记是玩家对个体投入的累计，丢了等于"喂过的副宠白喂"。
     * 上漂阈值见 [FusionRules.thresholdFor]（普通 5 / 稀有 8 / 史诗 12 / 传说 20）。
     *
     * ⚠️ 该列曾长期缺失（V6 → V7 补上）—— 与 `exotic_blood` / `gender` 是同款事故：
     * 规则在 [FusionRules] 里早已冻结，落点却没有，机制在真实运行中不可用。
     */
    val fusionMarks: Int = 0
) {
    /**
     * 是否在「家」的在册名单里。
     *
     * ⚠️ 规则本体在 [HomeSlots.isAtHome]，这里只是可读别名 ——
     * 绝不要写成 `HomeSlots.run { atHome }`，那会解析回本属性自身（无限递归）。
     */
    val atHome: Boolean get() = HomeSlots.isAtHome(this)

    /** 是否在手机桌面上（规则本体 [HomeSlots.isOnDesktop]） */
    val onDesktop: Boolean get() = HomeSlots.isOnDesktop(this)
    /** 升级所需经验 */
    val expToNext: Int get() = GrowthRules.expToNextLevel(level)

    /**
     * 是否已隐居（「后山小屋」，**可随时召回**，不是死亡）。
     *
     * ## 为什么是**派生属性**而不是存储字段
     *
     * 此前它是 `Pet` 上的一个独立布尔字段，与 `stage == RETIRED` **各说各话**：
     * `PetDao.observeByOwner` 等查询过滤的是**数据库列** `is_retired`，
     * 而领域层判断的是 **stage**，两者之间**没有任何同步逻辑**。
     * 结果是"后山小屋里的宠会不会出现在列表里"取决于你从哪个入口问。
     *
     * 现在收敛为**单一真相源 = [stage]**，本属性只是它的可读别名。
     * 数据库里的 `is_retired` 列**降级为派生缓存**（由 `PetMapper` 写入，
     * 供 DAO 走索引过滤），不再具有独立语义。
     *
     * ⚠️ 不使用 `stage == RETIRED` 直接写字面量，是为了让"隐居"这个概念
     * 只有一个可搜索的名字。
     *
     * @see PetStage.RETIRED
     * @see docs/00-产品需求文档PRD.md §2.8
     */
    val isRetired: Boolean get() = stage == PetStage.RETIRED

    /** 升级进度 0f~1f */
    val expProgress: Float
        get() = if (expToNext == Int.MAX_VALUE) 1f
        else (exp.toFloat() / expToNext).coerceIn(0f, 1f)

    /**
     * 能否参与配对生育。
     *
     * v2.0 起以「配对生育」取代「结婚」：只要满足成年 + 亲密度门槛即可参与，
     * 不要求"单身"（配对的双方各自保留宠物，不存在归属转移）。
     *
     * ⚠️ 这是**自身**是否够格（成年 + 亲密度）。性别是**双方**的约束，
     * 必须用 [canPairWith] 判断 —— 只查本方永远查不出"两只公的"。
     *
     * 详见 docs/00-产品需求文档PRD.md §11.1。
     */
    val canPair: Boolean
        get() = stage.canPair &&
                attributes.intimacy >= GrowthRules.PairingRules.requiredIntimacy

    /**
     * 能否与 [other] 配对。
     *
     * 三条**全部**满足才成立（docs/06 §2.3）：
     * 1. 双方各自够格（成年 + 亲密度，见 [canPair]）
     * 2. **一公一母**（见 [GrowthRules.PairingRules.gendersCompatible]）
     * 3. **不同主人**，防止自我繁殖（见 [GrowthRules.PairingRules.requireDifferentOwner]）
     *
     * ⚠️ 冷却 / 后代名额属于**配对关系**（`Pairing`）而非个体，
     * 由 `Pairing.canBreedNow` 判断，不在这里查。
     */
    fun canPairWith(other: Pet): Boolean =
        canPair && other.canPair &&
                GrowthRules.PairingRules.gendersCompatible(gender, other.gender) &&
                (!GrowthRules.PairingRules.requireDifferentOwner || ownerId != other.ownerId)

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

    /** 是否为灵兽（稀有度第 6 档）。注意与物种轴上的「异兽」无关。 */
    val isExoticBeast: Boolean get() = rarity == Rarity.MUTANT

    /** 是否携带灵血（灵兽本身，或流淌着灵兽血统的后代） */
    val carriesExoticBlood: Boolean get() = exoticBlood > 0

    /**
     * 是否带「**神兽特征层**」（翎羽描边 / 龟甲刻纹 / 独角鳞纹等**部分**特征）。
     *
     * 由稀有度直接决定（≥ 传说即得），因此**不需要持久化** ——
     * 它与 [hasFullDeityForm] 是两层，别混：这一层是"抽卡回报"，那一层是"长线追求"。
     */
    val hasDeityFeatures: Boolean get() = DeityRules.hasDeityFeatures(rarity, species.family)

    /**
     * 神兽形态的**渲染通道编号**（`0~3`），无完整形态时为 `null`。
     *
     * 直接喂给 Rive 的 `deity_id` 通道：0 蛟龙 / 1 麒麟 / 2 凤凰 / 3 神龟。
     */
    val deityId: Int? get() = if (hasFullDeityForm) species.family.deityId else null
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
