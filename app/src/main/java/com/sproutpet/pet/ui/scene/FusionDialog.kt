package com.sproutpet.pet.ui.scene

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.sproutpet.core.domain.pet.FusionRules
import com.sproutpet.core.domain.pet.Pet
import com.sproutpet.pet.R
import com.sproutpet.pet.data.local.OwnerLocalDataSource
import com.sproutpet.pet.data.repository.FuseFailure
import com.sproutpet.pet.data.repository.FuseOutcome
import com.sproutpet.pet.data.repository.FusionRepository
import com.sproutpet.pet.data.repository.UserRepository
import com.sproutpet.pet.databinding.DialogFusionBinding
import com.sproutpet.pet.ui.common.CommonDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 融合（design-v2 屏 26「融合 · 重复宠的去处」· 底部升起）。
 *
 * 这里**只做选择与告知**，不发明任何数值：
 * - 三枚门槛标签的金币数读 [FusionRules.costGold]
 * - 预览（印记 X → Y/阈值 · 经验 +N · 是否上漂）用 [FusionRules.check] / [FusionRules.resolve]
 *   **纯函数现算**，与最终落库走同一条路径 —— 预览不会骗人
 * - 副宠去向（入后山 · 不可取回）与 ADR-017 红线固定在警示条上，不以任何状态隐藏
 *
 * 主按钮是「二次确认」文案：点击后还会弹一次确认 —— 不可逆操作不允许一键完成（docs/10）。
 */
@AndroidEntryPoint
class FusionDialog : BottomSheetDialogFragment() {

    @Inject lateinit var fusionRepository: FusionRepository
    @Inject lateinit var userRepository: UserRepository
    @Inject lateinit var ownerLocal: OwnerLocalDataSource

    /** 去配对广场（24 屏；设计指定入口 = 图鉴浮卡，图鉴落地前由本入口承担） */
    var onGoPairing: (() -> Unit)? = null

    private var _binding: DialogFusionBinding? = null
    private val binding get() = requireNotNull(_binding)

    private var pets: List<Pet> = emptyList()
    private var target: Pet? = null
    private var material: Pet? = null
    private var gold: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogFusionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.cardTarget.setOnClickListener { pickTarget() }
        binding.cardMaterial.setOnClickListener { pickMaterial() }
        binding.btnConfirm.setOnClickListener { confirmFuse() }
        binding.btnGoPairing.setOnClickListener {
            onGoPairing?.invoke()
            dismiss()
        }

        binding.tvRules.text =
            getString(R.string.fusion_rules, FusionRules.costGold)

