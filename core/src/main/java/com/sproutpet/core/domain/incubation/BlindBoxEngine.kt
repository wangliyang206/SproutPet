package com.sproutpet.core.domain.incubation

import com.sproutpet.core.domain.gacha.GachaRules
import com.sproutpet.core.domain.pet.AttributeTendency
import com.sproutpet.core.domain.pet.GrowthRules
import com.sproutpet.core.domain.pet.Pet
import com.sproutpet.core.domain.pet.PetAttributes
import com.sproutpet.core.domain.pet.PetGender
import com.sproutpet.core.domain.pet.PetGenes
import com.sproutpet.core.domain.pet.PetSpecies
import com.sproutpet.core.domain.pet.PetStage
import com.sproutpet.core.domain.pet.Rarity
import kotlin.random.Random

/**
 * 盲盒开蛋结果。
 */
data class BlindBoxResult(
    val species: PetSpecies,
    val rarity: Rarity,
    val genes: PetGenes,
    /** 本次使用的保底道具（若有） */
    val guaranteedBy: String? = null,
    /** 是否触发了幸运加成 */
    val isLucky: Boolean = false,
    /**
     * 是否由「十连保稀有」兜底提升而来（[BlindBoxEngine.drawMany]）。
     *
     * 用于文案："这一只本来可能是普通"。**不能与 [isLucky] 合并** ——
     * 前者是"整批的最低保障"，后者是"单抽撞上了保底"，触发条件与话术都不同。
     */
    val flooredByBatch: Boolean = false
)

/**
 * 盲盒与孵化核心算法。
 *
 * 这是整个养成体系的入口，纯函数实现（除随机数外无副作用），
 * 因此可以完整地做单元测试。
 */
object BlindBoxEngine {

    /** 保底机制：连续 N 次未出史诗及以上，则下次必出 */
    const val PITY_THRESHOLD = 30

    /**
     * 距离下一次保底还剩几抽（供 UI 显示"距史诗保底还差 12 抽"）。
     *
     * ⚠️ 这条 helper 曾经**不存在** —— 而灵兽保底早有对称的
     * `MutantPityRules.remainingToThreshold`。PRD §2.1 承诺保底进度"对用户可见"，
     * 却没有能算出这个数的函数，做 UI 那天只能自己写一遍（于是就有了第二个真相源）。
     *
     * 语义：入参是**当前已连续未出高稀有的次数**，返回**还需要再抽几抽**才会触发保底。
     * `pityCount = 0` → 30 抽；`pityCount = 18` → "还差 12 抽"。
     */
    fun remainingToPity(pityCount: Int): Int =
        (PITY_THRESHOLD - pityCount).coerceAtLeast(0)

