package com.sproutpet.pet.ui.scene

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.sproutpet.core.domain.pet.GrowthRules
import com.sproutpet.core.domain.pet.Pet
import com.sproutpet.core.domain.social.NpcCandidateRules
import com.sproutpet.core.domain.social.Pairing
import com.sproutpet.pet.R
import com.sproutpet.pet.data.local.OwnerLocalDataSource
import com.sproutpet.pet.data.repository.PairingFailure
import com.sproutpet.pet.data.repository.PairingRepository
import com.sproutpet.pet.data.repository.PairingStartOutcome
import com.sproutpet.pet.data.repository.BreedOutcome
import com.sproutpet.pet.data.repository.UserRepository
import com.sproutpet.pet.databinding.DialogPairingPlazaBinding
import com.sproutpet.pet.ui.common.CommonDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 配对广场（design-v2 屏 24「找搭档」· 底部升起；配对后内嵌 25 屏的「配对进行」态）。
 *
 * 这里**只做选择与告知**，不发明任何数值：
 * - 前置清单的门槛数字读 [GrowthRules.PairingRules]（亲密度 70 / 金币 3000）
 * - NPC 候选来自 [NpcCandidateRules.candidatesFor]（`W-16-1` 拍板口径的唯一出口），
 *   「换一批」= 换随机种子，规则层不藏状态
 * - NPC **免确认**立即配对（ADR-011）；配对语义 =「生育搭档」，宠物归属永不变更
 * - 冷却与名额由 `Pairing.canBreedNow` 判定，UI 只展示倒计时
 *
 * 📌 入口偏差（诚实登记，见 `16 §二` 24 行）：设计指定入口 = 图鉴浮卡（08 屏），
 * 图鉴未落地前本轮入口 = 融合页「去配对广场」跳转。
 */
@AndroidEntryPoint
class PairingPlazaDialog : BottomSheetDialogFragment() {

    @Inject lateinit var pairingRepository: PairingRepository
    @Inject lateinit var userRepository: UserRepository
    @Inject lateinit var ownerLocal: OwnerLocalDataSource

    private var _binding: DialogPairingPlazaBinding? = null
    private val binding get() = requireNotNull(_binding)

    private var pairable: List<Pet> = emptyList()
    private var mine: Pet? = null
    private var candidates: List<NpcCandidateRules.NpcCandidate> = emptyList()
    private var gold: Int = 0
    private var activePairing: Pairing? = null
    private var seed: Long = System.currentTimeMillis()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogPairingPlazaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.cardMine.setOnClickListener { pickMine() }
        binding.btnRefresh.setOnClickListener {
            seed = System.currentTimeMillis()
            lifecycleScope.launch { refreshCandidates() }
        }
        binding.btnBreed.setOnClickListener { breed() }
        binding.btnRelease.setOnClickListener { release() }

