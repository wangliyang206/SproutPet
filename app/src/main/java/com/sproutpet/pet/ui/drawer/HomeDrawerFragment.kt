package com.sproutpet.pet.ui.drawer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.sproutpet.core.domain.pet.CodexProgress
import com.sproutpet.pet.R
import com.sproutpet.pet.databinding.FragmentHomeDrawerBinding
import com.sproutpet.pet.databinding.ViewDrawerEntryBinding

/**
 * 右缘抽屉（design-v2 屏⑨，docs/04 §1.1）。
 *
 * 承载图鉴/族谱/仓库/桌面宠物/成就/后山/设置入口与货币收纳
 * （**主界面不挂金币**，货币统一收进这里）。
 *
 * ## ⚠️ 抽屉是「只建一次、反复重画」的
 *
 * 早期实现把入口描述文案写死在 `bindEntry(..., descArg = 1)` 里，抽屉建完就再也不更新 ——
 * 于是图鉴入口永远显示「已收集 1 / 18」，养到 7 只也不变（2026-10-20 真机反馈）。
 *
 * 现在数据由 [render] 从外面推进来：场景每次打开抽屉都会调一次，
 * 数字跟着宠物表走。**不要在这里自己查库** —— 那会让抽屉和场景各有一份真相。
 */
class HomeDrawerFragment : Fragment() {

    private var _binding: FragmentHomeDrawerBinding? = null
    private val binding get() = requireNotNull(_binding)

    /** 入口点击回调（entryId = codex/family/bag/shop/desktop/achievement/hill/settings） */
    var onEntryClick: ((String) -> Unit)? = null

    // ── 最近一次拿到的数据（config change 后 onViewCreated 重放时直接用，不必等下一次 render） ──
    private var petTotal: Int = 0
    private var yardCount: Int = 0
    private var codex: CodexProgress = CodexProgress.EMPTY

    /**
     * 用最新数据重画抽屉的统计行。
     *
     * 幂等，可反复调用；`_binding` 还没就绪时只缓存字段（[onViewCreated] 会补画）。
     */
    fun render(petTotal: Int, yardCount: Int, codex: CodexProgress) {
        this.petTotal = petTotal
        this.yardCount = yardCount
        this.codex = codex
        val b = _binding ?: return
        applyStats(b)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeDrawerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // 图鉴的描述是**动态**的（[applyStats] 每次打开重算），这里只给占位，
        // 免得抽屉建好到第一次 render 之间露出半句「已收集 0 种 / 0 类群」
        bindEntry(binding.entryCodex, "📖", R.string.drawer_entry_codex, R.string.drawer_entry_codex_loading, "codex")
        bindEntry(binding.entryFamily, "🌳", R.string.drawer_entry_family, R.string.drawer_entry_family_desc, "family")
        bindEntry(binding.entryBag, "🎒", R.string.drawer_entry_bag, R.string.drawer_entry_bag_desc, "bag")
        bindEntry(binding.entryShop, "🛒", R.string.drawer_entry_shop, R.string.drawer_entry_shop_desc, "shop")
        bindEntry(binding.entryDesktop, "🖥️", R.string.drawer_entry_desktop, R.string.drawer_entry_desktop_desc, "desktop")
        bindEntry(binding.entryAchievement, "🏆", R.string.drawer_entry_achievement, R.string.drawer_entry_achievement_desc, "achievement")
        bindEntry(binding.entryHill, "⛰️", R.string.drawer_entry_hill, R.string.drawer_entry_hill_desc, "hill")
        bindEntry(binding.entrySettings, "⚙️", R.string.drawer_entry_settings, R.string.drawer_entry_settings_desc, "settings")

        applyStats(binding)
    }

    /**
     * 两处会变动的数字：图鉴入口（种 / 类群）与「我」那一行（在册只数）。
     *
     * 图鉴**不写分母**（design-v2 屏⑨ 的「已收集 8 种 / 5 类群」）——
     * 用户在这一行要判断的是"我收得多不多"，不是"还差几种"；
     * 分母与神兽位留给点进去的图鉴页（那里有空间解释）。
     */
    private fun applyStats(b: FragmentHomeDrawerBinding) {
        b.entryCodex.tvEntryDesc.text = getString(
            R.string.drawer_entry_codex_desc, codex.speciesSeen, codex.familiesSeen
        )
        b.tvDrawerDesc.text = if (yardCount > 0) {
            getString(R.string.drawer_me_desc_yard, petTotal, yardCount)
        } else {
            getString(R.string.drawer_me_desc, petTotal)
        }
    }

    private fun bindEntry(
        entry: ViewDrawerEntryBinding,
        icon: String,
        titleRes: Int,
        descRes: Int,
        entryId: String
    ) {
        entry.tvEntryIcon.text = icon
        entry.tvEntryTitle.setText(titleRes)
        entry.tvEntryDesc.setText(descRes)
        entry.root.setOnClickListener { onEntryClick?.invoke(entryId) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "HomeDrawerFragment"
    }
}
