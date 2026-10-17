package com.sproutpet.pet.ui.drawer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.sproutpet.pet.R
import com.sproutpet.pet.databinding.FragmentHomeDrawerBinding
import com.sproutpet.pet.databinding.ViewDrawerEntryBinding

/**
 * 右缘抽屉（design-v2 屏⑨，docs/04 §1.1）。
 *
 * 承载图鉴/族谱/仓库/桌面宠物/成就/后山/设置入口与货币收纳
 * （**主界面不挂金币**，货币统一收进这里）。
 * Phase 1 各入口为占位（后续阶段逐个落地），「桌面宠物」槽位管理随 U-11 在 Phase 3+ 完善。
 */
class HomeDrawerFragment : Fragment() {

    private var _binding: FragmentHomeDrawerBinding? = null
    private val binding get() = requireNotNull(_binding)

    /** 入口点击回调（entryId = codex/family/bag/desktop/achievement/hill/settings） */
    var onEntryClick: ((String) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeDrawerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        bindEntry(binding.entryCodex, "📖", R.string.drawer_entry_codex, R.string.drawer_entry_codex_desc, "codex", 1)
        bindEntry(binding.entryFamily, "🌳", R.string.drawer_entry_family, R.string.drawer_entry_family_desc, "family")
        bindEntry(binding.entryBag, "🎒", R.string.drawer_entry_bag, R.string.drawer_entry_bag_desc, "bag")
        bindEntry(binding.entryDesktop, "🖥️", R.string.drawer_entry_desktop, R.string.drawer_entry_desktop_desc, "desktop")
        bindEntry(binding.entryAchievement, "🏆", R.string.drawer_entry_achievement, R.string.drawer_entry_achievement_desc, "achievement")
        bindEntry(binding.entryHill, "⛰️", R.string.drawer_entry_hill, R.string.drawer_entry_hill_desc, "hill")
        bindEntry(binding.entrySettings, "⚙️", R.string.drawer_entry_settings, R.string.drawer_entry_settings_desc, "settings")

        val count = arguments?.getInt(ARG_PET_COUNT, 0) ?: 0
        binding.tvDrawerDesc.text = getString(R.string.drawer_me_desc, count.coerceAtLeast(1))
    }

    private fun bindEntry(
        entry: ViewDrawerEntryBinding,
        icon: String,
        titleRes: Int,
        descRes: Int,
        entryId: String,
        descArg: Int? = null
    ) {
        entry.tvEntryIcon.text = icon
        entry.tvEntryTitle.setText(titleRes)
        entry.tvEntryDesc.text = if (descArg != null) getString(descRes, descArg) else getString(descRes)
        entry.root.setOnClickListener {
            onEntryClick?.invoke(entryId)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_PET_COUNT = "pet_count"
        fun newInstance(petCount: Int): HomeDrawerFragment =
            HomeDrawerFragment().apply {
                arguments = Bundle().apply { putInt(ARG_PET_COUNT, petCount) }
            }
    }
}
