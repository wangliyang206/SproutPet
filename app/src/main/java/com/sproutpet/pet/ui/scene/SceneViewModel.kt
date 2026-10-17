package com.sproutpet.pet.ui.scene

import androidx.lifecycle.viewModelScope
import com.sproutpet.core.base.BaseViewModel
import com.sproutpet.core.base.UiEvent
import com.sproutpet.core.base.UiState
import com.sproutpet.core.domain.incubation.BlindBoxEngine
import com.sproutpet.core.domain.pet.GrowthRules
import com.sproutpet.core.domain.pet.Pet
import com.sproutpet.pet.data.local.OwnerLocalDataSource
import com.sproutpet.pet.data.repository.BlindBoxRepository
import com.sproutpet.pet.data.repository.IncubationRepository
import com.sproutpet.pet.data.repository.OpenBoxOutcome
import com.sproutpet.pet.data.repository.PetRepository
import com.sproutpet.pet.data.repository.UserRepository
import com.sproutpet.pet.domain.growth.FocusBoostUseCase
import com.sproutpet.pet.domain.growth.GainExpUseCase
import com.sproutpet.pet.domain.interaction.InteractionResult
import com.sproutpet.pet.domain.interaction.PetPetUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * 全屏场景状态（倒置 IA 主界面，design-v2 屏①/屏⑩）。
 */
data class SceneUiState(
    /** 首屏加载中（读 ownerId / 首帧数据） */
    val loading: Boolean = true,
    /** 是否夜晚（随真实时间，决定昼夜背景与宠物困倦节奏） */
    val isNight: Boolean = false,
    /** 本地主人 ID */
    val ownerId: Long = 0L,
    /** 家在册的已破壳宠物（站位层数据源） */
    val pets: List<Pet> = emptyList(),
    /** 孵化中的蛋（角落蛋的状态源），null 表示角落是「可开箱」状态 */
    val incubatingEgg: Pet? = null,
    /** 是否已有任何宠物（含蛋）—— 新用户引导显隐判据 */
    val hasAnyPet: Boolean = false,
    /** 距史诗保底还差几抽（`BlindBoxEngine.remainingToPity`） */
    val pityRemaining: Int = BlindBoxEngine.PITY_THRESHOLD,
    /** 相伴天数（自第一只宠物出生起算，无宠物时为 1） */
    val companionDays: Int = 1,
    /** 搭话气泡文案（null = 不展示） */
    val bubbleText: String? = null
) : UiState

/**
 * 场景一次性事件（模态卡 / 底部抽层 / 飘字）。
 */
sealed interface SceneEvent : UiEvent {
    /** 开箱揭晓模态卡（design-v2 屏④） */
    data class ShowReveal(val outcome: OpenBoxOutcome) : SceneEvent
    /** 孵化底部抽层（design-v2 屏⑤） */
    data class ShowIncubationSheet(val egg: Pet) : SceneEvent
    /** 破壳动画（3 秒 + 震动，design-v2 屏⑥前置） */
    data class ShowHatching(val pet: Pet) : SceneEvent
    /** 强制起名弹窗（design-v2 屏⑥） */
    data class ShowNaming(val pet: Pet) : SceneEvent
    /** 互动飘字（+N 经验） */
    data class FloatyExp(val petId: Long, val text: String) : SceneEvent
    /** 升级欢呼 */
    data class LevelUp(val petId: Long, val level: Int) : SceneEvent
    /** 轻提示 */
    data class ToastMsg(val msg: String) : SceneEvent
}

/**
 * 场景 ViewModel：盲盒 → 孵化 → 破壳 → 互动的编排中枢。
 *
 * 规则全部在 core 引擎（BlindBoxEngine / IncubationEngine / GrowthRules）与
 * `ApplyInteractionUseCase`，这里只做编排（A1/A3 红线）。
 */
