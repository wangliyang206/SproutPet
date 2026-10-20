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
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.sproutpet.core.domain.pet.Pet
import com.sproutpet.core.domain.pet.PetStage
import com.sproutpet.core.domain.pet.SpeciesFamily
import com.sproutpet.pet.R
import com.sproutpet.pet.data.repository.PetRepository
import com.sproutpet.pet.pet.renderer.PetAssetResolver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 宠物档案浮卡（design-v2 屏 21 · 长按宠物 ·「长按它 · 档案浮上来」）。
 *
 * 交互口径（docs/04 §1.1）：**长按后原地松手 = 档案浮上来**；拖走 = 拎起玩耍，不弹卡。
 * 「左右滑动卡片看其他成员」MVP 用 ‹ › 按钮代替（滑动翻页随渲染 Phase 3 补）。
 */
@AndroidEntryPoint
class PetProfileDialog : BottomSheetDialogFragment() {

    @Inject
    lateinit var petRepository: PetRepository

    /** 同批可切换的家庭成员（场景在册宠物 id，按当前站位顺序） */
    private val memberIds = mutableListOf<Long>()
    private var currentId: Long = -1L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        currentId = requireArguments().getLong(ARG_PET_ID)
        memberIds.clear()
        memberIds.addAll(requireArguments().getLongArray(ARG_MEMBERS)?.toList().orEmpty())
        if (currentId !in memberIds) memberIds.add(0, currentId)

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
            render(view as ViewGroup, currentId)
        }
    }

    private suspend fun render(card: ViewGroup, petId: Long) {
        card.removeAllViews()
        val ctx = requireContext()
        val pet = petRepository.queryById(petId)
        if (pet == null) {
            card.addView(note(ctx, getString(R.string.profile_pet_missing)))
            return
        }

        // ── 头部：形象 + 名字 + 主宠标 ──
        val head = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val iconRes = runCatching { PetAssetResolver.resolve(ctx, pet.species, pet.stage) }
        if (iconRes.isSuccess) {
            head.addView(ImageView(ctx).apply {
                setImageResource(iconRes.getOrThrow())
                layoutParams = LinearLayout.LayoutParams(dp(64f), dp(64f)).apply { marginEnd = dp(12f) }
            })
        }
        val headText = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }
        headText.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(ctx).apply {
                text = pet.nickname
                textSize = 20f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(0xFF212121.toInt())
            })
            if (pet.isMain) {
                addView(TextView(ctx).apply {
                    text = getString(R.string.profile_tag_main)
                    textSize = 10.5f
                    setTextColor(Color.WHITE)
                    background = GradientDrawable().apply {
                        cornerRadius = dp(8f).toFloat()
                        setColor(0xFFFF8A3D.toInt())
                    }
                    setPadding(dp(6f), dp(2f), dp(6f), dp(2f))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { marginStart = dp(8f) }
                })
            }
        })
        headText.addView(TextView(ctx).apply {
            text = getString(
                R.string.profile_subtitle,
                pet.gender.displayName,
                pet.species.family.displayName,
                pet.rarity.displayName,
                pet.generation
            )
            textSize = 12.5f
            setTextColor(0xFF5B6B58.toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(3f) }
        })
        head.addView(headText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(head)

        // ── 等级与经验 ──
        card.addView(TextView(ctx).apply {
            text = getString(
                R.string.profile_level,
                pet.level, pet.stage.displayName, pet.exp, pet.expToNext
            )
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFF212121.toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14f) }
        })
        card.addView(ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = if (pet.expToNext == Int.MAX_VALUE) 100 else pet.expToNext
            progress = if (pet.expToNext == Int.MAX_VALUE) 100 else pet.exp
            progressTintList = android.content.res.ColorStateList.valueOf(0xFF4CAF50.toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(8f)
            ).apply {
                topMargin = dp(6f)
                marginEnd = dp(4f)
            }
        })

        // ── 六格属性 ──
        card.addView(attrGrid(ctx, pet))

        // ── 解锁提示 ──
        card.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(12f).toFloat()
                setColor(0xFFF2F6F1.toInt())
            }
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12f) }
            addView(TextView(ctx).apply {
                text = if (pet.stage == PetStage.ADULT || pet.stage == PetStage.ELDER) {
                    getString(
                        R.string.profile_unlock_adult,
                        pet.attributes.intimacy,
                        com.sproutpet.core.domain.pet.GrowthRules.PairingRules.requiredIntimacy
                    )
                } else {
                    getString(R.string.profile_unlock_growing)
                }
                textSize = 11.5f
                setTextColor(0xFF5B6B58.toInt())
                setLineSpacing(dp(3f).toFloat(), 1f)
            })
        })

        // ── 成员切换（设计 = 左右滑动卡片；MVP 用 ‹ › ） ──
        if (memberIds.size > 1) {
            card.addView(switcher(ctx, card))
        }
    }

    private fun attrGrid(ctx: android.content.Context, pet: Pet): LinearLayout {
        val grid = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14f) }
        }
        val a = pet.attributes
        val cells = listOf(
            getString(R.string.profile_attr_stamina) to a.stamina,
            getString(R.string.profile_attr_strength) to a.strength,
            getString(R.string.profile_attr_agility) to a.agility,
            getString(R.string.profile_attr_wisdom) to a.wisdom,
            getString(R.string.profile_attr_charm) to a.charm,
            getString(R.string.profile_attr_power) to a.power
        )
        cells.chunked(3).forEach { rowCells ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(6f) }
            }
            rowCells.forEach { (label, value) ->
                row.addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(6f), dp(10f), dp(6f), dp(10f))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(12f).toFloat()
                        setColor(0xFFF7FBF6.toInt())
                        setStroke(dp(1f), 0xFFE3EBE1.toInt())
                    }
                    addView(TextView(ctx).apply {
                        text = label
                        textSize = 10.5f
                        setTextColor(0xFF9AA79A.toInt())
                    })
                    addView(TextView(ctx).apply {
                        text = value.toString()
                        textSize = 16f
                        setTypeface(typeface, Typeface.BOLD)
                        setTextColor(
                            if (label == getString(R.string.profile_attr_power)) 0xFFE8A33D.toInt()
                            else 0xFF212121.toInt()
                        )
                    })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(3f), 0, dp(3f), 0)
                })
            }
            grid.addView(row)
        }
        return grid
    }

    private fun switcher(ctx: android.content.Context, card: ViewGroup): LinearLayout {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14f) }
        }
        val index = memberIds.indexOf(currentId)

        fun arrowButton(label: String, delta: Int): TextView = TextView(ctx).apply {
            text = label
            textSize = 13f
            setTextColor(0xFF388E3C.toInt())
            setPadding(dp(16f), dp(6f), dp(16f), dp(6f))
            setOnClickListener {
                val next = ((index + delta) + memberIds.size) % memberIds.size
                currentId = memberIds[next]
                viewLifecycleOwner.lifecycleScope.launch { render(card, currentId) }
            }
        }
        row.addView(arrowButton("‹", -1))
        row.addView(TextView(ctx).apply {
            text = getString(R.string.profile_switch_hint)
            textSize = 11.5f
            setTextColor(0xFF9AA79A.toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(12f), 0, dp(12f), 0) }
        })
        row.addView(arrowButton("›", 1))
        return row
    }

    private fun note(ctx: android.content.Context, text: String) = TextView(ctx).apply {
        this.text = text
        textSize = 12f
        setTextColor(0xFF9AA79A.toInt())
        gravity = Gravity.CENTER
    }

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val ARG_PET_ID = "pet_id"
        private const val ARG_MEMBERS = "member_ids"
        const val TAG = "PetProfileDialog"

        fun newInstance(petId: Long, memberIds: List<Long>): PetProfileDialog =
            PetProfileDialog().apply {
                arguments = Bundle().apply {
                    putLong(ARG_PET_ID, petId)
                    putLongArray(ARG_MEMBERS, memberIds.toLongArray())
                }
            }
    }
}
