package com.sproutpet.pet.pet.renderer.frame

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * 帧动画渲染层的属性动画工具（渲染方案 §8：挤压回弹、纵向拉伸）。
 *
 * 全部动画以宠物**脚底**为锚点（pivotY = 底部），保证"站在地上"的物理感。
 */
object PetViewAnims {

    /**
     * 待机呼吸：轻微的纵向缩放循环。[liveliness] 越高节奏越快。
     * ⭐ v1.2 修复：缩放改为**相对基准**（基因体型 baseX/baseY）——
     * 旧版硬编码 1f 起点，体型 ≠1.0 的宠物待机时会被悄悄拉回标准体型。
     */
    fun breathing(target: View, liveliness: Float, baseX: Float = 1f, baseY: Float = 1f): Animator {
        val period = (2600L - (liveliness * 1200L).toLong()).coerceIn(900L, 2600L)
        val scaleY = ObjectAnimator.ofFloat(target, View.SCALE_Y, baseY, baseY * 1.045f, baseY).apply {
            duration = period
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
        }
        val scaleX = ObjectAnimator.ofFloat(target, View.SCALE_X, baseX, baseX * 0.97f, baseX).apply {
            duration = period
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
        }
        return AnimatorSet().apply { playTogether(scaleX, scaleY) }
    }

    /**
     * 走路弹跳（docs/15 §十）：溜达时身体小幅度上下起伏 + 轻微前倾，
     * 无限循环 —— 到达后由调用方 cancel。
     */
    fun walkBob(target: View): Animator {
        val bob = ObjectAnimator.ofFloat(target, View.TRANSLATION_Y, 0f, -6f, 0f).apply {
            duration = 260L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
        }
        val lean = ObjectAnimator.ofFloat(target, View.ROTATION, 0f, 3f, 0f, -3f, 0f).apply {
            duration = 520L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
        }
        return AnimatorSet().apply { playTogether(bob, lean) }
    }

    /**
     * 打哈欠（docs/15 §十）：身体缓缓拉长 → 停顿 → 收回。
     * 与呼吸共用锚点（脚底），一次性动画，结束回待机由调用方处理。
     */
    fun yawn(target: View, baseY: Float = 1f): Animator {
        val stretch = ObjectAnimator.ofFloat(target, View.SCALE_Y, baseY, baseY * 1.12f).setDuration(520)
        stretch.interpolator = AccelerateDecelerateInterpolator()
        val hold = ObjectAnimator.ofFloat(target, View.SCALE_Y, baseY * 1.12f, baseY * 1.12f).setDuration(420)
        val shrink = ObjectAnimator.ofFloat(target, View.SCALE_Y, baseY * 1.12f, baseY).setDuration(560)
        shrink.interpolator = OvershootInterpolator(1.2f)
        return AnimatorSet().apply { playSequentially(stretch, hold, shrink) }
    }

    /** 挤压回弹（被抚摸）：先压扁再回弹，< 100ms 内起第一帧。（v1.2：相对基准体型） */
    fun squashBounce(target: View, baseX: Float = 1f, baseY: Float = 1f): Animator {
        val down = ObjectAnimator.ofFloat(target, View.SCALE_Y, baseY, baseY * 0.82f).setDuration(90)
        val downX = ObjectAnimator.ofFloat(target, View.SCALE_X, baseX, baseX * 1.12f).setDuration(90)
        val up = ObjectAnimator.ofFloat(target, View.SCALE_Y, baseY * 0.82f, baseY).setDuration(320)
        val upX = ObjectAnimator.ofFloat(target, View.SCALE_X, baseX * 1.12f, baseX).setDuration(320)
        up.interpolator = OvershootInterpolator(2.4f)
        upX.interpolator = OvershootInterpolator(2.4f)
        return AnimatorSet().apply {
            playTogether(down, downX)
            playTogether(up, upX)
            play(up).after(down)
        }
    }

