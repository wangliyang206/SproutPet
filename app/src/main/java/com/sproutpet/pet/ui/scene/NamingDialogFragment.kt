package com.sproutpet.pet.ui.scene

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.sproutpet.core.domain.pet.Pet
import com.sproutpet.pet.R
import com.sproutpet.pet.databinding.DialogNamingBinding
import com.sproutpet.pet.pet.renderer.PetAssetResolver

/**
 * 强制起名弹窗（design-v2 屏⑥）：出生六维 + 必填昵称。
 *
 * **不可取消**（`isCancelable = false` + 屏蔽返回键）——
 * 强制命名是首日留存仪式的一部分（docs/04 Phase 1 验收「可破壳并强制起名」）。
 */
class NamingDialogFragment : DialogFragment() {

    /** 由场景层在 show 前注入 */
    var pet: Pet? = null

    /** > 0 时在弹窗内亮出「+N 连抽已到账」奖励条（docs/14 §1.6 · 2026-10-22） */
    var rewardTickets: Int = 0

    /** 命名确认回调（petId, name）；空名不会触发 */
    var onNamed: ((Long, String) -> Unit)? = null

    private var _binding: DialogNamingBinding? = null
    private val binding get() = requireNotNull(_binding)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        Dialog(requireContext()).apply {
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setCancelable(false)
            setCanceledOnTouchOutside(false)
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogNamingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val data = requireNotNull(pet) { "NamingDialogFragment 缺少 pet" }
        isCancelable = false

        // ⚠️ 起名卡是用户看见"我这只到底长什么样"的第二个画面（第一个是破壳），
        // 这里曾经硬编码 pet_sprout_chicken_*，导致**不管抽到什么都显示同一只鸡**
        // （docs/15 §0.1 的"全是鸡"在破壳后的残留）。取图必须走唯一执法点。
        binding.ivNamingPet.setImageResource(
            PetAssetResolver.resolve(requireContext(), data.species, data.stage)
        )
        renderStats(data)

        binding.etNamingInput.setText(data.nickname)
        binding.etNamingInput.setSelection(binding.etNamingInput.text.length)
        // 首孵奖励与起名**同屏**出现（docs/14 §1.6 · 2026-10-22）：
        // 用户点蛋 → 起名弹窗里就看到"+10 连抽"，不用等下一张卡才知道自己拿到了什么
        if (rewardTickets > 0) {
            binding.tvNamingReward.text = getString(R.string.naming_reward, rewardTickets)
            binding.tvNamingReward.visibility = View.VISIBLE
        }
        binding.btnNamingConfirm.setOnClickListener {
            val name = binding.etNamingInput.text.toString().trim()
            if (name.isEmpty()) {
                binding.etNamingInput.error = getString(R.string.naming_empty_error)
                return@setOnClickListener
            }
            onNamed?.invoke(data.id, name)
            dismiss()
        }
    }

    private fun renderStats(data: Pet) {
        val ctx = requireContext()
        val stats = listOf(
            "体力" to data.attributes.stamina,
            "力量" to data.attributes.strength,
            "敏捷" to data.attributes.agility,
            "智慧" to data.attributes.wisdom,
            "魅力" to data.attributes.charm,
            "亲密" to data.attributes.intimacy
        )
        val cellWidth = resources.displayMetrics.widthPixels.let { (it * 0.86f - 44 * it / 390f) }.toInt() / 3
        stats.forEach { (label, value) ->
            val cell = TextView(ctx).apply {
                text = "$label\n$value"
                gravity = android.view.Gravity.CENTER
                setBackgroundResource(R.drawable.bg_kv_chip)
                setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.scene_text))
                textSize = 12f
                setPadding(0, 16, 0, 16)
            }
            val params = GridLayout.LayoutParams().apply {
                width = cellWidth - 16
                setMargins(4, 4, 4, 4)
            }
            binding.gridNamingStats.addView(cell, params)
        }
    }


    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.86f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "naming_dialog"
        fun newInstance(pet: Pet, rewardTickets: Int = 0): NamingDialogFragment =
            NamingDialogFragment().apply {
                this.pet = pet
                this.rewardTickets = rewardTickets
            }
    }
}