    /**
     * 抽取宠物蛋。
     *
     * @param pityCount 当前保底计数（连续未出高稀有的次数）
     * @param guaranteedRarity 保底道具指定的最低稀有度，null 表示无。
     *                         ⚠️ 会被自动**钳制**到 [Rarity.maxDrawable]，**无法指定灵兽**
     *                         —— 防止有人用"灵兽保底券"绕开 ADR-017。
     * @param pool 可抽取的物种池，null 表示全部**可抽取**物种
     * @param random 随机源。默认 [Random.Default]；测试注入固定种子即可复现。
     * @return 抽取结果，同时返回新的保底计数
     */
    fun draw(
        pityCount: Int = 0,
        guaranteedRarity: Rarity? = null,
        pool: List<PetSpecies>? = null,
        random: Random = Random.Default
    ): DrawOutcome {
        // 盲盒池只含可抽取物种（「不入十类之种」只能靠混血获得，不参与抽取）
        val defaultPool = PetSpecies.drawableEntries()
        val speciesPool = (pool ?: defaultPool)
            .filter { it.isDrawable }
            .ifEmpty { defaultPool }

        // ⚠️ 安全钳制：保底指定档位**不得高于「可抽取的最高档」**。
        // 若有人传 Rarity.MUTANT（灵兽），会被压到 MYTHIC ——
        // 否则一张"灵兽保底券"就能绕开 ADR-017（灵兽只能遗传、不可抽取）。
        val cappedGuarantee = guaranteedRarity?.let {
            if (it.isDrawable) it else Rarity.maxDrawable
        }

        // 1. 先决定稀有度
        val pityTriggered = pityCount + 1 >= PITY_THRESHOLD
        val rarity = when {
            cappedGuarantee != null -> {
                // 保底道具：不低于指定稀有度
                val rolled = rollRarity(random)
                if (rolled.id >= cappedGuarantee.id) rolled else cappedGuarantee
            }
            pityTriggered -> {
                // 保底触发：至少史诗
                val rolled = rollRarity(random)
                if (rolled.isHighRarity) rolled else Rarity.EPIC
            }
            else -> rollRarity(random)
        }

        // 2. 再决定物种（高稀有度下，稀有种族出现概率提升）
        val species = rollSpecies(speciesPool, rarity, random)

        // 3. 生成基因
        val genes = PetGenes.random()

        val isNewPityCount = if (rarity.isHighRarity) 0 else pityCount + 1

        return DrawOutcome(
            result = BlindBoxResult(
                species = species,
                rarity = rarity,
                genes = genes,
                guaranteedBy = if (cappedGuarantee != null) "guarantee" else null,
                isLucky = pityTriggered
            ),
            newPityCount = isNewPityCount
        )
    }

    /**
     * 连抽（多连的核心 API）。
     *
     * ## 为什么必须由引擎来管"十连保底"，而不是让调用方拼十次 [draw]
     *
     * 本项目有一条反复出现的事故模式：**"规则写好了，但没有任何人调用它"**
     * （`isFirstPet` 漏传、`MAX_FOCUS_BOOST_RATIO` 无人读取）。如果"十连必出稀有"
     * 由 UI 层自己拼，那么换一个入口（活动页、跳过动画的快速连抽）就会漏掉它。
     * 所以兜底**写在这里**，调用方没有"忘了加"的机会。
     *
     * ## 规则
     *
     * - 逐抽调用 [draw]，**保底计数在批内连续传递**（十连不是一个"保底重置点"）；
     * - 当 `count >= GachaRules.TEN_PULL` 且整批**一只稀有及以上都没有**时，
     *   把**最后一只**提升为 [GachaRules.TEN_PULL_FLOOR]（稀有）。
     * - ⚠️ 兜底**不回滚保底计数** —— 提升发生在计数结算之后，
     *   计数仍按"刚才那抽是普通的"来累计，**对玩家只会更有利，不会更差**。
     *
     * @param count 本批抽数，必须 `1..GachaRules.MAX_BATCH`（只做十连，见 `GachaRules.MAX_BATCH`）
     * @throws IllegalArgumentException 抽数越界 —— 显式失败优于静默支持 50 连
     */
    fun drawMany(
        count: Int,
        pityCount: Int = 0,
        guaranteedRarity: Rarity? = null,
        pool: List<PetSpecies>? = null,
        random: Random = Random.Default
    ): MultiDrawOutcome {
        require(count >= 1) { "连抽次数必须 ≥ 1，收到 $count" }
        require(count <= GachaRules.MAX_BATCH) {
            "单次最多连抽 ${GachaRules.MAX_BATCH} 次（只做十连），收到 $count"
        }

        val results = ArrayList<BlindBoxResult>(count)
        var pity = pityCount
        repeat(count) {
            val outcome = draw(pity, guaranteedRarity, pool, random)
            results += outcome.result
            pity = outcome.newPityCount
        }

        val needsFloor = count >= GachaRules.TEN_PULL &&
            results.none { it.rarity.id >= GachaRules.TEN_PULL_FLOOR.id }
        if (needsFloor) {
            val last = results.lastIndex
            results[last] = results[last].copy(
                rarity = GachaRules.TEN_PULL_FLOOR,
                flooredByBatch = true
            )
        }

        return MultiDrawOutcome(
            results = results,
            newPityCount = pity,
            floorApplied = needsFloor
        )
    }

