package com.sproutpet.core.domain.pet

/**
 * 融合规则 —— 解决「重复宠物没有去处」。
 *
 * ## 它和「生育」是两套独立机制，别混
 *
 * | 机制 | 一句话 | 消耗 | 产出 | 定位 |
 * |------|-------|------|------|------|
 * | **生育**（[GrowthRules.BreedingRules]） | 两只宠生一个孩子 | 金币 + 冷却 | **新个体**（1 个后代） | 繁衍 |

 * | **融合**（本文件） | 把重复的那只"喂给"要留下的那只 | 一只副宠 + 金币 | **目标宠变强**（稀有度上漂） | 自我强化 |
 *
 * 二者产出的方向完全不同：生育是**横向铺开**（家族变大），融合是**纵向加深**（个体变强）。
 *
 * ## 三条硬规则
 *
 * 1. **必须同物种** —— 羽虫只能喂羽虫。跨物种融合会让"物种"这个轴失去意义，
 *    也会让玩家为了融合去凑毫不相干的宠物，破坏收集感。
 * 2. **副宠「入后山」，不消失** —— 它退出桌面与配对，但**保留在族谱与图鉴记录里**。
 *    这是情感产品的底线：不能真的"吃掉"一只玩家养过的宠物。
 *    但**不可取回** —— 不可逆是这份取舍的分量所在，所以 UI 必须二次确认。
 * 3. **灵兽永远不能由融合产生**（ADR-017 红线）—— 见下。
 *
 * ## 印记与上漂
 *
 * 每次融合给目标宠积累**融合印记**，印记数量按副宠稀有度加权（[markWeightOf]）：

 * ```
 * 普通 1 · 稀有 2 · 史诗 4 · 传说 8 · 神话 16
 * ```
 *
 * 印记累积到**当前稀有度对应的阈值**（[thresholdFor]）时，目标宠**上漂一档**：

 * ```
 * 普通 →(5) 稀有 →(8) 史诗 →(12) 传说 →(20) 神话
 * ```
 *
 * 两点设计意图：
 * - **向上不向下**：融合只会让目标变好，永远不会掉档，避免"我投入了却变差"的挫败。
 * - **溢出保留**：上漂后剩余印记**留到下一档**，玩家前期的投入不会被清零白费。
 *
 * ## ❌ 两条例外（都不能融合）
 *
 * - **灵兽不可作为材料**，也**不可作为目标**：灵兽是终极目标，
 *   把它当消耗品会制造严重的不可逆后悔，而且印记上限也只到神话，本就没有收益。
 * - **神话不可作为目标**：没有更高档可去（[thresholdFor] 返回 `null`）。
 *   直接拒绝，避免玩家白白损失一只副宠。
 *
 * ## ❌ 为什么**不**给属性面板加成
 *
 * 早期设计曾考虑"融合提升属性（递减收益）"。最终**放弃**，原因：
 * 那会造成**同稀有度、同物种的两只宠战力不等价**，玩家会得出"我这只不如别人的"结论 ——
 * 这与 [GrowthRules.ExoticBloodRules] 的红线同源（血统/外观不该制造数值优劣）。
 *
 * 融合的即时反馈改为**给目标经验**（[expGainedPerMaterialLevel] × 副宠等级），
 * 再叠加可见的印记进度条与上漂揭晓动画。经验是通用资源，不会制造"劣等同稀有度"。
 *
 * @see docs/10-融合机制设计.md
 */
object FusionRules {

    /** 融合消耗金币（少量，防止零成本刷印记） */
    const val costGold = 200

    /** 每级副宠折算给目标宠的经验 */
    const val expGainedPerMaterialLevel = 50

    /**
     * 副宠稀有度 → 融合印记数量。
     *
     * 按 2 的幂加权，让"高稀有副宠"的贡献显著（神话 16 = 16 只普通），
     * 从而形成"越稀有越舍不得喂、但喂了回报越大"的取舍张力。
     *
     * 返回 `null` 表示**该稀有度不可作为材料**。
     */
    fun markWeightOf(rarity: Rarity): Int? = when (rarity) {
        Rarity.COMMON -> 1
        Rarity.RARE -> 2
        Rarity.EPIC -> 4
        Rarity.LEGENDARY -> 8
        Rarity.MYTHIC -> 16
        // ❌ 灵兽不可作为材料（ADR-017 精神的延伸，见文件头说明）
        Rarity.MUTANT -> null
    }

    /**
     * 目标宠从**当前稀有度**上漂一档所需的印记阈值。
     *
     * 返回 `null` 表示**已封顶**（神话），不能再上漂 —— 这也正是
     * 「融合永远产不出灵兽」的实现方式：分布表里神话之上没有可去的档位。
     */
    fun thresholdFor(rarity: Rarity): Int? = when (rarity) {
        Rarity.COMMON -> 5
        Rarity.RARE -> 8
        Rarity.EPIC -> 12
        Rarity.LEGENDARY -> 20
        Rarity.MYTHIC -> null
        Rarity.MUTANT -> null
    }

