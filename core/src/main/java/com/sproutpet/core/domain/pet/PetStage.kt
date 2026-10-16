package com.sproutpet.core.domain.pet

/**
 * 宠物的生命阶段。
 *
 * 每个阶段对应：
 * - 不同的体型表现（美术资源）
 * - 不同的可解锁互动
 * - 不同的成长速率与上限
 *
 * 阶段流转：EGG → HATCHING → BABY → JUVENILE → ADULT → ELDER → RETIRED
 * 注意：EGG 与 HATCHING 严格来说属于「孵化期」，尚未成为真正的宠物个体，
 * 但为了数据模型统一，仍在 Pet 实体中以 stage 表示。
 *
 * RETIRED（隐居）不是"死亡"——宠物搬到"后山小屋"，可随时召回。
 * 详见 docs/00-产品需求文档PRD.md §2.8。
 */
enum class PetStage(val id: Int, val displayName: String) {

    /** 蛋：盲盒开出后、孵化完成前 */
    EGG(0, "蛋"),

    /** 破壳中：孵化倒计时归零后的过渡动画阶段（约 3 秒） */
    HATCHING(1, "破壳中"),

    /** 幼崽：刚破壳，体型最小，互动能力弱 */
    BABY(2, "幼崽"),

    /** 少年：开始有独立性格表现 */
    JUVENILE(3, "少年"),

    /** 成年：可以配对生育、参与排行、外观最完整 */
    ADULT(4, "成年"),

    /** 长者：成长到顶，拥有传承资格，外观更沉稳 */
    ELDER(5, "长者"),

    /** 隐居：从桌面退居"后山小屋"，**可随时召回**，不是死亡 */
    RETIRED(6, "隐居");

    /** 是否已破壳（可用于互动） */
    val isHatched: Boolean get() = this >= BABY

    /** 是否可以配对生育（成年或长者；隐居后不再参与） */
    val canPair: Boolean get() = this == ADULT || this == ELDER

    /** 是否可以生育（与 [canPair] 同义，保留语义化命名） */
    val canBreed: Boolean get() = canPair

    /** 是否在桌面上可展示 */
    val isOnDesktop: Boolean get() = isHatched && this != RETIRED

    companion object {
        fun fromId(id: Int): PetStage = entries.firstOrNull { it.id == id } ?: EGG
    }
}
