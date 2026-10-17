package com.sproutpet.pet.domain.growth

import com.sproutpet.core.domain.pet.Pet
import com.sproutpet.pet.data.repository.IncubationRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 专注陪伴加速（docs/04 §2.5）。
 *
 * 把一段陪伴时长按 `GrowthRules.FOCUS_BOOST_RATIO`（50%）抵扣孵化倒计时，
 * 并受 `MAX_FOCUS_BOOST_RATIO` 单蛋上限约束（执法点在 core `IncubationEngine`）。
 *
 * @return 加速后的蛋；蛋不存在或已破壳返回 null
 */
@Singleton
class FocusBoostUseCase @Inject constructor(
    private val incubationRepository: IncubationRepository
) {
    suspend fun execute(eggId: Long, focusMs: Long): Pet? =
        incubationRepository.applyFocusBoost(eggId, focusMs)
}
