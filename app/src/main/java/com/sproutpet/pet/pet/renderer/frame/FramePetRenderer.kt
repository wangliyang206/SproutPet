package com.sproutpet.pet.pet.renderer.frame

import android.animation.Animator
import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.DrawableRes
import com.sproutpet.core.domain.pet.PetGenes
import com.sproutpet.core.domain.pet.PetRenderState
import com.sproutpet.core.domain.pet.PetRenderer
import com.sproutpet.core.domain.pet.PetSpecies
import com.sproutpet.core.domain.pet.PetStage
import com.sproutpet.pet.R
import com.sproutpet.pet.pet.renderer.PetAssetResolver

/**
 * 帧动画占位渲染器（`RenderMode.FRAME_ANIM`，Phase 3 切 Rive 后由工厂无感替换）。
 *
 * 实现方式：内置一个 [ImageView]，按阶段切换占位 vector，用属性动画表达状态。
 *
 * ⚠️ **取图必须带物种**（docs/15 §5.1 R-3）：所有帧统一经
 * [PetAssetResolver] 四级回落（物种精确 → 物种阶段 → **类群** → 类群落回），
 * 旧版「`drawableOf(stage)` 只看阶段、缺资源静默回落成萌芽鸡」的缺陷已废除。
 * 动作状态（抚摸 / 玩耍 / 被拎起 / 睡觉）在 L1 阶段**不再切换表情帧**，
 * 一律用待机帧 + 属性动画表达（挤压回弹 / 跳跃 / 慢呼吸）——
 * 表情变体属于 L1.5（docs/15 §1.1），到位后由 [PetAssetResolver] 精确命中、渲染器无需再改。
 *
 * 渲染器**持有自己的视图**（[view]），由场景层添加到站位层。
 */
class FramePetRenderer(private val appContext: Context) : PetRenderer {

    /** 场景层挂载用：渲染器持有的根视图（宠物本体） */
    val view: FrameLayout = FrameLayout(appContext)

