package com.sproutpet.pet.ui.scene

import android.annotation.SuppressLint
import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View

/**
 * 宠物手势控制器（docs/04 §1.1/§1.3）。
 *
 * 手势语义（倒置 IA，就地反馈不跳页）：
 * - **摸（单击）** = 抚摸
 * - **双击** = 逗弄（兴奋跳跃）
 * - **长按** = 拎起，之后可拖拽，松手回落，快速甩出带抛物线
 *
 * 只负责识别与视图位移，业务反馈（加经验 / 飘字）经 [Listener] 回调给场景层。
 */
class PetGestureController(
    context: Context,
    private val target: View,
    private val listener: Listener
) : View.OnTouchListener {

    /** 手势事件回调（单击/双击已做 100ms 内首帧反馈的时序保障在场景层） */
    interface Listener {
        /** 单击：抚摸 */
        fun onPet()
        /** 双击：逗弄 */
        fun onExcite()
        /** 长按拎起（开始拖拽） */
        fun onGrabStart()
        /** 拖拽中（相对偏移，用于四肢下垂姿态） */
        fun onDragging(offsetX: Float, offsetY: Float)
        /** 松手（[fling] 为 true 表示甩出） */
        fun onRelease(fling: Boolean)
    }

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            listener.onPet()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            listener.onExcite()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            grabbing = true
            downRawX = e.rawX - target.translationX
            downRawY = e.rawY - target.translationY
            velocityTracker = VelocityTracker.obtain().also { it.addMovement(e) }
            listener.onGrabStart()
        }
    })

    private var grabbing = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var velocityTracker: VelocityTracker? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        if (!grabbing) return true

        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                target.translationX = event.rawX - downRawX
                target.translationY = event.rawY - downRawY
                velocityTracker?.addMovement(event)
                listener.onDragging(target.translationX, target.translationY)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val vt = velocityTracker
                var fling = false
                if (vt != null && event.actionMasked == MotionEvent.ACTION_UP) {
                    vt.addMovement(event)
                    vt.computeCurrentVelocity(1000)
                    if (kotlin.math.abs(vt.xVelocity) > FLING_THRESHOLD) {
                        fling = true
                        com.sproutpet.pet.pet.renderer.frame.PetViewAnims
                            .fling(target, vt.xVelocity, vt.yVelocity).start()
                    }
                }
                grabbing = false
                vt?.recycle()
                velocityTracker = null
                listener.onRelease(fling)
            }
        }
        return true
    }

    /** 挂到目标视图上 */
    @SuppressLint("ClickableViewAccessibility")
    fun attach() = target.setOnTouchListener(this)

    companion object {
        /** 甩出速度阈值（px/s） */
        private const val FLING_THRESHOLD = 1400f
    }
}
