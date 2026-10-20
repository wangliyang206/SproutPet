package com.sproutpet.pet.ui.scene

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.sproutpet.core.domain.gacha.GachaRules
import com.sproutpet.core.domain.pet.Pet
import com.sproutpet.core.domain.pet.Rarity
import com.sproutpet.pet.R
import com.sproutpet.pet.data.repository.TenPullOutcome
import com.sproutpet.pet.databinding.DialogTenPullBinding

/**
 * 十连结果 · 出壳队列（design-v2 屏 15 + 屏 31）。
 *
 * 三件事必须同时呈现，缺一条就退回"一只一只孵化"的旧观感：
 * 1. **10 颗蛋横排**（不是 10 行列表），按**稀有度降序** —— 第 1 颗最值钱；
 * 2. **每颗都标自己的破壳时刻**（T+60s / T+80s …），数字直接读 `GachaRules.queueHatchOffsetMs`；
 * 3. **「全部出壳」是零成本的** —— 不耐烦的人不必等满 4 分钟。
 *
 * 本类不计算任何时长，只消费 [TenPullOutcome]（`eggs[i].hatchAt` 已在仓库层按队列写好）。
 */
class TenPullDialog : DialogFragment() {

    var outcome: TenPullOutcome? = null

    /** 「全部出壳」：把整批蛋的 hatchAt 拉到现在（零成本） */
    var onRevealAll: ((List<Pet>) -> Unit)? = null

    /** 去融合（design-v2 屏 26 的指定入口；docs/02 §十一「15 屏去融合」） */
    var onGoFuse: (() -> Unit)? = null

    private var _binding: DialogTenPullBinding? = null
    private val binding get() = requireNotNull(_binding)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogTenPullBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val data = requireNotNull(outcome) { "TenPullDialog 缺少 outcome" }

        binding.tvQueueNote.text = getString(
            R.string.ten_pull_note,
            // 队列总长（秒）直接来自常量，展示层不自己乘
            GachaRules.QUEUE_TOTAL_CAP_MS / 1000,
            GachaRules.QUEUE_STAGGER_MS / 1000
        )
        binding.tvFloorNote.visibility = if (data.floorApplied) View.VISIBLE else View.GONE

        val now = System.currentTimeMillis()
        data.eggs.forEachIndexed { index, egg ->
            binding.llEggs.addView(buildCell(index, egg, now))
        }

        binding.btnRevealAll.setOnClickListener {
            onRevealAll?.invoke(data.eggs)
            dismiss()
        }
        binding.btnLater.setOnClickListener { dismiss() }
        binding.btnGoFuse.setOnClickListener {
            onGoFuse?.invoke()
            dismiss()
        }

        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.94f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    /** 单颗蛋格：蛋 + 稀有度 + 破壳时刻；第 1 颗高亮（design 屏 31 的 `cur` 态） */
    private fun buildCell(index: Int, egg: Pet, now: Long): View {
        val ctx = requireContext()
        val cell = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val eggView = ImageView(ctx).apply {
            setImageResource(
                if (index == 0) R.drawable.pet_egg_cracked else R.drawable.pet_egg_common
            )
            val size = if (index == 0) 40 else 32
            layoutParams = LinearLayout.LayoutParams(dp(size), dp(size)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        cell.addView(eggView)

        cell.addView(TextView(ctx).apply {
            text = rarityLabel(egg.rarity)
            textSize = if (index == 0) 10.5f else 9.5f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(ctx, rarityColorOf(egg.rarity)))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        // 第 1 颗标「现在」的队列位次，其余标相对秒数 —— 全部来自 hatchAt，不重算
        val seconds = ((egg.hatchAt - now) / 1000L).coerceAtLeast(0L)
        cell.addView(TextView(ctx).apply {
            text = getString(R.string.ten_pull_cell, seconds)
            textSize = 8.5f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(ctx, R.color.scene_hint))
        })

        if (index == 0) {
            cell.setBackgroundResource(R.drawable.bg_chip_white)
        }
        return cell
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun rarityLabel(rarity: Rarity): String = when (rarity) {
        Rarity.COMMON -> getString(R.string.rarity_common)
        Rarity.RARE -> getString(R.string.rarity_rare)
        Rarity.EPIC -> getString(R.string.rarity_epic)
        Rarity.LEGENDARY -> getString(R.string.rarity_legendary)
        Rarity.MYTHIC -> getString(R.string.rarity_mythic)
        Rarity.MUTANT -> getString(R.string.rarity_mutant)
    }

    private fun rarityColorOf(rarity: Rarity): Int = when (rarity) {
        Rarity.COMMON -> R.color.rarity_common
        Rarity.RARE -> R.color.rarity_rare
        Rarity.EPIC -> R.color.rarity_epic
        Rarity.LEGENDARY -> R.color.rarity_legendary
        Rarity.MYTHIC -> R.color.rarity_mythic
        Rarity.MUTANT -> R.color.rarity_mutant
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ten_pull"
        fun newInstance(outcome: TenPullOutcome): TenPullDialog =
            TenPullDialog().apply { this.outcome = outcome }
    }
}