    /** 融合被拒绝的原因 */
    enum class RejectReason(val message: String) {
        /** 与目标不是同一物种 */
        SPECIES_MISMATCH("只能与同物种的宠物融合"),

        /** 试图把灵兽当材料 */
        MATERIAL_IS_MUTANT("灵兽太珍贵了，不能作为融合材料"),

        /** 目标是灵兽 */
        TARGET_IS_MUTANT("灵兽已是最高档，无法再融合"),

        /** 目标已是神话（封顶） */
        TARGET_AT_CAP("神话已是融合的终点，副宠会白白损失"),

        /** 把自己喂给自己 */
        SAME_PET("不能与自己融合"),

        /** 目标未破壳 */
        TARGET_NOT_HATCHED("只有已破壳的宠物才能融合"),

        /** 副宠未破壳 */
        MATERIAL_NOT_HATCHED("宠物还在蛋里，不能作为融合材料")
    }

    /** 融合是否可行；可行时 `rejectReason` 为 `null` */
    data class FusionCheck(val allowed: Boolean, val rejectReason: RejectReason? = null) {
        companion object {
            val OK = FusionCheck(allowed = true)
            fun reject(reason: RejectReason) = FusionCheck(allowed = false, rejectReason = reason)
        }
    }

    /**
     * 判定一次融合是否允许。
     *
     * 检查顺序按"越早拒绝越省事"排列，保证返回的原因是最直观的那个。
     */
    fun check(target: Pet, material: Pet): FusionCheck {
        if (target.id == material.id) return FusionCheck.reject(RejectReason.SAME_PET)

        // 蛋不能参与 —— 副宠要入后山，至少得是一只"活过的"宠物
        if (!target.isHatched) return FusionCheck.reject(RejectReason.TARGET_NOT_HATCHED)

        // 灵兽两侧都禁
        if (target.rarity == Rarity.MUTANT) return FusionCheck.reject(RejectReason.TARGET_IS_MUTANT)
        if (material.rarity == Rarity.MUTANT) return FusionCheck.reject(RejectReason.MATERIAL_IS_MUTANT)

        // 封顶档直接拒绝，而不是"允许但无效果" —— 免得玩家损失副宠
        if (thresholdFor(target.rarity) == null) return FusionCheck.reject(RejectReason.TARGET_AT_CAP)

        if (target.species != material.species) return FusionCheck.reject(RejectReason.SPECIES_MISMATCH)

        // 这一条放最后：它最不常发生，而且玩家看到"蛋不能喂"比看到"物种不符"更困惑
        if (!material.isHatched) return FusionCheck.reject(RejectReason.MATERIAL_NOT_HATCHED)

        return FusionCheck.OK
    }

    /**
     * 融合结算结果。
     *
     * @param targetRarity 融合后的目标稀有度（上漂一档或不变）
     * @param targetMarks 融合后目标留在**当前档位**的印记（溢出已自动保留到新档）
     * @param drifted 本次是否触发上漂（用于播揭晓动画）
     * @param expGained 目标获得的经验
     * @param overflowWasted 因封顶而无处可去的印记（正常流程恒为 0，因为封顶档已被 [check] 拒绝）
     */
    data class FusionResult(
        val targetRarity: Rarity,
        val targetMarks: Int,
        val drifted: Boolean,
        val expGained: Int,
        val overflowWasted: Int = 0
    )

    /**
     * 结算一次融合（**纯函数，不修改入参**）。
     *
     * 调用方必须先过 [check]。本函数假定输入合法，只做算术：
     * 加印记 → 若越过阈值则上漂一档并把溢出印记带到新档 → 结算经验。
     *
     * @param target 留下的那只（要被强化的）
     * @param targetMarks 目标当前的印记数（持久化在宠物身上）
     * @param material 被消耗的那只（「入后山」）
     */
    fun resolve(target: Pet, targetMarks: Int, material: Pet): FusionResult {
        val incoming = markWeightOf(material.rarity) ?: 0
        var rarity = target.rarity
        var marks = targetMarks.coerceAtLeast(0) + incoming
        var drifted = false
        var wasted = 0

        // 用 while 而不是 if：单次融合理论上可能连跨两档（如 神话副宠 16 印记喂给普通目标）
        while (true) {
            val threshold = thresholdFor(rarity) ?: break
            if (marks < threshold) break
            val next = Rarity.fromId(rarity.id + 1)
            if (next == rarity || next == Rarity.MUTANT) {
                // 防御：绝不允许走到灵兽档（ADR-017 红线），剩余的印记作废并记录
                wasted = marks
                marks = threshold
                break
            }
            marks -= threshold
            rarity = next
            drifted = true
        }

        return FusionResult(
            targetRarity = rarity,
            targetMarks = marks,
            drifted = drifted,
            expGained = material.level.coerceAtLeast(0) * expGainedPerMaterialLevel,
            overflowWasted = wasted
        )
    }

    /** 该稀有度能否作为融合目标（即是否存在更高档可去） */
    fun canBeTarget(rarity: Rarity): Boolean = thresholdFor(rarity) != null

    /** 该稀有度能否作为融合材料 */
    fun canBeMaterial(rarity: Rarity): Boolean = markWeightOf(rarity) != null
}