    private val imageView = ImageView(appContext).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        scaleType = ImageView.ScaleType.FIT_CENTER
    }

    private var species: PetSpecies = PetSpecies.entries.first()
    private var stage: PetStage = PetStage.BABY
    private var currentAnimator: Animator? = null
    private var currentStateKey: String? = null

    /** 朝向（docs/15 §十 行为层）：true = 面向左。翻整棵 [view]，镜像不丢体型缩放 */
    private var facingLeft = false

    /** 最近一次待机的活泼度（setFacing / yawn 结束后续播呼吸用） */
    private var lastLiveliness = 0.5f

    init {
        view.addView(imageView)
        view.foregroundGravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        imageView.pivotX = 0.5f
        // 动画锚点 = 脚底（pivotY = 底缘），待视图量出高度后设定
        imageView.viewTreeObserver.addOnGlobalLayoutListener {
            if (imageView.height > 0) imageView.pivotY = imageView.height.toFloat()
        }
    }

    override fun load(species: PetSpecies, stage: PetStage, genes: PetGenes) {
        this.species = species
        this.stage = stage
        setImage(currentDrawable())
        // 个体差异化占位：体型 → 缩放，色相 → 轻微偏移（±30°）
        val scale = 0.85f + genes.bodySize * 0.3f
        imageView.scaleX = scale
        imageView.scaleY = scale
        baseScaleX = scale
        baseScaleY = scale
        imageView.colorFilter = hueFilter((genes.hueShift - 0.5f) * 60f)
        render(PetRenderState.Idle(genes.liveliness))
    }

    private var baseScaleX = 1f
    private var baseScaleY = 1f

    override fun render(state: PetRenderState) {
        // 幂等：同类持续状态（如 Idle）重复到达时不重置动画进度
        val key = stateKeyOf(state)
        if (key == currentStateKey && state is PetRenderState.Idle) return
        currentStateKey = key

        when (state) {
            is PetRenderState.Idle -> {
                lastLiveliness = state.liveliness
                setImage(currentDrawable())
                play(PetViewAnims.breathing(imageView, state.liveliness, baseScaleX, baseScaleY), keepBase = true)
            }
            // ── 动作状态（L1）：不换表情帧，属性动画即反馈（表情帧属 L1.5） ──
            is PetRenderState.Petted -> playAction { PetViewAnims.squashBounce(imageView, baseScaleX, baseScaleY) }
            is PetRenderState.Excited -> playAction { PetViewAnims.jump(imageView) }
            is PetRenderState.Grabbed -> play(PetViewAnims.grab(imageView, baseScaleX, baseScaleY))
            is PetRenderState.Dragging -> {
                setImage(currentDrawable())
                imageView.rotation = (state.offsetX / 24f).coerceIn(-9f, 9f)
            }
            is PetRenderState.Eating -> playAction { PetViewAnims.squashBounce(imageView) }
            is PetRenderState.Playing -> playAction { PetViewAnims.jump(imageView) }
            is PetRenderState.Sleeping -> {
                setImage(currentDrawable())
                play(PetViewAnims.breathing(imageView, 0f, baseScaleX, baseScaleY), keepBase = true)
            }
            is PetRenderState.Sleepy -> {
                setImage(currentDrawable())
                play(PetViewAnims.breathing(imageView, 0.1f, baseScaleX, baseScaleY), keepBase = true)
            }
            is PetRenderState.LevelUp -> playAction { PetViewAnims.jump(imageView) }
            is PetRenderState.Hatching -> setImage(R.drawable.pet_egg_cracked)
            is PetRenderState.Incubating -> setImage(
                if (state.progress >= 0.5f) R.drawable.pet_egg_cracked else R.drawable.pet_egg_common
            )
            else -> setImage(currentDrawable())
        }
    }

    /**
     * 一次性动作：从当前帧播一段属性动画，结束后回待机呼吸。
     * 与旧版 [oneShot] 的区别：**不切换表情帧**（L1 无表情资源，见类 KDoc）。
     */
    private fun playAction(anim: () -> Animator) {
        val animator = anim()
        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                imageView.rotation = 0f
                setImage(currentDrawable())
                play(PetViewAnims.breathing(imageView, 0.5f, baseScaleX, baseScaleY), keepBase = true)
            }
        })
        play(animator)
    }

    /** 当前（物种, 阶段）对应的资源 —— 取图的唯一入口（经 [PetAssetResolver] 四级回落） */
    private fun currentDrawable(): Int = PetAssetResolver.resolve(appContext, species, stage)

    private fun play(animator: Animator, keepBase: Boolean = false) {
        cancelCurrent()
        if (keepBase) {
            imageView.scaleX = baseScaleX
            imageView.scaleY = baseScaleY
            imageView.rotation = 0f
            imageView.translationY = 0f
        }
        currentAnimator = animator
        animator.start()
    }

    private fun setImage(@DrawableRes res: Int) {
        if (imageView.tag != res) {
            imageView.setImageResource(res)
            imageView.tag = res
        }
    }

    private fun cancelCurrent() {
        currentAnimator?.cancel()
        currentAnimator = null
    }

    private fun stateKeyOf(state: PetRenderState): String = state::class.simpleName.orEmpty()

    /** 色相偏移滤镜（个体差异化占位，正式美术到位后可移除） */
    private fun hueFilter(degrees: Float): ColorMatrixColorFilter {
        val matrix = ColorMatrix()
        matrix.setRotate(0, degrees)
        matrix.setRotate(1, degrees)
        matrix.setRotate(2, degrees)
        return ColorMatrixColorFilter(matrix)
    }

    override fun setFacing(facingLeft: Boolean) {
        if (this.facingLeft == facingLeft) return
        this.facingLeft = facingLeft
        // 翻 renderer 根视图：imageView 上的体型缩放/动画不受影响
        view.scaleX = if (facingLeft) -1f else 1f
    }

    /** 哈欠（docs/15 §十 YAWN）：拉长 → 停顿 → 回弹，结束回待机呼吸 */
    override fun playYawn() {
        playAction { PetViewAnims.yawn(imageView, baseScaleY) }
    }

    override fun supportsTouch(): Boolean = false // 触摸统一由手势控制器在容器层处理

    override fun setScale(scale: Float) {
        view.scaleX = scale
        view.scaleY = scale
    }

    override fun pause() = cancelCurrent()

    override fun resume() {
        if (currentStateKey == null) render(PetRenderState.Idle(0.5f))
    }

    override fun release() {
        cancelCurrent()
        (view.parent as? android.view.ViewGroup)?.removeView(view)
    }
}
