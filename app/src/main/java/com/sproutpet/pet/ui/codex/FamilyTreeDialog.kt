package com.sproutpet.pet.ui.codex

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
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.sproutpet.core.domain.pet.Pet
import com.sproutpet.core.domain.pet.PetStage
import com.sproutpet.pet.R
import com.sproutpet.pet.data.repository.PetRepository
import com.sproutpet.pet.pet.renderer.PetAssetResolver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 族谱（design-v2 屏 20 · 抽屉「族谱」入口 / 浮卡跳转）。
 *
 * MVP 三层：先辈（父母）→ 自己 + 生育搭档 → 后代（直系）。
 * 称谓口径（`00 §2.7`）：搭档 = 「生育搭档」，不称夫妻；虚线 = 配对，实线 = 血缘。
 * 「导出族谱图片」按钮随后续渲染阶段开放 —— 本版只给结构，不放假按钮。
 */
@AndroidEntryPoint
class FamilyTreeDialog : BottomSheetDialogFragment() {

    @Inject
    lateinit var petRepository: PetRepository

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
            render(view as ViewGroup, requireArguments().getLong(ARG_PET_ID))
        }
    }

    private suspend fun render(card: ViewGroup, petId: Long) {
        val ctx = requireContext()
        val pet = petRepository.queryById(petId)
        if (pet == null) {
            card.addView(centerNote(ctx, getString(R.string.family_pet_missing)))
            return
        }

        val parents = listOfNotNull(pet.parentIdA, pet.parentIdB)
            .filter { it > 0 }
            .mapNotNull { petRepository.queryById(it) }
        val spouse = pet.spouseId?.takeIf { it > 0 }?.let { petRepository.queryById(it) }
        val children = petRepository.childrenOf(petId)

        // ── 标题 ──
        card.addView(TextView(ctx).apply {
            text = getString(R.string.family_tree_title)
            textSize = 19f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFF212121.toInt())
        })
        card.addView(TextView(ctx).apply {
            text = getString(R.string.family_tree_subtitle, pet.nickname)
            textSize = 12.5f
            setTextColor(0xFF5B6B58.toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4f) }
        })

        // ── 先辈层 ──
        if (parents.isNotEmpty()) {
            card.addView(sectionLabel(ctx, getString(R.string.family_ancestors)))
            card.addView(memberRow(ctx, parents, emptySet()))
        }

        // ── 自己 + 搭档 ──
        card.addView(sectionLabel(ctx, getString(R.string.family_self)))
        val selfTags = buildSet {
            if (pet.isMain) add(getString(R.string.family_tag_main))
        }
        card.addView(
            memberRow(
                ctx,
                listOf(pet) + listOfNotNull(spouse),
                selfTags,
                spouseMarked = spouse != null
            )
        )

        // ── 后代层 ──
        card.addView(sectionLabel(ctx, getString(R.string.family_offspring)))
        if (children.isEmpty()) {
            card.addView(centerNote(ctx, getString(R.string.family_no_offspring)))
        } else {
            card.addView(memberRow(ctx, children.take(6), emptySet()))
        }

        // ── 口径注脚 ──
        card.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(0, dp(10f)) })
        card.addView(centerNote(ctx, getString(R.string.family_footer_rule)))

        // ── 三枚统计 ──
        val root = rootAncestor(pet)
        card.addView(statsRow(ctx, children.size, root))
    }

    /** 沿父系/母系一路向上找「血统源头」（族谱 MVP：取最长链的顶端） */
    private suspend fun rootAncestor(pet: Pet): Pet {
        var current = pet
        var hops = 0
        while (hops < 8) {
            val nextId = listOfNotNull(current.parentIdA, current.parentIdB).firstOrNull { it > 0 }
                ?: return current
            val next = petRepository.queryById(nextId) ?: return current
            current = next
            hops++
        }
        return current
    }

    // ═══════════ 成员芯片 ═══════════

    private fun memberRow(
        ctx: android.content.Context,
        members: List<Pet>,
        tags: Set<String>,
        spouseMarked: Boolean = false
    ): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6f) }
        }
        members.forEachIndexed { index, member ->
            if (index > 0) {
                row.addView(TextView(ctx).apply {
                    text = "·"
                    textSize = 14f
                    setTextColor(0xFFB6C2B4.toInt())
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT
                    ).apply { setMargins(dp(8f), 0, dp(8f), 0) }
                })
            }
            row.addView(
                memberChip(ctx, member, tags, dashed = spouseMarked && index == 1),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
        return row
    }

    private fun memberChip(
        ctx: android.content.Context,
        pet: Pet,
        tags: Set<String>,
        dashed: Boolean
    ): LinearLayout {
        val chip = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(8f), dp(10f), dp(8f), dp(8f))
            background = GradientDrawable().apply {
                cornerRadius = dp(14f).toFloat()
                setColor(0xFFF7FBF6.toInt())
                if (dashed) {
                    setStroke(dp(1.5f), 0xFF4CAF50.toInt())
                } else {
                    setStroke(dp(1f), 0xFFE3EBE1.toInt())
                }
            }
        }
        val icon = runCatching { PetAssetResolver.resolve(ctx, pet.species, pet.stage) }
        if (icon.isSuccess) {
            chip.addView(ImageView(ctx).apply {
                setImageResource(icon.getOrThrow())
                layoutParams = LinearLayout.LayoutParams(dp(38f), dp(38f))
            })
        } else {
            chip.addView(TextView(ctx).apply {
                text = "🐣"
                textSize = 26f
                gravity = Gravity.CENTER
            })
        }
        chip.addView(TextView(ctx).apply {
            text = pet.nickname
            textSize = 12.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFF212121.toInt())
            gravity = Gravity.CENTER
        })
        chip.addView(TextView(ctx).apply {
            text = getString(
                R.string.family_member_subtitle,
                pet.level,
                pet.stage.displayName,
                pet.rarity.displayName
            )
            textSize = 10.5f
            setTextColor(0xFF5B6B58.toInt())
            gravity = Gravity.CENTER
        })
        val tagText = buildString {
            tags.forEach { append(it) }
            if (pet.stage == PetStage.RETIRED) append(getString(R.string.family_tag_retired))
            if (pet.generation > 1) append(getString(R.string.family_tag_generation, pet.generation))
        }
        if (tagText.isNotEmpty()) {
            chip.addView(TextView(ctx).apply {
                text = tagText
                textSize = 10f
                setTextColor(0xFF388E3C.toInt())
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(2f) }
            })
        }
        return chip
    }

    private fun statsRow(
        ctx: android.content.Context,
        offspringCount: Int,
        root: Pet
    ): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14f) }
        }
        fun stat(label: String, value: String): LinearLayout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(6f), dp(10f), dp(6f), dp(10f))
            background = GradientDrawable().apply {
                cornerRadius = dp(12f).toFloat()
                setColor(0xFFF2F6F1.toInt())
            }
            addView(TextView(ctx).apply {
                text = label
                textSize = 10.5f
                setTextColor(0xFF9AA79A.toInt())
            })
            addView(TextView(ctx).apply {
                text = value
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(0xFF212121.toInt())
            })
        }
        fun add(view: View) = row.addView(
            view,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(3f), 0, dp(3f), 0)
            }
        )
        add(stat(getString(R.string.family_stat_offspring), offspringCount.toString()))
        add(stat(getString(R.string.family_stat_generation), root.generation.toString()))
        add(stat(getString(R.string.family_stat_root), root.nickname))
        return row
    }

    private fun sectionLabel(ctx: android.content.Context, text: String) = TextView(ctx).apply {
        this.text = text
        textSize = 12f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(0xFF388E3C.toInt())
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(14f) }
    }

    private fun centerNote(ctx: android.content.Context, text: String) = TextView(ctx).apply {
        this.text = text
        textSize = 11.5f
        setTextColor(0xFF9AA79A.toInt())
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8f) }
    }

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val ARG_PET_ID = "pet_id"
        const val TAG = "FamilyTreeDialog"

        fun newInstance(petId: Long): FamilyTreeDialog = FamilyTreeDialog().apply {
            arguments = Bundle().apply { putLong(ARG_PET_ID, petId) }
        }
    }
}
