package com.sproutpet.pet.ui.scene

import androidx.lifecycle.viewModelScope
import com.sproutpet.core.base.BaseViewModel
import com.sproutpet.core.base.UiEvent
import com.sproutpet.core.base.UiState
import com.sproutpet.core.domain.daynight.DayNight
import com.sproutpet.core.domain.gacha.GachaRules
import com.sproutpet.core.domain.item.ItemRegistry
import com.sproutpet.core.domain.pet.CodexProgress
import com.sproutpet.core.domain.gacha.TenPullEntry
import com.sproutpet.core.domain.incubation.BlindBoxEngine
import com.sproutpet.core.domain.pet.GrowthRules
import com.sproutpet.core.domain.pet.Pet
import com.sproutpet.pet.data.local.OwnerLocalDataSource
import com.sproutpet.pet.data.repository.BlindBoxRepository
import com.sproutpet.pet.data.repository.IncubationRepository
import com.sproutpet.pet.data.repository.ItemRepository
import com.sproutpet.pet.data.repository.OpenBoxOutcome
import com.sproutpet.pet.data.repository.TenPullOutcome
import com.sproutpet.pet.data.repository.PetRepository
import com.sproutpet.pet.data.repository.PetRoster
import com.sproutpet.pet.data.repository.UserRepository
import com.sproutpet.pet.domain.growth.FocusBoostUseCase
import com.sproutpet.pet.domain.growth.GainExpUseCase
import com.sproutpet.pet.domain.interaction.FeedPetUseCase
import com.sproutpet.pet.domain.interaction.InteractionResult
import com.sproutpet.pet.domain.interaction.PetPetUseCase
import com.sproutpet.pet.domain.interaction.PlayPetUseCase
import com.sproutpet.pet.domain.interaction.TrainPetUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import timber.log.Timber
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
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
    /**
     * 是否已有**已破壳**的宠物（蛋不算）。
     *
     * ⚠️ 口径必须与 `PetDao.observeHatchedCount`（`is_hatched = 1`）一致，
     * 不能写成「有没有 pet 记录」—— 蛋也是 pet 表的一行，那样首蛋一落库
     * 就变成 hasAnyPet = true，首蛋聚焦层永远不会显示（2026-10-20 真机问题①）。
     */
    val hasAnyPet: Boolean = false,
    /** 距史诗保底还差几抽（`BlindBoxEngine.remainingToPity`） */
    val pityRemaining: Int = BlindBoxEngine.PITY_THRESHOLD,
    /** 相伴天数（自第一只宠物出生起算，无宠物时为 1） */
    val companionDays: Int = 1,
    /** 搭话气泡文案（null = 不展示） */
    val bubbleText: String? = null,
    /** 手上的盲盒门票数量（`GachaRules.BOX_ITEM_ID` 的可用总量） */
    val ticketCount: Int = 0,
    /** 托盘 · 食物余量（`food_basic`，design-v2 屏③） */
    val foodCount: Int = 0,
    /** 托盘 · 玩具余量（毛线球 + 飞盘按可用耐久计） */
    val toyCount: Int = 0,
    /** 互动托盘是否收起（真机反馈 2026-10-20：底部三卡挡场景 → 折成右下小药丸） */
    val trayCollapsed: Boolean = false,
    /**
     * 院子里的只数（已破壳但 `home_slot = -1`）。
     *
     * v3.28（2026-10-22 用户拍板）后这是**常态**而不是兜底：破壳默认只入册一只
     * （家里没有宠物时才自动站出来），十连的其余 9 只都在院子里待命，
     * 想看谁就到「家庭成员」名册里放出谁。这个角标是待命宠的**唯一常驻入口**。
     */
    val yardCount: Int = 0,
    /**
     * 已破壳总数（在家 + 院子）。
     *
     * ⚠️ 不是 `pets.size` —— 那是**场景里站着的**（≤6）。抽屉里写「在册 6 只」
     * 而实际有 11 只，正是 2026-10-20 崩溃那次暴露的同一类口径混淆。
     */
    val petTotal: Int = 0,
    /** 图鉴进度（种 / 类群 / 神兽位）—— 抽屉入口与图鉴弹层同源 */
    val codex: CodexProgress = CodexProgress.EMPTY
) : UiState

