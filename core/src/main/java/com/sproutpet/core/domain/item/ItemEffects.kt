package com.sproutpet.core.domain.item

import com.sproutpet.core.domain.pet.Pet
import com.sproutpet.core.domain.pet.PetAttributes
import com.sproutpet.core.domain.pet.PetStage
import com.sproutpet.core.domain.pet.Rarity

/**
 * 道具效果的**唯一执行层**（`G-07` 的修复点）。
 *
 * ## 背景（为什么这个 object 必须存在）
 *
 * 此前 `ItemEffect` 八种效果**只有数据定义、没有任何代码会执行它们** ——
 * `ItemRegistry` 有 8 分类 / 11 道具，但 `GuaranteeRarity` 是唯一"半接线"的
 * （`BlindBoxEngine.draw` 有 `guaranteedRarity` 参数，却没人把道具接上去）。
 * 「道具系统」实际上只是**一份道具图鉴**。
 *
 * 本文件把「效果怎么作用于世界」收敛为**纯函数**：输入（效果列表 + 当前宠物），
 * 输出（变换结果），无副作用、无 Android 依赖 —— Repository / UseCase 只做「读出 → 应用 → 写回」。
 *
 * ## 边界（谁负责什么）
 *
 * | 事情 | 归谁 |
 * |------|------|
 * | 效果如何改变一只宠 / 钱包 / 好感 | **本文件**（纯函数） |
 * | 经验溢出后的**升级**与属性重算 | 调用方按 `GrowthRules.AttributeRules.onLevelUp` 循环推进（`G-06` 的消费约定） |
 * | 玩耍**消耗玩具耐久** | [ToyRules] |
 * | 读背包 / 写数据库 / 扣金币的事务 | L4/L6 的 UseCase（领域层不碰 IO） |
 */
object ItemEffectApplier {

    /** 一次应用的结果：各字段按效果类型填充，调用方按需取用 */
    data class Outcome(
        /** 变换后的宠物；效果全部不作用于宠物个体时为 `null` */
        val pet: Pet?,
        /** 钱包金币变化（[ItemEffect.AddGold]，可为负） */
        val walletGoldDelta: Int = 0,
        /** 双方好感度变化（[ItemEffect.AddAffection]，社交层消费） */
        val affectionDelta: Int = 0,
        /** 孵化剩余时长缩短（[ItemEffect.ReduceHatchTime]） */
        val hatchMsReduced: Long = 0,
        /** 开箱保底档（[ItemEffect.GuaranteeRarity]，由 `BlindBoxEngine.draw` 消费并钳制） */
        val guaranteedRarity: Rarity? = null
    )

    /**
     * 把一组效果应用到 [pet] 上。
     *
     * @param pet 当前宠物；**若效果里有需要个体的项（孵化/经验/亲密度/属性/阶段）而传入 null，
     *   直接抛错** —— 静默忽略会制造"道具用了没反应"的诡异现场。
     * @param now 当前时间戳（孵化钳制用；可注入以便测试）
     */
    fun apply(effects: List<ItemEffect>, pet: Pet?, now: Long = System.currentTimeMillis()): Outcome {
        var p = pet
        var gold = 0
        var affection = 0
        var hatchMs = 0L
        var guarantee: Rarity? = null

        val needsPet = effects.any {
            it is ItemEffect.ReduceHatchTime || it is ItemEffect.AddExp ||
                it is ItemEffect.AddIntimacy || it is ItemEffect.AddAttribute ||
                it is ItemEffect.AdvanceStage
        }
        if (needsPet) requireNotNull(p) { "效果 $effects 需要作用在一只宠物上，但 pet == null" }

        for (e in effects) when (e) {
            is ItemEffect.ReduceHatchTime -> {
                // 不得早于出生：孵化时长只会被缩短，不会被减成"负龄"
                val clamped = (p!!.hatchAt - e.reduceMs).coerceAtLeast(p.bornAt)
                hatchMs += p.hatchAt - clamped
                p = p.copy(hatchAt = clamped)
            }
            is ItemEffect.AddExp -> {
                // ⚠️ 只加经验，不在这里升级：升级涉及属性差量重算（GrowthRules.AttributeRules.onLevelUp）
                // 与阶段推进，属于 UseCase 的循环职责 —— 在道具层顺手升级会绕过那些规则。
                p = p!!.copy(exp = p.exp + e.exp)
            }
            is ItemEffect.AddIntimacy -> {
                p = p!!.copy(attributes = p.attributes.copy(
                    intimacy = (p.attributes.intimacy + e.value).coerceIn(0, 100)
                ))
            }
            is ItemEffect.AddAttribute -> {
                p = p!!.copy(attributes = p.attributes.bump(e.attribute, e.value))
            }
            is ItemEffect.AdvanceStage -> {
                p = p!!.copy(stage = e.stage)
            }
            is ItemEffect.AddGold -> gold += e.amount
            is ItemEffect.AddAffection -> affection += e.value
            is ItemEffect.GuaranteeRarity -> guarantee = e.rarity
        }

        return Outcome(
            pet = p.takeIf { needsPet } ?: pet,
            walletGoldDelta = gold,
            affectionDelta = affection,
            hatchMsReduced = hatchMs,
            guaranteedRarity = guarantee
        )
    }

    private fun PetAttributes.bump(attr: AttributeType, value: Int): PetAttributes = when (attr) {
        AttributeType.STAMINA -> copy(stamina = (stamina + value).coerceIn(0, PetAttributes.MAX_ATTRIBUTE))
        AttributeType.STRENGTH -> copy(strength = (strength + value).coerceIn(0, PetAttributes.MAX_ATTRIBUTE))
        AttributeType.AGILITY -> copy(agility = (agility + value).coerceIn(0, PetAttributes.MAX_ATTRIBUTE))
        AttributeType.WISDOM -> copy(wisdom = (wisdom + value).coerceIn(0, PetAttributes.MAX_ATTRIBUTE))
        AttributeType.CHARM -> copy(charm = (charm + value).coerceIn(0, PetAttributes.MAX_ATTRIBUTE))
    }
}

/**
 * 玩具耐久规则（`G-08` 的修复点）。
 *
 * 此前 `ItemCategory.TOY` **一件道具都没有**、`InventoryItem.durability` 有字段无来源，
 * 「玩耍消耗玩具耐久」（00 §2.4）是一句悬空的承诺。
 */
object ToyRules {

    /** 每次玩耍消耗的耐久 */
    const val WEAR_PER_PLAY = 1

    /**
     * 玩耍一次后的剩余耐久。
     *
     * @return 耐久耗尽返回 `0`（道具保留但不可用，见 `InventoryItem.isUsable`）；
     *   非玩具（`durability == null`）原样返回 `null`。
     */
    fun wear(durability: Int?): Int? = durability?.let { (it - WEAR_PER_PLAY).coerceAtLeast(0) }
}
