package com.sproutpet.pet.ui.scene

import android.animation.Animator
import android.content.ClipData
import android.graphics.Color
import android.graphics.RectF
import android.os.Bundle
import android.view.DragEvent
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
import com.sproutpet.core.domain.gacha.TenPullEntry
import com.sproutpet.core.domain.desktop.SceneRoster
import com.sproutpet.core.domain.pet.EggPresentation
import com.sproutpet.core.domain.pet.HomeSlots
import com.sproutpet.core.domain.pet.BehaviorDirector
import com.sproutpet.core.domain.pet.PetBehaviorAction
import com.sproutpet.core.domain.pet.PetBehaviorDecision
import com.sproutpet.core.domain.pet.PetRenderState
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.sproutpet.core.base.BaseFragment
import com.sproutpet.core.domain.desktop.ScenePetLayout
import com.sproutpet.core.domain.pet.Pet
import com.sproutpet.core.domain.pet.PetRenderer
import com.sproutpet.core.domain.pet.PetRendererFactory
import com.sproutpet.pet.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.sproutpet.pet.databinding.FragmentSceneBinding
import com.sproutpet.pet.pet.renderer.frame.PetViewAnims
import com.sproutpet.pet.ui.codex.CodexDialog
import com.sproutpet.pet.ui.codex.FamilyTreeDialog
import com.sproutpet.pet.ui.codex.PetProfileDialog
import com.sproutpet.pet.ui.drawer.HomeDrawerFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.hypot
import kotlin.random.Random

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
        val petId: Long,
        val renderer: com.sproutpet.pet.pet.renderer.frame.FramePetRenderer,
        val container: FrameLayout,
        val plate: TextView,
        val stateController: PetStateController,
        val lifecycleController: PetLifecycleController,
        var stage: com.sproutpet.core.domain.pet.PetStage,
        /** 本宠的站位槽（归一化坐标）—— 托盘收起等安全区变化时按它重新落位 */
        val layoutSlot: ScenePetLayout.Slot
    ) {
        /** 地面投影（2.5D 立体：跳起/拎起时反向缩放） */
        lateinit var shadow: View

        /** 被用户拎起中（行为循环据此让路） */
        var isGrabbed = false

        /** 溜达中的位移动画（拎起时要打断） */
        var moveAnim: android.view.ViewPropertyAnimator? = null

        /** 行为循环协程（docs/15 §十） */
        var behaviorJob: Job? = null
    }

    private val petSlots = HashMap<Long, PetSlot>()
    private var bubbleView: PetSpeechBubbleView? = null
    private var bubblePetId: Long = -1L

    /** 首蛋聚焦层的倒计时协程（docs/14 §1.4：每秒刷新 + 数字跳动 + 末 10 秒转暖橙） */
    private var firstEggTickerJob: Job? = null

    /**
     * 聚焦层当前是否「由数据驱动地显示着」。
     *
     * ⚠️ **不能拿 `visibility` 当状态**（2026-10-22 真机「大蛋慢慢消失，只剩背景」）：
     * 淡出动画跑到一半时 `visibility` 仍是 VISIBLE、`alpha` 已经是 0，
     * 此时再来一帧 `visible = true`，旧实现看到 `visibility == VISIBLE` 就跳过了淡入，
     * 于是停在「VISIBLE + 透明」——看不见、却仍然全屏拦截点击。
     * 用一个显式布尔量记住真实意图，动画只跟着它走。
     */
    private var firstEggFocusShown = false

    /** 聚焦层蛋当前是否处于「到点 · 裂缝露眼」态：状态刷新与本地倒计时两条路都靠它去重 */
    private var firstEggReady = false

    /** 聚焦层蛋的呼吸动画句柄：无限动画重复 start 会叠加互扯（蛋会莫名抖动/缩小） */
    private var firstEggBreathAnim: Animator? = null

    /** 上次触发脉冲的十连门票数（避免每次数据刷新都抖一下） */
    private var badgePulseFor: Int? = null

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

        // 角落的蛋：点击 = 三态入口（开箱 / 孵化抽层 / 破壳）
        binding.sceneEgg.root.setOnClickListener { viewModel.onEggClicked() }
        // 长按 = 开箱台（design-v2 屏 14）：十连入口之一，保底与门票也在这里
        // 主入口是蛋旁的「十连」徽章（长按不可发现，docs/14 §2.4）
        binding.sceneEgg.root.setOnLongClickListener {
            viewModel.onOpenBoxRequested()
            true
        }

        // 蛋旁的「十连」徽章 —— **主入口**（长按只有知道的人才会做，docs/14 §2.4）
        binding.sceneEgg.tvTenBadge.setOnClickListener { viewModel.onOpenBoxRequested() }

        // 院子角标（家里放不下的那些）
        binding.tvYardChip.setOnClickListener { viewModel.onYardRequested() }

        // 蛋的周期性晃动（孵化中吸引注意）
        startEggWobbleLoop()

        // 首蛋聚焦层（docs/14 §1.4）：首蛋孵化期盖在场景之上的一次性演出
        setupFirstEggFocus()

        // 互动托盘（design-v2 屏③ · K-2）：长按道具卡启动拖拽
        setupTrayDragSources()
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
        // 底部提示只在「既没宠物、也没蛋」时出现 —— 有蛋的时候它指向的角落蛋
        // 要么被聚焦层挡住、要么本身就是主入口，再说一句「点右下角的蛋」是噪音。
        binding.tvFootHint.visibility =
            if (state.hasAnyPet || state.incubatingEgg != null) View.GONE else View.VISIBLE
        // 首日第一帧：落蛋要写一次库，这几百毫秒里说清楚"在准备"，别让用户对着空场景猜
        binding.tvFootHint.text = if (state.loading) {
            getString(R.string.scene_preparing_egg)
        } else {
            getString(R.string.scene_foot_hint)
        }

        renderEgg(state)
        renderYardChip(state.yardCount)
        renderPets(state.pets)
        renderBubble(state)
        renderTray(state)
        renderDrawerStats(state)
    }

    /**
     * 抽屉里的数字随数据刷新（不必等用户下次打开抽屉）。
     *
     * 抽屉是「只建一次」的 Fragment，所以必须有人**主动推**新值进去 ——
     * 早期实现只在建的时候读一次 `petSlots.size`，之后永远不动。
     */
    private fun renderDrawerStats(state: SceneUiState) {
        val drawer = childFragmentManager.findFragmentByTag(HomeDrawerFragment.TAG)
            as? HomeDrawerFragment ?: return
        drawer.render(state.petTotal, state.yardCount, state.codex)
    }

    /**
     * 「院子里还有 N 只 ›」（三层链条：拥有不限 ⊃ 家在册 6）。
     *
     * v3.28（2026-10-22 用户拍板）后这是**常态**：破壳默认只入册一只，
     * 十连的其余 9 只都在院子待命。没有这个角标，用户会认为
     * **抽到的宠物丢了** —— 静默丢宠比崩溃更难被发现。
     * 常驻可见是硬要求（同「只靠手势的入口 = 没有入口」的教训）。
     */
    private fun renderYardChip(count: Int) {
        val chip = binding.tvYardChip
        if (count <= 0) {
            chip.visibility = View.GONE
        } else {
            chip.visibility = View.VISIBLE
            chip.text = getString(R.string.yard_chip, count)
        }
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
        // 数据未到：不猜、不画。宁可第一帧是纯背景，也不能把布局里的占位蛋当真数据 ——
        // 「占位冒充数据」正是 2026-10-22「大蛋慢慢消失」的另一半原因（见 fragment_scene.xml 注释）。
        if (state.loading) {
            binding.sceneEgg.root.visibility = View.GONE
            renderFirstEggFocus(state, null, false)
            return
        }
        val egg = state.incubatingEgg
        val tag = binding.sceneEgg.tvEggTag
        val iv = binding.sceneEgg.ivEgg
        // 首蛋聚焦层：除了这颗蛋没有任何已破壳宠物（docs/14 §1.4）
        // 判据收在 core 的 EggPresentation —— 位置只有三种，UI 不许自己再判一遍
        val stage = EggPresentation.of(egg, state.hasAnyPet)
        val focusVisible = stage == EggPresentation.FOCUS
        renderFirstEggFocus(state, egg, focusVisible)
        // 聚焦层是半透明压暗（#47…），角落蛋会从底下透出来造成"两颗蛋" —— 聚焦期藏掉角落蛋。
        // ⚠️ 传的是「聚焦层**真的**显示着」（firstEggFocusShown），不是「应该显示」：
        // 聚焦层若因时序/动画没能显示，角落蛋必须兜底可见 ——
        // 否则用户面对的是一屏什么都不能点的背景（2026-10-22 真机现象）。
        binding.sceneEgg.root.visibility =
            if (EggPresentation.cornerVisible(stage, firstEggFocusShown)) View.VISIBLE else View.GONE
        renderTenBadge(state)
        if (egg == null) {
            iv.setImageResource(R.drawable.pet_egg_common)
            tag.text = when {
                state.hasAnyPet -> getString(R.string.reveal_pity, state.pityRemaining)
                // 无蛋无宠 = 首蛋没落成（正常路径下不该出现）。这时**必须**把角落蛋
                // 说成一个明确入口，否则用户面对的是一屏背景，不知道下一步做什么。
                else -> getString(R.string.scene_egg_tag_start)
            }
            return
        }
        val ready = egg.hatchRemainMs() <= 0L
        // 到点 = 裂缝露眼（docs/14 §1.6 · 2026-10-22 用户拍板保留）；破壳过半 = 壳裂碎片；其余 = 完整蛋
        iv.setImageResource(
            when {
                ready -> R.drawable.pet_egg_ready
                egg.hatchProgress() >= 0.5f -> R.drawable.pet_egg_cracked
                else -> R.drawable.pet_egg_common
            }
        )
        tag.text = getString(
            if (ready) R.string.scene_egg_tag_ready else R.string.scene_egg_tag_incubating
        )
    }

    /**
     * 蛋旁的「十连」徽章（docs/14 §2.4 · 2026-10-20 真机反馈）。
     *
     * 判据全部来自 core 的 [TenPullEntry]（可见性 / 文案 / 是否脉冲），这里只画。
     * 门票从「不够」跳到「够 10 张」（首只奖励到账 / 补票）时脉冲三下 ——
     * 用户目光在宠物身上，全靠这一下把「这里多了一个能点的东西」递到眼角。
     */
    private fun renderTenBadge(state: SceneUiState) {
        val badge = binding.sceneEgg.tvTenBadge
        when (val entry = TenPullEntry.of(state.ticketCount, state.hasAnyPet)) {
            TenPullEntry.Hidden -> {
                badge.visibility = View.GONE
                badgePulseFor = null
            }
            is TenPullEntry.Ready -> {
                badge.visibility = View.VISIBLE
                badge.alpha = 1f
                badge.text = getString(R.string.ten_badge_ready, entry.tickets)
                if (badgePulseFor != entry.tickets) {
                    badgePulseFor = entry.tickets
                    pulseBadge(badge)
                }
            }
            is TenPullEntry.Short -> {
                badge.visibility = View.VISIBLE
                badge.alpha = 0.7f
                badge.text = getString(R.string.ten_badge_short, entry.missing)
                badgePulseFor = null
            }
        }
    }

    /** 脉冲三下（逐个做，不能用 setStartDelay 叠加 —— 同一 View 的后一次 animate 会取消前一次） */
    private fun pulseBadge(badge: View) {
        viewLifecycleOwner.lifecycleScope.launch {
            repeat(3) {
                badge.animate().scaleX(1.14f).scaleY(1.14f).setDuration(150L).start()
                delay(170L)
                badge.animate().scaleX(1f).scaleY(1f).setDuration(170L).start()
                delay(160L)
            }
        }
    }

    /**
     * 宠物站位：仅增删时重建对应槽位，等级/昵称变化就地更新。
     *
     * 🛡 **硬上限 [HomeSlots.MAX_HOME_PETS]**（2026-10-20 崩溃修复）。
     * 正常路径已由 `PetRepository.observeHomePets` 过滤 `atHome` 保证 ≤6，
     * 这里再 `take` 一次是**防御性冗余**：`homeLayout` 超限是 `require` 抛异常，
     * 发生在主线程 = 直接崩 App，代价远高于多一次截断。
     * （崩溃现场：十连 10 只 + 原有 1 只 = 11 只 → `IllegalArgumentException`）
     */
    private fun renderPets(pets: List<Pet>) {
        val layer = binding.petLayer
        val onStage = SceneRoster.onStage(pets)
        // 移除下线宠物（进院子 / 退休的也要从场景撤走，否则会站在原地当幽灵）
        val removed = petSlots.keys - onStage.map { it.id }.toSet()
        removed.forEach { id ->
            petSlots.remove(id)?.let { slot ->
                slot.lifecycleController.stop()
                slot.renderer.release()
                layer.removeView(slot.container)
            }
        }
        if (onStage.isEmpty()) return

        val slots = ScenePetLayout.homeLayout(onStage.size)
        onStage.forEachIndexed { index, pet ->
            val slot = petSlots[pet.id]
            if (slot == null) {
                addPetSlot(pet, slots[index])
            } else {
                // 就地更新：名牌 / 阶段变化时重载渲染资源
                slot.plate.text = "${pet.nickname} · ${getString(R.string.pet_plate_level, pet.level)}"
                if (slot.stage != pet.stage) {
                    slot.stage = pet.stage
                    slot.renderer.load(pet.species, pet.stage, pet.genes, pet.rarity)
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
            if (state is PetRenderState.Sleeping) {
                // 睡着 = 打呼噜气泡（docs/15 §十：基础状态也要有「演出」）
                spawnTalk(pet.id, getString(R.string.talk_sleep))
            }
        }
        stateController = PetStateController(renderer, lifecycleController)

        // 地面投影（2.5D 之一）：最底层，脚底下居中
        val shadow = View(ctx).apply { setBackgroundResource(R.drawable.bg_pet_shadow) }
        container.addView(shadow, FrameLayout.LayoutParams(
            (petSize * 0.66f).toInt(), (petSize * 0.16f).toInt(),
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ))

        val slotHolder = PetSlot(
            pet.id, renderer, container, plate, stateController, lifecycleController, pet.stage, slot
        )
        slotHolder.shadow = shadow
        petSlots[pet.id] = slotHolder

        // 手势接线（拎起起点用于「长按原地松手 = 档案浮卡」，见 design-v2 屏 21）
        var grabStartX = 0f
        var grabStartY = 0f
        val gestures = PetGestureController(ctx, container, object : PetGestureController.Listener {
            override fun onPet() {
                stateController.petted()
                viewModel.onPetPetted(pet.id)
                spawnHearts(container)
            }
            override fun onExcite() = stateController.excited()
            override fun onGrabStart() {
                grabStartX = container.translationX
                grabStartY = container.translationY
                slotHolder.isGrabbed = true
                // 打断溜达（拎起时宠物不再自己走）
                slotHolder.moveAnim?.cancel()
                slotHolder.moveAnim = null
                stateController.grabbed()
                // 影子视差：升空 → 影子缩小变淡（2.5D 高度感）
                shadow.animate().scaleX(0.55f).scaleY(0.55f).alpha(0.45f).setDuration(140L).start()
            }
            override fun onDragging(offsetX: Float, offsetY: Float) {
                stateController.dragging(offsetX, offsetY)
                // 透视倾斜（2.5D）：像捏着一张立体卡片，随拖动方向轻微翻转
                container.cameraDistance = 8_000f * resources.displayMetrics.density
                container.rotationY = (offsetX / 12f).coerceIn(-10f, 10f)
                container.rotationX = (-offsetY / 12f).coerceIn(-8f, 8f)
            }
            override fun onRelease(fling: Boolean) {
                slotHolder.isGrabbed = false
                container.animate().rotationX(0f).rotationY(0f).setDuration(200L).start()
                shadow.animate().scaleX(1f).scaleY(1f).alpha(0.8f).setDuration(260L).start()
                stateController.released()
                // 长按后没拖走（位移 < 12dp 且没有甩出）= 「长按它 · 档案浮上来」
                val dx = container.translationX - grabStartX
                val dy = container.translationY - grabStartY
                val tapRadius = dp(12f)
                if (!fling && dx * dx + dy * dy < tapRadius * tapRadius) {
                    showPetProfile(pet)
                }
            }
        })
        gestures.attach()

        // 互动托盘的投放目标（design-v2 屏③ · K-2）：道具拖到宠物身上松手 = 喂食/玩耍/训练
        container.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_ENTERED ->
                    container.animate().scaleX(1.07f).scaleY(1.07f).setDuration(90L).start()
                DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED ->
                    container.animate().scaleX(1f).scaleY(1f).setDuration(90L).start()
            }
            when (event.action) {
                DragEvent.ACTION_DROP -> {
                    (event.localState as? String)?.let { kind ->
                        viewModel.onTrayInteraction(kind, pet.id)
                    }
                    true
                }
                DragEvent.ACTION_DRAG_STARTED -> true
                else -> true
            }
        }

        // 站位（脚底中心 = 归一化坐标），映射到「安全区」内落位（修复真机问题②：
        // 原来按全屏归一化，前排 0.78 与左下角蛋/底部托盘重叠）
        val containerW = petSize
        val containerH = petSize + dp(30f)
        binding.petLayer.addView(container, FrameLayout.LayoutParams(containerW, containerH))
        binding.petLayer.post { placePet(slotHolder, animated = false) }

        renderer.load(pet.species, pet.stage, pet.genes, pet.rarity)
        lifecycleController.start(viewLifecycleOwner.lifecycleScope)
        startBehaviorLoop(slotHolder)
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

    // ═══════════ 互动托盘（design-v2 屏③ · K-2） ═══════════

    /** 托盘显隐 + 展开/收起；同步蛋的避让高度，安全区变化时全部宠物重新落位 */
    private fun renderTray(state: SceneUiState) {
        val tray = binding.interactionTray
        val visible = state.pets.isNotEmpty()
        tray.root.visibility = if (visible) View.VISIBLE else View.GONE
        tray.trayExpanded.visibility = if (visible && !state.trayCollapsed) View.VISIBLE else View.GONE
        tray.trayCollapsed.visibility = if (visible && state.trayCollapsed) View.VISIBLE else View.GONE
        if (tray.tvTrayCollapse.hasOnClickListeners().not()) {
            tray.tvTrayCollapse.setOnClickListener { viewModel.setTrayCollapsed(true) }
            tray.trayCollapsed.setOnClickListener { viewModel.setTrayCollapsed(false) }
        }
        tray.tvTrayCollapsedCount.text = getString(
            R.string.tray_collapsed_summary, state.foodCount + state.toyCount
        )
        if (visible && !state.trayCollapsed) {
            tray.tvTrayFeedCount.text =
                if (state.foodCount > 0) "×${state.foodCount}" else getString(R.string.tray_count_none)
            tray.tvTrayPlayCount.text =
                if (state.toyCount > 0) "×${state.toyCount}" else getString(R.string.tray_count_none)
            // 训练不消耗道具（GrowthRules 口径「消耗体力」无体力系统），数量位给经验增量
            tray.tvTrayTrainCount.text = getString(R.string.tray_count_exp)
            tray.trayFeed.alpha = if (state.foodCount > 0) 1f else 0.45f
            tray.trayPlay.alpha = if (state.toyCount > 0) 1f else 0.45f
        }
        // 蛋（右下角）避让：托盘展开抬到其上方；收起时落回
        val eggLp = binding.sceneEgg.root.layoutParams as FrameLayout.LayoutParams
        val targetBottom = dp(
            when {
                visible && !state.trayCollapsed -> 186f
                else -> 90f
            }
        )
        if (eggLp.bottomMargin != targetBottom) {
            eggLp.bottomMargin = targetBottom
            binding.sceneEgg.root.layoutParams = eggLp
        }
        repositionAll()
    }

    /**
     * 宠物安全区（修复真机问题②）：归一化站位只映射进这块矩形 ——
     * 顶部避开日签、底部避开互动托盘、右缘避开抽屉把手。
     * 托盘展开/收起会改变底部内边距，由 [repositionAll] 平滑过渡。
     */
    private fun petSafeRect(): RectF {
        val w = binding.petLayer.width.toFloat()
        val h = binding.petLayer.height.toFloat()
        val state = viewModel.uiState.value
        val bottomInset = when {
            state.pets.isEmpty() -> dp(28f)
            state.trayCollapsed -> dp(72f)
            else -> dp(176f)
        }
        return RectF(dp(16f).toFloat(), dp(92f).toFloat(), w - dp(56f), h - bottomInset)
    }

    /** 按槽位把一只宠物放进安全区（footX/footY = 脚底中心） */
    private fun placePet(slot: PetSlot, animated: Boolean) {
        if (binding.petLayer.width == 0) return
        val rect = petSafeRect()
        val w = slot.container.layoutParams.width
        val h = slot.container.layoutParams.height
        val footX = rect.left + slot.layoutSlot.x * rect.width()
        val footY = rect.top + slot.layoutSlot.y * rect.height()
        val tx = footX - w / 2f
        val ty = footY - h + dp(10f)
        if (animated) {
            slot.container.animate().translationX(tx).translationY(ty).setDuration(240L).start()
        } else {
            slot.container.translationX = tx
            slot.container.translationY = ty
        }
    }

    /** 安全区变化（托盘收起/展开）→ 全部宠物按各自槽位重新落位 */
    private fun repositionAll() {
        if (petSlots.isEmpty() || binding.petLayer.width == 0) return
        petSlots.values.forEach { placePet(it, animated = true) }
    }

    /** 托盘卡长按 → 系统拖拽（kind 走 localState，投放目标在每只宠物的容器上） */
    private fun setupTrayDragSources() {
        val tray = binding.interactionTray
        val sources = mapOf(
            tray.trayFeed to SceneViewModel.TRAY_KIND_FEED,
            tray.trayPlay to SceneViewModel.TRAY_KIND_PLAY,
            tray.trayTrain to SceneViewModel.TRAY_KIND_TRAIN
        )
        sources.forEach { (view, kind) ->
            view.setOnLongClickListener { v ->
                v.startDragAndDrop(
                    ClipData.newPlainText(kind, kind),
                    View.DragShadowBuilder(v),
                    kind,
                    0
                )
                true
            }
        }
    }

    /** 托盘互动后的宠物台词（就地浮起，design-v2 屏③「开饭啦！」） */
    private fun spawnTalk(petId: Long, text: String) {
        val slot = petSlots[petId] ?: return
        val ctx = context ?: return
        val talk = TextView(ctx).apply {
            this.text = text
            setBackgroundResource(R.drawable.bg_chip_white)
            setTextColor(ContextCompat.getColor(ctx, R.color.scene_text))
            textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(10f), dp(4f), dp(10f), dp(4f))
            alpha = 0f
        }
        binding.effectLayer.addView(talk, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        binding.effectLayer.post {
            talk.translationX = (slot.container.translationX + slot.container.width / 2f - talk.width / 2f)
                .coerceIn(0f, (binding.effectLayer.width - talk.width).toFloat().coerceAtLeast(0f))
            talk.translationY = (slot.container.translationY - dp(30f)).coerceAtLeast(dp(48f).toFloat())
            talk.animate().alpha(1f).setDuration(140L).withEndAction {
                talk.animate().alpha(0f).translationYBy(-dp(26f).toFloat())
                    .setStartDelay(1_200L).setDuration(320L)
                    .withEndAction { binding.effectLayer.removeView(talk) }.start()
            }.start()
        }
    }

    // ═══════════ 行为循环（docs/15 §十：溜达 / 张望 / 哈欠 / 社交） ═══════════

    private val behaviorRng = Random.Default

    /** 每宠一个循环：每 6~14s 向 BehaviorDirector 要一拍决策并执行（睡觉/被拎起时让路） */
    private fun startBehaviorLoop(slot: PetSlot) {
        slot.behaviorJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(behaviorRng.nextLong(BehaviorDirector.TICK_MIN_MS, BehaviorDirector.TICK_MAX_MS))
                if (!isActive || petSlots[slot.petId] !== slot) break
                if (slot.isGrabbed || slot.lifecycleController.isSleeping()) continue
                val rect = petSafeRect()
                if (rect.width() <= 0f || rect.height() <= 0f) continue
                val home = homeFoot(slot, rect)
                val decision = BehaviorDirector.decide(
                    behaviorRng,
                    sleeping = false,
                    companionCount = petSlots.size - 1,
                    homeX = home.x, homeY = home.y,
                    minX = rect.left, maxX = rect.right,
                    minY = rect.top, maxY = rect.bottom
                )
                when (decision.action) {
                    PetBehaviorAction.WANDER -> walkTo(slot, decision.targetX, decision.targetY, rect)
                    PetBehaviorAction.SOCIAL -> socialize(slot, rect)
                    PetBehaviorAction.YAWN -> {
                        slot.renderer.playYawn()
                        spawnTalk(slot.petId, getString(R.string.talk_yawn))
                    }
                    PetBehaviorAction.LOOK -> PetViewAnims.lookAround(slot.renderer.view).start()
                    PetBehaviorAction.REST -> Unit
                }
            }
        }
    }

    /** 家锚点（本宠站位在当前安全区里的脚底坐标） */
    private fun homeFoot(slot: PetSlot, rect: RectF): android.graphics.PointF {
        val fx = rect.left + slot.layoutSlot.x * rect.width()
        val fy = rect.top + slot.layoutSlot.y * rect.height()
        return android.graphics.PointF(fx, fy)
    }

    /** 当前脚底坐标（translation + 容器几何） */
    private fun footOf(slot: PetSlot): android.graphics.PointF {
        val w = slot.container.layoutParams.width
        val h = slot.container.layoutParams.height
        return android.graphics.PointF(
            slot.container.translationX + w / 2f,
            slot.container.translationY + h - dp(10f)
        )
    }

    /** 走过去（位移插值 + 走路弹跳 + 面向移动方向），到达后回调 */
    private fun walkTo(
        slot: PetSlot,
        footX: Float,
        footY: Float,
        rect: RectF,
        onArrive: (() -> Unit)? = null
    ) {
        val w = slot.container.layoutParams.width
        val h = slot.container.layoutParams.height
        val from = footOf(slot)
        val dist = hypot(footX - from.x, footY - from.y)
        if (dist < dp(8f)) {
            onArrive?.invoke()
            return
        }
        val duration = ((dist / dp(110f)) * 1000f).toInt().coerceIn(500, 2_600)
        slot.renderer.setFacing(footX < from.x)
        val bob = PetViewAnims.walkBob(slot.renderer.view)
        bob.start()
        slot.moveAnim = slot.container.animate()
            .translationX(footX.coerceIn(rect.left, rect.right) - w / 2f)
            .translationY(footY.coerceIn(rect.top, rect.bottom) - h + dp(10f))
            .setDuration(duration.toLong())
            .withEndAction {
                bob.cancel()
                slot.renderer.view.translationY = 0f
                slot.renderer.view.rotation = 0f
                slot.moveAnim = null
                onArrive?.invoke()
            }
            .also { it.start() }
    }

    /** 凑近最近的伙伴 → 对视 → 双方小跳；偶尔互相搭话（多宠互动，docs/15 §十） */
    private fun socialize(slot: PetSlot, rect: RectF) {
        val others = petSlots.values.filter { it !== slot && !it.isGrabbed }
        val other = others.minByOrNull { o ->
            val a = footOf(slot); val b = footOf(o)
            (a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y)
        } ?: return
        val from = footOf(slot)
        val to = footOf(other)
        val dx = to.x - from.x
        val dy = to.y - from.y
        val dist = hypot(dx, dy)
        val stop = dp(120f)
        val greet = {
            slot.renderer.setFacing(to.x < from.x)
            other.renderer.setFacing(from.x < to.x)
            slot.stateController.excited()
            other.stateController.excited()
            if (behaviorRng.nextDouble() < 0.4) {
                spawnTalk(slot.petId, getString(R.string.talk_social_1))
                spawnTalk(other.petId, getString(R.string.talk_social_2))
            }
        }
        if (dist <= stop) {
            greet()
            return
        }
        walkTo(
            slot,
            (to.x - dx / dist * stop).coerceIn(rect.left, rect.right),
            (to.y - dy / dist * stop).coerceIn(rect.top, rect.bottom),
            rect,
            onArrive = greet
        )
    }

    // ═══════════ 一次性事件 ═══════════

    private fun handleEvent(event: SceneEvent) {
        when (event) {
            is SceneEvent.ShowReveal ->
                BlindBoxRevealDialog.newInstance(event.outcome)
                    .show(childFragmentManager, BlindBoxRevealDialog.TAG)
            is SceneEvent.ShowOpenBoxSheet ->
                OpenBoxSheetDialog.newInstance(event.pityRemaining).apply {
                    onTenPull = { viewModel.onTenPull() }
                    onSingle = { viewModel.onOpenSingleBox() }
                    onGoShop = { openShop() }
                }.show(childFragmentManager, OpenBoxSheetDialog.TAG)
            is SceneEvent.ShowTenPull ->
                TenPullDialog.newInstance(event.outcome).apply {
                    onRevealAll = { eggs -> viewModel.onRevealAll(eggs) }
                    onGoFuse = { showFusion() }
                }.show(childFragmentManager, TenPullDialog.TAG)
            is SceneEvent.ShowIncubationSheet ->
                IncubationSheetDialog.newInstance(event.egg).apply {
                    onStartFocus = { egg -> viewModel.onStartFocus(egg) }
                    onPlaceholder = { name ->
                        toast(getString(R.string.drawer_placeholder, name))
                    }
                }.show(childFragmentManager, IncubationSheetDialog.TAG)
            is SceneEvent.ShowFocus -> {
                val focus = com.sproutpet.pet.ui.focus.FocusFragment.newInstance(event.egg.id)
                focus.onFocusFinished = { eggId, ms -> viewModel.onFocusSessionFinished(eggId, ms) }
                parentFragmentManager.beginTransaction()
                    .replace(R.id.scene_container, focus)
                    .addToBackStack(null)
                    .commit()
            }
            is SceneEvent.ShowHatching ->
                // ⭐ 出壳演出（3 秒裂蛋遮罩 +「要破壳了！」）已整体移除 ——
                // 2026-10-22 用户拍板：「用户点击了蛋，出壳时会有图片中的效果，这个不需要的」。
                // 点击 → 破壳结算 → 直接进强制起名，中间不再插动画层。
                viewModel.onHatchingAnimDone(event.pet)
            is SceneEvent.ShowNaming ->
                NamingDialogFragment.newInstance(event.pet, event.rewardTickets).apply {
                    onNamed = { petId, name -> viewModel.onNamed(petId, name) }
                }.show(childFragmentManager, NamingDialogFragment.TAG)
            SceneEvent.ShowYard ->
                PetRosterDialog().show(childFragmentManager, PetRosterDialog.TAG)
            // SceneEvent.FirstHatchReward 已删（2026-10-22 用户拍板：不要「第一只宝宝出生啦」弹卡）；
            // 奖励照发，只是不再打断 —— 起名弹窗里的奖励条 + 蛋旁「十连」徽章已经足够指向。
            is SceneEvent.FloatyExp -> spawnFloatyExp(event.petId, event.text)
            is SceneEvent.PetTalk -> spawnTalk(event.petId, event.text)
            is SceneEvent.LevelUp -> {
                petSlots[event.petId]?.stateController?.levelUp()
                toast(getString(R.string.interact_level_up, event.level))
            }
            is SceneEvent.ToastMsg -> toast(event.msg)
        }
    }

    // ═══════════ 抽屉 ═══════════

    /**
     * 抽屉**只建一次**，但每次打开都要**重画**（2026-10-20 真机反馈）。
     *
     * 旧实现在这里 `if (drawerAdded) return` —— 建完就再也不更新，
     * 而抽屉入口的「已收集 N」是 `bindEntry(..., descArg = 1)` 写死的常量，
     * 于是无论养多少只都显示「已收集 1 / 18」。
     *
     * 数字全部来自 [SceneUiState.codex] 与 [SceneUiState.petTotal]，
     * 它们又来自同一份 `PetRoster` 快照 —— 抽屉、场景、图鉴页不会互相矛盾。
     */
    private fun refreshDrawerIfNeeded() {
        val state = viewModel.uiState.value
        val existing = childFragmentManager.findFragmentByTag(HomeDrawerFragment.TAG)
            as? HomeDrawerFragment
        if (existing != null) {
            // config change 后 Fragment 被系统重建，`drawerAdded` 已失效；
            // 按 tag 取回同一个实例，避免往 drawerContainer 里叠第二个
            existing.render(state.petTotal, state.yardCount, state.codex)
            return
        }
        val drawer = HomeDrawerFragment()
        drawer.onEntryClick = { entryId ->
            binding.drawerLayout.closeDrawers()
            when (entryId) {
                "bag" -> openInventory()
                "shop" -> openShop()
                "codex" -> CodexDialog().show(childFragmentManager, CodexDialog.TAG)
                "family" -> showFamilyTree()
                else -> toast(getString(R.string.drawer_placeholder, entryLabel(entryId)))
            }
        }
        childFragmentManager.beginTransaction()
            .replace(binding.drawerContainer.id, drawer, HomeDrawerFragment.TAG)
            .commitAllowingStateLoss()
        // commit 是异步的：`_binding` 可能还没就绪，render 会先缓存字段，onViewCreated 补画
        drawer.render(state.petTotal, state.yardCount, state.codex)
    }

    private fun entryLabel(entryId: String): String = when (entryId) {
        "codex" -> getString(R.string.drawer_entry_codex)
        "family" -> getString(R.string.drawer_entry_family)
        "bag" -> getString(R.string.drawer_entry_bag)
        "shop" -> getString(R.string.drawer_entry_shop)
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

    /** 抽屉「商店 · 盲盒」（design-v2 09 屏条目；此前只能从仓库二级进入） */
    private fun openShop() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.scene_container, com.sproutpet.pet.ui.inventory.ShopFragment())
            .addToBackStack(null)
            .commit()
    }

    /** 族谱（design-v2 屏 20 · 底部升起）：以主宠为焦点，无主宠时用第一只在册宠 */
    private fun showFamilyTree() {
        val focusId = viewModel.uiState.value.pets.firstOrNull { it.isMain }?.id
            ?: viewModel.uiState.value.pets.firstOrNull()?.id
        if (focusId == null) {
            toast(getString(R.string.family_pet_missing))
            return
        }
        FamilyTreeDialog.newInstance(focusId).show(childFragmentManager, FamilyTreeDialog.TAG)
    }

    /** 档案浮卡（design-v2 屏 21 · 长按原地松手） */
    private fun showPetProfile(pet: com.sproutpet.core.domain.pet.Pet) {
        val memberIds = viewModel.uiState.value.pets.map { it.id }
        PetProfileDialog.newInstance(pet.id, memberIds)
            .show(childFragmentManager, PetProfileDialog.TAG)
    }

    /** 融合（design-v2 屏 26 · 底部升起）：重复宠的去处 */
    private fun showFusion() {
        FusionDialog().apply {
            onGoPairing = { showPairingPlaza() }
        }.show(childFragmentManager, FusionDialog.TAG)
    }

    /** 配对广场（design-v2 屏 24/25 · 底部升起）：生育搭档与后代 */
    private fun showPairingPlaza() {
        PairingPlazaDialog().show(childFragmentManager, PairingPlazaDialog.TAG)
    }

    // ═══════════ 蛋的周期晃动 ═══════════


    // ═══════════ 首蛋聚焦层（docs/14 §1.4 · W-14-1 拍板落地） ═══════════

    /** 接线：整层点击 = 敲蛋（可破壳时转为破壳）。
     *  2026-10-22 用户拍板：不再挂「起个名字 / 专注陪伴 / 去看图鉴」三个按钮 ——
     *  首蛋只有 1 分钟，加速没有意义；破壳才起名；图鉴走抽屉。这一层只做"看着它孵化"。 */
    private fun setupFirstEggFocus() {
        binding.firstEggFocus.root.setOnClickListener { onFirstEggTapped() }
    }

    /** 聚焦层显隐：可见时启动呼吸 + 倒计时；消失时 200ms 淡出并清理动效 */
    private fun renderFirstEggFocus(state: SceneUiState, egg: Pet?, visible: Boolean) {
        val f = binding.firstEggFocus
        if (!visible) {
            firstEggTickerJob?.cancel()
            firstEggTickerJob = null
            // 呼吸是无限动画：停掉并把缩放复位，否则下次显示会从上次的中间值接着跑
            firstEggBreathAnim?.cancel()
            firstEggBreathAnim = null
            f.ivFirstEgg.scaleX = 1f
            f.ivFirstEgg.scaleY = 1f
            if (!firstEggFocusShown) {
                // 从来没显示过：**不做任何动画**。冷启动第一帧走的就是这条路 ——
                // 真正要防的是「布局里的静态蛋被当数据画出来又淡掉」，
                // 那种"淡出"在用户眼里就是「一颗大蛋慢慢消失」。
                f.root.animate().cancel()
                f.root.visibility = View.GONE
                f.root.alpha = 1f
                return
            }
            firstEggFocusShown = false
            // 先取消可能还在跑的上一次动画，否则 withEndAction 会被新动画丢弃，
            // 留下 VISIBLE + alpha=0 的"隐形全屏遮罩"
            f.root.animate().cancel()
            f.root.animate().alpha(0f).setDuration(200L).withEndAction {
                f.root.visibility = View.GONE
                f.root.alpha = 1f
            }.start()
            return
        }
        val e = requireNotNull(egg)
        // `visibility` 也参与判定：view 被重建（返回栈 / 进程恢复）时布局默认是 GONE，
        // 若只看布尔量会跳过淡入，蛋就永远不出现。
        if (!firstEggFocusShown || f.root.visibility != View.VISIBLE) {
            firstEggFocusShown = true
            f.root.animate().cancel()
            f.root.visibility = View.VISIBLE
            // 淡入而不是直接出现：首蛋是打开 App 后的第一个画面主角，
            // 从 0 淡到 1（220ms）不会有"啪一下跳出来"的突兀（2026-10-22 修「闪」）
            f.root.alpha = 0f
            f.root.animate().cancel()
            f.root.animate().alpha(1f).setDuration(220L).start()
            // 呼吸（复用宠物待机动画，2.4s 往复）
            firstEggBreathAnim?.cancel()
            firstEggBreathAnim = PetViewAnims.breathing(f.ivFirstEgg, 0.5f).also { it.start() }
        }
        startFirstEggTicker(e)
        // 到点态（docs/14 §1.6 · 2026-10-22）：壳横向裂开一道缝，缝里有一双眼睛 ——
        // 用户看得出「里面有活物」，但看不出是什么品种。文案也从「敲敲它」换成「点一下让它出来」。
        val ready = e.hatchRemainMs() <= 0L
        firstEggReady = ready
        applyFirstEggReadyState(ready)
    }

    /**
     * 到点态的视觉切换（docs/14 §1.6）：蛋换「裂缝露眼」（2026-10-22 用户拍板**保留** ——
     * 要删的是出壳演出，不是它）+ 文案改成邀请点击。
     *
     * ⚠️ 必须被**两处**调用：状态刷新（[renderFirstEggFocus]）与本地倒计时（[startFirstEggTicker]）。
     * 只看状态刷新是不够的 —— 倒计时是 fragment 内的秒表，归零那一刻并**不会**产生新的
     * state emission，只靠状态刷新的话用户会盯着一个「00:00 + 完整蛋」的静止画面。
     */
    private fun applyFirstEggReadyState(ready: Boolean) {
        val f = binding.firstEggFocus
        f.ivFirstEgg.setImageResource(
            if (ready) R.drawable.pet_egg_ready else R.drawable.pet_egg_common
        )
        f.tvFirstEggHint.text = getString(
            if (ready) R.string.first_egg_hint_ready else R.string.first_egg_hint
        )
        f.tvFirstEggCrack.text = when {
            ready -> getString(R.string.first_egg_tap_hint_ready)
            viewModel.firstEggTaps > 0 ->
                getString(R.string.first_egg_crack_count, viewModel.firstEggTaps)
            else ->
                // 敲蛋是**可选互动**，不是开孵条件（docs/14 §1.5）：
                // 这句必须明确是"想它了可以敲"，而不是"你需要敲才能开始"
                getString(R.string.first_egg_tap_hint)
        }
    }

    /** 倒计时：每秒重算剩余（离开再回来按真实时间推进，docs/14 §1.2③），不暂停不重置 */
    private fun startFirstEggTicker(egg: Pet) {
        if (firstEggTickerJob?.isActive == true) return
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        firstEggTickerJob = viewLifecycleOwner.lifecycleScope.launch {
            val f = binding.firstEggFocus
            while (isActive && f.root.visibility == View.VISIBLE) {
                val sec = ((egg.hatchRemainMs() + 999) / 1000).toInt().coerceAtLeast(0)
                f.tvFirstEggCountdown.text =
                    String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60)
                // 末 10 秒转暖橙（docs/14 §1.4）
                f.tvFirstEggCountdown.setTextColor(
                    if (sec <= 10) Color.parseColor("#FFB300") else Color.parseColor("#FFFDE7")
                )
                // 数字每秒跳一下（120ms 缩放脉冲）
                f.tvFirstEggCountdown.animate().scaleX(1.06f).scaleY(1.06f).setDuration(60L)
                    .withEndAction {
                        f.tvFirstEggCountdown.animate().scaleX(1f).scaleY(1f).setDuration(60L).start()
                    }.start()
                if (f.tvFirstEggHatchAt.tag != egg.hatchAt) {
                    f.tvFirstEggHatchAt.tag = egg.hatchAt
                    f.tvFirstEggHatchAt.text =
                        getString(R.string.first_egg_hatch_at, fmt.format(Date(egg.hatchAt)))
                }
                // 归零那一刻切「裂缝露眼」态 —— 不依赖任何状态刷新（见 applyFirstEggReadyState）
                val ready = sec <= 0
                if (ready != firstEggReady) {
                    firstEggReady = ready
                    applyFirstEggReadyState(ready)
                }
                delay(1_000L)
            }
        }
    }

    /** 敲蛋：晃 + 细纹计数 + 递进胎动（5/15/30 次，docs/14 §1.2①）；到点则直接破壳 */
    private fun onFirstEggTapped() {
        val egg = viewModel.uiState.value.incubatingEgg ?: return
        if (egg.hatchRemainMs() <= 0L) {
            viewModel.onEggClicked()
            return
        }
        val taps = viewModel.onFirstEggTapped()
        val f = binding.firstEggFocus
        PetViewAnims.eggWobble(f.ivFirstEgg).start()
        f.tvFirstEggCrack.text = getString(R.string.first_egg_crack_count, taps)
        val talk = when (taps) {
            5 -> R.string.first_egg_talk_5
            15 -> R.string.first_egg_talk_15
            30 -> R.string.first_egg_talk_30
            else -> null
        }
        if (talk != null) f.tvFirstEggHint.text = getString(talk)
    }

    private fun startEggWobbleLoop() {
        viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                delay(4_000L)
                val s = viewModel.uiState.value
                if (s.incubatingEgg != null && !s.hasAnyPet && firstEggFocusShown) {
                    // 首蛋期：晃聚焦层的大蛋（docs/14 §1.4 每 4s 轻晃）
                    PetViewAnims.eggWobble(binding.firstEggFocus.ivFirstEgg).start()
                } else if (s.incubatingEgg != null &&
                    binding.sceneEgg.root.visibility == View.VISIBLE
                ) {
                    // 聚焦层没显示时蛋在角落 —— 别去晃一个 GONE 的 View
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
        firstEggTickerJob?.cancel()
        firstEggTickerJob = null
        // 聚焦层的显隐状态是 view 级的：view 重建后 binding 是新对象，
        // 布尔量若留着 true，重建的聚焦层（布局默认 GONE）会被判定为"已经显示过"而永远不出现。
        firstEggFocusShown = false
        firstEggReady = false
        firstEggBreathAnim?.cancel()
        firstEggBreathAnim = null
        petSlots.values.forEach { it.lifecycleController.stop(); it.renderer.release() }
        petSlots.clear()
        super.onDestroyView()
    }

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        /** 破壳动画时长（docs/04 §1.4：3 秒 + 震动） */

        fun newInstance(): SceneFragment = SceneFragment()
    }
}