    /**
     * 按权重随机稀有度。
     * 使用加权随机保证长期分布符合策划预期。
     *
     * 注意：**灵兽（MUTANT）权重为 0，不可抽取**，只能通过配对生育（含灵血传承）获得。
     */
    fun rollRarity(random: Random = Random.Default): Rarity {
        val drawable = Rarity.drawableEntries()
        val total = drawable.sumOf { it.baseWeight }
        if (total <= 0) return Rarity.COMMON
        var roll = random.nextInt(total)
        for (r in drawable) {
            roll -= r.baseWeight
            if (roll < 0) return r
        }
        return Rarity.COMMON
    }

    /**
     * 按稀有度加权抽取物种。
     * 高稀有度时，更倾向抽到"稀有种族"（此处简化为按种族索引偏移）。
     */
    private fun rollSpecies(pool: List<PetSpecies>, rarity: Rarity, random: Random): PetSpecies {
        if (pool.size == 1) return pool.first()
        // 高稀有度时引入轻微偏移，让结果更有"惊喜感"
        val bias = if (rarity.isHighRarity) 1 else 0
        val index = (random.nextInt(pool.size) + bias * random.nextInt(pool.size)) % pool.size
        return pool[index]
    }

    /**
     * 根据抽取结果创建一只处于「蛋」阶段的宠物。
     *
     * 性别在这里**随机分配**（[PetGender.random]），此后永不改变 ——
     * 落在"开蛋"而非"破壳"这一刻，是为了让中途重启 / 迁移都不会让性别漂移。
     *
     * @param ownerId 主人 ID
     * @param batchNo 盲盒批次号
     * @param isFirstPet 是否为该用户的**首只宠物**。首只额外缩短孵化时长，
     *                   保证当天就能玩到（ADR-013 首日体验优先）。
     *                   ⚠️ 调用方**必须显式传 `true`**，默认值是 `false` ——
     *                   这里没有"自动判断是否首只"的能力（那需要查库）。
     *                   曾经因为调用方漏传，`hatchDurationOf(isFirstPet)` 的减半
     *                   分支从未被执行过（文档承诺过但实际没生效）。
     */
    fun createEgg(
        ownerId: Long,
        result: BlindBoxResult,
        batchNo: String,
        isFirstPet: Boolean = false,
        now: Long = System.currentTimeMillis(),
        random: Random = Random.Default
    ): Pet {
        val hatchDuration = GrowthRules.hatchDurationOf(result.rarity, isFirstPet)
        return Pet(
            id = 0L, // 由数据库自增分配
            ownerId = ownerId,
            nickname = defaultNickname(result.species, result.rarity),
            species = result.species,
            rarity = result.rarity,
            stage = PetStage.EGG,
            level = 0,
            exp = 0,
            attributes = PetAttributes(
                stamina = 0, strength = 0, agility = 0,
                wisdom = 0, charm = 0, intimacy = 0
            ),
            genes = result.genes,
            bornAt = now,
            hatchAt = now + hatchDuration,
            isHatched = false,
            spouseId = null,
            familyId = null,
            parentIdA = null,
            parentIdB = null,
            batchNo = batchNo,
            gender = PetGender.random(random)
        )
    }

    /**
     * 破壳：把蛋转化为幼崽，并初始化属性。
     *
     * 属性按「等级 1 + 稀有度系数 + 种族倾向」计算。
     */
    fun hatch(pet: Pet, now: Long = System.currentTimeMillis()): Pet {
        val tendency = pet.species.tendency
        return pet.copy(
            stage = PetStage.BABY,
            level = 1,
            exp = 0,
            attributes = PetAttributes.ofLevel(1, pet.rarity, tendency),
            isHatched = true,
            hatchAt = now
        )
    }

