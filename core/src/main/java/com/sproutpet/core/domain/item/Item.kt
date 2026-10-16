package com.sproutpet.core.domain.item

import com.sproutpet.core.domain.pet.PetStage

/**
 * 道具分类。决定道具在背包中的分组与使用场景校验。
 */
enum class ItemCategory(val displayName: String) {
    /** 孵化类：加速孵化、保底稀有度 */
    INCUBATION("孵化道具"),

    /** 食物类：喂食提升经验与亲密度 */
    FOOD("食物"),

    /** 玩具类：玩耍提升经验，有耐久 */
    TOY("玩具"),

    /** 训练类：定向提升属性 */
    TRAINING("训练道具"),

    /** 生长类：直接加经验或跳级 */
    GROWTH("成长道具"),

    /** 社交类：送礼、提升好感 */
    SOCIAL("社交道具"),

    /** 装饰类：皮肤、配饰 */
    DECORATION("装饰"),

    /** 消耗类：盲盒、体力药水 */
    CONSUMABLE("消耗品")
}

/**
 * 道具定义（静态配置，来自本地配置表 + 可被远程配置覆盖）。
 */
data class ItemDefinition(
    val id: String,
    val name: String,
    val description: String,
    val category: ItemCategory,
    val iconRes: String,

    /** 稀有度影响外观 */
    val rarity: com.sproutpet.core.domain.pet.Rarity = com.sproutpet.core.domain.pet.Rarity.COMMON,

    /** 是否可堆叠 */
    val stackable: Boolean = true,

    /** 单次使用效果 */
    val effects: List<ItemEffect> = emptyList(),

    /** 使用后是否消耗 */
    val consumable: Boolean = true,

    /** 获取途径说明（展示给用户） */
    val sources: List<ItemSource> = emptyList(),

    /** 是否可通过商店购买 */
    val shopPriceGold: Int? = null,

    /** 是否可通过钻石购买 */
    val shopPriceGem: Int? = null
)

/**
 * 道具效果。用密封类描述，便于扩展新效果类型而无需改动使用逻辑。
 */
sealed interface ItemEffect {

    /** 减少孵化剩余时间 */
    data class ReduceHatchTime(val reduceMs: Long) : ItemEffect

    /** 增加经验 */
    data class AddExp(val exp: Int) : ItemEffect

    /** 增加亲密度 */
    data class AddIntimacy(val value: Int) : ItemEffect

    /** 定点增加属性（体力也走这里 —— 见下方说明） */
    data class AddAttribute(val attribute: AttributeType, val value: Int) : ItemEffect

    /** 提升孵化保底稀有度（使用后开出的宠物稀有度不低于该值） */
    data class GuaranteeRarity(val rarity: com.sproutpet.core.domain.pet.Rarity) : ItemEffect

    /** 直接推进到指定阶段（用于"生长加速"高级道具） */
    data class AdvanceStage(val stage: PetStage) : ItemEffect

    /**
     * 增加金币。
     *
     * 📌 **曾经有一个 `RestoreStamina`，已删除。**
     * 体力（`stamina`）是**属性**而不是资源（见 [AttributeType.STAMINA]），
     * 所以"恢复体力"与"增加体力属性"是同一件事 —— 用 [AddAttribute] 表达即可，
     * 单独留一个效果类型只会让人以为存在"体力值"这个概念。
     */
    data class AddGold(val amount: Int) : ItemEffect

    /** 增加双方好感度（社交道具） */
    data class AddAffection(val value: Int) : ItemEffect
}

/**
 * 可提升的属性类型。
 *
 * ⚠️ **`STAMINA` 是属性，不是"体力值"资源**（这条曾长期含糊，见 docs/09 U-01）。
 * 它随等级/稀有度/倾向成长，作用是"提高每日互动次数上限"，
 * **没有"消耗体力""体力不足无法互动"这类机制** ——
 * 那会制造"现在不能玩"的负反馈，与「治愈陪伴」的定位冲突。
 * 防刷由 `GrowthRules.DAILY_EXP_CAP` 与各互动的 `dailyLimit` 负责，不靠体力门槛。
 */
enum class AttributeType(val displayName: String) {
    STAMINA("体力"),
    STRENGTH("力量"),
    AGILITY("敏捷"),
    WISDOM("智慧"),
    CHARM("魅力")
}

/** 道具获取途径 */
enum class ItemSource(val displayName: String) {
    BLIND_BOX("盲盒"),
    SHOP("商店"),
    ACTIVITY("活动"),
    DAILY_TASK("日常任务"),
    BATTLE_PASS("通行证"),
    SOCIAL_GIFT("社交赠送"),
    OFFLINE_REWARD("离线奖励"),
    ACHIEVEMENT("成就")
}

/**
 * 用户背包中的一个道具条目。
 */
data class InventoryItem(
    val itemId: String,
    val count: Int,
    /** 玩具类道具的剩余耐久，null 表示无耐久概念 */
    val durability: Int? = null,
    /** 获取时间 */
    val acquiredAt: Long = System.currentTimeMillis(),
    /** 过期时间戳，null 表示永不过期 */
    val expireAt: Long? = null
) {
    val isExpired: Boolean
        get() = expireAt != null && System.currentTimeMillis() > expireAt

    val isUsable: Boolean
        get() = count > 0 && !isExpired && (durability == null || durability > 0)
}

/**
 * 道具配置仓库：集中管理所有道具定义。
 *
 * 实际项目中应由 JSON 配置表驱动，此处先内置基础道具。
 */
object ItemRegistry {

