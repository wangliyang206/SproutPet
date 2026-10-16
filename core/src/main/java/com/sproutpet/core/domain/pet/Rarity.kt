package com.sproutpet.core.domain.pet

/**
 * 宠物稀有度。决定：
 * - 盲盒开出的概率（[baseWeight]）
 * - 基础属性成长上限（[growthMultiplier]）
 * - 外观特效（描边、光效、粒子、异色）
 * - 遗传给后代时的概率分布（见 [GrowthRules.BreedingRules]）
 *
 * ⚠️ 本枚举是**稀有度轴**，请不要与**物种轴**混淆：
 * 最高档 [MUTANT]（「灵兽」）说的是"个体发生灵异变异"，
 * 而物种轴上的「异兽」说的是"不入十类之种"（星狐 / 月兔 / 荧波）。
 * 两者互不蕴含，命名细节见 [MUTANT]。
 *
 * 设计立场：本产品是「治愈陪伴」而非竞技，稀有度代表
 * **好看程度 + 省时间程度**，不是战力碾压。因此成长系数上限压在 ×2.4。
 *
 * @see docs/06-物种与稀有度设计.md
 */
enum class Rarity(
    val id: Int,
    val displayName: String,
    /** 基础开出权重（权重越高越常见）。0 表示**不可抽取**。 */
    val baseWeight: Int,
    /** 属性成长系数（乘算） */
    val growthMultiplier: Float,
    /** 遗传时特征被继承的概率 0~1（展示与文案用，实际分布见 BreedingRules） */
    val inheritProbability: Float,
    /** 外观特效层级描述，供 UI 展示 */
    val fxDescription: String
) {
    COMMON(0, "普通", 1000, 1.00f, 0.30f, "基础色"),
    RARE(1, "稀有", 400, 1.15f, 0.45f, "描边"),
    EPIC(2, "史诗", 120, 1.35f, 0.60f, "描边 + 光效"),
    LEGENDARY(3, "传说", 30, 1.60f, 0.78f, "光效 + 粒子"),
    MYTHIC(4, "神话", 5, 2.00f, 0.92f, "全套特效 + 专属动画"),

    /**
     * 灵兽：**不可抽取**的隐藏档，只能通过配对生育（及其「灵血」传承）获得。
     *
     * ⚠️ **命名区分（务必看清，这是两条正交的轴）**：
     * - 本条是**稀有度轴**的「灵兽」—— 指个体发生**灵异变异**
     *   （异色瞳 / 荧光 / 双色 / 变异体型）。
     * - **物种轴**上的「异兽」是另一回事 —— 指「不入十类之种」
     *   （星狐 / 月兔 / 荧波，见 [PetSpecies]）。
     *
     * 二者互不蕴含：**星狐（异兽）可以是普通稀有度；懒猫也可以是灵兽**。
     * 历史上两者都曾叫「异兽」，导致文档歧义，v1.2 起正式拆开。
     *
     * 它存在的意义不是"更值钱的奖品"，而是给顶稀有玩家一个**可持续追求的目标**，
     * 从而让神话不因通胀而贬值。详见 docs/06-物种与稀有度设计.md 第二章。
     */
    MUTANT(5, "灵兽", 0, 2.40f, 1.00f, "异色瞳 / 荧光 / 双色 / 变异体型");

    /** 是否为高稀有（史诗及以上），用于保底计数 */
    val isHighRarity: Boolean get() = this >= EPIC

    /** 是否可从盲盒池中抽出 */
    val isDrawable: Boolean get() = baseWeight > 0

    companion object {
        fun fromId(id: Int): Rarity = entries.firstOrNull { it.id == id } ?: COMMON

        /** 可抽取档位的权重之和（显式排除不可抽取档位） */
        fun totalWeight(): Int = entries.filter { it.isDrawable }.sumOf { it.baseWeight }

        /** 可抽取档位列表 */
        fun drawableEntries(): List<Rarity> = entries.filter { it.isDrawable }

        /**
         * 可抽取的**最高**档位（目前为 [MYTHIC]）。
         *
         * ⚠️ **用途是安全钳制**：任何"保底 / 指定稀有度"的道具或接口，
         * 都必须用它做上限，否则会绕开 ADR-017（灵兽只能遗传、不可抽取）。
         * 见 [com.sproutpet.core.domain.incubation.BlindBoxEngine.draw]。
         */
        val maxDrawable: Rarity
            get() = entries.filter { it.isDrawable }.maxByOrNull { it.id } ?: COMMON
    }
}
