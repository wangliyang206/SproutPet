package com.sproutpet.pet.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sproutpet.core.db.SproutPetDatabase
import com.sproutpet.core.db.entity.PairingEntity
import com.sproutpet.core.db.mapper.PetMapper
import com.sproutpet.core.domain.pet.FusionRules
import com.sproutpet.core.domain.pet.Pet
import com.sproutpet.core.domain.pet.PetAttributes
import com.sproutpet.core.domain.pet.PetGenes
import com.sproutpet.core.domain.pet.PetSpecies
import com.sproutpet.core.domain.pet.PetStage
import com.sproutpet.core.domain.pet.Rarity
import com.sproutpet.core.domain.social.PairingStatus
import com.sproutpet.core.domain.user.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [FusionRepositoryImpl] 编排层测试（Robolectric + 内存 Room，真实 DAO 走真库）。
 *
 * ## 为什么在 app 层测而不是只测 core
 *
 * [FusionRules] 的纯函数已有 1476 组穷举测试守住；但仓库层的**编排语义**
 * core 测不到 —— 扣钱、入后山、解除配对、"拒绝路径一分钱不扣"：
 * 这些正是「规则写好了、编排出错」的高发区（`G-04` / `G-15` 的同款盲区）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FusionRepositoryImplTest {

    private lateinit var db: SproutPetDatabase
    private lateinit var repo: FusionRepositoryImpl
    private val owner: Long = 100L

    private var gold: Int = 0

    private val userRepository = object : UserRepository {
        private val user = User(id = owner, nickname = "测试员", gold = 0, createdAt = 0L)
        override fun observeCurrent(): Flow<User?> = flowOf(user)
        override suspend fun current(): User? = user.copy(gold = this@FusionRepositoryImplTest.gold)
        override suspend fun getOrCreate(ownerId: Long): User = user
        override suspend fun updatePityCount(count: Int) = Unit
        override suspend fun addGold(delta: Int) {
            this@FusionRepositoryImplTest.gold = (this@FusionRepositoryImplTest.gold + delta).coerceAtLeast(0)
        }
        override suspend fun addGem(delta: Int) = Unit
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SproutPetDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = FusionRepositoryImpl(db.petDao(), db.pairingDao(), userRepository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── 成功路径 ──

    @Test
    fun `融合成功 - 目标加印记与经验 - 副宠入后山 - 扣 200 金币`() = runTest {
        gold = 500
        val target = savePet(nickname = "小豆芽", marks = 0, level = 10)
        val material = savePet(nickname = "糖球", marks = 0, level = 12)

        val outcome = repo.fuse(owner, target.id, material.id)

        val ok = outcome as FuseOutcome.Ok
        assertEquals(1, ok.success.fusion.targetMarks)          // 普通 +1 印记
        assertEquals(target.exp + FusionRules.expGainedPerMaterialLevel * 12, ok.success.target.exp)
        assertTrue(!ok.success.fusion.drifted)                  // 1 < 5，不上漂

        // 副宠：入后山（stage = RETIRED）、撤槽位，但仍"活着"（有 id 记录）
        val retired = db.petDao().queryById(material.id)!!
        assertEquals(PetStage.RETIRED.id, retired.stage)
        assertEquals(-1, retired.homeSlot)
        assertEquals(-1, retired.desktopSlot)

        // 金币：500 - 200
        assertEquals(500 - FusionRules.costGold, gold)
    }

    @Test
    fun `印记攒满触发上漂 - 溢出归入新档`() = runTest {
        gold = FusionRules.costGold
        // 普通（阈值 5）：已有 4 印记，再喂 1 只普通（+1）→ 恰好 5 → 上漂到稀有、印记归 0
        val target = savePet(nickname = "目标", rarity = Rarity.COMMON, marks = 4)
        val material = savePet(nickname = "副宠", rarity = Rarity.COMMON)

        val outcome = repo.fuse(owner, target.id, material.id) as FuseOutcome.Ok

        assertTrue(outcome.success.fusion.drifted)
        assertEquals(Rarity.RARE, outcome.success.fusion.targetRarity)
        assertEquals(0, outcome.success.fusion.targetMarks)
        assertEquals(Rarity.RARE.id, db.petDao().queryById(target.id)!!.rarity)
    }

    @Test
    fun `融合解除受影响的配对 - 双方搭档关系清空`() = runTest {
        gold = FusionRules.costGold
        val target = savePet(nickname = "目标", marks = 0)
        val material = savePet(nickname = "副宠", marks = 0)
        // 两人此前是配对搭档（同主人的重复宠互为搭档，用 is_same_owner=1 表达）
        db.pairingDao().insert(
            PairingEntity(
                petIdA = minOf(target.id, material.id),
                petIdB = maxOf(target.id, material.id),
                ownerIdA = owner, ownerIdB = owner, isSameOwner = true,
                pairedAt = 1_000L, status = PairingStatus.PAIRED.id
            )
        )
        val paired = PetMapper.toDomain(db.petDao().queryById(target.id)!!)
            .copy(spouseId = material.id)
        db.petDao().insert(PetMapper.toEntity(paired))

        repo.fuse(owner, target.id, material.id)

        // 配对记录 → RELEASED
        val pairing = db.pairingDao().queryActiveByPet(target.id)
        assertEquals(null, pairing)  // status=1 的已查不到
        // 目标的 spouseId 指向入后山的副宠 → 清空
        assertEquals(null, db.petDao().queryById(target.id)!!.spouseId)
        // 副宠自己的 spouseId 也清空
        assertEquals(null, db.petDao().queryById(material.id)!!.spouseId)
    }

    // ── 拒绝路径：整笔不扣 ──

    @Test
    fun `跨物种拒绝 - 金币分文不动`() = runTest {
        gold = 1_000
        val target = savePet(nickname = "目标", species = PetSpecies.SNAIL)
        val material = savePet(nickname = "副宠", species = PetSpecies.SCHOLAR_CHICKEN)

        val outcome = repo.fuse(owner, target.id, material.id)

        assertTrue(outcome is FuseOutcome.Rejected)
        assertEquals(
            FusionRules.RejectReason.SPECIES_MISMATCH,
            (outcome as FuseOutcome.Rejected).failure.ruleReason
        )
        assertEquals(1_000, gold)
    }

    @Test
    fun `主宠不可作为材料`() = runTest {
        gold = FusionRules.costGold
        val target = savePet(nickname = "目标")
        val material = savePet(nickname = "主宠副宠", isMain = true)

        val outcome = repo.fuse(owner, target.id, material.id)

        assertTrue(outcome is FuseOutcome.Rejected)
        assertEquals(FuseFailure.MaterialIsMain, (outcome as FuseOutcome.Rejected).failure)
        assertEquals(FusionRules.costGold, gold)
    }

    @Test
    fun `金币不足拒绝 - 还差多少要说清`() = runTest {
        gold = 100
        val target = savePet(nickname = "目标")
        val material = savePet(nickname = "副宠")

        val outcome = repo.fuse(owner, target.id, material.id)

        val rejected = outcome as FuseOutcome.Rejected
        assertEquals(FuseFailure.NotEnoughGold(FusionRules.costGold - 100), rejected.failure)
    }

    @Test
    fun `灵兽不可作为材料 - ADR-017 在编排层仍然成立`() = runTest {
        gold = FusionRules.costGold
        val target = savePet(nickname = "目标", rarity = Rarity.EPIC)
        val material = savePet(nickname = "灵兽", rarity = Rarity.MUTANT)

        val outcome = repo.fuse(owner, target.id, material.id)

        assertTrue(outcome is FuseOutcome.Rejected)
        assertEquals(
            FusionRules.RejectReason.MATERIAL_IS_MUTANT,
            (outcome as FuseOutcome.Rejected).failure.ruleReason
        )
        // 分文未动、两宠原样
        assertEquals(FusionRules.costGold, gold)
        assertNotEquals(PetStage.RETIRED.id, db.petDao().queryById(material.id)!!.stage)
    }

    // ── helpers ──

    private suspend fun savePet(
        nickname: String,
        species: PetSpecies = PetSpecies.SNAIL,
        rarity: Rarity = Rarity.COMMON,
        marks: Int = 0,
        level: Int = 5,
        isMain: Boolean = false
    ): Pet {
        val pet = Pet(
            id = 0L,
            ownerId = owner,
            nickname = nickname,
            species = species,
            rarity = rarity,
            stage = PetStage.ADULT,
            level = level,
            exp = 0,
            attributes = PetAttributes(stamina = 10, strength = 10, agility = 10, wisdom = 10, charm = 10, intimacy = 80),
            genes = PetGenes(0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f),
            bornAt = 1_000L,
            hatchAt = 2_000L,
            isHatched = true,
            spouseId = null,
            familyId = null,
            parentIdA = null,
            parentIdB = null,
            batchNo = "B-TEST",
            fusionMarks = marks,
            isMain = isMain
        )
        val id = db.petDao().insert(PetMapper.toEntity(pet))
        return pet.copy(id = id)
    }

    /** 测试可读性：把规则拒绝从编排拒绝里摘出来 */
    private val FuseFailure.ruleReason: FusionRules.RejectReason?
        get() = (this as? FuseFailure.RuleRejected)?.reason
}