@HiltViewModel
class SceneViewModel @Inject constructor(
    private val local: OwnerLocalDataSource,
    private val userRepository: UserRepository,
    private val petRepository: PetRepository,
    private val blindBoxRepository: BlindBoxRepository,
    private val incubationRepository: IncubationRepository,
    private val petPetUseCase: PetPetUseCase,
    private val focusBoostUseCase: FocusBoostUseCase
) : BaseViewModel<SceneUiState, SceneEvent>() {

    override fun createInitialState(): SceneUiState = SceneUiState()

    init {
        viewModelScope.launch {
            val ownerId = local.getOrCreateOwnerId()
            // 确保 user 行存在（保底计数的真相源，Phase 2 从 DataStore 迁至 user 表）
            userRepository.getOrCreate(ownerId)
            setState { copy(ownerId = ownerId, isNight = computeIsNight()) }
            combine(
                petRepository.observeHomePets(ownerId),
                incubationRepository.observeIncubatingEgg(ownerId),
                petRepository.observePetCount(ownerId),
                blindBoxRepository.observePityRemaining()
            ) { pets, egg, count, pity ->
                Quad(pets, egg, count, pity)
            }.collect { (pets, egg, count, pity) ->
                setState {
                    copy(
                        loading = false,
                        pets = pets,
                        incubatingEgg = egg,
                        hasAnyPet = count > 0,
                        pityRemaining = pity,
                        companionDays = companionDaysOf(pets, egg),
                        bubbleText = bubbleText ?: greetIfFirstLoad(pets)
                    )
                }
            }
        }
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    /** 回到前台时刷新昼夜（跨黄昏/清晨边界） */
    fun refreshDayNight() {
        setState { copy(isNight = computeIsNight()) }
    }

    private fun computeIsNight(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour < NIGHT_END_HOUR || hour >= NIGHT_START_HOUR
    }

    /**
     * 角落蛋被点击（唯一入口，三态分发）：
     * - 无蛋 → 开箱（揭晓模态卡）
     * - 孵化中 → 孵化抽层
     * - 到点 → 破壳动画 → 强制起名
     */
    fun onEggClicked() {
        val egg = currentState.incubatingEgg
        if (egg == null) {
            openBlindBox()
            return
        }
        viewModelScope.launch {
            val hatched = incubationRepository.hatchIfReady(egg.id)
            if (hatched != null) {
                sendEvent(SceneEvent.ShowHatching(hatched))
            } else {
                sendEvent(SceneEvent.ShowIncubationSheet(egg))
            }
        }
    }

    /** 开一次盲盒（引擎执法，仓库落库；首只自动 `isFirstPet = true`） */
    private fun openBlindBox() {
        viewModelScope.launch {
            val outcome = blindBoxRepository.openBox(currentState.ownerId)
            sendEvent(SceneEvent.ShowReveal(outcome))
        }
    }

    /** 破壳动画播完 → 弹出强制起名 */
    fun onHatchingAnimDone(pet: Pet) {
        sendEvent(SceneEvent.ShowNaming(pet))
    }

    /** 强制命名确认（唯一改名入口） */
    fun onNamed(petId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            sendEvent(SceneEvent.ToastMsg("名字不能为空哦"))
            return
        }
        viewModelScope.launch {
            petRepository.rename(petId, trimmed)
        }
    }

    /** 抚摸（单击）：结算 + 飘字 + 可能升级（受冷却/日限/经验上限约束） */
    fun onPetPetted(petId: Long) {
        viewModelScope.launch {
            when (val result = petPetUseCase.execute(petId, currentState.ownerId)) {
                is InteractionResult.Success -> {
                    sendEvent(SceneEvent.FloatyExp(petId, "+${result.expGained} 经验"))
                    if (result.leveledUp && result.newLevel != null) {
                        sendEvent(SceneEvent.LevelUp(petId, result.newLevel))
                    }
                }
                is InteractionResult.OnCooldown -> {
                    val secs = (result.remainingMs / 1000).coerceAtLeast(1)
                    sendEvent(SceneEvent.ToastMsg("刚摸过啦，${secs}s 后再来～"))
                }
                InteractionResult.DailyLimitReached ->
                    sendEvent(SceneEvent.ToastMsg("今天摸够 50 次啦，明天再来吧"))
                InteractionResult.PetNotFound -> Unit
                InteractionResult.ItemNotEnough -> Unit
            }
        }
    }

    /** 专注陪伴完成：按实际陪伴时长抵扣孵化时间 */
    fun onFocusSessionFinished(eggId: Long, focusMs: Long) {
        viewModelScope.launch {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(focusMs).toInt()
            val updated = focusBoostUseCase.execute(eggId, focusMs)
            if (updated != null) {
                sendEvent(SceneEvent.ToastMsg("陪伴 $minutes 分钟，孵化加快了！"))
            }
            // 陪伴经验：满 15 分钟给主宠加经验（docs/04 §2.5）
            if (focusMs >= COMPANION_EXP_FOCUS_MS) {
                currentState.pets.firstOrNull()?.let { mainPet ->
                    val result = GainExpUseCase.execute(mainPet, COMPANION_EXP_AMOUNT)
                    petRepository.save(result.pet)
                    if (result.leveledUp && result.newLevel != null) {
                        sendEvent(SceneEvent.LevelUp(result.pet.id, result.newLevel))
                    }
                    sendEvent(SceneEvent.FloatyExp(result.pet.id, "+$COMPANION_EXP_AMOUNT"))
                }
            }
        }
    }

    /** 点击气泡的「摸摸它」：对主宠结算一次抚摸并收起气泡 */
    fun onBubbleAction() {
        val main = currentState.pets.firstOrNull() ?: return
        dismissBubble()
        onPetPetted(main.id)
    }

    fun dismissBubble() {
        setState { copy(bubbleText = null) }
    }

    /** 首次加载到宠物时给一句搭话（本会话只弹一次） */
    private fun greetIfFirstLoad(pets: List<Pet>): String? {
        if (bubbleShown || pets.isEmpty()) return null
        bubbleShown = true
        return GREETINGS.random()
    }

    private fun companionDaysOf(pets: List<Pet>, egg: Pet?): Int {
        val firstBorn = (pets.map { it.bornAt } + listOfNotNull(egg?.bornAt)).minOrNull()
            ?: return 1
        val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - firstBorn).toInt() + 1
        return days.coerceAtLeast(1)
    }

    companion object {
        /** 夜晚起止（18:00 ~ 次日 6:00 用夜景） */
        private const val NIGHT_START_HOUR = 18
        private const val NIGHT_END_HOUR = 6

        /** 陪伴经验：满 15 分钟触发 */
        private const val COMPANION_EXP_FOCUS_MS = 15 * 60 * 1000L
        /** 一次长陪伴给主宠的经验（与一次训练量级相当，不触顶每日上限） */
        private const val COMPANION_EXP_AMOUNT = 150

        private val GREETINGS = listOf(
            "今天也想被你摸一下～",
            "你在忙什么呀？",
            "陪我玩一会儿嘛"
        )
    }
}

/** 会话级标记：搭话气泡只主动弹一次（避免刷屏，docs/00 §4.4） */
private var bubbleShown = false
