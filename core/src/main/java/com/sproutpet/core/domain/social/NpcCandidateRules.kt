package com.sproutpet.core.domain.social

import com.sproutpet.core.domain.pet.Pet
import com.sproutpet.core.domain.pet.PetGender
import com.sproutpet.core.domain.pet.PetSpecies
import kotlin.random.Random

/**
 * NPC 候选宠生成规则 —— `W-16-1`（2026-10-19 拍板）的**唯一真相源**。
 *
 * ## 背景
 *
 * 配对广场（design-v2 屏 24/25）的七条前置、冷却、金币、名额、性别取反
 * 全部早有冻结口径；唯独「NPC 候选宠本身长什么样」全项目没有定义 ——
 * `00 §2.5` 只说"系统按稀有度/属性匹配"，`08 S16` 只定上限 3 次。
 * 拍板前 24/25 屏因此未动一行代码（先拍板再写代码）。
 *
 * ## 拍板口径（逐条对应代码）
 *
 * 1. **一次 3 只**（[CANDIDATE_COUNT]）
 * 2. **物种均匀随机**，取 **15 种五虫**（[SPECIES_POOL]）——
 *    ⚠️ 拍板口径写的是"18 物种池"，执行修正为 15：18 = 15 虫 + 3 异兽，
 *    异兽（`SpeciesFamily.UNCATEGORIZED`）本就是**配对才可能产出**的稀缺项
 *    （`BreedingRules` 异兽产出率 4%~40%），放进 NPC 候选等于免费送异兽血脉，
 *    与该设计直接冲突。同虫 / 跨虫的挑选乐趣不受影响。
 * 3. **稀有度 = 我方宠同档** —— 后代分布以 `max(父母)` 为中心，
 *    NPC 高一档等于免费抬轿，低一档等于白白稀释，同档才是"不白送不克扣"
 * 4. **等级 = 我方 ±3**（[LEVEL_JITTER]），钳制到 `[1, MAX_LEVEL]`
 * 5. **性别永远取反**（`U-09` 冻结口径）—— 单机阶段 NPC 是唯一配对路径，
 *    这里卡性别等于把核心玩法锁死
 * 6. **名字从 NPC 名池随机**（[NAME_POOL]）
 *
 * 本文件**只描述候选**，不落库 —— NPC 宠在玩家真正选择「立即配对」时才创建
 * （见 `PairingRepository.startNpcPairing`），避免"逛个广场就冒出一堆没人要的宠"。
 */
object NpcCandidateRules {

    /** 一次生成的候选数量 */
    const val CANDIDATE_COUNT = 3

    /** 等级抖动幅度（±3） */
    const val LEVEL_JITTER = 3

    /**
     * 候选物种池：**15 种五虫**（`PetSpecies.drawableEntries()`）。
     * 异兽 3 种不进池（见文件头「执行修正」）。
     */
    val SPECIES_POOL: List<PetSpecies> = PetSpecies.drawableEntries()

    /** NPC 名池（生活语义，与「院子」同调 —— 不用"NPC_01"这类机器名） */
    val NAME_POOL: List<String> = listOf(
        "花卷", "糯米", "团子", "麻薯", "布丁", "汤圆", "豆包", "奶盖",
        "雪球", "栗子", "芋圆", "杏仁", "桃酥", "米糕", "芝麻", "桂圆"
    )

    /** 一只 NPC 候选（只是描述，不是可持久化的 [Pet]；稀有度恒等我方宠，故不设字段） */
    data class NpcCandidate(
        val nickname: String,
        val species: PetSpecies,
        val level: Int,
        /** 恒为我方宠的取反性别（拍板第 5 条） */
        val gender: PetGender,
        /** 是否与我方**同虫**（UI 展示「同虫稳 / 跨虫搏」用） */
        val sameFamily: Boolean
    )

    /**
     * 为我方一只宠生成 [CANDIDATE_COUNT] 只 NPC 候选。
     *
     * 纯函数（`random` 注入）：同样的输入 + 同样的随机源 = 同样的候选，
     * UI 侧「换一批」只要换 `random` 种子，规则层不藏状态。
     */
    fun candidatesFor(myPet: Pet, random: Random = Random.Default): List<NpcCandidate> =
        List(CANDIDATE_COUNT) {
            val species = SPECIES_POOL[random.nextInt(SPECIES_POOL.size)]
            val level = (myPet.level + random.nextInt(LEVEL_JITTER * 2 + 1) - LEVEL_JITTER)
                .coerceIn(1, com.sproutpet.core.domain.pet.GrowthRules.MAX_LEVEL)
            NpcCandidate(
                nickname = NAME_POOL[random.nextInt(NAME_POOL.size)],
                species = species,
                level = level,
                gender = myPet.gender.opposite,
                sameFamily = species.family == myPet.species.family
            )
        }
}
