package com.sproutpet.pet.ui.scene

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.sproutpet.pet.databinding.ViewSpeechBubbleBinding

/**
 * 搭话气泡（docs/04 §1.1，docs/00 §4.4）。
 *
 * 宠物主动搭话驱动任务；点击行动按钮即回应（Phase 1：回应 = 摸一次主宠）。
 * 由场景特效层挂载到主宠头顶上方。
 */
class PetSpeechBubbleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val binding = ViewSpeechBubbleBinding.inflate(LayoutInflater.from(context), this, true)

    /**
     * @param text 搭话文案
     * @param actionLabel 行动按钮文案（null 则隐藏按钮）
     * @param onAction 点击行动按钮
     */
    fun bind(text: String, actionLabel: String?, onAction: () -> Unit) {
        binding.tvBubbleText.text = text
        if (actionLabel == null) {
            binding.tvBubbleAction.visibility = GONE
        } else {
            binding.tvBubbleAction.visibility = VISIBLE
            binding.tvBubbleAction.text = actionLabel
            binding.tvBubbleAction.setOnClickListener { onAction() }
        }
    }
}
