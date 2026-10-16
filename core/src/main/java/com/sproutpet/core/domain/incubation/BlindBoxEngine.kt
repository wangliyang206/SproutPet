package com.sproutpet.core.domain.incubation

import com.sproutpet.core.domain.pet.AttributeTendency
import com.sproutpet.core.domain.pet.GrowthRules
import com.sproutpet.core.domain.pet.Pet
import com.sproutpet.core.domain.pet.PetAttributes
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
    val isLucky: Boolean = false
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
     * 抽取宠物蛋。
     *
     * @param pityCount 当前保底计数（连续未出高稀有的次数）
     * @param guaranteedRarity 保底道具指定的最低稀有度，null 表示无
     * @param pool 可抽取的物种池，null 表示全部**可抽取**物种
     * @return 抽取结果，同时返回新的保底计数
     */
    fun draw(
        pityCount: Int = 0,
        guaranteedRarity: Rarity? = null,
        pool: List<PetSpecies>? = null
    ): DrawOutcome {
        // 盲盒池只含可抽取物种（异种只能靠混血获得，不参与抽取）
        val defaultPool = PetSpecies.drawableEntries()
        val speciesPool = (pool ?: defaultPool)
            .filter { it.isDrawable }
            .ifEmpty { defaultPool }

        // 1. 先决定稀有度
        val pityTriggered = pityCount + 1 >= PITY_THRESHOLD
        val rarity = when {
            guaranteedRarity != null -> {
                // 保底道具：不低于指定稀有度
                val rolled = rollRarity()
                if (rolled.id >= guaranteedRarity.id) rolled else guaranteedRarity
            }
            pityTriggered -> {
                // 保底触发：至少史诗
                val rolled = rollRarity()
                if (rolled.isHighRarity) rolled else Rarity.EPIC
            }
            else -> rollRarity()
        }

        // 2. 再决定物种（高稀有度下，稀有种族出现概率提升）
        val species = rollSpecies(speciesPool, rarity)

        // 3. 生成基因
        val genes = PetGenes.random()

        val isNewPityCount = if (rarity.isHighRarity) 0 else pityCount + 1

        return DrawOutcome(
            result = BlindBoxResult(
                species = species,
                rarity = rarity,
                genes = genes,
                guaranteedBy = if (guaranteedRarity != null) "guarantee" else null,
                isLucky = pityTriggered
            ),
            newPityCount = isNewPityCount
        )
    }

    /**
     * 按权重随机稀有度。
     * 使用加权随机保证长期分布符合策划预期。
     *
     * 注意：**异兽（MUTANT）权重为 0，不可抽取**，只能通过配对生育获得。
     */
    fun rollRarity(): Rarity {
        val drawable = Rarity.drawableEntries()
        val total = drawable.sumOf { it.baseWeight }
        if (total <= 0) return Rarity.COMMON
        var roll = Random.nextInt(total)
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
    private fun rollSpecies(pool: List<PetSpecies>, rarity: Rarity): PetSpecies {
        if (pool.size == 1) return pool.first()
        // 高稀有度时引入轻微偏移，让结果更有"惊喜感"
        val bias = if (rarity.isHighRarity) 1 else 0
        val index = (Random.nextInt(pool.size) + bias * Random.nextInt(pool.size)) % pool.size
        return pool[index]
    }

    /**
     * 根据抽取结果创建一只处于「蛋」阶段的宠物。
     *
     * @param ownerId 主人 ID
     * @param batchNo 盲盒批次号
     */
    fun createEgg(
        ownerId: Long,
        result: BlindBoxResult,
        batchNo: String,
        now: Long = System.currentTimeMillis()
    ): Pet {
        val hatchDuration = GrowthRules.hatchDurationOf(result.rarity)
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
            batchNo = batchNo
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
     * @param focusMs 本次陪伴时长
     * @return 可抵扣的孵化时间
     */
    fun focusBoostMs(focusMs: Long): Long = (focusMs * FOCUS_BOOST_RATE).toLong()

    private const val FOCUS_BOOST_RATE = 0.5f

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
