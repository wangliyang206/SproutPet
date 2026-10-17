package com.sproutpet.pet.pet.renderer.frame

import android.content.Context
import com.sproutpet.core.domain.pet.PetRenderer
import com.sproutpet.core.domain.pet.PetRendererFactory
import com.sproutpet.core.domain.pet.RenderMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 帧动画渲染器工厂（Phase 1 占位实现）。
 *
 * Phase 3 切换 Rive 时只需改这里的分发，上层（场景/桌宠）零改动 ——
 * 这正是 `PetRendererFactory` 存在的意义（见 core PetRenderer.kt 头注）。
 */
@Singleton
class FramePetRendererFactory @Inject constructor(
    @ApplicationContext private val context: Context
) : PetRendererFactory {

    override fun create(mode: RenderMode): PetRenderer = when (mode) {
        // RIVE / LOTTIE / MODEL_3D 尚未接入，统一直接降级为帧动画占位
        RenderMode.FRAME_ANIM, RenderMode.RIVE, RenderMode.LOTTIE, RenderMode.MODEL_3D ->
            FramePetRenderer(context)
    }
}
