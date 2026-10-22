package com.sproutpet.pet.data.repository

import com.sproutpet.core.db.entity.PetDao
import com.sproutpet.core.db.mapper.PetMapper
import com.sproutpet.core.domain.desktop.SceneRoster
import com.sproutpet.core.domain.pet.CodexProgress
import com.sproutpet.core.domain.pet.GrowthRules
import com.sproutpet.core.domain.pet.HomeSlots
import com.sproutpet.core.domain.pet.Pet
import com.sproutpet.pet.domain.interaction.ApplyInteractionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 场景名册快照：[home] = 该站在场景里的（≤6）· [yard] = 家里放不下的那批。
 *
 * 两者**互斥且并集 = 全部已破壳未隐居** —— 由 `core.SceneRoster` 保证（有单测）。
 */
data class PetRoster(
    val home: List<Pet> = emptyList(),
    val yard: List<Pet> = emptyList(),
    /**
     * 图鉴进度（种 / 类群 / 神兽位）。
     *
     * 塞进名册快照而不是再开一路 Flow：`combine` 只有 2~5 个重载（场景已用满 5 路），
     * 而且图鉴本来就是「从同一份宠物表里数出来的」，没有理由再查一次库。
     */
    val codex: CodexProgress = CodexProgress.EMPTY
) {
    val yardCount: Int get() = yard.size

    /** 已破壳总数（在家 + 院子）—— 抽屉「在册 N 只」用这个，不是场景里站着的只数 */
    val hatchedTotal: Int get() = home.size + yard.size
}

/**
 * 宠物仓库（docs/04 §1.5）：观察 + 保存 + 互动结算。
 *
 * 规则运算全部委托 core 引擎与 [ApplyInteractionUseCase]，本层只做存取与编排（A5 红线）。
 */
interface PetRepository {

    /** 家在册的已破壳宠物（场景站位层的数据源）—— **必须过滤 `atHome`**，见实现注释 */
    fun observeHomePets(ownerId: Long): Flow<List<Pet>>

    /**
     * 场景名册**一次算两半**（在家 / 院子）。
     *
     * 为什么要合成一个 Flow：两者必须**互斥且不重不漏**，分成两条独立的查询流去算，
     * 中间必然出现「一只同时在两边」或「两边都没有」的瞬时帧 —— 场景会把同一只画两遍。
     * 用同一份 `observeByOwner` 快照切两刀，天然没有这个问题。
     *
     * 顺带解决一个 API 限制：`kotlinx.coroutines.combine` 只有 2~5 个 Flow 的重载，
     * 场景已经有 5 路了，再加一路只能嵌套 —— 不如在这里合并成一个。
     */
    fun observeRoster(ownerId: Long): Flow<PetRoster>

    /**
     * 「院子」= 已破壳但不在家（`home_slot = -1`）的宠物。
     *
     * ⚠️ 与 [observeHomePets] 严格互斥（`atHome` 的真假两半），两边加起来 = 全部已破壳。
     */
    fun observeYardPets(ownerId: Long): Flow<List<Pet>>

    /**
     * 把院子里的一只接回家。
     *
     * @return 落到的槽位；家满（6 只）或宠物不在院时返回 `null` ——
     *         调用方必须**引导用户先收起一只**，不能静默失败
     */
    suspend fun bringHome(petId: Long): Int?

    /**
     * 把在家的某只收进院子（空出槽位给 [bringHome]）。
     *
     * @return 是否成功；宠物不在家 / 是主宠（主宠不可离家的不变量见 `HomeSlots.validate`）返回 false
     */
    suspend fun sendToYard(petId: Long): Boolean

    /** 该主人是否已有任何宠物（含蛋），用于新用户引导显隐 */
    fun observePetCount(ownerId: Long): Flow<Int>

    /** 已破壳的宠物数（⭐ 不含蛋）—— 「家里有没有宠物」的判断一律用它 */
    fun observeHatchedPetCount(ownerId: Long): Flow<Int>

    suspend fun queryById(id: Long): Pet?

    /**
     * 图鉴进度的**一次性快照**（模态弹层用：不做实时订阅，避免列表在手指下跳动）。
     *
     * 与 [observeRoster] 里的 `codex` 同一判据（[CodexProgress.of]），不会出现
     * 「抽屉说 8 种、点进去说 7 种」。
     */
    suspend fun codexProgress(ownerId: Long): CodexProgress

    /**
     * 全部在册宠物快照（含蛋、含待命；**不含后山** —— 与 [codexProgress] 同源同过滤，
     * 不会出现「格子亮了但 ×N 对不上」）。
     *
     * 图鉴用它在**物种格上直接亮出宠物昵称**（2026-10-22 真机反馈：「孵化出第一只了，
     * 图鉴中没有」—— 图鉴是物种口径，一格一个物种、只有"已收集"三个字，
     * 用户在格子里找不到"我的那只"。把昵称亮出来，"收集到的是它"就不再需要猜）。
     */
    suspend fun petsByOwner(ownerId: Long): List<Pet>

    /** 族谱（design-v2 屏 20）：某只宠物的直系后代 */
    suspend fun childrenOf(petId: Long): List<Pet>

    /** 新增或更新（存在即更新） */
    suspend fun save(pet: Pet): Long

    /** 破壳起名（强制命名是唯一改名入口） */
    suspend fun rename(petId: Long, name: String)

