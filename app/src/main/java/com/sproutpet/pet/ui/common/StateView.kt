package com.sproutpet.pet.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.sproutpet.pet.R
import com.sproutpet.pet.databinding.LayoutStateViewBinding

/**
 * 三态视图（docs/04 §1.1）：加载 / 空 / 错误。
 *
 * 默认全部隐藏（即「内容态」），抽屉内列表页按需切换。
 */
class StateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val binding = LayoutStateViewBinding.inflate(LayoutInflater.from(context), this, true)

    /** 加载态 */
    fun showLoading() {
        binding.progress.visibility = VISIBLE
        binding.stateContainer.visibility = GONE
    }

    /** 空态 */
    fun showEmpty(message: String = context.getString(R.string.empty_data)) {
        showMessage(message, showRetry = false)
    }

    /** 错误态（可带重试） */
    fun showError(
        message: String = context.getString(R.string.network_error),
        onRetry: (() -> Unit)? = null
    ) {
        showMessage(message, showRetry = onRetry != null)
        binding.btnRetry.setOnClickListener { onRetry?.invoke() }
    }

    /** 内容态（全隐藏，让出给真实内容） */
    fun showContent() {
        binding.progress.visibility = GONE
        binding.stateContainer.visibility = GONE
    }

    private fun showMessage(message: String, showRetry: Boolean) {
        binding.progress.visibility = GONE
        binding.stateContainer.visibility = VISIBLE
        binding.tvState.text = message
        binding.btnRetry.visibility = if (showRetry) VISIBLE else GONE
    }
}
