package com.sproutpet.core.domain.pet

import kotlin.random.Random

/**
 * 宠物性别：公 / 母。
 *
 * ## 它负责什么
 *
 * 性别是**配对生育的前置条件之一**（成年 + 一公一母 + 异主 + 冷却结束）。
 * 判定入口统一在 [GrowthRules.PairingRules.gendersCompatible]，
 * 与其余配对前置条件放在一起，避免规则散落导致"漏判"。见 docs/00-产品需求文档PRD.md §5.2。
 *
 * ## 它不负责什么（红线：ADR-021）
 *
 * ⚠️ 性别**不参与任何数值计算** —— 不影响力成长、不影响孵化时长、不影响稀有度概率。
 * 它唯一的作用是"配对资格"，以及族谱上的一个展示位（docs/00 §2.7）。
 * 若哪天有人想在数值里加"公的力气大一点"，那是**违反 ADR-021** 的改动。
 *
 * ## 分配时机
 *
 * **出生即定**（[random]），此后永不改变：
 * - 盲盒开蛋：见 [com.sproutpet.core.domain.incubation.BlindBoxEngine.createEgg]
 * - 生育后代：见 [GrowthRules.BreedingRules]
 *
 * 之所以不在"孵化完成"时才分配：蛋期就落库，中途重启/迁移都不会让性别漂移。
 *
 * ## 存储约定
 *
 * [id] 是写入数据库的整数值（`pet.gender` 列），**不可重排** ——
 * 重排会让存量数据整体变性。0 = 公，1 = 母。
 */
enum class PetGender(val id: Int, val displayName: String) {

    /** 公 */
    MALE(0, "公"),

    /** 母 */
    FEMALE(1, "母");

    /** 另一半性别 */
    val opposite: PetGender get() = if (this == MALE) FEMALE else MALE

    companion object {

        /** 从数据库整数值还原（未知值按 [MALE] 兜底，不抛异常） */
        fun fromId(id: Int): PetGender = entries.firstOrNull { it.id == id } ?: MALE

        /**
         * 随机分配性别。
         *
         * 刻意保持 **1:1**：本产品的核心长线玩法需要"一公一母"，
         * 若某一边比例偏低，玩家会大量遇到"条件都满足但性别撞车"的挫败，
         * 而这种挫败无法通过努力化解 —— 属于结构性劝退，必须从源头避免。
         *
         * @param random 随机源，便于测试注入固定序列
         */
        fun random(random: Random = Random.Default): PetGender =
            if (random.nextBoolean()) MALE else FEMALE
    }
}
