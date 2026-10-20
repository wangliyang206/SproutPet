package com.sproutpet.pet.ui.scene

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.sproutpet.pet.R

/**
 * 首只孵化奖励卡（`docs/00 §2.1` 门票来源 · `docs/14 §2.4`）。
 *
 * 为什么值得单独一张卡：
 * 破壳 → 起名之后，用户手里突然多了 **10 张盲盒**。如果只把数字悄悄加进背包，
 * 用户既不知道该干什么，也**不知道十连在哪**（真机反馈的原话：「只有长按蛋才会
 * 弹出十连抽界面，用户是不知道可以长按的」）。所以奖励必须**自带目的地**：
 * 主按钮直接把开箱台叫出来，次按钮只是把票留着。
 *
 * 样式与 [com.sproutpet.pet.ui.common.CommonDialog] 一致（26dp 圆角白卡），
 * 但不复用它的 FragmentResult 机制 —— 这里需要两个明确回调，不需要"全局监听"。
 */
class FirstHatchRewardDialog : DialogFragment() {

    /** 现在去开箱台 */
    var onGoOpenBox: (() -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        val dp = { v: Float -> (v * resources.displayMetrics.density).toInt() }

        val title = TextView(ctx).apply {
            text = getString(R.string.first_reward_title)
            setTextColor(ContextCompat.getColor(ctx, R.color.scene_text))
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val amount = TextView(ctx).apply {
            text = getString(R.string.first_reward_amount)
            setTextColor(ContextCompat.getColor(ctx, R.color.brand_primary_dark))
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(10f), 0, 0)
        }
        val message = TextView(ctx).apply {
            text = getString(R.string.first_reward_body)
            setTextColor(ContextCompat.getColor(ctx, R.color.scene_sub))
            textSize = 13f
            setPadding(0, dp(8f), 0, 0)
        }
        val confirm = TextView(ctx).apply {
            text = getString(R.string.first_reward_go)
            setTextColor(ContextCompat.getColor(ctx, R.color.white))
            textSize = 14.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_btn_primary)
            setPadding(0, dp(11f), 0, dp(11f))
            setOnClickListener {
                dismiss()
                onGoOpenBox?.invoke()
            }
        }
        val later = TextView(ctx).apply {
            text = getString(R.string.first_reward_later)
            setTextColor(ContextCompat.getColor(ctx, R.color.brand_primary_dark))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(10f), 0, 0)
            setOnClickListener { dismiss() }
        }

        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22f), dp(20f), dp(22f), dp(18f))
            setBackgroundResource(R.drawable.bg_card_round)
            addView(title)
            addView(amount)
            addView(message)
            addView(confirm, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(16f) })
            addView(later)
        }

        return Dialog(ctx).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(content)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.82f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    companion object {
        const val TAG = "first_hatch_reward"
    }
}
