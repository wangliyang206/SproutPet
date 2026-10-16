package com.sproutpet.core.db.mapper

import com.sproutpet.core.domain.pet.Pet
import com.sproutpet.core.domain.pet.PetAttributes
import com.sproutpet.core.domain.pet.PetGender
import com.sproutpet.core.domain.pet.PetGenes
import com.sproutpet.core.domain.pet.PetSpecies
import com.sproutpet.core.domain.pet.PetStage
import com.sproutpet.core.domain.pet.Rarity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PetMapper] 往返测试：领域对象 → 数据库实体 → 领域对象，字段必须无损。
 *
 * ## 为什么单独立一个测试类
 *
 * 领域层的单元测试（如 [com.sproutpet.core.domain.pet.ExoticBloodRulesTest]）
 * 直接构造 [Pet]，**完全不经过数据库**。所以如果某个领域字段忘了映射，
 * 那些测试依然全绿 —— 事故就藏在缝里。
 *
 * 真实案例：ADR-023「灵血传承」的 `exoticBlood` 在领域层实现完整、测试齐全，
 * 但 `PetEntity` 没有对应列、`PetMapper` 也没有映射。后果是**宠物一旦落库，
 * 灵血浓度就被静默重置为 0**，机制在真实运行中完全失效。
 *
 * 本测试类就是为堵住这类"领域 ↔ 持久化"的缝隙而存在。
 */
class PetMapperTest {

    // ═══════════ 灵血传承（ADR-023）回归测试 ═══════════

    @Test
    fun `exotic blood survives a round trip`() {
        for (blood in 0..GrowthRulesMaxBlood) {
            val original = samplePet(exoticBlood = blood)

            val restored = PetMapper.toDomain(PetMapper.toEntity(original))

            assertEquals(
                "灵血浓度 $blood 在往返后必须保持不变（曾因缺列被静默重置为 0）",
                blood,
                restored.exoticBlood
            )
        }
    }

    @Test
    fun `carrier with full blood is not reset to zero`() {
        val carrier = samplePet(rarity = Rarity.MYTHIC, exoticBlood = GrowthRulesMaxBlood)

        val entity = PetMapper.toEntity(carrier)
        val restored = PetMapper.toDomain(entity)

        assertEquals(GrowthRulesMaxBlood, entity.exoticBlood)
        assertEquals(GrowthRulesMaxBlood, restored.exoticBlood)
        assertTrue("非灵兽但携带满灵血，应被识别为血统携带者", restored.carriesExoticBlood)
    }

    @Test
    fun `exotic beast keeps its blood through storage`() {
        val beast = samplePet(rarity = Rarity.MUTANT, exoticBlood = GrowthRulesMaxBlood)

        val restored = PetMapper.toDomain(PetMapper.toEntity(beast))

        assertTrue(restored.isExoticBeast)
        assertTrue(restored.carriesExoticBlood)
    }

    // ═══════════ 神兽形态（ADR-026）回归测试 ═══════════

    @Test
    fun `deity form flag survives a round trip`() {
        for (flag in listOf(false, true)) {
            val original = samplePet(
                species = PetSpecies.SCHOLAR_CHICKEN,
                rarity = Rarity.MUTANT,
                hasFullDeityForm = flag
            )

            val restored = PetMapper.toDomain(PetMapper.toEntity(original))

            assertEquals(
                "神兽形态是出生时结算、此后不变的特征，落库后必须原样回来",
                flag,
                restored.hasFullDeityForm
            )
        }
    }

    @Test
    fun `deity id is only exposed for pets that actually have the form`() {
        val withForm = PetMapper.toDomain(
            PetMapper.toEntity(
                samplePet(
                    species = PetSpecies.SCHOLAR_CHICKEN, // 羽虫，神兽形态为凤凰
                    rarity = Rarity.MUTANT,
                    hasFullDeityForm = true
                )
            )
        )
        val withoutForm = PetMapper.toDomain(
            PetMapper.toEntity(
                samplePet(
                    species = PetSpecies.SCHOLAR_CHICKEN,
                    rarity = Rarity.MUTANT,
                    hasFullDeityForm = false
                )
            )
        )

        assertEquals(2, withForm.deityId)  // 羽虫之长：凤凰
        assertEquals(null, withoutForm.deityId)
        assertTrue(withForm.hasDeityFeatures)
    }

    // ═══════════ 性别（G-01）回归测试 ═══════════

    @Test
    fun `gender survives a round trip`() {
        for (gender in PetGender.entries) {
            val original = samplePet(gender = gender)

            val entity = PetMapper.toEntity(original)
            val restored = PetMapper.toDomain(entity)

            assertEquals(
                "性别是出生即定的前置条件，落库后必须原样回来",
                gender.id,
                entity.gender
            )
            assertEquals(gender, restored.gender)
        }
    }

    @Test
    fun `an out of range gender column does not crash the read path`() {
        // 迁移随机化 / 脏数据都可能留下越界值，读取不能抛异常。
        val entity = PetMapper.toEntity(samplePet()).copy(gender = 42)

        assertEquals(PetGender.MALE, PetMapper.toDomain(entity).gender)
    }

    // ═══════════ 全字段往返 ═══════════

