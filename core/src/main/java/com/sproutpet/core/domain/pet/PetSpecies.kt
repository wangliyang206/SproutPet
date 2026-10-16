package com.sproutpet.core.domain.pet

/**
 * 宠物物种。每个物种有：
 * - 独立的美术资源与体型曲线
 * - 属性倾向（有的偏力量、有的偏智慧）
 * - 专属互动动作与性格关键词
 * - 所属「五虫」类群（见 [SpeciesFamily]）
 *
 * 新增物种 = 新增一个枚举项 + 一套 Rive 资源 + 一行注册，
 * 不需要改动任何业务逻辑代码。
 *
 * ## 分类体系源自《西游记》第五十八回如来原话
 *
 * > 「周天之内有五仙：乃天、地、神、人、鬼。**有五虫：乃蠃、鳞、毛、羽、昆**。
 * > 这厮非天、非地、非神、非人、非鬼，亦非蠃、非鳞、非毛、非羽、非昆。
 * > **又有四猴混世，不入十类之种**。」
 *
 * 据此建立三级结构：
 * 1. **五虫**（蠃/鳞/毛/羽/昆）= 五大类群，对应可抽取的常规物种池
 * 2. **五虫之长** = 神兽/灵兽（凤凰、麒麟、蛟龙、神龟、圣人），作为高稀有档的形象
 * 3. **不入十类之种** = 异兽（混世四猴式），**不可抽取**，只能通过混血获得
 *
 * 当前共 15 个可抽取物种（五虫各 3 个）+ 3 个不可抽异兽 = 18 个物种。
 *
 * @see docs/06-物种与稀有度设计.md
 * @see docs/07-五虫体系设计.md
 */