    /**
     * 结算一次互动并持久化。
     *
     * @return 结算结果；宠物不存在时返回 null（调用方按无操作处理）
     */
    suspend fun applyInteraction(petId: Long, type: GrowthRules.InteractionType): ApplyInteractionUseCase.Result?
}

@Singleton
class PetRepositoryImpl @Inject constructor(
    private val petDao: PetDao
) : PetRepository {

    /**
     * ⚠️ **必须同时过滤 `isHatched` 与 `isAtHome`**（2026-10-20 崩溃修复）。
     *
     * 这个方法此前只过滤了「已破壳」，于是**全部已破壳宠物**都被喂给
     * `ScenePetLayout.homeLayout(count)` —— 而它 `require(count in 0..6)`。
     * 首只孵化奖励送 10 抽 → 十连开出 10 只 + 原来的 1 只 = 11 只 ⇒
     * `IllegalArgumentException: 家的在册上限是 6，收到 11`，主线程崩。
     *
     * 三层链条（`HomeSlots` 注释）：**拥有不限 ⊃ 家在册 6 ⊃ 桌面 3**。
     * 家的「在册」判定唯一位是 `home_slot != -1`，不是「有这只宠物」。
     *
     * 排序用 `homeSlot`（不是战力）：站位必须稳定 —— 重启后大家还站在原来的位置，
     * 这是陪伴感的一部分（`ScenePetLayout` 的设计约束 2）。
     */
    override fun observeHomePets(ownerId: Long): Flow<List<Pet>> =
        petDao.observeByOwner(ownerId).map { entities ->
            // 判据统一在 core 的 SceneRoster（filter atHome + 按 homeSlot 排序 + 硬夹 6 只），
            // 这里不再自己写一套 filter —— 同一件事两处维护，早晚会分叉
            SceneRoster.onStage(PetMapper.toDomainList(entities))
        }

    override fun observeRoster(ownerId: Long): Flow<PetRoster> =
        petDao.observeByOwner(ownerId).map { entities ->
            val all = PetMapper.toDomainList(entities)
            PetRoster(
                home = SceneRoster.onStage(all),
                yard = SceneRoster.yard(all),
                // 同一份快照里数出来 —— 三个数字必须同源，否则会出现
                // 「场景 6 只 / 抽屉 7 只」这类中间帧矛盾
                codex = CodexProgress.of(all)
            )
        }

    override fun observeYardPets(ownerId: Long): Flow<List<Pet>> =
        petDao.observeYardPets(ownerId).map(PetMapper::toDomainList)

    override suspend fun bringHome(petId: Long): Int? = withContext(Dispatchers.IO) {
        val pet = petDao.queryById(petId)?.let(PetMapper::toDomain) ?: return@withContext null
        if (!pet.isHatched || pet.isRetired || HomeSlots.isAtHome(pet)) return@withContext pet.homeSlot
        val peers = petDao.queryByOwner(pet.ownerId).map(PetMapper::toDomain)
        val slot = HomeSlots.nextFreeHomeSlot(peers) ?: return@withContext null
        petDao.updateHomeSlot(petId, slot, System.currentTimeMillis())
        slot
    }

    override suspend fun sendToYard(petId: Long): Boolean = withContext(Dispatchers.IO) {
        val pet = petDao.queryById(petId)?.let(PetMapper::toDomain) ?: return@withContext false
        if (!HomeSlots.isAtHome(pet)) return@withContext false
        // ⚠️ 主宠不能离家：`HomeSlots.validate` 的不变量④要求主宠恒在家、恒占桌面 0 号
        if (pet.isMain) return@withContext false
        petDao.updateHomeSlot(petId, HomeSlots.NO_SLOT, System.currentTimeMillis())
        true
    }

    override fun observePetCount(ownerId: Long): Flow<Int> =
        petDao.observeByOwner(ownerId).map { it.size }

    override fun observeHatchedPetCount(ownerId: Long): Flow<Int> =
        petDao.observeHatchedCount(ownerId)

    override suspend fun queryById(id: Long): Pet? = withContext(Dispatchers.IO) {
        petDao.queryById(id)?.let(PetMapper::toDomain)
    }

    override suspend fun codexProgress(ownerId: Long): CodexProgress = withContext(Dispatchers.IO) {
        CodexProgress.of(PetMapper.toDomainList(petDao.queryByOwner(ownerId)))
    }

    override suspend fun petsByOwner(ownerId: Long): List<Pet> = withContext(Dispatchers.IO) {
        PetMapper.toDomainList(petDao.queryByOwner(ownerId))
    }

    override suspend fun childrenOf(petId: Long): List<Pet> = withContext(Dispatchers.IO) {
        petDao.queryChildren(petId).map(PetMapper::toDomain)
    }

    override suspend fun save(pet: Pet): Long = withContext(Dispatchers.IO) {
        petDao.insert(PetMapper.toEntity(pet))
    }

    override suspend fun rename(petId: Long, name: String) {
        withContext(Dispatchers.IO) {
            petDao.queryById(petId)?.let { entity ->
                petDao.update(entity.copy(nickname = name))
            }
        }
    }

    override suspend fun applyInteraction(
        petId: Long,
        type: GrowthRules.InteractionType
    ): ApplyInteractionUseCase.Result? = withContext(Dispatchers.IO) {
        val pet = petDao.queryById(petId)?.let(PetMapper::toDomain) ?: return@withContext null
        val result = ApplyInteractionUseCase.execute(pet, type)
        petDao.insert(PetMapper.toEntity(result.pet))
        result
    }
}