    @Test
    fun `every field survives a round trip`() {
        val original = samplePet(
            id = 42L,
            stage = PetStage.RETIRED,
            exoticBlood = 2,
            hasFullDeityForm = true,
            spouseId = 7L,
            familyId = 9L,
            gender = PetGender.FEMALE
        )

        val restored = PetMapper.toDomain(PetMapper.toEntity(original))

        assertEquals(original.id, restored.id)
        assertEquals(original.ownerId, restored.ownerId)
        assertEquals(original.nickname, restored.nickname)
        assertEquals(original.species, restored.species)
        assertEquals(original.rarity, restored.rarity)
        assertEquals(original.stage, restored.stage)
        assertEquals(original.level, restored.level)
        assertEquals(original.exp, restored.exp)
        assertEquals(original.attributes, restored.attributes)
        assertEquals(original.genes, restored.genes)
        assertEquals(original.bornAt, restored.bornAt)
        assertEquals(original.hatchAt, restored.hatchAt)
        assertEquals(original.isHatched, restored.isHatched)
        assertEquals(original.spouseId, restored.spouseId)
        assertEquals(original.familyId, restored.familyId)
        assertEquals(original.parentIdA, restored.parentIdA)
        assertEquals(original.parentIdB, restored.parentIdB)
        assertEquals(original.batchNo, restored.batchNo)
        assertEquals(original.exoticBlood, restored.exoticBlood)
        assertEquals(original.hasFullDeityForm, restored.hasFullDeityForm)
        assertEquals(original.gender, restored.gender)
        assertEquals(original.isRetired, restored.isRetired)
    }

    // ═══════════ 隐居：单一真相源（stage）+ 派生缓存列 ═══════════

    @Test
    fun `retired stage and flag travel together`() {
        val retired = samplePet(stage = PetStage.RETIRED)

        val restored = PetMapper.toDomain(PetMapper.toEntity(retired))

        assertEquals(PetStage.RETIRED, restored.stage)
        assertTrue("isRetired 必须是 stage 的派生值，不能各说各话", restored.isRetired)
    }

    @Test
    fun `the retired cache column always mirrors the stage`() {
        // 列 `is_retired` 是**派生缓存**（供 DAO 走索引过滤），真相源是 stage。
        // 这条测试守住"写入始终一致" —— 它们曾经是两套互不同步的真相源，
        // 导致"隐居的宠会不会出现在列表里"取决于查询走列还是走 stage。
        for (stage in PetStage.entries) {
            val entity = PetMapper.toEntity(samplePet(stage = stage))

            assertEquals(
                "stage=$stage 时 is_retired 缓存写错了，DAO 会过滤掉/放进不该有的行",
                stage == PetStage.RETIRED,
                entity.isRetired
            )
        }
    }

    @Test
    fun `a stale retired column cannot resurrect a retired pet into the domain`() {
        // 脏数据防护：即使列被写成 0（例如某次迁移/外部写入），
        // 领域层的 isRetired 仍由 stage 决定，不会被列带偏。
        val entity = PetMapper.toEntity(samplePet(stage = PetStage.RETIRED))
            .copy(isRetired = false)

        val restored = PetMapper.toDomain(entity)

        assertEquals(PetStage.RETIRED, restored.stage)
        assertTrue(restored.isRetired)
    }

    @Test
    fun `list mapping preserves order and size`() {
        val pets = listOf(samplePet(id = 1L), samplePet(id = 2L), samplePet(id = 3L))

        val restored = PetMapper.toDomainList(pets.map(PetMapper::toEntity))

        assertEquals(3, restored.size)
        assertEquals(listOf(1L, 2L, 3L), restored.map { it.id })
    }

    // ═══════════ 夹具 ═══════════

    private fun samplePet(
        id: Long = 1L,
        ownerId: Long = 100L,
        nickname: String = "小蜗",
        species: PetSpecies = PetSpecies.SNAIL,
        rarity: Rarity = Rarity.COMMON,
        stage: PetStage = PetStage.BABY,
        exoticBlood: Int = 0,
        hasFullDeityForm: Boolean = false,
        spouseId: Long? = null,
        familyId: Long? = null,
        gender: PetGender = PetGender.MALE
    ): Pet = Pet(
        id = id,
        ownerId = ownerId,
        nickname = nickname,
        species = species,
        rarity = rarity,
        stage = stage,
        level = 5,
        exp = 120,
        attributes = PetAttributes(
            stamina = 10,
            strength = 20,
            agility = 30,
            wisdom = 40,
            charm = 50,
            intimacy = 60
        ),
        genes = PetGenes(
            bodySize = 0.25f,
            hueShift = 0.5f,
            patternDensity = 0.75f,
            eyeSize = 0.4f,
            tailLength = 0.6f,
            liveliness = 0.8f
        ),
        bornAt = 1_700_000_000_000L,
        hatchAt = 1_700_000_300_000L,
        isHatched = true,
        spouseId = spouseId,
        familyId = familyId,
        parentIdA = null,
        parentIdB = null,
        batchNo = "B20261016",
        exoticBlood = exoticBlood,
        hasFullDeityForm = hasFullDeityForm,
        gender = gender
    )

    private companion object {
        /** 与 [com.sproutpet.core.domain.pet.GrowthRules.ExoticBloodRules.MAX_BLOOD] 对齐 */
        const val GrowthRulesMaxBlood = 3
    }
}
