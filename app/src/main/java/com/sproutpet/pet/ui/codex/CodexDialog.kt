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
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.sproutpet.core.domain.pet.CodexProgress
import com.sproutpet.core.domain.pet.Pet
import com.sproutpet.core.domain.pet.PetSpecies
import com.sproutpet.core.domain.pet.PetStage
import com.sproutpet.core.domain.pet.SpeciesFamily
import com.sproutpet.pet.R
import com.sproutpet.pet.data.local.OwnerLocalDataSource
import com.sproutpet.pet.data.repository.PetRepository
import com.sproutpet.pet.pet.renderer.PetAssetResolver
import com.sproutpet.pet.ui.scene.PetRosterDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 宠物图鉴（design-v2 屏 20 · 抽屉「图鉴」入口）。
 *
 * ## 结构：每个类群 4 格 = 3 个可抽物种 + 1 个神兽位
 *
 * 「五虫每种只有 3 只」不是被截断，而是**物种表本来就这么大**：
 * 五虫 × 3 = 15 种可抽 + 不入十类 3 种 = 18 种（[docs/07 §四]）。
 *
 * 但每类群还有**第 4 格** —— 该虫之长（神兽）。四虫有形象（蛟龙/麒麟/凤凰/神龟），
 * 蠃虫按 [`docs/07 §七`] 的立场**永久留白**（「倮虫三百六十，而圣人为之长」，
 * 把圣人做成可攀比的外观皮是结构性文化风险，见 ADR-020）。
 *
 * 这格此前没画，于是图鉴看起来像"每类群只有 3 个格子"，
 * 用户自然会问「为什么只有 3 只」—— 缺的正是这一格。
 *
 * ## 计数口径
 *
 * 三个维度分开（[CodexProgress]）：**种**（18）· **类群**（6）· **神兽位**（4）。
 * 只写「N/18」会让人以为养 18 只宠物就满图鉴了，而 18 是物种数。
 */
@AndroidEntryPoint
class CodexDialog : BottomSheetDialogFragment() {

    @Inject
    lateinit var petRepository: PetRepository

    @Inject
    lateinit var local: OwnerLocalDataSource

