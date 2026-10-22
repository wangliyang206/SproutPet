package com.sproutpet.core.domain.pet

/**
 * 图鉴进度（design-v2 屏 08 · 抽屉「图鉴」入口）。
 *
 * ## 为什么是三个维度而不是一个「N/18」
 *
 * 这三个数字回答的是**三个不同的问题**，混在一起就会互相误导：
 *
 * | 维度 | 用户的问题 | 现状 |
 * |------|-----------|------|
 * | **种** | 我见过多少种？ | 18 种 = 五虫 15 + 异兽 3 |
 * | **类群** | 五虫我走遍了几虫？ | 6 个（五虫 + 不入十类） |
 * | **神兽位** | 四虫之长拿到几个？ | 4 个（蠃虫按典籍不设形，见 ADR-020） |
 *
 * 只显示「N/18」会让人以为「收集 18 只宠物就满图鉴了」——
 * 而 18 是**物种**数，同一物种养 10 只也还是 1 种。
 *
 * ## 「见」的口径
 *
 * **已破壳才算**（含已入后山/已退休）。抽到蛋不算 —— 蛋里是什么，用户此刻并不知道
 * （`docs/07 §七`）。
 * 判据只有本文件一处：[of] 里 filter `isHatched`，UI 与仓库都调它，不各写一套。
 *
 * @property seenSpecies 已见过的 `PetSpecies.id`（不是宠物 id）
 * @property seenDeities 已拥有**完整神兽形态**的类群（`Pet.hasFullDeityForm`，
 *   不是「有神兽特征」——那一层由稀有度现算，不算收集）
 */
data class CodexProgress(
    val seenSpecies: Set<String> = emptySet(),
    val seenDeities: Set<SpeciesFamily> = emptySet()
) {

    // ── 种 ──

    val speciesSeen: Int get() = seenSpecies.size

    val speciesTotal: Int get() = PetSpecies.entries.size

    // ── 类群 ──

    /** 至少见过该类群里一个物种的类群数（「不入十类」也算一个类群） */
    val familiesSeen: Int get() = SpeciesFamily.entries.count { family ->
        PetSpecies.entries.any { it.family == family && it.id in seenSpecies }
    }

    val familiesTotal: Int get() = SpeciesFamily.entries.size

    // ── 神兽位 ──

    /** 分母是**有形象**的类群数（4），不是 5 —— 蠃虫的位永远不设 */
    val deitiesTotal: Int get() = SpeciesFamily.entries.count { it.hasDeityForm }

    val deitiesSeen: Int get() = seenDeities.count { it.hasDeityForm }

    // ── 单类群查询（图鉴分组标题与格子用） ──

    fun isCollected(species: PetSpecies): Boolean = species.id in seenSpecies

    fun speciesSeenIn(family: SpeciesFamily): Int =
        PetSpecies.entries.count { it.family == family && it.id in seenSpecies }

    fun speciesTotalIn(family: SpeciesFamily): Int =
        PetSpecies.entries.count { it.family == family }

    /** 该类群是否有神兽**形象**（蠃虫返回 false，但语义是「不设」而非「未获得」） */
    fun familyHasDeitySlot(family: SpeciesFamily): Boolean = family.hasDeityForm

    /** 该类群的神兽形态是否已获得 —— 蠃虫恒 `false`（它没有可获得的形态） */
    fun hasDeity(family: SpeciesFamily): Boolean =
        family.hasDeityForm && family in seenDeities

    companion object {

        val EMPTY = CodexProgress()

        /**
         * 从**全部**宠物（含蛋、含已退休）算进度。
         *
         * 调用方不必自己过滤：这里只认 `isHatched`，蛋不会污染进度
         * （否则「抽到一只没见过的蛋」会让图鉴提前亮一格，破壳时反而没有惊喜）。
         */
        fun of(pets: List<Pet>): CodexProgress {
            val hatched = pets.filter { it.isHatched }
            if (hatched.isEmpty()) return EMPTY
            return CodexProgress(
                seenSpecies = hatched.mapTo(mutableSetOf()) { it.species.id },
                seenDeities = hatched.filter { it.hasFullDeityForm }
                    .mapTo(mutableSetOf()) { it.species.family }
            )
        }
    }
}
