package com.sproutpet.core.domain.pet

import com.sproutpet.core.domain.pet.GrowthRules.BreedingRules.PairingKind

/**
 * 神兽形态规则 —— 决定一只宠物**以什么形态被渲染**。
 *
 * ## 两条轴，别混
 *
 * 本文件只回答"这只宠物**长什么样**"，不涉及任何数值。
 * 与它相邻的两条轴是：
 * - **稀有度轴**（[Rarity]）—— 决定材质与特效层级
 * - **物种轴**（[PetSpecies]）—— 决定基础骨骼
 * 神兽形态是叠在物种骨骼之上的**第三层**，三者正交。
 *
 * ## 形态分两层（ADR-026）
 *
 * | 层 | 触发条件 | 定位 |
 * |----|---------|------|
 * | **神兽特征层**（翎羽描边 / 龟甲刻纹 / 独角鳞纹） | 稀有度 ≥ 传说 | 稀有度回报，抽到就有 |
 * | **完整神兽形态层**（凤凰 / 麒麟 / 蛟龙 / 神龟） | **灵兽 × 同虫** | 长线追求目标 |
 *
 * ### 为什么要拆成两层
 *
 * 改造前是"稀有度 ≥ 传说即**自动**带完整形态层"，后果有两个：
 * 1. **神兽沦为抽卡的免费赠品** —— 它不是一个追求目标，只是高稀有度的外观回报，
 *    获得这件事既无动作也无门槛，玩家没有获得感。
 * 2. **同虫配对没有任何存在理由** —— 加成表里 `同虫 ×1.00` 严格劣于 `跨虫 ×1.35`，
 *    而同虫产出异兽只有 1%。玩家找不到任何理由选同虫。
 *
 * 拆开后两条路**各有一块专属领地**，且**不需要给同虫加任何数值**（守住 ADR-021）：
 * ```
 * 跨虫路线  →  异兽（物种轴「不入十类之种」）   4% / 次
 * 同虫路线  →  完整神兽形态层                   需先在同虫线上出灵兽
 * 抽卡路线  →  素材与低阶宠
 * ```
 *
 * ## 已知代价（必须承认的设计取舍）
 *
 * 于是**灵兽会分成两种长相**：同虫线出的有完整神兽形态，跨虫线出的没有
 * （它已有"更高概率"与"异兽"两块收益）。
 * 这是刻意的差异，不是缺陷 —— 但它是这个方案唯一的代价，记录在此以免日后被当成 bug。
 *
 * ## 与「基因」的分工
 *
 * 同一物种、同一稀有度、同一形态的两只宠物**仍然长得不一样** ——
 * 那是 [PetGenes] 的职责（体型 / 色相 / 花纹 / 眼色 / 尾长）。
 * 形态层管"骨架与部件"，基因管"个体差异"。
 *
 * @see SpeciesFamily.deityId
 * @see Pet.hasFullDeityForm
 * @see docs/07-五虫体系设计.md §5.1
 */
object DeityRules {

    /**
     * 是否拥有**神兽特征层**（部分特征，如翎羽描边）。
     *
     * 门槛是「稀有度 ≥ 传说」，与配对方式无关 —— 这保持了"抽卡也有回报"的爽感，
     * 是 [Rarity.LEGENDARY] 及以上档位的既有外观承诺。
     *
     * ⚠️ 蠃虫除外：其「长」为圣人，依 ADR-020 不设形。
     */
    fun hasDeityFeatures(rarity: Rarity, family: SpeciesFamily): Boolean =
        rarity.id >= Rarity.LEGENDARY.id && family.hasDeityForm

    /**
     * 是否拥有**完整神兽形态层**（凤凰 / 麒麟 / 蛟龙 / 神龟）。
     *
     * 三个条件**同时**成立：
     * 1. 个体是**灵兽**（[Rarity.MUTANT]）—— 灵兽只能靠生育及其灵血传承获得
     * 2. 物种属**五虫之一且该虫有神兽形态**（蠃虫无、不入十类无）
     * 3. 本次诞生自**同虫配对**（[GrowthRules.BreedingRules.PairingKind.SAME_FAMILY]）
     *
     * ⚠️ 第 3 条要求调用方提供**诞生时的配对类型** —— 这是本规则唯一无法从
     * 「当前的宠物自身」推出来的信息，因此结果必须在出生时算好并持久化，
     * 见 [Pet.hasFullDeityForm]。
     *
     * @param pairingKind 诞生时的配对类型；抽取得到的宠物传 `null`
     */
    fun hasFullDeityForm(rarity: Rarity, family: SpeciesFamily, pairingKind: PairingKind?): Boolean =
        rarity == Rarity.MUTANT &&
                family.hasDeityForm &&
                pairingKind == PairingKind.SAME_FAMILY

    /**
     * 神兽形态的渲染通道编号，无形态时为 `null`。
     *
     * 直接透传 [SpeciesFamily.deityId]，供 Rive 的 `deity_id` 通道使用。
     */
    fun deityIdOf(family: SpeciesFamily): Int? = family.deityId

    /**
     * 出生时的形态结算入口 —— 把三个输入换算成**要持久化的那个布尔值**。
     *
     * @param rarity 后代最终稀有度（含灵血觉醒的结果）
     * @param species 后代最终物种
     * @param pairingKind 诞生时的配对类型；抽取得到传 `null`
     * @return 该个体是否拥有完整神兽形态层
     */
    fun resolveAtBirth(rarity: Rarity, species: PetSpecies, pairingKind: PairingKind?): Boolean =
        hasFullDeityForm(rarity, species.family, pairingKind)
}