    /** 默认昵称生成 */
    private fun defaultNickname(species: PetSpecies, rarity: Rarity): String =
        "${species.displayName}·${rarity.displayName}"
}

/**
 * 抽取结果与更新后的保底计数。
 */
data class DrawOutcome(
    val result: BlindBoxResult,
    val newPityCount: Int
)

/**
 * 连抽（[BlindBoxEngine.drawMany]）的结果。
 *
 * @param results 逐抽结果，**顺序即动画顺序**
 * @param newPityCount 整批结束后的保底计数
 * @param floorApplied 本批是否触发了「十连保稀有」兜底（文案用：这一批的最低保障生效了）
 */
data class MultiDrawOutcome(
    val results: List<BlindBoxResult>,
    val newPityCount: Int,
    val floorApplied: Boolean = false
)

/**
 * 孵化状态计算。
 */
object IncubationEngine {

    /**
     * 应用加速道具后的新孵化完成时间。
     *
     * @param currentHatchAt 当前孵化完成时间戳
     * @param reduceMs 减少的毫秒数（Long.MAX_VALUE 表示立即完成）
     * @param now 当前时间
     */
    fun applySpeedUp(
        currentHatchAt: Long,
        reduceMs: Long,
        now: Long = System.currentTimeMillis()
    ): Long {
        if (reduceMs == Long.MAX_VALUE) return now
        return (currentHatchAt - reduceMs).coerceAtLeast(now)
    }

    /**
     * 计算自然加速：宠物在「陪伴专注」时孵化会加快。
     *
     * 设计意图：把"等待"变成"陪伴"，提升留存。
     *
     * ⚠️ **抵扣比例只有一处定义**（`GrowthRules.FOCUS_BOOST_RATIO`）。
     * 本文件曾私自定义过一个同值的 `private const FOCUS_BOOST_RATE`，
     * 属于"同一数值两处定义"—— 改一处忘一处就会静默不一致，已删除。
     *
     * @param focusMs 本次陪伴时长
     * @return 可抵扣的孵化时间
     */
    fun focusBoostMs(focusMs: Long): Long = (focusMs * GrowthRules.FOCUS_BOOST_RATIO).toLong()

    /**
     * 计算自然加速，并施加**单颗蛋的抵扣上限**。
     *
     * ⚠️ 这是 `GrowthRules.MAX_FOCUS_BOOST_RATIO`（"每颗蛋最多抵扣 50%"）的**唯一执法点**。
     * 该常量此前**从未被任何代码读取** —— 也就是 PRD §2.2 承诺的上限形同不存在，
     * 玩家理论上可以用"专注陪伴"把整颗蛋的孵化时间全部抵掉。
     *
     * @param focusMs 累计陪伴时长
     * @param remainingMs 该蛋**剩余的**自然孵化时间（用于算上限）
     * @return 实际可抵扣的毫秒数（不会超过 `剩余 × MAX_FOCUS_BOOST_RATIO`）
     */
    fun focusBoostMs(focusMs: Long, remainingMs: Long): Long {
        if (focusMs <= 0L || remainingMs <= 0L) return 0L
        val cap = (remainingMs * GrowthRules.MAX_FOCUS_BOOST_RATIO).toLong()
        return focusBoostMs(focusMs).coerceAtMost(cap)
    }

    /**
     * 孵化进度分段描述，用于界面文案。
     */
    fun stageDescription(progress: Float): String = when {
        progress < 0.25f -> "蛋壳还很安静…"
        progress < 0.5f -> "里面好像有心跳声"
        progress < 0.75f -> "蛋壳开始出现裂纹了"
        progress < 1f -> "快要破壳了！"
        else -> "准备破壳！"
    }
}
