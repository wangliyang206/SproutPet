package com.sproutpet.pet.ui.scene

import android.graphics.Color
import android.os.Bundle
import android.os.Vibrator
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.sproutpet.core.base.BaseFragment
import com.sproutpet.core.domain.desktop.ScenePetLayout
import com.sproutpet.core.domain.pet.Pet
import com.sproutpet.core.domain.pet.PetRenderer
import com.sproutpet.core.domain.pet.PetRendererFactory
import com.sproutpet.pet.R
import com.sproutpet.pet.databinding.FragmentSceneBinding
import com.sproutpet.pet.pet.renderer.frame.PetViewAnims
import com.sproutpet.pet.ui.drawer.HomeDrawerFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 全屏生活场景（倒置 IA 主界面，design-v2 屏①/屏⑩）。
 *
 * 职责：
 * - 昼夜背景（随真实时间）
 * - 宠物站位层（`ScenePetLayout.homeLayout` 两排各 3）
 * - 角落的蛋（开箱 / 孵化 / 破壳唯一直接入）
 * - 右缘抽屉把手
 * - 搭话气泡与互动飘字（特效层）
 *
 * 业务编排全部在 [SceneViewModel]，这里只做渲染与手势接线（A1 红线）。
 */
@AndroidEntryPoint
class SceneFragment : BaseFragment<FragmentSceneBinding>() {

    @Inject
    lateinit var rendererFactory: PetRendererFactory

    private val viewModel: SceneViewModel by viewModels()

    /** 每只已破壳宠物一套视图（按 petId 复用，避免数据刷新导致动画重置） */
    private inner class PetSlot(
        val renderer: PetRenderer,
        val container: FrameLayout,
        val plate: TextView,
        val stateController: PetStateController,
        val lifecycleController: PetLifecycleController,
        var stage: com.sproutpet.core.domain.pet.PetStage
    )

    private val petSlots = HashMap<Long, PetSlot>()
    private var bubbleView: PetSpeechBubbleView? = null
    private var bubblePetId: Long = -1L
    private var drawerAdded = false

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentSceneBinding =
        FragmentSceneBinding.inflate(inflater, container, false)

    override fun initView(view: View, savedInstanceState: Bundle?) {
        // 状态栏/导航栏沉浸式留白：日签与把手避让系统条
        ViewCompat.setOnApplyWindowInsetsListener(binding.tvDayChip) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            (v.layoutParams as? FrameLayout.LayoutParams)?.topMargin = top + dp(14f)
            insets
        }