/**
 * 场景一次性事件（模态卡 / 底部抽层 / 飘字）。
 */
sealed interface SceneEvent : UiEvent {
    /** 开箱揭晓模态卡（design-v2 屏④） */
    data class ShowReveal(val outcome: OpenBoxOutcome) : SceneEvent
    /** 开箱台底部升起（design-v2 屏 14）—— 十连入口（徽章 / 长按 / 够 10 张时点蛋） */
    data class ShowOpenBoxSheet(val pityRemaining: Int) : SceneEvent
    /** 十连结果 · 出壳队列（design-v2 屏 15 / 屏 31） */
    data class ShowTenPull(val outcome: TenPullOutcome) : SceneEvent
    /** 孵化底部抽层（design-v2 屏⑤） */
    data class ShowIncubationSheet(val egg: Pet) : SceneEvent
    /** 专注陪伴番茄钟页（docs/04 §2.5） */
    data class ShowFocus(val egg: Pet) : SceneEvent
    /** 破壳动画（3 秒 + 震动，design-v2 屏⑥前置） */
    data class ShowHatching(val pet: Pet) : SceneEvent
    /** 强制起名弹窗（design-v2 屏⑥）。`rewardTickets` > 0 时弹窗内同步亮出十连奖励
     *  （docs/14 §1.6 · 2026-10-22：点击破壳 = 起名 + 奖励一次看到，不再分两步感知） */
    data class ShowNaming(val pet: Pet, val rewardTickets: Int = 0) : SceneEvent
    /** 互动飘字（+N 经验） */
    data class FloatyExp(val petId: Long, val text: String) : SceneEvent
    /** 宠物台词（托盘互动后的就地回应，design-v2 屏③「开饭啦！」） */
    data class PetTalk(val petId: Long, val text: String) : SceneEvent
    /** 打开「院子」（家里放不下的那些） */
    data object ShowYard : SceneEvent
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
    private val itemRepository: ItemRepository,
    private val petPetUseCase: PetPetUseCase,
    private val feedPetUseCase: FeedPetUseCase,
    private val playPetUseCase: PlayPetUseCase,
    private val trainPetUseCase: TrainPetUseCase,
    private val focusBoostUseCase: FocusBoostUseCase
) : BaseViewModel<SceneUiState, SceneEvent>() {

    override fun createInitialState(): SceneUiState = SceneUiState()

    /** 托盘收起/展开（落 DataStore，下次启动保持用户的选择） */
    fun setTrayCollapsed(collapsed: Boolean) {
        viewModelScope.launch { local.setTrayCollapsed(collapsed) }
    }

    init {
        viewModelScope.launch {
            val ownerId = local.getOrCreateOwnerId()
            // 确保 user 行存在（保底计数的真相源，Phase 2 从 DataStore 迁至 user 表）
            userRepository.getOrCreate(ownerId)
            setState { copy(ownerId = ownerId, isNight = computeIsNight()) }
            grantTicketsIfNeeded(ownerId)
            grantTrialBonusIfNeeded(ownerId)
            placeStarterEggIfNeeded(ownerId)
            // 首帧是否为「冷启动那一次」—— 冷启动补发奖励时没有起名流程可等，必须当场给卡
            var firstEmission = true
            combine(
                // 名册（在家 ≤6 / 院子）由仓库一次算好 —— 两条独立的流会在中间帧里
                // 出现「同一只同时在两边」，场景会把它画两遍
                petRepository.observeRoster(ownerId),
                incubationRepository.observeIncubatingEgg(ownerId),
                petRepository.observeHatchedPetCount(ownerId),
                blindBoxRepository.observePityRemaining(),
                itemRepository.observeInventory(ownerId)
            ) { roster, egg, count, pity, inventory ->
                val food = itemRepository.availableCount(ownerId, TRAY_FOOD_ID)
                val toys = TOY_IDS.sumOf { itemRepository.availableCount(ownerId, it) }
                Row(
                    roster, egg, count, pity,
                    // ⚠️ 门票 = **张数**不是记录数：blind_box 可堆叠（U-13 ③），十张票在一条记录里。
                    // 曾写成 inventory.count { itemId == BOX } → 首孵奖励 10 张到账后徽章仍显示
                    // 「差 9」、十连也判不够（2026-10-22 真机反馈）。堆叠道具一律 sumOf(count)。
                    inventory.filter { it.itemId == GachaRules.BOX_ITEM_ID }.sumOf { it.count },
                    food, toys
                )
            }.collect { (roster, egg, count, pity, tickets, food, toys) ->
                val pets = roster.home
                setState {
                    copy(
                        loading = false,
                        pets = pets,
                        incubatingEgg = egg,
                        // ⭐ 已破壳口径（修复 2026-10-20 真机问题①：原 observePetCount 含蛋，
                        // 抽到首蛋后 hasAnyPet=true → 首蛋聚焦层永不显示）
                        hasAnyPet = count > 0,
                        pityRemaining = pity,
                        companionDays = companionDaysOf(pets, egg),
                        bubbleText = bubbleText ?: greetIfFirstLoad(pets),
                        ticketCount = tickets,
                        foodCount = food,
                        toyCount = toys,
                        yardCount = roster.yardCount,
                        petTotal = roster.hatchedTotal,
                        codex = roster.codex
                    )
                }
                // 首蛋兜底：库里既没有蛋也没有宠物 —— 正常路径下首帧就该有蛋，
                // 走到这里说明落库曾失败。不兜底的话用户看到的是「一屏背景，什么都没有」。
                if (egg == null && count == 0 && !starterEggSettled &&
                    starterEggRetries < STARTER_EGG_MAX_RETRY
                ) {
                    starterEggRetries++
                    Timber.w("首蛋缺失，第 ${starterEggRetries} 次兜底重落")
                    viewModelScope.launch { placeStarterEggIfNeeded(ownerId) }
                }
                // 首只破壳即发奖（任何破壳路径都会推高 count：点蛋破壳 / 十连「全部出壳」）
                if (count > 0 && !firstHatchRewardChecked) {
                    firstHatchRewardChecked = true
                    // 奖励**静默入账**（2026-10-22 用户拍板：不再弹「第一只宝宝出生啦」卡）。
                    // 只在「本会话里亲眼看着破壳」时（非首帧）给起名弹窗配奖励条；
                    // 冷启动补发没有起名流程可等，纯静默。
                    val atLaunch = firstEmission
                    viewModelScope.launch { grantFirstHatchReward(ownerId, celebrate = !atLaunch) }
                }
                firstEmission = false
            }
        }
        viewModelScope.launch {
            local.observeTrayCollapsed().collect { c -> setState { copy(trayCollapsed = c) } }
        }
    }

    private data class Row<A, B, C, D, E, F, G>(
        val a: A, val b: B, val c: C, val d: D, val e: E, val f: F, val g: G
    )

    /** 回到前台时刷新昼夜（跨黄昏/清晨边界） */
    fun refreshDayNight() {
        setState { copy(isNight = computeIsNight()) }
        // 首蛋兜底：上次落库失败（首次启动 IO 抖动 / 票务异常）时，回前台再试一次。
        // 首日没有蛋 = 没有宠物 = 用户没有任何可做的事，代价远高于多查一次库。
        if (!starterEggSettled && starterEggRetries < STARTER_EGG_MAX_RETRY) {
            starterEggRetries++
            viewModelScope.launch {
                placeStarterEggIfNeeded(local.getOrCreateOwnerId())
            }
        }
    }

    private fun computeIsNight(): Boolean = DayNight.isNightNow()

    /**
     * 首蛋敲蛋计数（docs/14 §1.2① · 内存态）。
     *
     * ⚠️ 细纹/胎动的**持久化**（破壳后蛋壳仍留在场景里）需要 DB 字段，待 V7 迁移补；
     * 本轮先落「敲 → 有回应 + 计数 + 递进胎动」的会话内体验（docs/14 A-1 / A-7）。
     */
    var firstEggTaps: Int = 0
        private set

    /** 首只破壳奖励是否已检查过（本会话只查一次，否则每条数据刷新都要读一次 DataStore） */
    private var firstHatchRewardChecked = false

    /** 首蛋是否已就位（库里已有蛋/宠，或本次已成功落库）—— 兜底重试的闸门 */
    private var starterEggSettled = false

    /** 首蛋兜底重试次数（异常情况下也不能无限重试） */
    private var starterEggRetries = 0

    /** 奖励已到账、等破壳动画播完随起名弹窗亮奖励条（2026-10-22 起不再有独立庆祝卡） */
    private var rewardPendingCelebration = 0

    /** 聚焦层「敲蛋」：计一次，返回累计次数供 UI 刷新细纹文案 */
    fun onFirstEggTapped(): Int {
        firstEggTaps++
        return firstEggTaps
    }

    /**
     * 角落蛋被点击（三态分发）：
     * - 无蛋 → 够 10 张门票时进开箱台，否则直接抽 1 个
     * - 孵化中 → 孵化抽层
     * - 到点 → 破壳动画 → 强制起名
     */
    fun onEggClicked() {
        val egg = currentState.incubatingEgg
        if (egg == null) {
            // 手上够十连时不要"点一下就直接开 1 个"—— 一次误触就把十连拆成 1 + 9
            if (TenPullEntry.routesToSheet(currentState.ticketCount)) {
                onOpenBoxRequested()
            } else {
                openBlindBox()
            }
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

    /** 开一次盲盒（引擎执法，仓库落库；首只自动 `isFirstPet = true`）—— 消耗 1 张门票 */
    private fun openBlindBox() {
        viewModelScope.launch {
            val ownerId = currentState.ownerId
            if (!consumeTickets(ownerId, 1)) {
                sendEvent(SceneEvent.ToastMsg("盲盒不够啦，去商店补票～"))
                return@launch
            }
            val outcome = blindBoxRepository.openBox(ownerId)
            sendEvent(SceneEvent.ShowReveal(outcome))
        }
    }

    // ═══════════ 抽蛋 · 开箱台与十连（design-v2 屏 13/14/15/31） ═══════════

    /** 院子入口（场景左上的「院子里 N 只」角标） */
    fun onYardRequested() {
        sendEvent(SceneEvent.ShowYard)
    }

    /** 开箱台入口（蛋旁的「十连」徽章 / 长按蛋 / 够 10 张时点蛋） */
    fun onOpenBoxRequested() {
        sendEvent(SceneEvent.ShowOpenBoxSheet(currentState.pityRemaining))
    }

    /**
     * 开箱台的「开 1 个」：**不看角落蛋的状态**，直接抽一次。
     *
     * 与 [onEggClicked] 的区别：后者是"场景里的那个蛋"的三态入口
     * （可开箱 / 孵化中 / 到点破壳），在已有蛋孵化时点它只会打开孵化抽层。
     * 开箱台是"再买一个"的语义，必须能独立开箱。
     */
    fun onOpenSingleBox() {
        openBlindBox()
    }

    /**
     * 十连：**先扣 10 张门票，再抽**（扣不动就一只都不抽 —— 不允许"抽完发现没票"）。
     *
     * 时序全部由仓库按 `GachaRules.queueOffsetsMs` 写进 `hatchAt`，
     * 这里只负责把结果交给结果页。
     */
    fun onTenPull() {
        viewModelScope.launch {
            val ownerId = currentState.ownerId
            if (!consumeTickets(ownerId, GachaRules.TEN_PULL)) {
                val lack = GachaRules.TEN_PULL - currentState.ticketCount
                sendEvent(SceneEvent.ToastMsg("还差 $lack 张盲盒，去商店补票吧～"))
                return@launch
            }
            val outcome = blindBoxRepository.openMany(ownerId)
            sendEvent(SceneEvent.ShowTenPull(outcome))
        }
    }

    /** 「全部出壳」：零成本把整批蛋拉到现在（docs/14 §2.2） */
    fun onRevealAll(eggs: List<Pet>) {
        viewModelScope.launch {
            var count = 0
            eggs.forEach { egg ->
                if (incubationRepository.forceHatch(egg.id) != null) count++
            }
            sendEvent(SceneEvent.ToastMsg(if (count > 0) "$count 只全出来啦！" else "它们已经出来了"))
        }
    }

    /**
     * 首次进入：**自动把第一颗蛋放进场景**（`docs/14 §1.5` · 2026-10-22 用户拍板）。
     *
     * 用户原话：「第一个蛋还需要用户手点吗？直接告诉用户正在孵化您的第一颗蛋，不就行嘛」
     *
     * 为什么这是对的口径：首日的第一颗蛋不是"抽奖"，是**开场**。用户此刻还不知道
     * 角落那个小蛋可以点，也没有"盲盒 / 门票 / 十连"的任何认知 —— 让他先点一下才能开始，
     * 等于把开场变成一个需要猜的操作（`docs/05` 坑表：只靠手势的入口 = 没有入口）。
     *
     * 三条边界：
     * 1. **只在完全没有宠物记录时触发**（蛋本身也是 pet 记录，所以有蛋就不会重复送）；
     * 2. **落库成功后才记账**（[OwnerLocalDataSource.markStarterEggPlaced]）——
     *    先记账再抽的话，抽的过程一断，用户票没了、蛋也没有；顺序反过来最坏只是重试一次；
     * 3. **不发 `ShowReveal`**：揭晓卡是抽奖语言，开场不需要 —— 蛋直接进入孵化，
     *    聚焦层第一帧就告诉用户「正在孵化你的第一颗蛋」。
     */
    private suspend fun placeStarterEggIfNeeded(ownerId: Long) {
        if (starterEggSettled) return
        if (local.isStarterEggPlaced()) {
            starterEggSettled = true
            return
        }
        if (petRepository.observePetCount(ownerId).first() > 0) {
            // 老用户 / 上次已经落过：补记账，避免每次启动都白查一遍
            local.markStarterEggPlaced()
            starterEggSettled = true
            return
        }
        // 首蛋是**开场赠礼**，不是一次"消费"（docs/14 §1.5）。
        // 有票就扣一张（保持 00 §2.1「首次免费 1 抽」的经济口径），
        // 扣不到也照送 —— 那张票本来就是 `grantTicketsIfNeeded` 刚发的，
        // 一旦票务任何一环抖动就不给蛋，用户首日面对的是一屏空背景。
        if (!consumeTickets(ownerId, 1)) {
            Timber.w("首蛋：扣票未成功，改为直接赠送（首日绝不允许空场景）")
        }
        val placed = runCatching { blindBoxRepository.openBox(ownerId) }
            .onFailure { Timber.e(it, "首蛋自动落库失败 —— 回前台会重试，用户也可点角落蛋自行开箱") }
            .getOrNull() ?: return
        // 顺序：先落库、再记账。反了会出现「记了账、蛋没落」→ 首日永久空场景。
        local.markStarterEggPlaced()
        starterEggSettled = true
        Timber.i("首蛋已自动落入场景：${placed.egg.species.displayName} · ${placed.egg.hatchAt} 破壳")
    }

    /** 门票够不够，不够就**整笔不扣** */
    private suspend fun consumeTickets(ownerId: Long, count: Int): Boolean {
        val have = itemRepository.availableCount(ownerId, GachaRules.BOX_ITEM_ID)
        if (have < count) return false
        repeat(count) { itemRepository.consume(ownerId, GachaRules.BOX_ITEM_ID) }
        return true
    }

    /**
     * 门票补发（进入场景时执行一次）：
     * 1. **新用户首次 1 个免费盲盒**（00 §2.1）
     * 2. **每日免费 2 抽**（签到 1 + 任务全清 1 → 两者的领取界面尚未做，规则先落地）
     */
    private suspend fun grantTicketsIfNeeded(ownerId: Long) {
        val pets = petRepository.observePetCount(ownerId).first()
        val tickets = itemRepository.availableCount(ownerId, GachaRules.BOX_ITEM_ID)
        if (pets == 0 && tickets == 0) {
            itemRepository.acquire(ownerId, GachaRules.BOX_ITEM_ID, 1)
        }
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (local.claimDailyFreeTickets(today)) {
            itemRepository.acquire(ownerId, GachaRules.BOX_ITEM_ID, DAILY_FREE_TICKETS)
        }
    }

    /**
     * **试用阶段资源包**（2026-10-22 用户拍板，仅测试期 · `docs/09 T-01`）：
     * 盲盒门票 ×100 · 金币 ×100,000 · 其余每种道具 ×1000，一次性静默到账。
     *
     * ⚠️⚠️ **上线前必须移除本函数与调用点**（DataStore 键 `trial_bonus_granted` 一并回收）——
     * 这是给试用灌水的临时口径，不许悄悄转正。
     */
    private suspend fun grantTrialBonusIfNeeded(ownerId: Long) {
        if (!local.claimTrialBonus()) return
        itemRepository.acquire(ownerId, GachaRules.BOX_ITEM_ID, TRIAL_TICKETS)
        userRepository.addGold(TRIAL_GOLD)
        ItemRegistry.all
            .filter { it.id != GachaRules.BOX_ITEM_ID }
            .forEach { def ->
                if (def.stackable) {
                    itemRepository.acquire(ownerId, def.id, TRIAL_ITEM_COUNT)
                } else {
                    // 玩具按份发（独立耐久，不可堆叠）—— 一份一条记录
                    repeat(TRIAL_ITEM_COUNT) { itemRepository.acquire(ownerId, def.id) }
                }
            }
        Timber.i("试用资源包已发放：门票×%d 金币×%d 其余道具各×%d", TRIAL_TICKETS, TRIAL_GOLD, TRIAL_ITEM_COUNT)
    }

    /** 破壳动画播完 → 弹出强制起名（若首孵奖励已记账，弹窗内同步亮出 +10 连抽） */
    fun onHatchingAnimDone(pet: Pet) {
        val reward = rewardPendingCelebration
        rewardPendingCelebration = 0
        sendEvent(SceneEvent.ShowNaming(pet, rewardTickets = reward))
    }

    /**
     * 首只孵化奖励（`docs/00 §2.1` · 2026-10-20 用户拍板）：**第一只破壳后一次性送 10 抽**。
     *
     * 为什么发在"破壳"而不是"抽到"：奖励的是**养成的第一个成果**，
     * 而且这个时刻用户刚看完破壳 + 起名，情绪最高，最愿意立刻再抽一次。
     *
     * 幂等由 [OwnerLocalDataSource.claimFirstHatchReward] 保证（DataStore 事务内判重），
     * 所以点蛋破壳 / 十连「全部出壳」/ 冷启动补发三条路径都调它也不会重复发。
     */
    private suspend fun grantFirstHatchReward(ownerId: Long, celebrate: Boolean = false): Boolean {
        if (!local.claimFirstHatchReward()) return false
        itemRepository.acquire(ownerId, GachaRules.BOX_ITEM_ID, FIRST_HATCH_REWARD_TICKETS)
        // 奖励条随起名弹窗同屏（2026-10-22 起不再有独立庆祝卡 —— 用户拍板去掉）
        if (celebrate) rewardPendingCelebration = FIRST_HATCH_REWARD_TICKETS
        return true
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
            handleInteraction(petId, petPetUseCase.execute(petId, currentState.ownerId))
        }
    }

    /**
     * 托盘互动（design-v2 屏③ · `K-2`）：道具拖到宠物身上松手。
     *
     * 规则（冷却/日限/道具消耗/经验上限）全部在 `InteractionExecutor` 执法 ——
     * 这是 `FeedPet` / `PlayPet` / `TrainPet` 三个 UseCase 的**第一个调用方**。
     */
    fun onTrayInteraction(kind: String, petId: Long) {
        viewModelScope.launch {
            val ownerId = currentState.ownerId
            val result = when (kind) {
                TRAY_KIND_FEED -> feedPetUseCase.execute(petId, ownerId)
                TRAY_KIND_PLAY -> playPetUseCase.execute(petId, ownerId)
                TRAY_KIND_TRAIN -> trainPetUseCase.execute(petId, ownerId)
                else -> return@launch
            }
            handleInteraction(petId, result, kind)
        }
    }

    /** 互动结果统一出口：飘字（经验+亲密）→ 台词 → 升级 → 冷却/日限/缺道具提示 */
    private suspend fun handleInteraction(petId: Long, result: InteractionResult, kind: String? = null) {
        when (result) {
            is InteractionResult.Success -> {
                sendEvent(SceneEvent.FloatyExp(
                    petId, "+${result.expGained} 经验 · 亲密 +${result.intimacyGained}"
                ))
                if (kind != null) {
                    sendEvent(SceneEvent.PetTalk(petId, talkOf(kind)))
                }
                if (result.leveledUp && result.newLevel != null) {
                    sendEvent(SceneEvent.LevelUp(petId, result.newLevel))
                }
            }
            is InteractionResult.OnCooldown ->
                // 冷却期**静默**（docs/14 §1.6 · 2026-10-22 用户拍板）：
                // 不再弹「刚互动过啦，等 N 秒再来」。抚摸路径在 UI 层本来就有
                // 宠物动作 + 爱心反馈（SceneFragment.onPet），点它永远有回应，
                // 只是这一下不计经验 —— 冷却仍是经济护栏，但不再是打断用户的弹窗。
                Unit
            InteractionResult.DailyLimitReached -> {
                val type = kindToType(kind)
                sendEvent(SceneEvent.ToastMsg("今天${type.displayName}够 ${type.dailyLimit} 次啦，明天再来吧"))
            }
            InteractionResult.ItemNotEnough ->
                sendEvent(SceneEvent.ToastMsg("没有道具啦，去商店补一点吧～"))
            InteractionResult.PetNotFound -> Unit
        }
    }

    private fun talkOf(kind: String): String = when (kind) {
        TRAY_KIND_FEED -> talkFeed
        TRAY_KIND_PLAY -> talkPlay
        else -> talkTrain
    }

    private fun kindToType(kind: String?): GrowthRules.InteractionType = when (kind) {
        TRAY_KIND_FEED -> GrowthRules.InteractionType.FEED
        TRAY_KIND_PLAY -> GrowthRules.InteractionType.PLAY
        TRAY_KIND_TRAIN -> GrowthRules.InteractionType.TRAIN
        else -> GrowthRules.InteractionType.PET
    }

    /** 点击孵化抽层「专注陪伴」→ 打开番茄钟页 */
    fun onStartFocus(egg: Pet) {
        sendEvent(SceneEvent.ShowFocus(egg))
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
        /** 陪伴经验：满 15 分钟触发 */
        private const val COMPANION_EXP_FOCUS_MS = 15 * 60 * 1000L
        /** 一次长陪伴给主宠的经验（与一次训练量级相当，不触顶每日上限） */
        private const val COMPANION_EXP_AMOUNT = 150

        /** 每日免费门票数（签到 1 + 任务全清 1，docs/00 §2.1） */
        private const val DAILY_FREE_TICKETS = 2

        /**
         * 首蛋落库失败时最多重试几次。
         *
         * 首蛋是首日**唯一**的内容入口：没有蛋就没有宠物、没有互动、没有可看的倒计时。
         * 但重试也不能无限 —— 真失败（例如磁盘满）时要停在「角落蛋 + 点我开始孵化」这个
         * 用户可自救的状态，而不是在后台空转。
         */
        private const val STARTER_EGG_MAX_RETRY = 2

        /**
         * 首只孵化成功后的一次性奖励（`docs/00 §2.1` · 2026-10-20 拍板）。
         *
         * 定成「刚好一次十连」而不是"随便给几张"：十连是本作抽蛋的**唯一连抽形态**，
         * 送 10 抽 = 送**一次完整的十连体验**，用户不需要先攒 5 天。
         */
        const val FIRST_HATCH_REWARD_TICKETS = GachaRules.TEN_PULL

        // ═══ 试用阶段资源包（2026-10-22 用户拍板 · 仅测试期 · docs/09 T-01 上线前回收）═══
        const val TRIAL_TICKETS = 100
        const val TRIAL_GOLD = 100_000
        const val TRIAL_ITEM_COUNT = 1000

        /** 托盘 kind（跨 View.startDragAndDrop 的 localState 传递） */
        const val TRAY_KIND_FEED = "feed"
        const val TRAY_KIND_PLAY = "play"
        const val TRAY_KIND_TRAIN = "train"

        /** 托盘喂食卡对应的消耗物（与 InteractionExecutor.feedItemId 一致） */
        const val TRAY_FOOD_ID = "food_basic"
        /** 托盘玩耍卡覆盖的玩具（与 InteractionExecutor.playItemCandidates 一致） */
        private val TOY_IDS = listOf("toy_yarn", "toy_frisbee")

        /** 宠物台词（design-v2 屏③；暂为常量，气泡系统任务化时随 `00 §4.4` 挂钩） */
        private val talkFeed = "开饭啦！"
        private val talkPlay = "再玩再玩！"
        private val talkTrain = "感觉变强了！"

        private val GREETINGS = listOf(
            "今天也想被你摸一下～",
            "你在忙什么呀？",
            "陪我玩一会儿嘛"
        )
    }
}

/** 会话级标记：搭话气泡只主动弹一次（避免刷屏，docs/00 §4.4） */
private var bubbleShown = false