    /** ScrollView 的唯一子 View —— 所有内容都挂在这里，不能挂到 ScrollView 本身。 */
    private var contentCard: LinearLayout? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        // NestedScrollView（不是 ScrollView）：BottomSheetBehavior 只认 NestedScrolling 协议，
        // 普通 ScrollView 滑到底后「往回拖」会被当成"拖拽整张抽屉"，内容卡死回不了顶部
        // （2026-10-22 真机反馈③）。
        val scroll = androidx.core.widget.NestedScrollView(ctx).apply { overScrollMode = View.OVER_SCROLL_NEVER }
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
        refresh()
    }

    override fun onStart() {
        super.onStart()
        // 每次回到前台都重画一次：图鉴是一次性快照渲染，孵化 / 起名 / 放出之后
        // 再打开时必须是最新数据 —— 这里是"打开即最新"的兜底（2026-10-22 真机反馈①）。
        refresh()
    }

    private fun refresh() {
        val card = contentCard ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            render(card)
        }
    }

    private suspend fun render(card: LinearLayout) {
        val ctx = card.context
        // 模态列表用一次性快照（不做实时订阅，避免内容在手指下跳动）；
        // 与抽屉入口同源（CodexProgress.of），不会「抽屉 8 种、点进去 7 种」
        val ownerId = local.getOrCreateOwnerId()
        val codex = petRepository.codexProgress(ownerId)
        // 物种 → 在册个体（含蛋 / 待命）：×N 角标与「我的那只叫什么」都从这里出
        val ownedPets = petRepository.petsByOwner(ownerId).groupBy { it.species.id }
        if (!isAdded) return
        card.removeAllViews()

        // ── 头部：标题 + 收集统计（三维度） ──
        card.addView(title(ctx, getString(R.string.codex_title)))
        card.addView(
            subtitle(
                ctx,
                getString(
                    R.string.codex_summary,
                    codex.speciesSeen, codex.speciesTotal,
                    codex.familiesSeen, codex.familiesTotal,
                    codex.deitiesSeen, codex.deitiesTotal
                )
            )
        )

        // ── 分组：五虫按类群，异兽殿后 ──
        val groups = SpeciesFamily.entries.filter { it.isWorm } + SpeciesFamily.UNCATEGORIZED
        groups.forEachIndexed { index, family ->
            val members = PetSpecies.entries.filter { it.family == family }

            card.addView(
                groupHeader(
                    ctx = ctx,
                    titleText = if (family.isWorm) {
                        "${family.displayName} · ${family.definition}"
                    } else {
                        getString(R.string.codex_exotic_header)
                    },
                    countText = if (family.isWorm) {
                        // 每行 4 格 = 3 物种 + 1 神兽位：分母必须连神兽位一起说清，
                        // 只写「N/3 种」用户必然以为少了一格（2026-10-22 真机反馈②）
                        getString(
                            R.string.codex_family_count_worm,
                            codex.speciesSeenIn(family),
                            codex.speciesTotalIn(family),
                            when {
                                !family.hasDeityForm -> getString(R.string.codex_deity_none)
                                codex.hasDeity(family) -> "1/1"
                                else -> "0/1"
                            }
                        )
                    } else {
                        getString(
                            R.string.codex_family_count,
                            codex.speciesSeenIn(family),
                            codex.speciesTotalIn(family)
                        )
                    },
                    traitText = if (family.isWorm) {
                        members.joinToString(" / ") { it.personality }
                    } else {
                        getString(R.string.codex_exotic_trait)
                    },
                    dotColor = familyDotColor(family)
                )
            )

            // 3 个物种 + 1 个神兽位（异兽组没有神兽位）= 每行 4 格
            val cells = members
                .map { cellView(ctx, it, codex.isCollected(it), family, ownedPets[it.id].orEmpty()) }
                .toMutableList()
            if (family.isWorm) {
                cells += deityCell(ctx, family, codex.hasDeity(family))
            }
            cells.chunked(COLS).forEach { row ->
                card.addView(buildRow(ctx, row))
            }

            if (index < groups.lastIndex) {
                card.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(0, dp(10f)) })
            }
        }

        card.addView(View(ctx).apply { layoutParams = LinearLayout.LayoutParams(0, dp(12f)) })
        card.addView(footnote(ctx, getString(R.string.codex_deity_footer)))
        card.addView(footnote(ctx, getString(R.string.codex_footer)))
    }

    // ═══════════ 视图工厂 ═══════════

    /** 每行 [COLS] 格；最后一格不足时补空 View 保持列宽一致 */
    private fun buildRow(ctx: android.content.Context, cells: List<View>): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8f) }
        }
        repeat(COLS) { col ->
            val cell = cells.getOrElse(col) { View(ctx) }
            row.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(3f), 0, dp(3f), 0)
            })
        }
        return row
    }

    /**
     * 一个物种格：形象 + 名字 + 状态（已收集 / 未遇见）。
     *
     * ## 为什么已收集的格子可以点（2026-10-22）
     *
     * 图鉴是**物种**口径：一格一个物种，同种养 10 只也还是一格 —— 重复宠在这套
     * 口径里完全不可见，而「重复 = 融合材料」。点进去看的是**个体**
     * （[PetRosterDialog]）：谁在家、谁待命、放出 / 收起 / 去融合都在那里。
     *
     * @param owned 该物种的在册个体（含蛋 / 待命）；非空时格子可点进个体列表 */
    private fun cellView(
        ctx: android.content.Context,
        species: PetSpecies,
        collected: Boolean,
        family: SpeciesFamily,
        owned: List<Pet>
    ): LinearLayout {
        val cell = cell(ctx, collected, familyDotColor(family))
        if (collected) {
            val icon = runCatching { PetAssetResolver.resolve(ctx, species, PetStage.ADULT) }
            if (icon.isSuccess) {
                cell.addView(ImageView(ctx).apply {
                    setImageResource(icon.getOrThrow())
                    layoutParams = LinearLayout.LayoutParams(dp(40f), dp(40f))
                })
            } else {
                // DEBUG 下缺失资源的占位 —— 图鉴层不炸界面（15 §5.1 的「缺失可见」在渲染层）
                cell.addView(bigText(ctx, "🐣", 26f, 0xFFB6C2B4.toInt()))
            }
        } else {
            cell.addView(bigText(ctx, "？", 24f, 0xFFB6C2B4.toInt()))
        }
        cell.addView(cellName(ctx, species.displayName, collected))
        cell.addView(
            cellCaption(
                ctx,
                when {
                    // 已收集但个体不在册 = 都进后山了（is_retired 过滤），仍是收集态
                    !collected -> getString(R.string.codex_unseen)
                    owned.isEmpty() -> getString(R.string.codex_collected)
                    owned.size > 1 -> getString(R.string.codex_cell_multi, owned.size)
                    // 只有 1 只 → 直接亮昵称：「收集到的是我的那只」不再需要猜
                    // （2026-10-22 真机反馈①：孵化出第一只了，图鉴里却"没有"——
                    //   用户在 18 个物种格里找自己的宠物，只看到"已收集"三个字）
                    else -> owned.first().nickname
                },
                collected
            )
        )
        if (owned.isNotEmpty()) {
            cell.isClickable = true
            cell.setOnClickListener { openRoster(species) }
        }
        return cell
    }

    /** 物种格 → 该物种的个体列表（放出 / 收起 / 融合都在那里） */
    private fun openRoster(species: PetSpecies) {
        if (!isAdded) return
        PetRosterDialog.newInstance(species.id)
            .show(childFragmentManager, PetRosterDialog.TAG)
    }

    /**
     * 神兽位（每类群第 4 格）。
     *
     * 三种状态必须长得**不一样**，否则用户会以为"这个格子坏了"：
     * - **蠃虫**：云雾留白 + 「此位不设形」—— 这是**立场**，不是没做（ADR-020）
     * - **未获得**：问号 + 「未获得」
     * - **已获得**：类群色描边 + 神兽名 + 「已获得」
     */
    private fun deityCell(
        ctx: android.content.Context,
        family: SpeciesFamily,
        owned: Boolean
    ): LinearLayout {
        val hasSlot = family.hasDeityForm
        val cell = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(5f), dp(10f), dp(5f), dp(9f))
            background = GradientDrawable().apply {
                cornerRadius = dp(14f).toFloat()
                when {
                    !hasSlot -> {
                        // 云雾留白：浅灰渐变比纯色更像"雾"
                        colors = intArrayOf(0xFFF7F9F6.toInt(), 0xFFEDF2EB.toInt())
                        gradientType = GradientDrawable.LINEAR_GRADIENT
                        orientation = GradientDrawable.Orientation.TOP_BOTTOM
                        setStroke(dp(1f), 0xFFE3EBE1.toInt())
                    }
                    owned -> {
                        setColor(Color.WHITE)
                        setStroke(dp(1.5f), familyDotColor(family))
                    }
                    else -> {
                        setColor(0xFFF7F9F6.toInt())
                        setStroke(dp(1f), 0xFFE3EBE1.toInt())
                    }
                }
            }
        }
        val glyph = when {
            !hasSlot -> "☁️"
            owned -> deityEmoji(family)
            else -> "？"
        }
        cell.addView(bigText(ctx, glyph, if (!hasSlot) 24f else 24f, 0xFFB6C2B4.toInt()))
        cell.addView(
            cellName(
                ctx,
                if (hasSlot) family.deity else getString(R.string.codex_deity_none_name),
                owned
            )
        )
        cell.addView(
            cellCaption(
                ctx,
                when {
                    !hasSlot -> getString(R.string.codex_deity_none)
                    owned -> getString(R.string.codex_deity_collected)
                    else -> getString(R.string.codex_deity_unseen)
                },
                owned
            )
        )
        return cell
    }

    /** 格子底色 / 描边：收集到就有类群色的边，没收集就是灰边 */
    private fun cell(ctx: android.content.Context, collected: Boolean, dotColor: Int) =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4f), dp(10f), dp(4f), dp(8f))
            background = GradientDrawable().apply {
                cornerRadius = dp(14f).toFloat()
                setColor(if (collected) Color.WHITE else 0xFFF2F6F1.toInt())
                setStroke(dp(1f), if (collected) dotColor else 0xFFE3EBE1.toInt())
            }
        }

    /** 格子里的名字：4 格以后每格变窄，字号必须跟着降（否则"软软章"会折成两行把行高撑乱） */
    private fun cellName(ctx: android.content.Context, text: String, collected: Boolean) =
        TextView(ctx).apply {
            this.text = text
            textSize = 12f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(if (collected) 0xFF212121.toInt() else 0xFFB6C2B4.toInt())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(3f) }
        }

    private fun cellCaption(ctx: android.content.Context, text: String, collected: Boolean) =
        TextView(ctx).apply {
            this.text = text
            textSize = 10f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(if (collected) 0xFF5B6B58.toInt() else 0xFFB6C2B4.toInt())
            gravity = Gravity.CENTER
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

    /**
     * 类群标题：**两行**。
     *
     * 第一行 = 彩点 + 类群名 + 右侧「N/M 种」；第二行 = 释义 + 性格串。
     * 早期版本把这三样塞进一行，右端只剩十几 dp，「N/M 种」根本放不进去 ——
     * 而它恰恰是回答「为什么每类群只有 3 个」的关键数字。
     */
    private fun groupHeader(
        ctx: android.content.Context,
        titleText: String,
        countText: String,
        traitText: String,
        dotColor: Int
    ): LinearLayout {
        val block = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14f) }
        }
        val firstRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        firstRow.addView(View(ctx).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(dotColor)
            }
            layoutParams = LinearLayout.LayoutParams(dp(8f), dp(8f)).apply { marginEnd = dp(6f) }
        })
        firstRow.addView(TextView(ctx).apply {
            text = titleText
            textSize = 13f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFF212121.toInt())
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        firstRow.addView(TextView(ctx).apply {
            text = countText
            textSize = 11f
            setTextColor(0xFF5B6B58.toInt())
            setPadding(dp(6f), 0, 0, 0)
        })
        block.addView(firstRow)
        block.addView(TextView(ctx).apply {
            text = traitText
            textSize = 10.5f
            setTextColor(0xFF9AA79A.toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(2f) }
        })
        return block
    }

    private fun footnote(ctx: android.content.Context, text: String) = TextView(ctx).apply {
        this.text = text
        textSize = 11.5f
        setTextColor(0xFF9AA79A.toInt())
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4f) }
    }

    private fun bigText(ctx: android.content.Context, text: String, size: Float, color: Int) =
        TextView(ctx).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
        }

    private fun deityEmoji(family: SpeciesFamily): String = when (family) {
        SpeciesFamily.LUO -> "☁️"
        SpeciesFamily.LIN -> "🐉"
        SpeciesFamily.MAO -> "🦄"
        SpeciesFamily.YU -> "🕊️"
        SpeciesFamily.KUN -> "🐢"
        SpeciesFamily.UNCATEGORIZED -> "？"
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

        /** 每行 4 格：3 个可抽物种 + 1 个神兽位（异兽组第 4 格留空） */
        private const val COLS = 4
    }
}
