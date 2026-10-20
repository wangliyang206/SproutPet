package com.sproutpet.core.domain.desktop

import com.sproutpet.core.domain.pet.HomeSlots
import com.sproutpet.core.domain.pet.Pet

/**
 * 场景名册：**谁该出现在场景里、谁在院子** —— 三层链条的渲染侧唯一执法点。
 *
 * ## 为什么必须有这一层（2026-10-20 崩溃）
 *
 * 三层链条是 **拥有不限 ⊃ 家在册 6 ⊃ 桌面 3**（[HomeSlots]）。
 * 但「场景里该站几只」此前**没有任何一处判据**：
 *
 * - `PetRepository.observeHomePets` 名字叫"家在册"，实现只过滤了「已破壳」
 * - `SceneFragment.renderPets` 直接把拿到的列表整个喂给
 *   [ScenePetLayout.homeLayout]，而它 `require(count in 0..6)`
 *
 * 于是「首只孵化奖励 10 抽」→ 十连开出 10 只 + 原来的 1 只 = **11** 只 ⇒
 * 主线程 `IllegalArgumentException: 家的在册上限是 6，收到 11`，App 直接崩。
 *
 * 崩溃不是"多了一只"，而是**没有一处代码负责回答"多出来的去哪"**。
 * 这个对象就是那个答案：
 *
 * | 输入 | [onStage] | [yard] |
 * |------|-----------|--------|
 * | 蛋（未破壳） | 无 | 无（蛋走角落蛋的位置，不进名册） |
 * | 已破壳 + 在家（`home_slot ≥ 0`） | **最多 6 只** | 无 |
 * | 已破壳 + 待命（`home_slot = -1`） | 无 | **全部** |
 * | 已隐居（`isRetired`） | 无 | 无（后山不是院子） |
 *
 * ## 两条不变量
 *
 * 1. `onStage(x).size <= HomeSlots.MAX_HOME_PETS` —— **恒成立，永不抛异常**
 *    （与 `homeLayout` 的 `require` 互为安全网：那边是"给了非法输入就喊"，这边是"不给非法输入"）
 * 2. `onStage(x).map{id} ∩ yard(x).map{id} = ∅` —— 名册互斥，否则同一只会在两处各画一次
 */
object SceneRoster {

    /** 场景站位顺序：按 `homeSlot` —— 重启后大家还站在原位（陪伴感的一部分） */
    fun onStage(pets: List<Pet>): List<Pet> =
        pets.filter { it.isHatched && !it.isRetired && HomeSlots.isAtHome(it) }
            .sortedBy { it.homeSlot }
            .take(HomeSlots.MAX_HOME_PETS)

    /** 院子：已破壳、不隐居、但不在家的那些（战力降序 —— 值钱的排前面） */
    fun yard(pets: List<Pet>): List<Pet> =
        pets.filter { it.isHatched && !it.isRetired && !HomeSlots.isAtHome(it) }
            .sortedByDescending { it.power }
}
