package com.sproutpet.pet.ui.scene

import com.sproutpet.core.domain.pet.PetRenderState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 生命周期控制器（docs/04 §1.3）：待机 → 困倦 → 睡眠的定时器。
 *
 * - 无交互 [SLEEPY_AFTER_MS] 后进入困倦（呼吸变慢）
 * - 无交互 [SLEEP_AFTER_MS] 后睡着（睡眠帧 + Zzz 由场景特效层补）
 * - 任何交互立即重置回待机
 *
 * Phase 1 用前台协程计时即可；后台收益与通知属 Phase 2/8。
 */
class PetLifecycleController(
    private val liveliness: Float,
    private val onStateChanged: (PetRenderState) -> Unit
) {
    private var scope: CoroutineScope? = null
    private var job: Job? = null
    private var sleeping = false

    /** 启动计时（场景 STARTED 时调用） */
    fun start(scope: CoroutineScope) {
        this.scope = scope
        restart()
    }

    /** 有交互：重置回待机并重新计时 */
    fun onInteraction() {
        if (scope != null) restart()
    }

    /** 是否在睡觉（拖拽等动作可据此决定是否打断睡眠） */
    fun isSleeping(): Boolean = sleeping

    private fun restart() {
        val s = scope ?: return
        job?.cancel()
        sleeping = false
        onStateChanged(PetRenderState.Idle(liveliness))
        job = s.launch {
            delay(SLEEPY_AFTER_MS)
            onStateChanged(PetRenderState.Sleepy)
            delay(SLEEP_AFTER_MS - SLEEPY_AFTER_MS)
            sleeping = true
            onStateChanged(PetRenderState.Sleeping)
        }
    }

    /** 停止计时（场景 STOPPED / 视图销毁时调用） */
    fun stop() {
        job?.cancel()
        job = null
        scope = null
    }

    companion object {
        /** 无交互多久进入困倦（1 分钟） */
        const val SLEEPY_AFTER_MS = 60_000L

        /** 无交互多久睡着（5 分钟；docs/04 Phase 5 的 30 分钟是桌面多宠口径，家里更活泼） */
        const val SLEEP_AFTER_MS = 300_000L
    }
}