    /** 兴奋跳跃（被双击）：向上跃起后落地回弹。 */
    fun jump(target: View): Animator {
        val up = ObjectAnimator.ofFloat(target, View.TRANSLATION_Y, 0f, -target.height * 0.22f)
            .setDuration(220)
        up.interpolator = DecelerateInterpolator()
        val down = ObjectAnimator.ofFloat(target, View.TRANSLATION_Y, -target.height * 0.22f, 0f)
            .setDuration(340)
        down.interpolator = BounceInterpolator()
        return AnimatorSet().apply { playSequentially(up, down) }
    }

    /** 拎起（长按/拖拽）：纵向拉伸 + 轻微放大，模拟"被提溜起来"。（v1.2：相对基准体型） */
    fun grab(target: View, baseX: Float = 1f, baseY: Float = 1f): Animator {
        val stretch = ObjectAnimator.ofFloat(target, View.SCALE_Y, baseY, baseY * 1.14f).setDuration(140)
        val widen = ObjectAnimator.ofFloat(target, View.SCALE_X, baseX, baseX * 0.92f).setDuration(140)
        val lift = ObjectAnimator.ofFloat(target, View.TRANSLATION_Z, 0f, 24f).setDuration(140)
        return AnimatorSet().apply { playTogether(stretch, widen, lift) }
    }

    /** 松手落地：从拉伸态回弹归位。（v1.2：相对基准体型） */
    fun release(target: View, baseX: Float = 1f, baseY: Float = 1f): Animator {
        val sy = ObjectAnimator.ofFloat(target, View.SCALE_Y, baseY * 1.14f, baseY).setDuration(300)
        val sx = ObjectAnimator.ofFloat(target, View.SCALE_X, baseX * 0.92f, baseX).setDuration(300)
        val sz = ObjectAnimator.ofFloat(target, View.TRANSLATION_Z, 24f, 0f).setDuration(300)
        sy.interpolator = OvershootInterpolator(1.8f)
        sx.interpolator = OvershootInterpolator(1.8f)
        return AnimatorSet().apply { playTogether(sy, sx, sz) }
    }

    /** 甩出抛物线：按初速度抛出，重力减速后落地回弹。 */
    fun fling(target: View, velocityX: Float, velocityY: Float): Animator {
        val distance = (velocityX * 0.22f).coerceIn(-target.width * 1.6f, target.width * 1.6f)
        val throwX = ObjectAnimator.ofFloat(
            target, View.TRANSLATION_X, target.translationX, target.translationX + distance
        ).setDuration(420)
        throwX.interpolator = DecelerateInterpolator(1.6f)
        val hop = ObjectAnimator.ofFloat(
            target, View.TRANSLATION_Y, target.translationY, target.translationY - 60f
        ).setDuration(200)
        hop.interpolator = AccelerateInterpolator()
        val land = ObjectAnimator.ofFloat(
            target, View.TRANSLATION_Y, target.translationY - 60f, 0f
        ).setDuration(300)
        land.interpolator = BounceInterpolator()
        return AnimatorSet().apply {
            playTogether(throwX, hop)
            play(land).after(hop)
        }
    }

    /** 张望（行为层 LOOK）：原地小角度左右摆头一次。 */
    fun lookAround(target: View): Animator {
        val look = ObjectAnimator.ofFloat(target, View.ROTATION, 0f, 7f, -6f, 3f, 0f)
            .setDuration(900)
        look.interpolator = AccelerateDecelerateInterpolator()
        return look
    }

    /** 蛋壳晃动（孵化中）：小角度左右摇摆一次。 */
    fun eggWobble(target: View): Animator {
        val rotate = ObjectAnimator.ofFloat(target, View.ROTATION, 0f, -7f, 7f, -4f, 4f, 0f)
            .setDuration(620)
        rotate.interpolator = AccelerateDecelerateInterpolator()
        return rotate
    }
}
