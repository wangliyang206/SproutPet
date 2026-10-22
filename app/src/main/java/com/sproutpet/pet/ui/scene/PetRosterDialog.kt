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
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.sproutpet.core.domain.pet.FusionRules
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
 * 家庭成员名册（design-v2 屏 11「家庭成员 · 名册」）。
 *
 * ## 为什么要有它（2026-10-22 用户需求）
 *
 * 原话：「十连抽出来的宠物放入仓库或者图鉴中，可以选择**放出来**和**收回去**，
 * 目前屏幕只允许最多出现六个是吧，这个数量暂时不变。」
 *
 * 此前的实现只有一个**单向**的「院子」：院子里的能「接回家」，
 * 而**在家的一只都收不起来** —— 只有家满时通过换位流程"顺带"收起一只。
 * 于是「6 只站满了，我想换个人站」这件事没有入口，只能先把院子里的接回来触发换位。
 *
 * 本弹窗把 design-v2 **屏 11** 的完整名册落地，两段都在同一个列表里：
 *
 * - **在家 · N/6**：每行「收起」（主宠除外 —— `HomeSlots.validate` 不变量④）
 * - **待命 · N**：每行「放出」（家满时先问「收起谁」，不静默替换）
 *
 * ## 数量口径（三层链条，一个字都没改）
 *
 * ```
 * 拥有不限  ⊃  家在册 6  ⊃  桌面 3
 * ```
 *
 * 上限读 [HomeSlots.MAX_HOME_PETS]，**不在这里另写 6** —— 与 `tools/acquisition_sim.py`
 * 同源，改上限必须先过 ADR-024 复算。
 *
 * ## 同种重复 → 融合
 *
 * 融合（[FusionRules]）只认「同物种」。名册是同种重复**第一次被看见**的地方：
 * 行内「同种 ×N · 去融合」直接把用户带到 [FusionDialog]，并预选该物种的可行目标。
 * 副宠「入后山、不可取回」的警示仍由融合弹窗自己承担（不可逆操作不在这里偷偷发生）。
 *
 * @param speciesId 非空 = 只看该物种的个体（从图鉴某一格点进来时用）
 */
@AndroidEntryPoint
class PetRosterDialog : BottomSheetDialogFragment() {

    @Inject
    lateinit var petRepository: PetRepository

    @Inject
    lateinit var local: OwnerLocalDataSource

    /** 只看某物种（图鉴 → 物种格 → 个体列表）；null = 全部 */
    private var speciesId: String? = null

    /** ScrollView 的唯一子 View */
    private var contentCard: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speciesId = arguments?.getString(ARG_SPECIES_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        // NestedScrollView（不是 ScrollView）：BottomSheetBehavior 只认 NestedScrolling 协议，
        // 普通 ScrollView 滑到底后「往回拖」会被当成"拖拽整张抽屉"，内容卡死回不了顶部
        // （2026-10-22 真机反馈③，与图鉴同修）。
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
        scroll.addView(card)
        return scroll
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch { render() }
    }

