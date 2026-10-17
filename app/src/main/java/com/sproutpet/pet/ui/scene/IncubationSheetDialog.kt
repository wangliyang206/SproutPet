package com.sproutpet.pet.ui.scene

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.sproutpet.core.domain.incubation.IncubationEngine
import com.sproutpet.core.domain.pet.Pet
import com.sproutpet.pet.R
import com.sproutpet.pet.data.local.OwnerLocalDataSource
import com.sproutpet.pet.databinding.SheetIncubationBinding
import com.sproutpet.pet.domain.item.UseItemUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 孵化底部抽层（design-v2 屏⑤）。
 *
 * - 倒计时每秒刷新（`IncubationEngine.stageDescription` 给进度文案）
 * - 专注陪伴：打开 [FocusFragment] 番茄钟页，结束/提前停止按实际时长抵扣
 *   （抵扣比例与单蛋上限的执法点在 core `IncubationEngine`）
 * - 道具加速 / 好友助力：G-08 道具已接入 `speed_up_hour`；社交属 Phase 7，优雅降级为「敬请期待」
 */
@AndroidEntryPoint
class IncubationSheetDialog : BottomSheetDialogFragment() {

    /** 由场景层在 show 前注入 */
    var egg: Pet? = null

    /** 打开专注番茄钟页 */
    var onStartFocus: ((Pet) -> Unit)? = null

    /** 「敬请期待」提示回调 */
    var onPlaceholder: ((String) -> Unit)? = null

    @Inject lateinit var useItemUseCase: UseItemUseCase
    @Inject lateinit var ownerLocal: OwnerLocalDataSource

    private var _binding: SheetIncubationBinding? = null
    private val binding get() = requireNotNull(_binding)

    private var ticker: CountDownTimer? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetIncubationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val data = requireNotNull(egg) { "IncubationSheetDialog 缺少 egg" }

        // 倒计时：每秒刷新
        renderCountdown(data)
        ticker = object : CountDownTimer(data.hatchRemainMs(), 1000L) {
            override fun onTick(millisUntilFinished: Long) = renderCountdown(requireNotNull(egg))
            override fun onFinish() {
                binding.tvSheetCountdown.text = getString(R.string.scene_egg_tag_ready)
                binding.tvSheetStage.text = IncubationEngine.stageDescription(1f)
            }
        }.start()

        // 三行条目
        bindRow(binding.rowFocus, "🌿", getString(R.string.incubate_focus), getString(R.string.incubate_focus_desc))
        bindRow(binding.rowItem, "⚡", getString(R.string.incubate_item), getString(R.string.incubate_item_desc))
        bindRow(binding.rowFriend, "👫", getString(R.string.incubate_friend), getString(R.string.incubate_friend_desc))

        binding.rowFocus.root.setOnClickListener { onStartFocus?.invoke(data) }
        binding.rowItem.root.setOnClickListener { useSpeedUpItem(data) }
        binding.rowFriend.root.setOnClickListener {
            onPlaceholder?.invoke(getString(R.string.incubate_friend))
        }
    }

    private fun bindRow(row: com.sproutpet.pet.databinding.ViewSheetRowBinding, icon: String, title: String, desc: String) {
        row.tvRowIcon.text = icon
        row.tvRowTitle.text = title
        row.tvRowDesc.text = desc
    }

    /** 使用「时间沙漏」加速孵化（docs/04 §2.4） */
    private fun useSpeedUpItem(data: Pet) {
        viewLifecycleOwner.lifecycleScope.launch {
            val ownerId = ownerLocal.getOrCreateOwnerId()
            val result = useItemUseCase.execute(SPEED_UP_ITEM_ID, ownerId, data.id)
            when (result) {
                is UseItemUseCase.Result.Success -> {
                    result.pet?.let {
                        egg = it
                        renderCountdown(it)
                    }
                    Toast.makeText(requireContext(), "孵化时间 -1 小时", Toast.LENGTH_SHORT).show()
                }
                UseItemUseCase.Result.NotEnough ->
                    Toast.makeText(requireContext(), "没有可用的时间沙漏", Toast.LENGTH_SHORT).show()
                else -> Unit
            }
        }
    }

    private fun renderCountdown(data: Pet) {
        val remain = data.hatchRemainMs()
        val totalSec = remain / 1000
        val mm = totalSec / 60
        val ss = totalSec % 60
        binding.tvSheetCountdown.text = String.format("%02d:%02d", mm, ss)
        binding.tvSheetStage.text = IncubationEngine.stageDescription(data.hatchProgress())
    }

    override fun onDestroyView() {
        ticker?.cancel()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "incubation_sheet"

        /** 加速道具 id（ItemRegistry.speed_up_hour，减少 1 小时孵化） */
        private const val SPEED_UP_ITEM_ID = "speed_up_hour"

        fun newInstance(egg: Pet): IncubationSheetDialog =
            IncubationSheetDialog().apply { this.egg = egg }
    }
}