        binding.drawerHandle.setOnClickListener {
            binding.drawerLayout.openDrawer(Gravity.END)
        }
        binding.drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) = refreshDrawerIfNeeded()
        })

        // 角落的蛋：三态入口（开箱 / 孵化抽层 / 破壳）
        binding.sceneEgg.root.setOnClickListener { viewModel.onEggClicked() }

        // 蛋的周期性晃动（孵化中吸引注意）
        startEggWobbleLoop()
    }

    override fun initData() {
        viewModel.uiState.collectInView(::render)
        viewModel.events.collectInView(::handleEvent)
    }

    // ═══════════ 状态渲染 ═══════════

    private fun render(state: SceneUiState) {
        renderBackground(state.isNight)
        binding.tvDayChip.text = if (state.hasAnyPet) {
            getString(R.string.scene_day_chip, state.companionDays)
        } else {
            getString(R.string.scene_day_chip_new)
        }
        binding.tvFootHint.visibility = if (state.hasAnyPet) View.GONE else View.VISIBLE

        renderEgg(state)
        renderPets(state.pets)
        renderBubble(state)
    }

    private fun renderBackground(isNight: Boolean) {
        val res = if (isNight) R.drawable.bg_home_night else R.drawable.bg_home_day
        if (binding.ivBg.tag != res) {
            binding.ivBg.setImageResource(res)
            binding.ivBg.tag = res
        }
    }

    /** 角落蛋三态：可开箱（含免费引导） / 孵化中 / 可破壳 */
    private fun renderEgg(state: SceneUiState) {
        val egg = state.incubatingEgg
        val tag = binding.sceneEgg.tvEggTag
        val iv = binding.sceneEgg.ivEgg
        if (egg == null) {
            iv.setImageResource(R.drawable.pet_egg_common)
            tag.text = if (state.hasAnyPet) {
                getString(R.string.reveal_pity, state.pityRemaining)
            } else {
                getString(R.string.scene_egg_tag_free)
            }
            return
        }
        val ready = egg.hatchRemainMs() <= 0L
        iv.setImageResource(
            if (ready || egg.hatchProgress() >= 0.5f) R.drawable.pet_egg_cracked else R.drawable.pet_egg_common
        )
        tag.text = getString(
            if (ready) R.string.scene_egg_tag_ready else R.string.scene_egg_tag_incubating
        )
    }

    /** 宠物站位：仅增删时重建对应槽位，等级/昵称变化就地更新 */
    private fun renderPets(pets: List<Pet>) {
        val layer = binding.petLayer
        // 移除下线宠物
        val removed = petSlots.keys - pets.map { it.id }.toSet()
        removed.forEach { id ->
            petSlots.remove(id)?.let { slot ->
                slot.lifecycleController.stop()
                slot.renderer.release()
                layer.removeView(slot.container)
            }
        }
        if (pets.isEmpty()) return

        val slots = ScenePetLayout.homeLayout(pets.size)
        pets.forEachIndexed { index, pet ->
            val slot = petSlots[pet.id]
            if (slot == null) {
                addPetSlot(pet, slots[index])
            } else {
                // 就地更新：名牌 / 阶段变化时重载渲染资源
                slot.plate.text = "${pet.nickname} · ${getString(R.string.pet_plate_level, pet.level)}"
                if (slot.stage != pet.stage) {
                    slot.stage = pet.stage
                    slot.renderer.load(pet.species, pet.stage, pet.genes)
                }
            }
        }
    }

    private fun addPetSlot(pet: Pet, slot: ScenePetLayout.Slot) {
        val ctx = requireContext()
        val petSize = (dp(120f) * slot.scale).toInt()

        val renderer = rendererFactory.create() as com.sproutpet.pet.pet.renderer.frame.FramePetRenderer
        val container = FrameLayout(ctx)
        val plate = TextView(ctx).apply {
            text = "${pet.nickname} · ${getString(R.string.pet_plate_level, pet.level)}"
            setBackgroundResource(R.drawable.bg_chip_white)
            setTextColor(ContextCompat.getColor(ctx, R.color.scene_text))
            textSize = 11f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(10f), dp(4f), dp(10f), dp(4f))
        }

        // 布局：宠物本体 + 名牌（名牌在宠物上方居中）
        container.addView(renderer.view, FrameLayout.LayoutParams(petSize, petSize, Gravity.CENTER))
        container.addView(plate, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ))

        lateinit var stateController: PetStateController
        val lifecycleController = PetLifecycleController(pet.genes.liveliness) { state ->
            stateController.onBaseStateChanged(state)
        }
        stateController = PetStateController(renderer, lifecycleController)

        val slotHolder = PetSlot(renderer, container, plate, stateController, lifecycleController, pet.stage)
        petSlots[pet.id] = slotHolder

        // 手势接线
        val gestures = PetGestureController(ctx, container, object : PetGestureController.Listener {
            override fun onPet() {
                stateController.petted()
                viewModel.onPetPetted(pet.id)
                spawnHearts(container)
            }
            override fun onExcite() = stateController.excited()
            override fun onGrabStart() = stateController.grabbed()
            override fun onDragging(offsetX: Float, offsetY: Float) =
                stateController.dragging(offsetX, offsetY)
            override fun onRelease(fling: Boolean) = stateController.released()
        })
        gestures.attach()

        // 站位（脚底中心 = 归一化坐标），待层布局完成后落位
        val containerW = petSize
        val containerH = petSize + dp(30f)
        binding.petLayer.addView(container, FrameLayout.LayoutParams(containerW, containerH))
        binding.petLayer.post {
            val w = binding.petLayer.width
            val h = binding.petLayer.height
            container.translationX = slot.x * w - containerW / 2f
            container.translationY = slot.y * h - containerH + dp(10f)
        }

        renderer.load(pet.species, pet.stage, pet.genes)
        lifecycleController.start(viewLifecycleOwner.lifecycleScope)
    }

    // ═══════════ 气泡与飘字 ═══════════

    private fun renderBubble(state: SceneUiState) {
        val text = state.bubbleText
        val mainPet = state.pets.firstOrNull()
        if (text == null || mainPet == null) {
            bubbleView?.let { binding.effectLayer.removeView(it) }
            bubbleView = null
            bubblePetId = -1L
            return
        }
        if (bubbleView != null && bubblePetId == mainPet.id) return
        bubbleView?.let { binding.effectLayer.removeView(it) }
        val bubble = PetSpeechBubbleView(requireContext())
        bubble.bind(text, getString(R.string.bubble_action_pet)) {
            viewModel.onBubbleAction()
        }
        bubbleView = bubble
        bubblePetId = mainPet.id
        binding.effectLayer.addView(bubble, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        binding.effectLayer.post {
            val slot = petSlots[mainPet.id] ?: return@post
            bubble.translationX = (slot.container.translationX + slot.container.width / 2f - bubble.width / 3f)
                .coerceIn(0f, (binding.effectLayer.width - bubble.width).toFloat().coerceAtLeast(0f))
            bubble.translationY = (slot.container.translationY - bubble.height - dp(6f)).coerceAtLeast(dp(48f).toFloat())
        }
        // 8 秒后自动收起（不刷屏，docs/00 §4.4）
        viewLifecycleOwner.lifecycleScope.launch {
            delay(8_000L)
            if (bubbleView === bubble) viewModel.dismissBubble()
        }
    }

    /** +N 经验飘字（design-v2 .floaty.exp） */
    private fun spawnFloatyExp(petId: Long, text: String) {
        val slot = petSlots[petId] ?: return
        val ctx = context ?: return
        val floaty = TextView(ctx).apply {
            this.text = text
            setTextColor(ContextCompat.getColor(ctx, R.color.brand_primary_dark))
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setShadowLayer(6f, 0f, 2f, Color.WHITE)
        }
        binding.effectLayer.addView(floaty, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        binding.effectLayer.post {
            floaty.translationX = slot.container.translationX + slot.container.width / 2f - floaty.width / 2f
            floaty.translationY = slot.container.translationY
            floaty.animate()
                .translationYBy(-dp(56f).toFloat())
                .alpha(0f)
                .setDuration(900L)
                .withEndAction { binding.effectLayer.removeView(floaty) }
                .start()
        }
    }

    /** 抚摸爱心（design-v2 .heart） */
    private fun spawnHearts(container: View) {
        val ctx = context ?: return
        repeat(3) { i ->
            val heart = TextView(ctx).apply {
                text = "❤️"
                textSize = 16f
                alpha = 0f
            }
            binding.effectLayer.addView(heart, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            binding.effectLayer.post {
                heart.translationX = container.translationX + container.width / 2f + (i - 1) * dp(18f).toFloat()
                heart.translationY = container.translationY + dp(10f).toFloat()
                heart.animate()
                    .alpha(1f)
                    .translationYBy(-dp((36 + i * 12).toFloat()).toFloat())
                    .translationXBy(((i - 1) * dp(10f)).toFloat())
                    .setDuration(650L + i * 120L)
                    .withEndAction {
                        heart.animate().alpha(0f).setDuration(250L)
                            .withEndAction { binding.effectLayer.removeView(heart) }.start()
                    }
                    .start()
            }
        }
    }

    // ═══════════ 一次性事件 ═══════════

    private fun handleEvent(event: SceneEvent) {
        when (event) {
            is SceneEvent.ShowReveal ->
                BlindBoxRevealDialog.newInstance(event.outcome)
                    .show(childFragmentManager, BlindBoxRevealDialog.TAG)
            is SceneEvent.ShowIncubationSheet ->
                IncubationSheetDialog.newInstance(event.egg).apply {
                    onFocusFinished = { eggId, ms -> viewModel.onFocusSessionFinished(eggId, ms) }
                    onPlaceholder = { name ->
                        toast(getString(R.string.drawer_placeholder, name))
                    }
                }.show(childFragmentManager, IncubationSheetDialog.TAG)
            is SceneEvent.ShowHatching -> playHatchingOverlay(event.pet)
            is SceneEvent.ShowNaming ->
                NamingDialogFragment.newInstance(event.pet).apply {
                    onNamed = { petId, name -> viewModel.onNamed(petId, name) }
                }.show(childFragmentManager, NamingDialogFragment.TAG)
            is SceneEvent.FloatyExp -> spawnFloatyExp(event.petId, event.text)
            is SceneEvent.LevelUp -> {
                petSlots[event.petId]?.stateController?.levelUp()
                toast(getString(R.string.interact_level_up, event.level))
            }
            is SceneEvent.ToastMsg -> toast(event.msg)
        }
    }

    /** 破壳动画：遮罩 + 裂蛋晃动 + 震动（3 秒，docs/04 §1.4） */
    private fun playHatchingOverlay(pet: Pet) {
        val ctx = context ?: return
        val scrim = View(ctx).apply {
            setBackgroundColor(ContextCompat.getColor(ctx, R.color.scene_scrim))
        }
        val egg = ImageView(ctx).apply {
            setImageResource(R.drawable.pet_egg_cracked)
        }
        val title = TextView(ctx).apply {
            text = getString(R.string.hatching_title)
            setTextColor(Color.WHITE)
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val layer = binding.effectLayer
        layer.addView(scrim, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        layer.addView(egg, FrameLayout.LayoutParams(dp(120f), dp(120f), Gravity.CENTER))
        layer.addView(title, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
        ).apply { bottomMargin = dp(140f) })

        @Suppress("DEPRECATION")
        val vibrator = ContextCompat.getSystemService(ctx, Vibrator::class.java)
        @Suppress("DEPRECATION")
        vibrator?.vibrate(400L)

        val wobble = PetViewAnims.eggWobble(egg)
        wobble.start()
        viewLifecycleOwner.lifecycleScope.launch {
            repeat(4) {
                delay(620L)
                PetViewAnims.eggWobble(egg).start()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            delay(HATCHING_DURATION_MS)
            layer.removeView(scrim)
            layer.removeView(egg)
            layer.removeView(title)
            viewModel.onHatchingAnimDone(pet)
        }
    }

    // ═══════════ 抽屉 ═══════════

    private fun refreshDrawerIfNeeded() {
        if (drawerAdded) return
        drawerAdded = true
        val drawer = HomeDrawerFragment.newInstance(petSlots.size)
        drawer.onEntryClick = { entryId ->
            binding.drawerLayout.closeDrawers()
            when (entryId) {
                "bag" -> openInventory()
                else -> toast(getString(R.string.drawer_placeholder, entryLabel(entryId)))
            }
        }
        childFragmentManager.beginTransaction()
            .replace(binding.drawerContainer.id, drawer)
            .commitAllowingStateLoss()
    }

    private fun entryLabel(entryId: String): String = when (entryId) {
        "codex" -> getString(R.string.drawer_entry_codex)
        "family" -> getString(R.string.drawer_entry_family)
        "bag" -> getString(R.string.drawer_entry_bag)
        "desktop" -> getString(R.string.drawer_entry_desktop)
        "achievement" -> getString(R.string.drawer_entry_achievement)
        "hill" -> getString(R.string.drawer_entry_hill)
        "settings" -> getString(R.string.drawer_entry_settings)
        else -> entryId
    }

    private fun openInventory() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.scene_container, com.sproutpet.pet.ui.inventory.InventoryFragment())
            .addToBackStack(null)
            .commit()
    }

    // ═══════════ 蛋的周期晃动 ═══════════

    private fun startEggWobbleLoop() {
        viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                delay(4_000L)
                if (viewModel.uiState.value.incubatingEgg != null) {
                    PetViewAnims.eggWobble(binding.sceneEgg.ivEgg).start()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshDayNight()
    }

    override fun onDestroyView() {
        petSlots.values.forEach { it.lifecycleController.stop(); it.renderer.release() }
        petSlots.clear()
        super.onDestroyView()
    }

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        /** 破壳动画时长（docs/04 §1.4：3 秒 + 震动） */
        private const val HATCHING_DURATION_MS = 3_000L

        fun newInstance(): SceneFragment = SceneFragment()
    }
}
