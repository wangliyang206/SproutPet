package com.sproutpet.core.domain.pet

/**
 * 宠物渲染状态：驱动动画的「输入信号」。
 *
 * 这是**渲染层与业务层的唯一契约**。
 * 无论底层用 Rive / Lottie / 帧动画 / 3D，业务层只产出 PetRenderState，
 * 渲染实现负责把它翻译成具体的动画参数。
 *
 * 设计原则：
 * - 业务层不关心动效如何实现
 * - 渲染层不关心业务规则
 * - 新增动效 = 新增一个 RenderState 枚举值 + 美术资源
 */
sealed interface PetRenderState {

    /** 待机：呼吸、眨眼、偶尔张望 */
    data class Idle(val liveliness: Float) : PetRenderState

    /** 被抚摸：开心冒爱心 */
    data object Petted : PetRenderState

    /** 被双击：兴奋跳跃 */
    data object Excited : PetRenderState

    /** 被长按：举起 / 被拎起来 */
    data object Grabbed : PetRenderState

    /** 被拖动中：四肢下垂（物理感） */
    data class Dragging(val offsetX: Float, val offsetY: Float) : PetRenderState

    /** 喂食中 */
    data object Eating : PetRenderState

    /** 玩耍中 */
    data object Playing : PetRenderState

    /** 训练中 */
    data object Training : PetRenderState

    /** 疲倦：体力不足 */
    data object Tired : PetRenderState

    /** 困倦：长时间无交互，开始打哈欠 */
    data object Sleepy : PetRenderState

    /** 睡着：Zzz 气泡 */
    data object Sleeping : PetRenderState

    /** 升级：光环 + 欢呼 */
    data object LevelUp : PetRenderState

    /** 专注陪伴：与主人一起工作 */
    data object Focusing : PetRenderState

    /** 破壳中：蛋壳裂开的过渡动画 */
    data class Hatching(val progress: Float) : PetRenderState

    /** 孵化中：蛋微微晃动 */
    data class Incubating(val progress: Float) : PetRenderState

    /** 结婚：撒花 */
    data object Marrying : PetRenderState

    /** 低心情：背对用户 */
    data object Upset : PetRenderState
}

/**
 * 宠物渲染器抽象。
 *
 * 任何渲染实现（Rive / Lottie / 帧动画 / 3D）都实现此接口，
 * 业务代码只依赖此接口，实现**可插拔替换**。
 *
 * 实现约定：
 * - [render] 必须幂等：相同状态重复调用不应重置动画进度
 * - [release] 必须释放所有原生资源（Rive 的 Artboard / StateMachine 等）
 * - 实现类应保证线程安全或明确标注只能在主线程调用
 */
interface PetRenderer {

    /**
     * 加载某个宠物个体的渲染资源。
     *
     * @param species 种族（决定加载哪个美术资源）
     * @param stage 当前阶段（决定用哪套体型资源）
     * @param genes 基因（决定外观微调参数）
     * @param rarity 稀有度（⭐ 2026-10-22 用户需求「不同稀有度外形要不一样」：
     *   决定光环档位与稀有度脚标 —— [Rarity.fxLevel] 判据，UI 只做映射）
     */
    fun load(species: PetSpecies, stage: PetStage, genes: PetGenes, rarity: Rarity = Rarity.COMMON)

    /**
     * 应用一个渲染状态。
     * 实现方负责与当前状态做差分，避免重复触发同一动画。
     */
    fun render(state: PetRenderState)

    /**
     * 是否支持触控交互（悬浮窗场景需要）。
     * 返回 false 时上层会退化为在容器上叠加透明触摸层。
     */
    fun supportsTouch(): Boolean = true

    /**
     * 设置缩放比例（用于悬浮窗大小调节）。
     */
    fun setScale(scale: Float) {}

    /**
     * 朝向（docs/15 §十 行为层）：溜达 / 社交时面向移动方向。
     * 默认空实现 —— 占位渲染器翻转整体视图，Rive 版可换行走循环。
     */
    fun setFacing(facingLeft: Boolean) {}

    /** 打哈欠（行为层 YAWN）：默认空实现 */
    fun playYawn() {}

    /** 暂停动画（省电，如应用进入后台） */
    fun pause() {}

    /** 恢复动画 */
    fun resume() {}

    /** 释放资源 */
    fun release()
}

/**
 * 渲染器工厂：由 Hilt 注入，按配置决定返回哪种实现。
 *
 * 这样在 Phase 1（用属性动画占位）到 Phase 3（切 Rive）之间，
 * 只需替换工厂实现，上层代码零改动。
 */
interface PetRendererFactory {
    /**
     * 创建一个渲染器实例。
     * @param mode 渲染模式，可从远程配置下发，便于灰度切换
     */
    fun create(mode: RenderMode = RenderMode.current()): PetRenderer
}

/**
 * 渲染模式。可通过远程配置下发，实现不更新 App 就切换动效方案。
 */
enum class RenderMode {
    /** Rive 矢量状态机（首选） */
    RIVE,

    /** Lottie 矢量动画 */
    LOTTIE,

    /** 多帧图 + 属性动画（降级方案，低端机使用） */
    FRAME_ANIM,

    /** 真 3D（仅应用内展示场景使用） */
    MODEL_3D;

    companion object {
        /**
         * 当前生效模式。
         * Phase 1 默认 FRAME_ANIM 占位，Phase 3 切换为 RIVE。
         */
        fun current(): RenderMode = FRAME_ANIM
    }
}