    private suspend fun render() {
        val card = contentCard ?: return
        card.removeAllViews()
        val ctx = requireContext()
        val ownerId = local.getOrCreateOwnerId()

        // 一次性快照：模态列表不做实时订阅（避免内容在手指下跳动、误点错人）。
        // 在家 / 待命用 observeRoster **同一份快照**切两刀 —— 两条独立查询会出现
        // 「一只同时在两边」的中间帧（见 PetRepository.observeRoster 注释）。
        val roster = petRepository.observeRoster(ownerId).first()
        val home = roster.home
        val yard = roster.yard
        val all = home + yard
        val filtered = speciesId?.let { id -> all.filter { it.species.id == id } } ?: all
        val dupCounts = all.groupingBy { it.species }.eachCount()

        // ── 头部 ──
        card.addView(TextView(ctx).apply {
            text = speciesId?.let { id ->
                val species = filtered.firstOrNull()?.species
                if (species != null) {
                    getString(R.string.roster_species_title, species.displayName, filtered.size)
                } else {
                    getString(R.string.roster_title)
                }
            } ?: getString(R.string.roster_title)
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFF212121.toInt())
        })
        card.addView(TextView(ctx).apply {
            text = getString(
                R.string.roster_subtitle,
                filtered.size,
                home.size,
                HomeSlots.MAX_HOME_PETS,
                yard.size
            )
            textSize = 12.5f
            setTextColor(0xFF5B6B58.toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6f) }
        })

        if (filtered.isEmpty()) {
            card.addView(TextView(ctx).apply {
                text = getString(R.string.roster_empty)
                textSize = 13f
                setTextColor(0xFF8A9A87.toInt())
                gravity = Gravity.CENTER
                setPadding(0, dp(28f), 0, dp(20f))
            })
            card.addView(primaryButton(getString(R.string.roster_close)) { dismiss() })
            return
        }

        // ── 在家 ──
        val homeRows = filtered.filter { HomeSlots.isAtHome(it) }.sortedBy { it.homeSlot }
        if (homeRows.isNotEmpty()) {
            card.addView(sectionHeader(
                getString(
                    R.string.roster_sec_home,
                    home.size,
                    HomeSlots.MAX_HOME_PETS,
                    HomeSlots.MAX_HOME_PETS
                )
            ))
            homeRows.forEach { petRow(card, it, inHome = true, homeCount = home.size, dup = dupCounts[it.species] ?: 1) }
        }

        // ── 待命（院子）──
        val yardRows = filtered.filterNot { HomeSlots.isAtHome(it) }
        if (yardRows.isNotEmpty()) {
            card.addView(sectionHeader(getString(R.string.roster_sec_yard, yardRows.size)))
            yardRows.forEach { petRow(card, it, inHome = false, homeCount = home.size, dup = dupCounts[it.species] ?: 1) }
        }

        card.addView(TextView(ctx).apply {
            text = getString(
                R.string.roster_footnote,
                HomeSlots.MAX_HOME_PETS,
                FusionRules.costGold
            )
            textSize = 11.5f
            setTextColor(0xFF9AA79A.toInt())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14f) }
        })
    }

    /**
     * 一行 = 形象 + 名字/信息 + 动作按钮。
     *
     * ⚠️ **自己往 [host] 里加视图，返回 Unit** —— 早期版本 `return row` 且调用方又
     * `card.addView(...)`，同一个 View 被加两次会抛 `IllegalStateException`。
     */
    /** @param homeCount 当前在家只数 —— 放出前先判家满没满（上限读 [HomeSlots.MAX_HOME_PETS]） */
    private fun petRow(host: LinearLayout, pet: Pet, inHome: Boolean, homeCount: Int, dup: Int) {
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

        // 文案块（名字 / 等级·物种·类群 / 同种重复 chip）
        val textBlock = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        textBlock.addView(TextView(ctx).apply {
            text = pet.nickname
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFF212121.toInt())
        })
        textBlock.addView(TextView(ctx).apply {
            text = getString(
                R.string.roster_pet_desc,
                pet.level,
                pet.species.displayName,
                pet.species.family.displayName,
                // 稀有度跟着个体走（同种可不同档），必须亮出来（2026-10-22 用户需求）
                pet.rarity.displayName
            )
            textSize = 11.5f
            setTextColor(0xFF7A8B77.toInt())
        })
        if (dup > 1) {
            // 同种重复 = 融合的入口。融合只认同物种（FusionRules.check）。
            textBlock.addView(TextView(ctx).apply {
                text = getString(R.string.roster_dup, dup)
                textSize = 10.5f
                setTextColor(0xFF9A6A00.toInt())
                setPadding(dp(7f), dp(2f), dp(7f), dp(2f))
                background = GradientDrawable().apply {
                    cornerRadius = dp(8f).toFloat()
                    setColor(0xFFFFF3D6.toInt())
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(3f) }
                setOnClickListener { openFusion(pet) }
            })
        }
        row.addView(textBlock, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        // 动作：在家 → 收起；待命 → 放出。主宠不能收起（不变量④）。
        when {
            inHome && pet.isMain -> row.addView(crownTag())
            inHome -> row.addView(primaryButton(getString(R.string.roster_act_send)) {
                viewLifecycleOwner.lifecycleScope.launch {
                    if (petRepository.sendToYard(pet.id)) {
                        toast(getString(R.string.roster_sent, pet.nickname))
                    } else {
                        toast(getString(R.string.roster_send_failed, pet.nickname))
                    }
                    render()
                }
            })
            else -> row.addView(primaryButton(getString(R.string.roster_act_release)) {
                if (homeCount < HomeSlots.MAX_HOME_PETS) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        val slot = petRepository.bringHome(pet.id)
                        if (slot != null) {
                            toast(getString(R.string.roster_released, pet.nickname))
                        } else {
                            toast(getString(R.string.roster_home_full))
                        }
                        render()
                    }
                } else {
                    // 家满 → 先让用户选收起谁（不静默替换：被换下去的那只也是养了很久的）
                    showSwapPicker(host, pet)
                }
            })
        }
        host.addView(row)
        host.addView(divider())
    }

    /** 家满：先收起一只（主宠不可选） */
    private fun showSwapPicker(host: LinearLayout, incoming: Pet) {
        val ctx = requireContext()
        val sheet = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(18f).toFloat()
                setColor(0xFFF6F9F4.toInt())
            }
            setPadding(dp(14f), dp(14f), dp(14f), dp(14f))
        }
        sheet.addView(TextView(ctx).apply {
            text = getString(R.string.roster_swap_title, incoming.nickname)
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFF3C4A39.toInt())
        })
        viewLifecycleOwner.lifecycleScope.launch {
            val ownerId = local.getOrCreateOwnerId()
            val roster = petRepository.observeRoster(ownerId).first()
            roster.home.sortedBy { it.homeSlot }.forEach { candidate ->
                val canSend = !candidate.isMain
                sheet.addView(TextView(ctx).apply {
                    text = if (canSend) {
                        getString(R.string.roster_swap_item, candidate.nickname, candidate.level)
                    } else {
                        getString(R.string.roster_swap_main, candidate.nickname)
                    }
                    textSize = 13f
                    setTextColor(if (canSend) 0xFF2E4A2B.toInt() else 0xFF9AA79A.toInt())
                    setPadding(dp(4f), dp(11f), dp(4f), dp(11f))
                    isEnabled = canSend
                    setOnClickListener {
                        viewLifecycleOwner.lifecycleScope.launch {
                            if (!petRepository.sendToYard(candidate.id)) {
                                toast(getString(R.string.roster_send_failed, candidate.nickname))
                                return@launch
                            }
                            val slot = petRepository.bringHome(incoming.id)
                            if (slot != null) {
                                toast(
                                    getString(
                                        R.string.roster_swapped,
                                        incoming.nickname,
                                        candidate.nickname
                                    )
                                )
                            } else {
                                // 极端兜底：收起成功但接回失败（并发写），把话说清楚
                                toast(getString(R.string.roster_home_full))
                            }
                            render()
                        }
                    }
                })
            }
            host.addView(sheet)
        }
    }

    private fun openFusion(pet: Pet) {
        FusionDialog().apply {
            initialSpeciesId = pet.species.id
            onGoPairing = null
        }.show(childFragmentManager, FusionDialog.TAG)
    }

    private fun sectionHeader(text: String): TextView = TextView(requireContext()).apply {
        this.text = text
        textSize = 11.5f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(0xFF44553F.toInt())
        setPadding(0, dp(16f), 0, dp(2f))
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

    /** 主宠标记：不可收起，但也不该长得像"按钮坏了" */
    private fun crownTag(): TextView = TextView(requireContext()).apply {
        text = getString(R.string.roster_main_tag)
        textSize = 11f
        setTextColor(0xFF9A6A00.toInt())
        setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
        background = GradientDrawable().apply {
            cornerRadius = dp(12f).toFloat()
            setColor(0xFFFFF3D6.toInt())
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
    private fun dp(v: Int): Int = dp(v.toFloat())

    companion object {
        const val TAG = "PetRosterDialog"
        private const val ARG_SPECIES_ID = "species_id"

        /** @param speciesId 非空 = 只看该物种的个体（图鉴格子点进来） */
        fun newInstance(speciesId: String? = null): PetRosterDialog =
            PetRosterDialog().apply {
                arguments = Bundle().apply {
                    if (speciesId != null) putString(ARG_SPECIES_ID, speciesId)
                }
            }
    }
}