    /** 全部道具定义 */
    val all: List<ItemDefinition> = listOf(
        // ===== 孵化类 =====
        ItemDefinition(
            id = "speed_up_hour",
            name = "沙漏碎片",
            description = "减少 1 小时孵化时间",
            category = ItemCategory.INCUBATION,
            iconRes = "ic_item_hourglass",
            effects = listOf(ItemEffect.ReduceHatchTime(60 * 60 * 1000L)),
            sources = listOf(ItemSource.DAILY_TASK, ItemSource.SHOP),
            shopPriceGold = 200
        ),
        ItemDefinition(
            id = "speed_up_day",
            name = "时光沙漏",
            description = "减少 24 小时孵化时间",
            category = ItemCategory.INCUBATION,
            iconRes = "ic_item_timeglass",
            rarity = com.sproutpet.core.domain.pet.Rarity.RARE,
            effects = listOf(ItemEffect.ReduceHatchTime(24 * 60 * 60 * 1000L)),
            sources = listOf(ItemSource.SHOP, ItemSource.ACTIVITY),
            shopPriceGold = 3_000
        ),
        ItemDefinition(
            id = "instant_hatch",
            name = "奇迹之羽",
            description = "立即完成孵化",
            category = ItemCategory.INCUBATION,
            iconRes = "ic_item_feather",
            rarity = com.sproutpet.core.domain.pet.Rarity.EPIC,
            effects = listOf(ItemEffect.ReduceHatchTime(Long.MAX_VALUE)),
            sources = listOf(ItemSource.SHOP, ItemSource.ACTIVITY),
            shopPriceGem = 100
        ),
        ItemDefinition(
            id = "guarantee_epic",
            name = "命运骰子",
            description = "使用后开出的宠物稀有度不低于史诗",
            category = ItemCategory.INCUBATION,
            iconRes = "ic_item_dice",
            rarity = com.sproutpet.core.domain.pet.Rarity.EPIC,
            effects = listOf(ItemEffect.GuaranteeRarity(com.sproutpet.core.domain.pet.Rarity.EPIC)),
            sources = listOf(ItemSource.SHOP, ItemSource.ACTIVITY),
            shopPriceGem = 300
        ),

        // ===== 食物类 =====
        ItemDefinition(
            id = "food_basic",
            name = "谷物饲料",
            description = "基础食物，+20 经验，+3 亲密度",
            category = ItemCategory.FOOD,
            iconRes = "ic_item_grain",
            effects = listOf(ItemEffect.AddExp(20), ItemEffect.AddIntimacy(3)),
            sources = listOf(ItemSource.SHOP, ItemSource.OFFLINE_REWARD),
            shopPriceGold = 50
        ),
        ItemDefinition(
            id = "food_premium",
            name = "营养便当",
            description = "高级食物，+60 经验，+8 亲密度",
            category = ItemCategory.FOOD,
            iconRes = "ic_item_bento",
            rarity = com.sproutpet.core.domain.pet.Rarity.RARE,
            effects = listOf(ItemEffect.AddExp(60), ItemEffect.AddIntimacy(8)),
            sources = listOf(ItemSource.SHOP),
            shopPriceGold = 200
        ),

        // ===== 训练类 =====
        ItemDefinition(
            id = "train_strength",
            name = "铁哑铃",
            description = "力量 +5",
            category = ItemCategory.TRAINING,
            iconRes = "ic_item_dumbbell",
            effects = listOf(ItemEffect.AddAttribute(AttributeType.STRENGTH, 5)),
            sources = listOf(ItemSource.SHOP, ItemSource.DAILY_TASK),
            shopPriceGold = 150
        ),
        ItemDefinition(
            id = "train_wisdom",
            name = "知识之书",
            description = "智慧 +5",
            category = ItemCategory.TRAINING,
            iconRes = "ic_item_book",
            effects = listOf(ItemEffect.AddAttribute(AttributeType.WISDOM, 5)),
            sources = listOf(ItemSource.SHOP, ItemSource.DAILY_TASK),
            shopPriceGold = 150
        ),

        // ===== 成长类 =====
        ItemDefinition(
            id = "exp_small",
            name = "成长果实",
            description = "+200 经验",
            category = ItemCategory.GROWTH,
            iconRes = "ic_item_fruit",
            effects = listOf(ItemEffect.AddExp(200)),
            sources = listOf(ItemSource.SHOP, ItemSource.ACTIVITY),
            shopPriceGold = 500
        ),

        // ===== 社交类 =====
        ItemDefinition(
            id = "gift_flower",
            name = "一束花",
            description = "赠送后双方好感度 +10",
            category = ItemCategory.SOCIAL,
            iconRes = "ic_item_flower",
            effects = listOf(ItemEffect.AddAffection(10)),
            sources = listOf(ItemSource.SHOP, ItemSource.SOCIAL_GIFT),
            shopPriceGold = 100
        ),

        // ===== 消耗品 =====
        ItemDefinition(
            id = "blind_box",
            name = "神秘盲盒",
            description = "开启后随机获得一只宠物蛋",
            category = ItemCategory.CONSUMABLE,
            iconRes = "ic_item_box",
            rarity = com.sproutpet.core.domain.pet.Rarity.RARE,
            sources = listOf(ItemSource.SHOP, ItemSource.ACTIVITY)
        )
    )

    private val map: Map<String, ItemDefinition> by lazy { all.associateBy { it.id } }

    fun find(id: String): ItemDefinition? = map[id]

    fun ofCategory(category: ItemCategory): List<ItemDefinition> =
        all.filter { it.category == category }
}
