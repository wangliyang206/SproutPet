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

/**
 * 帧动画占位渲染器（`RenderMode.FRAME_ANIM`，Phase 3 切 Rive 后由工厂无感替换）。
 *
 * 实现方式：内置一个 [ImageView]，按阶段切换占位 vector，用属性动画表达状态。
 * 美术占位仅「萌芽鸡」一套（见 docs/04 §1.2），其他物种暂以基因色相偏移区分个体。
 *
 * 渲染器**持有自己的视图**（[view]），由场景层添加到站位层。
 */
class FramePetRenderer(context: Context) : PetRenderer {

    /** 场景层挂载用：渲染器持有的根视图（宠物本体） */
    val view: FrameLayout = FrameLayout(context)

    private val imageView = ImageView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        scaleType = ImageView.ScaleType.FIT_CENTER
    }

    private var stage: PetStage = PetStage.BABY
    private var currentAnimator: Animator? = null
    private var currentStateKey: String? = null

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
        this.stage = stage
        imageView.setImageResource(drawableOf(stage))
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
                setImage(drawableOf(stage))
                play(PetViewAnims.breathing(imageView, state.liveliness), keepBase = true)
            }
            is PetRenderState.Petted -> oneShot(R.drawable.pet_sprout_chicken_happy) {
                PetViewAnims.squashBounce(imageView)
            }
            is PetRenderState.Excited -> oneShot(R.drawable.pet_sprout_chicken_happy) {
                PetViewAnims.jump(imageView)
            }
            is PetRenderState.Grabbed -> {
                setImage(R.drawable.pet_sprout_chicken_grabbed)
                play(PetViewAnims.grab(imageView))
            }
            is PetRenderState.Dragging -> {
                setImage(R.drawable.pet_sprout_chicken_grabbed)
                imageView.rotation = (state.offsetX / 24f).coerceIn(-9f, 9f)
            }
            is PetRenderState.Eating -> oneShot(R.drawable.pet_sprout_chicken_happy) {
                PetViewAnims.squashBounce(imageView)
            }
            is PetRenderState.Playing -> oneShot(R.drawable.pet_sprout_chicken_happy) {
                PetViewAnims.jump(imageView)
            }
            is PetRenderState.Sleeping -> {
                setImage(R.drawable.pet_sprout_chicken_sleep)
                play(PetViewAnims.breathing(imageView, 0f), keepBase = true)
            }
            is PetRenderState.Sleepy -> {
                setImage(drawableOf(stage))
                play(PetViewAnims.breathing(imageView, 0.1f), keepBase = true)
            }
            is PetRenderState.LevelUp -> oneShot(R.drawable.pet_sprout_chicken_happy) {
                PetViewAnims.jump(imageView)
            }
            is PetRenderState.Hatching -> setImage(R.drawable.pet_egg_cracked)
            is PetRenderState.Incubating -> setImage(
                if (state.progress >= 0.5f) R.drawable.pet_egg_cracked else R.drawable.pet_egg_common
            )
            else -> setImage(drawableOf(stage))
        }
    }

    /** 一次性动作：换动作帧 → 播动画 → 回到待机帧 */
    private fun oneShot(@DrawableRes actionRes: Int, anim: () -> Animator) {
        setImage(actionRes)
        cancelCurrent()
        val animator = anim()
        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                imageView.rotation = 0f
                setImage(drawableOf(stage))
                play(PetViewAnims.breathing(imageView, 0.5f), keepBase = true)
            }
        })
        currentAnimator = animator
        animator.start()
    }

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

    @DrawableRes
    private fun drawableOf(stage: PetStage): Int = when (stage) {
        PetStage.EGG, PetStage.HATCHING -> R.drawable.pet_egg_common
        PetStage.BABY -> R.drawable.pet_sprout_chicken_baby
        PetStage.JUVENILE -> R.drawable.pet_sprout_chicken_juvenile
        PetStage.ADULT -> R.drawable.pet_sprout_chicken_adult
        PetStage.ELDER, PetStage.RETIRED -> R.drawable.pet_sprout_chicken_elder
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
