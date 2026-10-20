package com.sproutpet.pet.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sproutpet.core.db.SproutPetDatabase
import com.sproutpet.core.db.mapper.PetMapper
import com.sproutpet.core.domain.pet.GrowthRules
import com.sproutpet.core.domain.pet.Pet
import com.sproutpet.core.domain.pet.PetAttributes
import com.sproutpet.core.domain.pet.PetGender
import com.sproutpet.core.domain.pet.PetGenes
import com.sproutpet.core.domain.pet.PetSpecies
import com.sproutpet.core.domain.pet.PetStage
import com.sproutpet.core.domain.pet.Rarity
import com.sproutpet.core.domain.social.NpcCandidateRules
import com.sproutpet.core.domain.social.PairingStatus
import com.sproutpet.core.domain.user.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

/**
 * [PairingRepositoryImpl] 编排层测试（Robolectric + 内存 Room）。
 *
 * 守 `W-16-1` 拍板口径的**执行侧**（候选生成规则本身由 [NpcCandidateRulesTest] 守）：
 * NPC 宠落库形态、七条前置的编排拦截、冷却/名额、出生上下文字段一次算全。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PairingRepositoryImplTest {

    private lateinit var db: SproutPetDatabase
    private lateinit var repo: PairingRepositoryImpl
    private val owner: Long = 100L
    private var gold: Int = 0

    private val userRepository = object : UserRepository {
        private val user = User(id = owner, nickname = "测试员", gold = 0, createdAt = 0L)
        override fun observeCurrent(): Flow<User?> = flowOf(user)
        override suspend fun current(): User? = user.copy(gold = this@PairingRepositoryImplTest.gold)
        override suspend fun getOrCreate(ownerId: Long): User = user
        override suspend fun updatePityCount(count: Int) = Unit
        override suspend fun addGold(delta: Int) {
            this@PairingRepositoryImplTest.gold = (this@PairingRepositoryImplTest.gold + delta).coerceAtLeast(0)
        }
        override suspend fun addGem(delta: Int) = Unit
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SproutPetDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = PairingRepositoryImpl(db.petDao(), db.pairingDao(), userRepository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `NPC 配对成功 - 扣 3000 - NPC 宠同档稀有度且性别取反 - 双方互指`() = runTest {
        gold = 10_000
        val mine = savePet(gender = PetGender.MALE)
        val candidate = NpcCandidateRules.candidatesFor(mine, Random(42)).first()

        val outcome = repo.startNpcPairing(owner, mine.id, candidate)

        val ok = outcome as PairingStartOutcome.Ok
        assertEquals(PairingStatus.PAIRED, ok.pairing.status)
        assertTrue(ok.pairing.partnerIsNpc)
        assertEquals(GrowthRules.PairingRules.costGold, 10_000 - gold)

        // NPC 宠：系统托管 owner、成年、稀有度同档、性别取反、亲密度达标
        val npc = db.petDao().queryById(ok.npcPetId)!!.let(PetMapper::toDomain)
        assertEquals(SYSTEM_NPC_OWNER_ID, npc.ownerId)
        assertEquals(PetStage.ADULT, npc.stage)
        assertEquals(mine.rarity, npc.rarity)
        assertEquals(PetGender.FEMALE, npc.gender)
        assertTrue(npc.attributes.intimacy >= GrowthRules.PairingRules.requiredIntimacy)

        // 双方互指搭档
        assertEquals(ok.npcPetId, db.petDao().queryById(mine.id)!!.spouseId)
        assertEquals(mine.id, db.petDao().queryById(ok.npcPetId)!!.spouseId)
    }

    @Test
    fun `未成年或亲密度不足拒绝 - 金币不动`() = runTest {
        gold = 10_000
        val baby = savePet(stage = PetStage.BABY, level = 1, intimacy = 90)
        val candidate = NpcCandidateRules.candidatesFor(baby, Random(1)).first()

        val outcome = repo.startNpcPairing(owner, baby.id, candidate)

        assertTrue(outcome is PairingStartOutcome.Rejected)
        assertEquals(PairingFailure.PetNotPairable, (outcome as PairingStartOutcome.Rejected).failure)
        assertEquals(10_000, gold)
    }

    @Test
    fun `金币不足拒绝 - 还差多少要说清`() = runTest {
        gold = 1_000
        val mine = savePet()
        val candidate = NpcCandidateRules.candidatesFor(mine, Random(1)).first()

        val outcome = repo.startNpcPairing(owner, mine.id, candidate)

        val rejected = outcome as PairingStartOutcome.Rejected
        assertEquals(
            PairingFailure.NotEnoughGold(GrowthRules.PairingRules.costGold - 1_000),
            rejected.failure
        )
    }

    @Test
    fun `已有配对再配拒绝`() = runTest {
        gold = 10_000
        val mine = savePet()
        val first = NpcCandidateRules.candidatesFor(mine, Random(1)).first()
        repo.startNpcPairing(owner, mine.id, first)

        val second = NpcCandidateRules.candidatesFor(mine, Random(2)).first()
        val outcome = repo.startNpcPairing(owner, mine.id, second)

        assertEquals(PairingFailure.AlreadyPaired, (outcome as PairingStartOutcome.Rejected).failure)
    }

    @Test
    fun `首配即可生育 - 后代出生上下文一次算全并进院子`() = runTest {
        gold = 10_000
        val mine = savePet()
        val candidate = NpcCandidateRules.candidatesFor(mine, Random(42)).first()
        val pairing = (repo.startNpcPairing(owner, mine.id, candidate) as PairingStartOutcome.Ok).pairing

        val now = System.currentTimeMillis()
        val outcome = repo.breed(owner, pairing.id, now)

        val baby = (outcome as BreedOutcome.Ok).offspring
        assertEquals(PetStage.BABY, baby.stage)
        assertEquals(1, baby.level)
        assertEquals(2, baby.generation)                       // max(1,1)+1
        assertEquals(mine.id, baby.parentIdA)
        assertNotEquals(mine.id, baby.parentIdB)
        assertEquals(-1, baby.homeSlot)                        // 进「院子」待命
        assertEquals(false, baby.isMain)

        // 落库后仍在、配对计数推进
        assertNotNull(db.petDao().queryById(baby.id))
        val updated = db.pairingDao().observeActive().first().first { it.id == pairing.id }
        assertEquals(1, updated.offspringCount)
        assertEquals(now, updated.lastBreedAt)
    }

    @Test
    fun `冷却 3 天内再生育拒绝 - 说清还剩几天`() = runTest {
        gold = 10_000
        val mine = savePet()
        val candidate = NpcCandidateRules.candidatesFor(mine, Random(42)).first()
        val pairing = (repo.startNpcPairing(owner, mine.id, candidate) as PairingStartOutcome.Ok).pairing
        val now = System.currentTimeMillis()
        repo.breed(owner, pairing.id, now)

        val outcome = repo.breed(owner, pairing.id, now + 1000L)

        val rejected = outcome as BreedOutcome.Rejected
        val cooldown = rejected.failure as PairingFailure.Cooldown
        assertTrue(cooldown.remainMs > 0L)
        assertTrue(cooldown.remainMs <= GrowthRules.PairingRules.cooldownMs)
    }

    @Test
    fun `NPC 名额 3 次用完拒绝`() = runTest {
        gold = 10_000
        val mine = savePet()
        val candidate = NpcCandidateRules.candidatesFor(mine, Random(42)).first()
        val pairingId = (repo.startNpcPairing(owner, mine.id, candidate) as PairingStartOutcome.Ok).pairing.id

        // 直接把计数顶到 NPC 上限（3）—— 名额语义由 S16 冻结，这里只验编排拦截
        val entity = db.pairingDao().observeActive().first().first { it.id == pairingId }
        db.pairingDao().update(entity.copy(offspringCount = 3, lastBreedAt = 0L))

        val outcome = repo.breed(owner, pairingId, System.currentTimeMillis())

        assertEquals(PairingFailure.NoOffspringQuota, (outcome as BreedOutcome.Rejected).failure)
    }

    @Test
    fun `解除搭档 - 互指清空且状态 RELEASED`() = runTest {
        gold = 10_000
        val mine = savePet()
        val candidate = NpcCandidateRules.candidatesFor(mine, Random(42)).first()
        val pairing = (repo.startNpcPairing(owner, mine.id, candidate) as PairingStartOutcome.Ok).pairing

        repo.releasePairing(pairing.id)

        assertEquals(null, db.petDao().queryById(mine.id)!!.spouseId)
        val released = db.pairingDao().observeActive().first().none { it.id == pairing.id }
        assertTrue(released)
    }

    // ── helpers ──

    private suspend fun savePet(
        gender: PetGender = PetGender.MALE,
        stage: PetStage = PetStage.ADULT,
        level: Int = 30,
        intimacy: Int = 80
    ): Pet {
        val pet = Pet(
            id = 0L,
            ownerId = owner,
            nickname = "小豆芽",
            species = PetSpecies.SPROUT_CHICKEN,
            rarity = Rarity.EPIC,
            stage = stage,
            level = level,
            exp = 0,
            attributes = PetAttributes(10, 10, 10, 10, 10, intimacy),
            genes = PetGenes.random(),
            bornAt = 1_000L,
            hatchAt = 2_000L,
            isHatched = true,
            spouseId = null,
            familyId = null,
            parentIdA = null,
            parentIdB = null,
            batchNo = "B-TEST",
            gender = gender
        )
        val id = db.petDao().insert(PetMapper.toEntity(pet))
        return pet.copy(id = id)
    }
}
