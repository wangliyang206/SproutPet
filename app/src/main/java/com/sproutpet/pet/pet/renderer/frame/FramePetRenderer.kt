package com.sproutpet.pet.pet.renderer.frame

import android.animation.Animator
import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.sproutpet.core.domain.pet.PetGenes
import com.sproutpet.core.domain.pet.PetRenderState
import com.sproutpet.core.domain.pet.PetRenderer
import com.sproutpet.core.domain.pet.PetSpecies
import com.sproutpet.core.domain.pet.PetStage
import com.sproutpet.core.domain.pet.Rarity
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

    /**
     * 稀有度光环（⭐ 2026-10-22 用户需求「不同稀有度外形要不一样」· 占位实现 docs/02 §12）。
     *
     * 径向渐变光晕垫在宠物**身后**，强度随 [Rarity.fxLevel] 递增：
     * 普通 = 无（基础色）· 稀有 = 微光（描边档）· 史诗+ = 光效档逐级加亮。
     * 粒子与专属动画等 Rive 美术（docs/02 §5.1）到位后按同一档位替换，视图层级不动。
     */
    private val auraView = View(appContext).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        visibility = View.GONE
    }

    /** 稀有度脚标：名牌位（槽位下方居中），稀有度色底 + 白字 —— 「这是个什么档位的宠」一眼可见 */
    private val rarityChip = TextView(appContext).apply {
        textSize = 9f
        maxLines = 1
        setTextColor(android.graphics.Color.WHITE)
        gravity = Gravity.CENTER
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply { bottomMargin = 0 }
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
        view.addView(auraView)
        view.addView(imageView)
        view.addView(rarityChip)
        view.foregroundGravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        imageView.pivotX = 0.5f
        // 动画锚点 = 脚底（pivotY = 底缘），待视图量出高度后设定
        imageView.viewTreeObserver.addOnGlobalLayoutListener {
            if (imageView.height > 0) imageView.pivotY = imageView.height.toFloat()
        }
    }

    override fun load(species: PetSpecies, stage: PetStage, genes: PetGenes, rarity: Rarity) {
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
        applyRarityFx(rarity)
        render(PetRenderState.Idle(genes.liveliness))
    }

    /**
     * 稀有度视觉分层（判据 = [Rarity.fxLevel]，视觉在此映射 · docs/02 §12）：
     * - **脚标**：所有档位都显示稀有度名（色底白字；普通灰 / 稀有蓝 / 史诗紫 / 传说橙 / 神话红 / 灵兽青）
     * - **光环**：档位 0 不画；1~4 逐级加亮加大的径向光晕（颜色 = 稀有度色）
     */
    private fun applyRarityFx(rarity: Rarity) {
        val color = ContextCompat.getColor(appContext, rarityColorRes(rarity))
        // 脚标
        rarityChip.text = rarity.displayName
        val chipBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = appContext.resources.displayMetrics.density * 9f
            setColor(color)
        }
        rarityChip.background = chipBg
        rarityChip.visibility = View.VISIBLE
        // 光环
        if (rarity.fxLevel <= 0) {
            auraView.visibility = View.GONE
            return
        }
        val density = appContext.resources.displayMetrics.density
        val alpha = when (rarity.fxLevel) {
            1 -> 60
            2 -> 105
            3 -> 150
            else -> 190
        }
        auraView.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            gradientType = GradientDrawable.RADIAL_GRADIENT
            colors = intArrayOf(
                (alpha shl 24) or (color and 0x00FFFFFF),
                ((alpha / 2).coerceAtMost(255) shl 24) or (color and 0x00FFFFFF),
                0x00000000
            )
            // 径向渐变半径：0.5 = 铺满视图（中心即宠物身体）
            setGradientCenter(0.5f, 0.45f)
            useLevel = false
        }
        // 档位越高光环越外扩（占位：直接用 padding 收边模拟；Rive 版替换为真粒子）
        val inset = (4 - rarity.fxLevel) * density * 4f
        auraView.setPadding(inset.toInt(), inset.toInt(), inset.toInt(), inset.toInt())
        auraView.visibility = View.VISIBLE
    }

    private fun rarityColorRes(rarity: Rarity): Int = when (rarity) {
        Rarity.COMMON -> R.color.rarity_common
        Rarity.RARE -> R.color.rarity_rare
        Rarity.EPIC -> R.color.rarity_epic
        Rarity.LEGENDARY -> R.color.rarity_legendary
        Rarity.MYTHIC -> R.color.rarity_mythic
        Rarity.MUTANT -> R.color.rarity_mutant
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
