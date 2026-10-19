package com.sproutpet.pet.ui.scene

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.sproutpet.core.domain.gacha.GachaRules
import com.sproutpet.pet.R
import com.sproutpet.pet.data.local.OwnerLocalDataSource
import com.sproutpet.pet.data.repository.ItemRepository
import com.sproutpet.pet.databinding.SheetOpenBoxBinding
import com.sproutpet.pet.ui.common.CommonDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 开箱台（design-v2 屏 14 · 底部升起）。
 *
 * 这里**只做入口与门槛**，不重算任何概率或时长：
 * - 保底进度来自 `BlindBoxEngine.remainingToPity`（经仓库的 Flow 传进来）
 * - 门票数量来自 `ItemRepository.availableCount(BOX_ITEM_ID)`
 * - 十连门槛 = `GachaRules.TEN_PULL`（10 张），时长由 `GachaRules.queueOffsetsMs` 决定
 *
 * ⚠️ 为什么要单独一屏：产品口径是「**只做十连**」（`00 §2.1`），
 * 而在此之前代码里只有一个点蛋就单抽的入口 —— 「十连」在 UI 上根本不存在（`docs/16 K-1`）。
 */
@AndroidEntryPoint
class OpenBoxSheetDialog : BottomSheetDialogFragment() {

    /** 保底进度（距史诗还差几抽），由场景层传入 */
    var pityRemaining: Int = GachaRules.TEN_PULL

    /** 十连（门票已扣、结果已落库，交给场景层弹结果页） */
    var onTenPull: (() -> Unit)? = null

    /** 单抽（保持既有链路） */
    var onSingle: (() -> Unit)? = null

    /** 去商店补票 */
    var onGoShop: (() -> Unit)? = null

    @Inject lateinit var itemRepository: ItemRepository
    @Inject lateinit var ownerLocal: OwnerLocalDataSource

    private var _binding: SheetOpenBoxBinding? = null
    private val binding get() = requireNotNull(_binding)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetOpenBoxBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.tvPity.text = getString(R.string.open_box_pity, pityRemaining)

        binding.btnTenPull.setOnClickListener {
            val have = tickets
            if (have < GachaRules.TEN_PULL) {
                // 说清差几张，而不是「按钮灰着不解释」
                binding.tvTenHint.text =
                    getString(R.string.open_box_need_ticket, GachaRules.TEN_PULL - have)
                return@setOnClickListener
            }
            dismiss()
            onTenPull?.invoke()
        }

        binding.btnSingle.setOnClickListener {
            if (tickets < 1) {
                binding.tvTenHint.text = getString(R.string.open_box_need_ticket, 1)
                return@setOnClickListener
            }
            dismiss()
            onSingle?.invoke()
        }

        binding.btnOdds.setOnClickListener { showOdds() }
        binding.btnGoShop.setOnClickListener {
            dismiss()
            onGoShop?.invoke()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val ownerId = ownerLocal.getOrCreateOwnerId()
            tickets = itemRepository.availableCount(ownerId, GachaRules.BOX_ITEM_ID)
            binding.tvTicketCount.text = getString(R.string.open_box_ticket_count, tickets)
            if (tickets < GachaRules.TEN_PULL) {
                binding.tvTenHint.text =
                    getString(R.string.open_box_need_ticket, GachaRules.TEN_PULL - tickets)
            }
        }
    }

    private var tickets: Int = 0

    /** 概率公示（00 §6.3 承诺「概率全公开」；中国区上架必含综合概率） */
    private fun showOdds() {
        CommonDialog.newInstance(
            title = getString(R.string.open_box_odds_title),
            message = getString(
                R.string.open_box_odds_body,
                GachaRules.COMPREHENSIVE_EPIC_PLUS_RATE * 100f
            )
        ).show(parentFragmentManager, "odds")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "open_box_sheet"
        fun newInstance(pityRemaining: Int): OpenBoxSheetDialog =
            OpenBoxSheetDialog().apply { this.pityRemaining = pityRemaining }
    }
}