        lifecycleScope.launch { reload() }
    }

    private suspend fun reload() {
        val ownerId = ownerLocal.getOrCreateOwnerId()
        pairable = pairingRepository.observePairablePets(ownerId).first()
        gold = userRepository.current()?.gold ?: 0
        binding.tvGold.text = getString(R.string.pairing_gold, gold)

        // 默认选第一只可配宠；它若已在配对中 → 直接进入「配对进行」态
        mine = pairable.firstOrNull { it.spouseId == null } ?: pairable.firstOrNull()
        mine?.let { m -> activePairing = pairingRepository.activePairingOf(m.id) }

        refreshMineRow()
        refreshChecklist()
        if (activePairing != null) {
            showPairingStatus()
        } else {
            binding.llPairingStatus.visibility = View.GONE
            binding.llCandidates.removeAllViews()
            refreshCandidates()
        }
    }

    private suspend fun refreshCandidates() {
        val m = mine
        binding.llCandidates.removeAllViews()
        if (m == null || activePairing != null) return
        candidates = pairingRepository.npcCandidates(m, seed)
        candidates.forEach { candidate ->
            binding.llCandidates.addView(buildCandidateRow(candidate))
        }
    }

    private fun refreshMineRow() {
        binding.tvMine.text = mine?.let {
            getString(
                R.string.pairing_pet_item,
                it.nickname, it.level, it.attributes.intimacy,
                if (it.spouseId != null) getString(R.string.pairing_paired_tag) else ""
            )
        } ?: getString(R.string.pairing_mine_none)
    }

    /** 前置清单：数字全部来自 PairingRules / 当前数据 */
    private fun refreshChecklist() {
        val m = mine
        val rules = GrowthRules.PairingRules
        if (m == null) {
            binding.tvChecklist.text = getString(R.string.pairing_mine_none)
            return
        }
        binding.tvChecklist.text = if (m.canPair) {
            getString(
                R.string.pairing_checklist_ok,
                rules.requiredIntimacy, rules.costGold, gold
            )
        } else {
            getString(
                R.string.pairing_checklist_immature,
                rules.requiredIntimacy, rules.costGold, gold
            )
        }
    }

    private fun kindLabel(sameFamily: Boolean): String =
        getString(if (sameFamily) R.string.pairing_same_family else R.string.pairing_cross_family)

    private fun buildCandidateRow(candidate: NpcCandidateRules.NpcCandidate): View {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_btn_ghost)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
        }
        row.addView(TextView(ctx).apply {
            text = getString(
                R.string.pairing_candidate,
                candidate.nickname, candidate.species.displayName, candidate.level,
                kindLabel(candidate.sameFamily),
                " · ${candidate.gender.displayName}"
            )
            textSize = 13f
            setTextColor(ContextCompat.getColor(ctx, R.color.scene_text))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(ctx).apply {
            text = getString(R.string.pairing_pair_now)
            textSize = 13.5f
            setTextColor(ContextCompat.getColor(ctx, R.color.brand_primary_dark))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(10), 0, 0, 0)
            setOnClickListener { startPairing(candidate) }
        })
        return row
    }

    private fun pickMine() {
        if (pairable.isEmpty()) return
        val labels = pairable.map {
            getString(
                R.string.pairing_pet_item,
                it.nickname, it.level, it.attributes.intimacy,
                if (it.spouseId != null) getString(R.string.pairing_paired_tag) else ""
            )
        }.toTypedArray()
        val checked = pairable.indexOfFirst { it.id == mine?.id }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.pairing_pick_mine)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                lifecycleScope.launch {
                    mine = pairable[which]
                    activePairing = pairingRepository.activePairingOf(mine!!.id)
                    seed = System.currentTimeMillis()
                    refreshMineRow()
                    refreshChecklist()
                    if (activePairing != null) {
                        showPairingStatus()
                    } else {
                        binding.llPairingStatus.visibility = View.GONE
                        refreshCandidates()
                    }
                    dialog.dismiss()
                }
            }
            .show()
    }

    private fun startPairing(candidate: NpcCandidateRules.NpcCandidate) {
        val m = mine ?: return
        lifecycleScope.launch {
            val ownerId = ownerLocal.getOrCreateOwnerId()
            when (val outcome = pairingRepository.startNpcPairing(ownerId, m.id, candidate)) {
                is PairingStartOutcome.Ok -> {
                    gold = userRepository.current()?.gold ?: gold
                    binding.tvGold.text = getString(R.string.pairing_gold, gold)
                    showPairingSuccess(outcome, m, candidate)
                    // 进入「配对进行」态
                    activePairing = pairingRepository.activePairingOf(m.id)
                    refreshMineRow()
                    showPairingStatus()
                    binding.llCandidates.removeAllViews()
                }
                is PairingStartOutcome.Rejected -> {
                    CommonDialog.newInstance(
                        getString(R.string.pairing_fail_title),
                        startFailureMessage(outcome.failure)
                    ).show(parentFragmentManager, "pairing_rejected")
                }
            }
        }
    }

    /** 25 屏简化态：搭档信息 + 冷却倒计时 + 生育/解除 */
    private suspend fun showPairingStatus() {
        val pairing = activePairing ?: return
        val m = mine ?: return
        val npcPet = pairingRepositoryPartner(pairing)
        binding.llPairingStatus.visibility = View.VISIBLE
        binding.tvPartner.text = npcPet?.let {
            getString(
                R.string.pairing_partner,
                it.nickname, it.species.displayName, it.level
            )
        } ?: getString(R.string.pairing_partner, "？", "？", 0)

        val rules = GrowthRules.PairingRules
        val quota = rules.maxOffspringFor(partnerIsNpc = true)
        val remainDays = pairing.breedCooldownRemainMs() / (24 * 60 * 60 * 1000L)
        binding.tvPairingState.text = if (pairing.canBreedNow()) {
            getString(R.string.pairing_state_ready, pairing.offspringCount, quota)
        } else {
            getString(R.string.pairing_state_cooldown, remainDays, pairing.offspringCount, quota)
        }
        binding.btnBreed.isEnabled = pairing.canBreedNow()
        binding.btnBreed.alpha = if (pairing.canBreedNow()) 1f else 0.5f
    }

    private suspend fun pairingRepositoryPartner(pairing: Pairing): Pet? {
        val m = mine ?: return null
        return pairingRepository.queryPetById(pairing.partnerOf(m.id))
    }

    private fun breed() {
        val pairing = activePairing ?: return
        val m = mine ?: return
        lifecycleScope.launch {
            val ownerId = ownerLocal.getOrCreateOwnerId()
            when (val outcome = pairingRepository.breed(ownerId, pairing.id, System.currentTimeMillis())) {
                is BreedOutcome.Ok -> {
                    val baby = outcome.offspring
                    activePairing = pairingRepository.activePairingOf(m.id)
                    showPairingStatus()
                    CommonDialog.newInstance(
                        getString(R.string.pairing_offspring_title),
                        getString(
                            R.string.pairing_offspring_message,
                            baby.nickname, baby.species.displayName, baby.rarity.displayName,
                            baby.generation
                        ),
                        showCancel = false
                    ).show(parentFragmentManager, "pairing_offspring")
                }
                is BreedOutcome.Rejected -> {
                    CommonDialog.newInstance(
                        getString(R.string.pairing_fail_title),
                        breedFailureMessage(outcome.failure)
                    ).show(parentFragmentManager, "pairing_breed_rejected")
                }
            }
        }
    }

    private fun release() {
        val pairing = activePairing ?: return
        val m = mine ?: return
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.pairing_release)
            .setMessage(getString(R.string.pairing_footer))
            .setPositiveButton(R.string.action_confirm) { _, _ ->
                lifecycleScope.launch {
                    pairingRepository.releasePairing(pairing.id)
                    activePairing = null
                    mine = mine?.copy(spouseId = null)
                    refreshMineRow()
                    refreshChecklist()
                    binding.llPairingStatus.visibility = View.GONE
                    refreshCandidates()
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showPairingSuccess(
        outcome: PairingStartOutcome.Ok,
        mine: Pet,
        candidate: NpcCandidateRules.NpcCandidate
    ) {
        val kindText = when (outcome.kind) {
            GrowthRules.BreedingRules.PairingKind.SAME_FAMILY -> getString(R.string.pairing_kind_same)
            GrowthRules.BreedingRules.PairingKind.CROSS_FAMILY -> getString(R.string.pairing_kind_cross)
            GrowthRules.BreedingRules.PairingKind.EXOTIC_MIX -> getString(R.string.pairing_kind_exotic_mix)
            GrowthRules.BreedingRules.PairingKind.EXOTIC_PAIR -> getString(R.string.pairing_kind_exotic_pair)
        }
        CommonDialog.newInstance(
            getString(R.string.pairing_success_title),
            getString(
                R.string.pairing_success_message,
                mine.nickname, candidate.nickname, kindText,
                candidate.species.displayName,
                outcome.kind.upwardBonus
            ),
            showCancel = false
        ).show(parentFragmentManager, "pairing_success")
    }

    private fun startFailureMessage(failure: PairingFailure): String = when (failure) {
        PairingFailure.PetNotPairable -> getString(R.string.pairing_fail_immature)
        PairingFailure.AlreadyPaired -> getString(R.string.pairing_fail_already)
        is PairingFailure.NotEnoughGold -> getString(R.string.pairing_fail_gold, failure.need)
        is PairingFailure.Cooldown -> getString(R.string.pairing_fail_cooldown, failure.remainMs / (24 * 60 * 60 * 1000L))
        PairingFailure.NoOffspringQuota -> getString(R.string.pairing_fail_quota, getString(R.string.pairing_kind_same))
        PairingFailure.PairingNotFound, PairingFailure.PetNotFound ->
            getString(R.string.pairing_fail_notfound)
    }

    private fun breedFailureMessage(failure: PairingFailure): String = when (failure) {
        is PairingFailure.Cooldown -> getString(R.string.pairing_fail_cooldown, failure.remainMs / (24 * 60 * 60 * 1000L))
        else -> startFailureMessage(failure)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "PairingPlazaDialog"
    }
}