enum class PetSpecies(
    val id: String,
    val displayName: String,
    /** 属性倾向：影响成长时各属性的加成 */
    val tendency: AttributeTendency,
    /** 所属五虫类群，用于「族谱」归类与跨虫配对规则 */
    val family: SpeciesFamily,
    /** 性格关键词，供 UI 展示与文案库使用 */
    val personality: String,
    /**
     * 是否可在盲盒池中抽取。
     * 不入十类之种（异兽）不可抽，只能通过跨虫混血获得 —— 它们是长线追求目标。
     */
    val isDrawable: Boolean = true
) {
    // ═══════════ 蠃虫 LUO（倮虫）：无毛羽鳞甲的裸露生物 ═══════════
    // 现代对应：水生软体、两栖。取"软萌、慢、黏"的气质。

    /** 蜗蜗：蜗牛，慢吞吞、背着家走 */
    SNAIL("snail", "蜗蜗", AttributeTendency.BALANCED, SpeciesFamily.LUO, "慢、黏人"),

    /** 泡泡蛙：蛙类，蹦跳、爱叫 */
    BUBBLE_FROG("bubble_frog", "泡泡蛙", AttributeTendency.STRENGTH, SpeciesFamily.LUO, "跳脱、爱叫"),

    /** 软软章：章鱼，多手、聪明 */
    SOFT_OCTOPUS("soft_octopus", "软软章", AttributeTendency.WISDOM, SpeciesFamily.LUO, "聪明、多手"),

    // ═══════════ 鳞虫 LIN：有鳞者（鱼、蛇、龙） ═══════════

    /** 锦鲤鲤：锦鲤，慢游、象征好运 */
    KOI("koi", "锦鲤鲤", AttributeTendency.BALANCED, SpeciesFamily.LIN, "悠然、好运"),

    /** 小青蛇：蛇，安静、却灵动 */
    GRASS_SNAKE("grass_snake", "小青蛇", AttributeTendency.AGILITY, SpeciesFamily.LIN, "安静、灵动"),

    /** 小龙崽：幼龙，力量型、爱炫 */
    LITTLE_DRAGON("little_dragon", "小龙崽", AttributeTendency.STRENGTH, SpeciesFamily.LIN, "爱炫、自信"),

    // ═══════════ 毛虫 MAO：有毛者（兽） ═══════════

    /** 瞌睡猫：慵懒、傲娇 */
    SLEEPY_CAT("sleepy_cat", "瞌睡猫", AttributeTendency.BALANCED, SpeciesFamily.MAO, "慵懒、傲娇"),

    /** 绒绒犬：忠诚、热情 */
    FLUFF_DOG("fluff_dog", "绒绒犬", AttributeTendency.STRENGTH, SpeciesFamily.MAO, "忠诚、热情"),

    /** 疾风猫：警觉、敏捷 */
    SWIFT_CAT("swift_cat", "疾风猫", AttributeTendency.AGILITY, SpeciesFamily.MAO, "警觉、敏捷"),

    // ═══════════ 羽虫 YU：有羽者（鸟、禽） ═══════════

    /** 萌芽鸡：初始物种，均衡、憨 */
    SPROUT_CHICKEN("sprout_chicken", "萌芽鸡", AttributeTendency.BALANCED, SpeciesFamily.YU, "憨、好奇"),

    /** 元气鸡：活泼、话多 */
    VIGOR_CHICKEN("vigor_chicken", "元气鸡", AttributeTendency.AGILITY, SpeciesFamily.YU, "活泼、话多"),

    /** 学者鸡：安静、爱看书 */
    SCHOLAR_CHICKEN("scholar_chicken", "学者鸡", AttributeTendency.WISDOM, SpeciesFamily.YU, "安静、爱看书"),

    // ═══════════ 昆虫 KUN：有甲者（虫、蜂、龟） ═══════════

    /** 蜜蜜蜂：蜜蜂，勤劳、爱干活 */
    HONEY_BEE("honey_bee", "蜜蜜蜂", AttributeTendency.AGILITY, SpeciesFamily.KUN, "勤劳、爱干活"),

    /** 圆壳龟：龟，沉稳、长寿 */
    SHELL_TURTLE("shell_turtle", "圆壳龟", AttributeTendency.STRENGTH, SpeciesFamily.KUN, "沉稳、迟钝"),

    /** 闪闪萤：萤火虫，发光、神秘 */
    GLOW_BUG("glow_bug", "闪闪萤", AttributeTendency.WISDOM, SpeciesFamily.KUN, "发光、神秘"),

    // ═══════════ 不入十类之种：异兽（不可抽取） ═══════════
    // 「又有四猴混世，不入十类之种」—— 它们不属任何一虫，只能靠混血诞生。

    /** 星狐：神秘聪慧，跨虫混血触发 */
    STAR_FOX("star_fox", "星狐", AttributeTendency.WISDOM, SpeciesFamily.UNCATEGORIZED, "神秘、聪慧", isDrawable = false),

    /** 月兔：安静轻盈，跨虫混血触发 */
    MOON_RABBIT("moon_rabbit", "月兔", AttributeTendency.AGILITY, SpeciesFamily.UNCATEGORIZED, "安静、轻盈", isDrawable = false),

    /** 荧波：梦幻漂浮，异兽同族配对触发 */
    GLOW_JELLY("glow_jelly", "荧波", AttributeTendency.BALANCED, SpeciesFamily.UNCATEGORIZED, "梦幻、漂浮", isDrawable = false);

    companion object {
        fun fromId(id: String): PetSpecies =
            entries.firstOrNull { it.id == id } ?: SPROUT_CHICKEN

        /** 可从盲盒池抽取的物种 */
        fun drawableEntries(): List<PetSpecies> = entries.filter { it.isDrawable }

        /** 按五虫类群分组 */
        fun byFamily(family: SpeciesFamily): List<PetSpecies> =
            entries.filter { it.family == family }

        /** 五虫类群（不含「不入十类」） */
        fun wormFamilies(): List<SpeciesFamily> = SpeciesFamily.entries.filter { it.isWorm }
    }
}

/** 属性倾向 */
enum class AttributeTendency(val displayName: String) {
    BALANCED("均衡"),
    STRENGTH("力量"),
    AGILITY("敏捷"),
    WISDOM("智慧")
}

/**
 * 五虫类群。源自《西游记》第五十八回如来原话：「有五虫，乃蠃、鳞、毛、羽、昆」。
 *
 * 同虫配对无惩罚；跨虫配对成功率高、且更容易产出混血异兽。
 *
 * 每虫各有其「长」（《大戴礼记·易本命》），即神兽，见 [Deity]。
 *
 * ⚠️ 历史数据兼容：v2.0 早期版本使用 `BIRD`/`MAMMAL`/`OVINE`/`EXOTIC`，
 * 现已并入五虫体系。旧常量保留为**兼容别名**，请勿在新代码中使用；
 * 读取旧数据请走 [fromName]。
 */
