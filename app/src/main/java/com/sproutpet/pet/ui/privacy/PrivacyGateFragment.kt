package com.sproutpet.pet.ui.privacy

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.text.HtmlCompat
import androidx.lifecycle.lifecycleScope
import com.sproutpet.core.base.BaseFragment
import com.sproutpet.pet.R
import com.sproutpet.pet.SproutPetApplication
import com.sproutpet.pet.data.local.OwnerLocalDataSource
import com.sproutpet.pet.databinding.FragmentPrivacyGateBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 首启 · 隐私政策全屏门（design-v2 屏 19 · docs/00 §八 红线 5 · `16 §三 K-4`）。
 *
 * **这是全 App 唯一一次全屏阻断**：同意之后处处都是「家」，不再弹任何合规门。
 *
 * 合规要点（缺一不可）：
 * 1. 同意前**不初始化任何采集类 SDK** —— 因此 `SproutPetApplication.onCreate` 里
 *    只做无采集行为的初始化，SDK 初始化统一挂在 [SproutPetApplication.onPrivacyAgreed]
 * 2. 必须提供**拒绝**路径（[R.string.privacy_disagree]）—— 只给「同意」不给「退出」不合规
 * 3. 隐私政策正文必须**可完整阅读**（[R.string.privacy_policy_body]），不能只有一个勾选框
 */
@AndroidEntryPoint
class PrivacyGateFragment : BaseFragment<FragmentPrivacyGateBinding>() {

    @Inject
    lateinit var ownerLocal: OwnerLocalDataSource

    /** 同意后的回调（由 [com.sproutpet.pet.ui.MainActivity] 注入：切到场景） */
    var onAgreed: (() -> Unit)? = null

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentPrivacyGateBinding = FragmentPrivacyGateBinding.inflate(inflater, container, false)

    override fun initView(view: View, savedInstanceState: Bundle?) {
        // 三条实话带 <b> 强调，需要按 HTML 解析（字符串里已转义）
        binding.tvPoint1.text = HtmlCompat.fromHtml(
            getString(R.string.privacy_point_1), HtmlCompat.FROM_HTML_MODE_LEGACY
        )
        binding.tvPoint2.text = HtmlCompat.fromHtml(
            getString(R.string.privacy_point_2), HtmlCompat.FROM_HTML_MODE_LEGACY
        )
        binding.tvPoint3.text = HtmlCompat.fromHtml(
            getString(R.string.privacy_point_3), HtmlCompat.FROM_HTML_MODE_LEGACY
        )
        binding.tvPoint1.movementMethod = LinkMovementMethod.getInstance()

        binding.btnAgree.setOnClickListener { agree() }
        binding.btnPolicy.setOnClickListener { showFullPolicy() }
        binding.btnDisagree.setOnClickListener { disagreeAndExit() }
    }

    private fun agree() {
        binding.btnAgree.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            // 先落盘再放行 —— 反过来的话，进程被杀会导致「同意了但下次又弹」
            ownerLocal.setPrivacyAgreed()
            SproutPetApplication.privacyAgreed = true
            SproutPetApplication.instance.onPrivacyAgreed()
            onAgreed?.invoke()
        }
    }

    private fun showFullPolicy() {
        val text = TextView(requireContext()).apply {
            text = HtmlCompat.fromHtml(
                getString(R.string.privacy_policy_body), HtmlCompat.FROM_HTML_MODE_LEGACY
            )
            textSize = 13.5f
            setPadding(48, 32, 48, 16)
            setTextColor(requireContext().getColor(R.color.text_primary))
            movementMethod = LinkMovementMethod.getInstance()
        }
        val scroll = ScrollView(requireContext()).apply { addView(text) }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.privacy_policy_title)
            .setView(scroll)
            .setPositiveButton(R.string.privacy_agree) { _, _ -> agree() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun disagreeAndExit() {
        toast(getString(R.string.privacy_exit_toast))
        requireActivity().finish()
    }

    companion object {
        const val TAG = "PrivacyGateFragment"
    }
}
