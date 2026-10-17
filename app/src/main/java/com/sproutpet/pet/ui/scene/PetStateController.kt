package com.sproutpet.pet.ui.scene

import com.sproutpet.core.domain.pet.PetRenderState
import com.sproutpet.core.domain.pet.PetRenderer

/**
 * 渲染状态编排（docs/04 §1.3）：App 侧语义动作 → [PetRenderState]。
 *
 * 集中一处做翻译，避免场景层散落 `render(...)` 调用；
 * 同步通知 [PetLifecycleController] 重置困倦计时。
 */
class PetStateController(
    private val renderer: PetRenderer,
    private val lifecycle: PetLifecycleController
) {

    /** 当前生命周期给出的基础状态（待机 / 困倦 / 睡眠） */
    private var baseState: PetRenderState = PetRenderState.Idle(0.5f)

    /** 生命周期基础状态变化（由 [PetLifecycleController] 回调） */
    fun onBaseStateChanged(state: PetRenderState) {
        baseState = state
        renderer.render(state)
    }

    /** 抚摸：挤压回弹 + 爱心（爱心由场景特效层补） */
    fun petted() {
        lifecycle.onInteraction()
        renderer.render(PetRenderState.Petted)
    }

    /** 双击逗弄：兴奋跳跃 */
    fun excited() {
        lifecycle.onInteraction()
        renderer.render(PetRenderState.Excited)
    }

    /** 长按拎起 */
    fun grabbed() {
        lifecycle.onInteraction()
        renderer.render(PetRenderState.Grabbed)
    }

    /** 拖拽中 */
    fun dragging(offsetX: Float, offsetY: Float) {
        renderer.render(PetRenderState.Dragging(offsetX, offsetY))
    }

    /** 松手：回到当前基础状态 */
    fun released() {
        renderer.render(baseState)
    }

    /** 升级欢呼 */
    fun levelUp() {
        lifecycle.onInteraction()
        renderer.render(PetRenderState.LevelUp)
    }
}