        lifecycleScope.launch { reload() }
    }

    private suspend fun reload() {
        val ownerId = ownerLocal.getOrCreateOwnerId()
        pets = fusionRepository.observeFusiblePets(ownerId).first()
        gold = userRepository.current()?.gold ?: 0
        binding.tvGold.text = getString(R.string.fusion_gold, gold)

        if (pets.isEmpty()) {
            binding.tvPreview.text = getString(R.string.fusion_empty)
            binding.btnConfirm.isEnabled = false
            binding.btnConfirm.alpha = 0.4f
            return
        }

        // 默认目标 = 主宠（没有主宠标记则取第一只）；材料默认同物种第一只
        target = pets.firstOrNull { it.isMain } ?: pets.first()
        material = defaultMaterialFor(target)
        refreshPreview()
    }

    /** 材料候选：与目标同物种、非自身、可作材料、非主宠（主宠在仓库层也会拒，提前过滤） */
    private fun materialCandidates(): List<Pet> {
        val t = target ?: return emptyList()
        return pets.filter {
            it.id != t.id &&
                it.species == t.species &&
                FusionRules.canBeMaterial(it.rarity) &&
                !it.isMain
        }
    }

    /** 目标候选：可作融合目标（有更高档可去）且至少存在一个同物种材料 */
    private fun targetCandidates(): List<Pet> =
        pets.filter { t -> FusionRules.canBeTarget(t.rarity) && pets.any {
            it.id != t.id && it.species == t.species &&
                FusionRules.canBeMaterial(it.rarity) && !it.isMain
        } }

    private fun defaultMaterialFor(target: Pet?): Pet? =
        materialCandidates().firstOrNull()

    private fun pickTarget() {
        val candidates = targetCandidates()
        if (candidates.isEmpty()) return
        val labels = candidates.map { label(it) }.toTypedArray()
        val checked = candidates.indexOfFirst { it.id == target?.id }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.fusion_pick_target)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                target = candidates[which]
                // 换目标后，原材料大概率不同物种 —— 重置为合法默认
                material = if (material?.species == target?.species) material
                else defaultMaterialFor(target)
                refreshPreview()
                dialog.dismiss()
            }
            .show()
    }

    private fun pickMaterial() {
        val candidates = materialCandidates()
        if (candidates.isEmpty()) return
        val labels = candidates.map { label(it) }.toTypedArray()
        val checked = candidates.indexOfFirst { it.id == material?.id }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.fusion_pick_material)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                material = candidates[which]
                refreshPreview()
                dialog.dismiss()
            }
            .show()
    }

    private fun label(pet: Pet): String = getString(
        R.string.fusion_pet_item,
        pet.nickname,
        pet.level,
        pet.rarity.displayName,
        if (pet.isMain) getString(R.string.fusion_main_tag) else ""
    )

    /** 预览 = 纯函数现算，与落库同一条路径（不自己重算任何数字） */
    private fun refreshPreview() {
        val t = target
        val m = material
        if (t == null || m == null) {
            binding.tvPreview.text = getString(R.string.fusion_empty)
            binding.btnConfirm.isEnabled = false
            return
        }
        binding.tvTarget.text = label(t)
        binding.tvMaterial.text = label(m)

        val check = FusionRules.check(t, m)
        if (!check.allowed) {
            binding.tvPreview.text = getString(
                R.string.fusion_preview_invalid, check.rejectReason?.message.orEmpty()
            )
            binding.btnConfirm.isEnabled = false
            binding.btnConfirm.alpha = 0.4f
            return
        }
        binding.btnConfirm.alpha = 1f

        val result = FusionRules.resolve(t, t.fusionMarks, m)
        val threshold = FusionRules.thresholdFor(t.rarity)
        val driftText = if (result.drifted) {
            getString(R.string.fusion_preview_drift, result.targetRarity.displayName)
        } else {
            getString(R.string.fusion_preview_cap, result.targetMarks)
        }
        binding.tvPreview.text = getString(
            R.string.fusion_preview,
            t.fusionMarks,
            result.targetMarks,
            threshold ?: 0,
            result.expGained,
            driftText
        )

        val enoughGold = gold >= FusionRules.costGold
        binding.btnConfirm.isEnabled = enoughGold
        binding.btnConfirm.text = if (enoughGold) {
            getString(R.string.fusion_confirm, m.nickname)
        } else {
            getString(R.string.fusion_fail_gold, FusionRules.costGold - gold)
        }
    }

    /** 二次确认（docs/10：不可逆操作必须弹窗再确认一次） */
    private fun confirmFuse() {
        val t = target ?: return
        val m = material ?: return
        val result = FusionRules.resolve(t, t.fusionMarks, m)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.fusion_confirm_title)
            .setMessage(
                getString(
                    R.string.fusion_confirm_message,
                    m.nickname, t.nickname,
                    FusionRules.markWeightOf(m.rarity) ?: 0,
                    result.expGained,
                    m.nickname
                )
            )
            .setPositiveButton(R.string.action_confirm) { _, _ -> doFuse() }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun doFuse() {
        val t = target ?: return
        val m = material ?: return
        lifecycleScope.launch {
            val ownerId = ownerLocal.getOrCreateOwnerId()
            when (val outcome = fusionRepository.fuse(ownerId, t.id, m.id)) {
                is FuseOutcome.Ok -> {
                    showResult(outcome)
                }
                is FuseOutcome.Rejected -> {
                    CommonDialog.newInstance(
                        getString(R.string.fusion_fail_title),
                        failureMessage(outcome.failure)
                    ).show(parentFragmentManager, "fusion_rejected")
                    refreshPreview()
                }
            }
        }
    }

    private suspend fun showResult(outcome: FuseOutcome.Ok) {
        val s = outcome.success
        // 阈值按**融合后**稀有度取 —— 上漂后进度条归位到新档
        val threshold = FusionRules.thresholdFor(s.fusion.targetRarity)
        val driftText = if (s.fusion.drifted) {
            getString(R.string.fusion_success_drift, s.fusion.targetRarity.displayName)
        } else ""
        val levelText = if (s.leveledUp && s.newLevel != null) {
            getString(R.string.fusion_success_level, s.newLevel)
        } else ""
        gold = userRepository.current()?.gold ?: gold
        binding.tvGold.text = getString(R.string.fusion_gold, gold)
        CommonDialog.newInstance(
            getString(R.string.fusion_success_title),
            getString(
                R.string.fusion_success_message,
                s.target.nickname,
                FusionRules.markWeightOf(s.material.rarity) ?: 0,
                s.fusion.targetMarks,
                threshold ?: 0,
                s.expGained,
                driftText,
                levelText,
                s.material.nickname
            ),
            showCancel = false
        ).show(parentFragmentManager, "fusion_done")
        // 重载列表（副宠已入后山，会从候选里消失）
        reload()
    }

    private fun failureMessage(failure: FuseFailure): String = when (failure) {
        is FuseFailure.RuleRejected -> failure.reason.message
        FuseFailure.MaterialIsMain -> getString(R.string.fusion_fail_main)
        is FuseFailure.NotEnoughGold -> getString(R.string.fusion_fail_gold, failure.need)
        FuseFailure.PetNotFound -> getString(R.string.fusion_fail_notfound)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "FusionDialog"
    }
}
