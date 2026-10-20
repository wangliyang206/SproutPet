package com.sproutpet.pet.ui.scene

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.sproutpet.core.domain.pet.HomeSlots
import com.sproutpet.core.domain.pet.Pet
import com.sproutpet.pet.R
import com.sproutpet.pet.data.local.OwnerLocalDataSource
import com.sproutpet.pet.data.repository.PetRepository
import com.sproutpet.pet.pet.renderer.PetAssetResolver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 「院子」：家里站不下的那些宠物（`home_slot = -1`）。
 *
 * ## 为什么要它（2026-10-20 崩溃修复的另一半）
 *
 * 三层链条是 **拥有不限 ⊃ 家在册 6 ⊃ 桌面 3**。首只孵化奖励送 10 抽 → 十连开出 10 只，
 * 家里只站得下 6 只。**修好崩溃之后，多出来的 4 只如果没有任何入口，就等于消失** ——
 * 崩溃变成"静默丢宠"，比崩溃更难被发现，也更伤。
 *
 * ## 接回家的两条路径
 *
 * - 家没满 → 直接占空槽（`PetRepository.bringHome`）
 * - 家满了 → **先让用户选一只收起**（不静默替换：被换下去的那只也是用户养了很久的）
 *
 * ⚠️ 主宠不能收起（`HomeSlots.validate` 不变量④：主宠恒在家、恒占桌面 0 号），
 * 它的按钮显示为「主宠 · 不能收起」。
 */
@AndroidEntryPoint
class YardDialog : BottomSheetDialogFragment() {

    @Inject
    lateinit var petRepository: PetRepository

    @Inject
    lateinit var local: OwnerLocalDataSource

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val scroll = ScrollView(ctx).apply { overScrollMode = View.OVER_SCROLL_NEVER }
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(24f), dp(20f), dp(28f))
            background = GradientDrawable().apply {
                cornerRadius = dp(26f).toFloat()
                setColor(Color.WHITE)
            }
        }
        scroll.addView(card)
        return scroll
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            render(view as LinearLayout)
        }
    }

    private suspend fun render(card: LinearLayout) {
        card.removeAllViews()
        val ctx = requireContext()
        val ownerId = local.getOrCreateOwnerId()
        // 一次性快照即可：这是模态列表，不做实时订阅（避免列表在用户手指下跳动、误点错人）
        val yard = petRepository.observeYardPets(ownerId).first()
        val home = petRepository.observeHomePets(ownerId).first()

        // ── 头部 ──
        card.addView(TextView(ctx).apply {
            text = getString(R.string.yard_title)
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFF212121.toInt())
        })
        card.addView(TextView(ctx).apply {
            text = getString(R.string.yard_subtitle, home.size, HomeSlots.MAX_HOME_PETS)
            textSize = 12.5f
            setTextColor(0xFF5B6B58.toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6f) }
        })

        if (yard.isEmpty()) {
            card.addView(TextView(ctx).apply {
                text = getString(R.string.yard_empty)
                textSize = 13f
                setTextColor(0xFF8A9A87.toInt())
                gravity = Gravity.CENTER
                setPadding(0, dp(28f), 0, dp(20f))
            })
            card.addView(primaryButton(getString(R.string.yard_close)) { dismiss() })
            return
        }

        // ── 列表 ──
        yard.forEach { pet -> petRow(card, pet, home) }
    }

    /**
     * 一行 = 形象 + 名字/信息 + 动作按钮。
     *
     * ⚠️ **自己往 [host] 里加视图，返回 Unit** —— 早期版本 `return row` 且调用方又
     * `card.addView(...)`，同一个 View 被加两次会抛 `IllegalStateException`
     * （或者更糟：静默地把它从原位挪走）。
     */
    private fun petRow(host: LinearLayout, pet: Pet, home: List<Pet>) {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12f), 0, dp(12f))
        }
        runCatching { PetAssetResolver.resolve(ctx, pet.species, pet.stage) }.getOrNull()?.let { res ->
            row.addView(ImageView(ctx).apply {
                setImageResource(res)
                layoutParams = LinearLayout.LayoutParams(dp(52f), dp(52f))
                    .apply { marginEnd = dp(12f) }
            })
        }
        row.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(ctx).apply {
                text = pet.nickname
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(0xFF212121.toInt())
            })
            addView(TextView(ctx).apply {
                text = getString(
                    R.string.yard_pet_desc,
                    pet.rarity.displayName,
                    pet.level
                )
                textSize = 11.5f
                setTextColor(0xFF7A8B77.toInt())
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        // 家满 → 不直接接（会挤掉别人），先让用户选收起谁
        row.addView(primaryButton(getString(R.string.yard_take_home)) {
            if (home.size < HomeSlots.MAX_HOME_PETS) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val slot = petRepository.bringHome(pet.id)
                    if (slot != null) {
                        toast(getString(R.string.yard_took_home, pet.nickname))
                        reload()
                    } else {
                        toast(getString(R.string.yard_home_full))
                    }
                }
            } else {
                showSwapPicker(host, pet, home)
            }
        })
        host.addView(row)
        host.addView(divider())
    }

    /** 家满：先收起一只（主宠不可选） */
    private fun showSwapPicker(host: LinearLayout, incoming: Pet, home: List<Pet>) {
        val ctx = requireContext()
        val sheet = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8f), 0, dp(4f))
            background = GradientDrawable().apply {
                cornerRadius = dp(18f).toFloat()
                setColor(0xFFF6F9F4.toInt())
            }
            setPadding(dp(14f), dp(14f), dp(14f), dp(14f))
        }
        sheet.addView(TextView(ctx).apply {
            text = getString(R.string.yard_swap_title, incoming.nickname)
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFF3C4A39.toInt())
        })
        home.forEach { candidate ->
            val canSend = !candidate.isMain
            sheet.addView(TextView(ctx).apply {
                text = if (canSend) {
                    getString(R.string.yard_swap_item, candidate.nickname, candidate.level)
                } else {
                    getString(R.string.yard_swap_main, candidate.nickname)
                }
                textSize = 13f
                setTextColor(if (canSend) 0xFF2E4A2B.toInt() else 0xFF9AA79A.toInt())
                setPadding(dp(4f), dp(11f), dp(4f), dp(11f))
                isEnabled = canSend
                setOnClickListener {
                    viewLifecycleOwner.lifecycleScope.launch {
                        if (!petRepository.sendToYard(candidate.id)) {
                            toast(getString(R.string.yard_send_failed, candidate.nickname))
                            return@launch
                        }
                        val slot = petRepository.bringHome(incoming.id)
                        if (slot != null) {
                            toast(getString(R.string.yard_swapped, incoming.nickname, candidate.nickname))
                        } else {
                            // 极端兜底：收起成功但接回失败（并发写），把话说清楚
                            toast(getString(R.string.yard_home_full))
                        }
                        reload()
                    }
                }
            })
        }
        host.addView(sheet)
    }

    private fun reload() {
        viewLifecycleOwner.lifecycleScope.launch {
            (view as? LinearLayout)?.let { render(it) }
        }
    }

    private fun divider(): View = View(requireContext()).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(1f)
        )
        setBackgroundColor(0xFFEDF2EA.toInt())
    }

    private fun primaryButton(label: String, onClick: () -> Unit): TextView =
        TextView(requireContext()).apply {
            text = label
            textSize = 13.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(14f).toFloat()
                setColor(0xFF3E7B44.toInt())
            }
            setPadding(dp(16f), dp(9f), dp(16f), dp(9f))
            setOnClickListener { onClick() }
        }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        const val TAG = "YardDialog"
    }
}