enum class SpeciesFamily(
    val displayName: String,
    /** 该虫的「长」（神兽），见《大戴礼记·易本命》 */
    val deity: String,
    /** 该虫的释义，供 UI 展示 */
    val definition: String
) {
    /** 蠃虫：倮虫，无毛羽鳞甲者（通「裸」）。今指水生软体、两栖 */
    LUO("蠃虫", "圣人", "倮虫，无毛羽鳞甲者"),

    /** 鳞虫：有鳞者（鱼、蛇、龙） */
    LIN("鳞虫", "蛟龙", "有鳞者，鱼蛇之属"),

    /** 毛虫：有毛者（兽） */
    MAO("毛虫", "麒麟", "有毛者，走兽之属"),

    /** 羽虫：有羽者（鸟、禽） */
    YU("羽虫", "凤凰", "有羽者，飞禽之属"),

    /** 昆虫：有甲者（虫、蜂、龟） */
    KUN("昆虫", "神龟", "有甲者，虫豸之属"),

    /**
     * 不入十类之种：如来所说「又有四猴混世，不入十类之种」。
     * 不属任何一虫，**不可抽取**，只能通过跨虫混血诞生。
     */
    UNCATEGORIZED("不入十类", "—", "非蠃非鳞非毛非羽非昆");

    /** 是否为五虫之一（不含「不入十类」） */
    val isWorm: Boolean get() = this == LUO || this == LIN || this == MAO || this == YU || this == KUN

    /** 是否为可抽取类群（五虫可抽，「不入十类」不可抽） */
    val isDrawable: Boolean get() = isWorm

    /**
     * 该虫是否**有可具象化的神兽形态**。
     *
     * ⚠️ 蠃虫返回 `false` —— 见 ADR-020。
     *
     * 《大戴礼记·易本命》：「倮虫三百六十，而**圣人**为之长」。
     * 蠃虫（倮虫）之长是「人」，而神兽形态是会被玩家穿戴、展示、攀比的外观皮。
     * 把「圣人」做成可抽取、可比较的皮肤，属于结构性的文化风险，故**不设形象**。
     *
     * **这不是"没做"，而是"依据典籍不设"**：蠃虫的神兽槽位在图鉴中显示为云雾留白，
     * 配文「蠃虫之长，其名曰人。此位不设形。」—— 留白即科普入口。
     *
     * ❌ **反向红线**：不得为蠃虫补拟人化/萌化的替代神兽（如"螺贝之灵"）。
     * 替代品会把"基于典籍的立场"降级为"自我审查"，对外解释力完全不同。
     */
    val hasDeityForm: Boolean get() = isWorm && this != LUO

    companion object {
        /** 五虫（按如来原话顺序：蠃鳞毛羽昆） */
        val FIVE_WORMS: List<SpeciesFamily> = listOf(LUO, LIN, MAO, YU, KUN)

        /**
         * 解析持久化的类群名，兼容历史数据。
         *
         * ⚠️ **不使用枚举别名常量**：早期曾把 BIRD/MAMMAL/EXOTIC 加为枚举项，
         * 这会产生"幽灵类群"污染 [entries]，导致 [FIVE_WORMS] 计数与遍历出错。
         * 正确做法是**只在解析层做字符串映射**，枚举本体保持干净。
         *
         * 映射关系：
         * - v1.0：FELINE → 毛虫
         * - v2.0 早期：BIRD → 羽虫；MAMMAL / OVINE / FELINE → 毛虫；EXOTIC → 不入十类
         */
        fun fromName(name: String): SpeciesFamily = when (name) {
            "BIRD", "禽类" -> YU
            "MAMMAL", "兽类", "OVINE", "羊类", "FELINE", "猫科" -> MAO
            "EXOTIC", "异种" -> UNCATEGORIZED
            else -> entries.firstOrNull { it.name == name } ?: YU
        }
    }
}
