package com.sproutpet.pet.ui.common

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.sproutpet.pet.R

/**
 * 通用弹窗（docs/04 §1.1）：标题 + 正文 + 主/次按钮。
 *
 * 统一弹窗样式（26dp 圆角白卡），起名仪式等少数模态用。
 */
class CommonDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        val args = requireArguments()
        val dp = { v: Float -> (v * resources.displayMetrics.density).toInt() }

        val title = TextView(ctx).apply {
            text = args.getString(ARG_TITLE).orEmpty()
            setTextColor(ContextCompat.getColor(ctx, R.color.scene_text))
            textSize = 16.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val message = TextView(ctx).apply {
            text = args.getString(ARG_MESSAGE).orEmpty()
            setTextColor(ContextCompat.getColor(ctx, R.color.scene_sub))
            textSize = 13.5f
            setPadding(0, dp(12f), 0, 0)
        }
        val confirm = TextView(ctx).apply {
            text = args.getString(ARG_CONFIRM).orEmpty().ifEmpty { getString(R.string.action_confirm) }
            setTextColor(ContextCompat.getColor(ctx, R.color.white))
            textSize = 14.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setBackgroundResource(R.drawable.bg_btn_primary)
            setPadding(0, dp(11f), 0, dp(11f))
            setOnClickListener {
                parentFragmentManager.setFragmentResult(
                    REQUEST_KEY, Bundle().apply { putBoolean(RESULT_CONFIRMED, true) }
                )
                dismiss()
            }
        }
        val cancel = TextView(ctx).apply {
            text = args.getString(ARG_CANCEL).orEmpty().ifEmpty { getString(R.string.action_cancel) }
            setTextColor(ContextCompat.getColor(ctx, R.color.brand_primary_dark))
            textSize = 13f
            gravity = android.view.Gravity.CENTER
            setPadding(0, dp(10f), 0, 0)
            setOnClickListener { dismiss() }
            visibility = if (args.getBoolean(ARG_SHOW_CANCEL, true)) android.view.View.VISIBLE else android.view.View.GONE
        }

        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22f), dp(20f), dp(22f), dp(18f))
            setBackgroundResource(R.drawable.bg_card_round)
            addView(title)
            addView(message)
            addView(confirm, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(18f) })
            addView(cancel)
        }

        return Dialog(ctx).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(content)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.8f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    companion object {
        const val REQUEST_KEY = "common_dialog"
        const val RESULT_CONFIRMED = "confirmed"

        private const val ARG_TITLE = "title"
        private const val ARG_MESSAGE = "message"
        private const val ARG_CONFIRM = "confirm"
        private const val ARG_CANCEL = "cancel"
        private const val ARG_SHOW_CANCEL = "show_cancel"

        fun newInstance(
            title: String,
            message: String,
            confirm: String? = null,
            cancel: String? = null,
            showCancel: Boolean = true
        ): CommonDialog = CommonDialog().apply {
            arguments = Bundle().apply {
                putString(ARG_TITLE, title)
                putString(ARG_MESSAGE, message)
                putString(ARG_CONFIRM, confirm)
                putString(ARG_CANCEL, cancel)
                putBoolean(ARG_SHOW_CANCEL, showCancel)
            }
        }
    }
}
