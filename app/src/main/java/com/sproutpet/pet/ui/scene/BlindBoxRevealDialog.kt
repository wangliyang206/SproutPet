package com.sproutpet.pet.ui.scene

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.sproutpet.core.domain.pet.Rarity
import com.sproutpet.pet.R
import com.sproutpet.pet.data.repository.OpenBoxOutcome
import com.sproutpet.pet.databinding.DialogRevealBinding

/**
 * 开箱揭晓 · 底部升起面板（design-v2 屏④ · `K-5` 落地）。
 *
 * v1.1（2026-10-20）：从全屏模态 [DialogFragment] 改为 [BottomSheetDialogFragment] ——
 * 04 屏设计是「底部升起面板 + 场景仍在背后」（[02 §10.2](../../../docs/02-宠物渲染方案.md) 硬规则②
 * 「禁止跳页式全屏切换」），旧实现违背了倒置 IA 的「就地发生」。
 *
 * 必备要素（docs/04 §1.4）：稀有度揭晓、距保底还差 N 抽、概率公示入口。
 */
class BlindBoxRevealDialog : BottomSheetDialogFragment() {

    /** 由场景层在 show 前注入（不跨进程死亡恢复，Phase 1 可接受） */
    var outcome: OpenBoxOutcome? = null

    private var _binding: DialogRevealBinding? = null
    private val binding get() = requireNotNull(_binding)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogRevealBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val data = requireNotNull(outcome) { "BlindBoxRevealDialog 缺少 outcome" }
        val result = data.result

        // 稀有度徽章：底色 = 档位色
        binding.tvRevealRarity.apply {
            text = result.rarity.displayName
            backgroundTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), rarityColorOf(result.rarity))
            )
        }
        binding.tvRevealSpecies.text = result.species.displayName
        binding.tvRevealFamily.text = result.species.family.displayName
        binding.tvRevealGender.text = data.egg.gender.displayName
        binding.tvRevealGeneration.text = getString(R.string.reveal_kv_generation, data.egg.generation)
        binding.tvRevealPity.text = if (result.isLucky) {
            getString(R.string.reveal_pity_triggered)
        } else {
            getString(R.string.reveal_pity, data.pityRemaining)
        }

        binding.btnRevealHome.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun rarityColorOf(rarity: Rarity): Int = when (rarity) {
        Rarity.COMMON -> R.color.rarity_common
        Rarity.RARE -> R.color.rarity_rare
        Rarity.EPIC -> R.color.rarity_epic
        Rarity.LEGENDARY -> R.color.rarity_legendary
        Rarity.MYTHIC -> R.color.rarity_mythic
        Rarity.MUTANT -> R.color.rarity_mutant
    }

    companion object {
        const val TAG = "blind_box_reveal"
        fun newInstance(outcome: OpenBoxOutcome): BlindBoxRevealDialog =
            BlindBoxRevealDialog().apply { this.outcome = outcome }
    }
}
