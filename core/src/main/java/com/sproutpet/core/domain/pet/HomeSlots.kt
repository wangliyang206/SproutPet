package com.sproutpet.core.domain.pet

/**
 * 「家」与「桌面」的槽位规则（`U-12` 三层数量链条的唯一执法点）。
 *
 * ## 三层链条（含包关系，docs/00 §4.1）
 *
 * ```
 * 拥有不限  ⊃  家在册 6  ⊃  桌面 3
 * ```
 *
 * - **家在册**：`homeSlot` ∈ `0..5`；`-1`（[NO_SLOT]）= 待命（不在家）
 * - **桌面**：`desktopSlot` ∈ `0..2`；`-1` = 未上桌。**桌面 ⊆ 在家**（桌上的宠必须先在册）
 * - **主宠**：[Pet.isMain] —— 「在任何地方都优先表达的那只」，**恒定占桌面 0 号槽位**
 *
 * ## 为什么规则要住在 core 而不是 UI
 *
 * 「桌上的宠必须先在册」「主宠恒占 0 号」这类约束如果由 UI 各自维护，
 * 图鉴 / 族谱 / 抽屉三个入口就会写出三套校验（本项目已多次发生「写了但没人调用」）。
 * 收敛到 [validate] 一处：任何写入前先过它，返回空列表即合法。
 *
 * ⚠️ 数值与 `tools/acquisition_sim.py` 的 `MAX_HOME_PETS` / `MAX_DESKTOP_PETS` 同源 ——
 * **改这里必须同步 sim 并触发 ADR-024 复算**（并行繁育线数由「家在册」决定）。
 */
object HomeSlots {

    /** 家在册上限（与 `tools/acquisition_sim.py` 的 `MAX_HOME_PETS` 同源） */
    const val MAX_HOME_PETS = 6

    /** 桌面上限（与 `tools/acquisition_sim.py` 的 `MAX_DESKTOP_PETS` 同源） */
    const val MAX_DESKTOP_PETS = 3

    /** 「不在家 / 未上桌」的哨兵值 */
    const val NO_SLOT = -1

    /** 主宠在桌面的固定槽位（0 号） */
    const val MAIN_DESKTOP_SLOT = 0

    /**
     * 宠物是否在「家」的在册名单里。
     *
     * ⚠️ 这里刻意**用函数而不是扩展属性**：`Pet.atHome` 已经是 [Pet] 的成员，
     * 若此处也叫 `Pet.atHome`，[Pet] 内部任何 `atHome` 引用都会解析回成员自身 ——
     * 造成无限递归（曾真的 StackOverflow）。函数名不参与属性遮蔽，天然安全。
     */
    fun isAtHome(pet: Pet): Boolean = pet.homeSlot != NO_SLOT

    /** 宠物是否在手机桌面上（同 [isAtHome] 的命名理由） */
    fun isOnDesktop(pet: Pet): Boolean = pet.desktopSlot != NO_SLOT

    /**
     * 校验一组宠物的槽位状态是否满足全部不变量。
     *
     * @return 违规描述列表；**空列表 = 合法**。
     */
    fun validate(pets: List<Pet>): List<String> {
        val bad = mutableListOf<String>()

        val atHome = pets.filter { isAtHome(it) }
        val onDesktop = pets.filter { isOnDesktop(it) }

        // ① 含包约束：桌面 ⊆ 在家
        onDesktop.filterNot { isAtHome(it) }.forEach {
            bad += "${it.nickname}：在桌面(d=${it.desktopSlot})但不在家(h=${it.homeSlot})"
        }

        // ② 数量上限
        if (atHome.size > MAX_HOME_PETS) bad += "在家 ${atHome.size} 只 > 上限 $MAX_HOME_PETS"
        if (onDesktop.size > MAX_DESKTOP_PETS) bad += "桌面 ${onDesktop.size} 只 > 上限 $MAX_DESKTOP_PETS"

        // ③ 槽位不重复（同 owner 内；跨 owner 的家/桌不共享，这里按 owner 分组检查）
        pets.groupBy { it.ownerId }.forEach { (_, group) ->
            val homeSlots = group.mapNotNull { it.homeSlot.takeIf { s -> s >= 0 } }
            homeSlots.groupingBy { it }.eachCount().filterValues { it > 1 }
                .forEach { (s, n) -> bad += "家槽位 $s 被 $n 只同时占用" }

            val deskSlots = group.mapNotNull { it.desktopSlot.takeIf { s -> s >= 0 } }
            deskSlots.groupingBy { it }.eachCount().filterValues { it > 1 }
                .forEach { (s, n) -> bad += "桌面槽位 $s 被 $n 只同时占用" }
        }

        // ④ 主宠不变量：恒占桌面 0 号
        val mains = pets.filter { it.isMain }
        when {
            mains.isEmpty() -> bad += "没有主宠"
            mains.size > 1 -> bad += "主宠有 ${mains.size} 只（必须恰好 1 只）"
            else -> {
                val m = mains.first()
                if (m.desktopSlot != MAIN_DESKTOP_SLOT) {
                    bad += "主宠桌面槽位应为 $MAIN_DESKTOP_SLOT，实际 ${m.desktopSlot}"
                }
                if (!isAtHome(m)) bad += "主宠必须在家（主宠不可处于待命）"
            }
        }

        // ⑤ 桌面 0 号必须就是主宠
        onDesktop.filter { it.desktopSlot == MAIN_DESKTOP_SLOT && !it.isMain }
            .forEach { bad += "${it.nickname}：占桌面 0 号但不是主宠" }

        // ⑥ 隐居宠不占任何槽位（后山不等于家）
        pets.filter { it.isRetired && (isAtHome(it) || isOnDesktop(it)) }
            .forEach { bad += "${it.nickname}：已隐居却仍占槽位（隐居宠不占在册名额）" }

        return bad
    }

    /**
     * 「放出」（待命 → 在家）：返回该宠应得的 home 槽位；
     * 满员时返回 `null` —— **调用方应引导「收起谁」，而不是报错**（`U-12` 拍板）。
     */
    fun nextFreeHomeSlot(pets: List<Pet>): Int? {
        val used = pets.mapNotNull { it.homeSlot.takeIf { s -> s >= 0 } }.toSet()
        if (used.size >= MAX_HOME_PETS) return null
        return (0 until MAX_HOME_PETS).first { it !in used }
    }

    /**
     * 「上桌」：返回该宠应得的桌面槽位；满员返回 `null`（引导「换谁下来」）。
     * 主宠永远拿 [MAIN_DESKTOP_SLOT]。
     */
    fun nextFreeDesktopSlot(pets: List<Pet>, isMain: Boolean): Int? {
        if (isMain) return MAIN_DESKTOP_SLOT
        val used = pets.mapNotNull { it.desktopSlot.takeIf { s -> s >= 0 } }.toSet()
        if (used.size >= MAX_DESKTOP_PETS) return null
        return (0 until MAX_DESKTOP_PETS).first { it !in used }
    }
}
