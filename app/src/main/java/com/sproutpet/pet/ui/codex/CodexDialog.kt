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
import com.sproutpet.core.domain.pet.PetSpecies
import com.sproutpet.core.domain.pet.PetStage
import com.sproutpet.core.domain.pet.SpeciesFamily
import com.sproutpet.pet.pet.renderer.PetAssetResolver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 宠物图鉴（design-v2 屏 08 · 抽屉「图鉴」入口）。
 *
 * 18 种 = 五虫 15（可抽）+ 异兽 3（不可抽，跨虫混血的长线目标）。
 * 收集判定 = 见过即收集（含已入后山，[com.sproutpet.core.db.entity.PetDao.querySeenSpecies]）。
 * 未遇见只显示「？」—— 概率数字是公示口径，不在这里剧透。
 */
@AndroidEntryPoint
class CodexDialog : BottomSheetDialogFragment() {

    @Inject
    lateinit var petRepository: com.sproutpet.pet.data.repository.PetRepository

    /** ScrollView 的唯一子 View —— 所有内容都挂在这里，不能挂到 ScrollView 本身。 */
    private var contentCard: LinearLayout? = null

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
        contentCard = card
        scroll.addView(
            card,
            android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        return scroll
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val card = contentCard ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            render(card)
        }
    }

    private suspend fun render(card: LinearLayout) {
        val seen = petRepository.seenSpeciesIds()
        val ctx = card.context
        if (!isAdded) return
        card.removeAllViews()

        // ── 头部：标题 + 收集统计 ──
        card.addView(title(ctx, getString(com.sproutpet.pet.R.string.codex_title)))
        val total = PetSpecies.entries.size
        val familiesSeen = SpeciesFamily.entries.count { family ->
            PetSpecies.entries.any { it.family == family && it.id in seen }
        }
        card.addView(
            subtitle(
                ctx,
                getString(com.sproutpet.pet.R.string.codex_summary, seen.size, total, familiesSeen, 6)
            )
        )

        // ── 分组：五虫按类群，异兽殿后 ──
        val groups = SpeciesFamily.entries.filter { it.isWorm } + SpeciesFamily.UNCATEGORIZED
        groups.forEachIndexed { index, family ->
            val members = PetSpecies.entries.filter { it.family == family }
            val seenInFamily = members.count { it.id in seen }

            card.addView(
                groupHeader(
                    ctx,
                    if (family.isWorm) {
                        "${family.displayName} · ${family.definition}"
                    } else {
                        getString(com.sproutpet.pet.R.string.codex_exotic_header)
                    },
                    if (family.isWorm) {
                        members.joinToString(" / ") { it.personality }
                    } else {
                        getString(com.sproutpet.pet.R.string.codex_exotic_trait)
                    },
                    familyDotColor(family)
                )
            )

            // 每行 3 格
            members.chunked(3).forEach { row ->
                card.addView(buildRow(ctx, row, seen, family))
            }
            if (index < groups.lastIndex) {
                card.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(0, dp(10f)) })
            }
        }

        card.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(0, dp(12f)) })
        card.addView(
            footnote(
                ctx,
                getString(com.sproutpet.pet.R.string.codex_footer)
            )
        )
    }

    // ═══════════ 视图工厂 ═══════════

    private fun buildRow(
        ctx: android.content.Context,
        species: List<PetSpecies>,
        seen: Set<String>,
        family: SpeciesFamily
    ): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8f) }
        }
        repeat(3) { col ->
            val cell = if (col < species.size) {
                cellView(ctx, species[col], species[col].id in seen, family)
            } else {
                View(ctx)
            }
            row.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(4f), 0, dp(4f), 0)
            })
        }
        return row
    }

    private fun cellView(
        ctx: android.content.Context,
        species: PetSpecies,
        collected: Boolean,
        family: SpeciesFamily
    ): LinearLayout {
        val cell = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(6f), dp(12f), dp(6f), dp(10f))
            background = GradientDrawable().apply {
                cornerRadius = dp(14f).toFloat()
                setColor(if (collected) Color.WHITE else 0xFFF2F6F1.toInt())
                setStroke(dp(1f), if (collected) familyDotColor(family) else 0xFFE3EBE1.toInt())
            }
        }
        if (collected) {
            val icon = runCatching { PetAssetResolver.resolve(ctx, species, PetStage.ADULT) }
            if (icon.isSuccess) {
                cell.addView(ImageView(ctx).apply {
                    setImageResource(icon.getOrThrow())
                    layoutParams = LinearLayout.LayoutParams(dp(44f), dp(44f))
                })
            } else {
                // DEBUG 下缺失资源的占位 —— 图鉴层不炸界面（15 §5.1 的「缺失可见」在渲染层）
                cell.addView(bigText(ctx, "🐣", 30f))
            }
        } else {
            cell.addView(bigText(ctx, "？", 26f))
        }
        cell.addView(
            TextView(ctx).apply {
                text = species.displayName
                textSize = 13f
                setTextColor(if (collected) 0xFF212121.toInt() else 0xFFB6C2B4.toInt())
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4f) }
            }
        )
        cell.addView(
            TextView(ctx).apply {
                text = getString(
                    if (collected) com.sproutpet.pet.R.string.codex_collected
                    else com.sproutpet.pet.R.string.codex_unseen
                )
                textSize = 10.5f
                setTextColor(if (collected) 0xFF5B6B58.toInt() else 0xFFB6C2B4.toInt())
                gravity = Gravity.CENTER
            }
        )
        return cell
    }

    private fun title(ctx: android.content.Context, text: String) = TextView(ctx).apply {
        this.text = text
        textSize = 19f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(0xFF212121.toInt())
    }

    private fun subtitle(ctx: android.content.Context, text: String) = TextView(ctx).apply {
        this.text = text
        textSize = 12.5f
        setTextColor(0xFF5B6B58.toInt())
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4f) }
    }

    private fun groupHeader(
        ctx: android.content.Context,
        titleText: String,
        traitText: String,
        dotColor: Int
    ): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14f) }
        }
        row.addView(View(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(dotColor)
            }
            layoutParams = LinearLayout.LayoutParams(dp(8f), dp(8f)).apply { marginEnd = dp(6f) }
        })
        row.addView(TextView(ctx).apply {
            text = titleText
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFF212121.toInt())
        })
        row.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))
        row.addView(TextView(ctx).apply {
            text = traitText
            textSize = 10.5f
            setTextColor(0xFF9AA79A.toInt())
        })
        return row
    }

    private fun footnote(ctx: android.content.Context, text: String) = TextView(ctx).apply {
        this.text = text
        textSize = 11.5f
        setTextColor(0xFF9AA79A.toInt())
        gravity = Gravity.CENTER
    }

    private fun bigText(ctx: android.content.Context, text: String, size: Float) = TextView(ctx).apply {
        this.text = text
        textSize = size
        setTextColor(0xFFB6C2B4.toInt())
        gravity = Gravity.CENTER
    }

    private fun familyDotColor(family: SpeciesFamily): Int = when (family) {
        SpeciesFamily.LUO -> 0xFF4FC3F7.toInt()
        SpeciesFamily.LIN -> 0xFF26A69A.toInt()
        SpeciesFamily.MAO -> 0xFFBA8DB0.toInt()
        SpeciesFamily.YU -> 0xFFFF8A65.toInt()
        SpeciesFamily.KUN -> 0xFF9CCC65.toInt()
        SpeciesFamily.UNCATEGORIZED -> 0xFF78909C.toInt()
    }

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
    private fun dp(v: Int): Int = dp(v.toFloat())

    companion object {
        const val TAG = "CodexDialog"
        fun newInstance(): CodexDialog = CodexDialog()
    }
}
