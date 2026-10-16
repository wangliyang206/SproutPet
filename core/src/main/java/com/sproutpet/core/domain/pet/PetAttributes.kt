package com.sproutpet.core.domain.pet

/**
 * 宠物属性。决定互动效果、排行分数、繁殖后代的遗传基础。
 *
 * 取值范围 0~1000，由以下方式提升：
 * - 升级（阶段成长）自动增加
 * - 互动行为（抚摸/喂食/玩耍）小幅增加
 * - 道具使用定向增加
 * - 遗传自父母
 */
data class PetAttributes(
    /** 体力：影响活跃时长与互动次数上限 */
    val stamina: Int = 10,
    /** 力量：影响竞技类互动表现 */
    val strength: Int = 10,
    /** 敏捷：影响反应类互动表现 */
    val agility: Int = 10,
    /** 智慧：影响学习类互动与经验获取效率 */
    val wisdom: Int = 10,
    /** 魅力：影响社交、结婚成功率与排行榜人气 */
    val charm: Int = 10,
    /** 亲密度：与主人之间的羁绊，0~100（独立于其他属性） */
    val intimacy: Int = 0
) {
    /** 综合战力（排行榜主排序依据，可加权配置） */
    val power: Int
        get() = (stamina * 1 + strength * 2 + agility * 2 + wisdom * 2 + charm * 1) / 8 * 10

    /** 四项基础属性总和 */
    val totalBase: Int get() = stamina + strength + agility + wisdom + charm

    /** 各项属性上限 */
    companion object {
        const val MAX_ATTRIBUTE = 1000
        const val MAX_INTIMACY = 100

        /**
         * ⚠️⚠️ **ADR-021 红线：本函数不得引入 `SpeciesFamily`（五虫类群）参数。**
         *
         * 属性只依赖**稀有度**与**个体属性倾向**，五虫类群**完全不参与数值计算**。
         *
         * **为什么**：一旦类群影响数值，玩家就被迫在"战力"与"喜欢"之间取舍，
         * 而本产品的核心价值是**治愈陪伴**——让玩家做这种取舍是从根上背叛定位。
         * 且数值必有强弱，会立刻制造出"劣等虫"的挫败感，这是结构性问题，无补偿手段可修。
         *
         * 五虫靠**配对加成（跨虫上漂 1.35 > 同虫 1.0）、图鉴收集、身份标识、神兽形态、叙事文案**
         * 来建立价值，全部与数值无关。见 ADR-021 与 `docs/07-五虫体系设计.md` §9之一。
         *
         * 注：`tendency` 是**物种级**的个体差异（如"瞌睡猫慵懒"），**不是类群级**。
         * 各物种的倾向分配已刻意跨类群打散，避免类群被间接编码进数值。
         */
        fun ofLevel(level: Int, rarity: Rarity, tendency: AttributeTendency): PetAttributes {
            val base = (6 + level * 2).coerceAtMost(MAX_ATTRIBUTE)
            val mul = rarity.growthMultiplier
            fun grow(weight: Float) = (base * weight * mul).toInt().coerceIn(1, MAX_ATTRIBUTE)
            return PetAttributes(
                stamina = grow(tendency.staminaWeight),
                strength = grow(tendency.strengthWeight),
                agility = grow(tendency.agilityWeight),
                wisdom = grow(tendency.wisdomWeight),
                charm = grow(tendency.charmWeight),
                intimacy = 0
            )
        }
    }
}

/** 属性倾向对应的权重分布 */
val AttributeTendency.staminaWeight: Float get() = when (this) {
    AttributeTendency.BALANCED -> 1.0f
    AttributeTendency.STRENGTH -> 1.25f
    AttributeTendency.AGILITY -> 0.95f
    AttributeTendency.WISDOM -> 0.90f
}

val AttributeTendency.strengthWeight: Float get() = when (this) {
    AttributeTendency.BALANCED -> 1.0f
    AttributeTendency.STRENGTH -> 1.40f
    AttributeTendency.AGILITY -> 0.85f
    AttributeTendency.WISDOM -> 0.80f
}

val AttributeTendency.agilityWeight: Float get() = when (this) {
    AttributeTendency.BALANCED -> 1.0f
    AttributeTendency.STRENGTH -> 0.85f
    AttributeTendency.AGILITY -> 1.40f
    AttributeTendency.WISDOM -> 0.95f
}

val AttributeTendency.wisdomWeight: Float get() = when (this) {
    AttributeTendency.BALANCED -> 1.0f
    AttributeTendency.STRENGTH -> 0.80f
    AttributeTendency.AGILITY -> 0.95f
    AttributeTendency.WISDOM -> 1.40f
}

val AttributeTendency.charmWeight: Float get() = when (this) {
    AttributeTendency.BALANCED -> 1.0f
    AttributeTendency.STRENGTH -> 0.90f
    AttributeTendency.AGILITY -> 1.10f
    AttributeTendency.WISDOM -> 1.05f
}
