package com.sproutpet.core.domain.pet

import com.sproutpet.core.domain.incubation.BlindBoxEngine
import com.sproutpet.core.domain.incubation.BlindBoxResult
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 性别（G-01）回归测试。
 *
 * ## 为什么单独守这一块
 *
 * 性别在**产品文档里被反复依赖**（00 §5.2「配对需要一公一母」、06 §2.3、
 * 04 Phase 6 验收、01 目录树的 `PetGender.kt`），却在代码里**长期完全不存在** ——
 * 配对前置条件实际只校验了"成年 + 亲密度"两条。
 *
 * 这类"文档到处引用、代码一处没有"的字段最容易在交接时被忽略：
 * 读文档的人以为已经有了，读代码的人以为文档在别处实现了。
 * 所以这里既守**数据契约**（id 不可重排），也守**行为契约**（一公一母 + 异主）。
 */
class PetGenderTest {

    // ─────────────── 数据契约：id 不可重排 ───────────────

    @Test
    fun `there are exactly two genders`() {
        assertEquals(2, PetGender.entries.size)
    }

    @Test
    fun `gender ids are contiguous from zero`() {
        // 这两个值直接写进数据库 `pet.gender` 列，重排会让存量宠物整体变性。
        assertEquals(0, PetGender.MALE.id)
        assertEquals(1, PetGender.FEMALE.id)
        assertEquals(listOf(0, 1), PetGender.entries.map { it.id })
    }

    @Test
    fun `display names are the documented ones`() {
        assertEquals("公", PetGender.MALE.displayName)
        assertEquals("母", PetGender.FEMALE.displayName)
    }

    @Test
    fun `fromId round trips for every gender`() {
        for (gender in PetGender.entries) {
            assertEquals(gender, PetGender.fromId(gender.id))
        }
    }

    @Test
    fun `an unknown id falls back instead of throwing`() {
        // 迁移/脏数据都可能在列里留下越界值，读取路径不能崩。
        assertEquals(PetGender.MALE, PetGender.fromId(-7))
        assertEquals(PetGender.MALE, PetGender.fromId(99))
    }

    @Test
    fun `opposite flips and is symmetric`() {
        assertEquals(PetGender.FEMALE, PetGender.MALE.opposite)
        assertEquals(PetGender.MALE, PetGender.FEMALE.opposite)
        assertEquals(PetGender.MALE, PetGender.MALE.opposite.opposite)
    }

    // ─────────────── 随机分配必须 1:1 ───────────────

    @Test
    fun `random assignment is balanced`() {
        // 一公一母是配对硬条件；某一边比例偏低会制造"怎么配都撞性别"的结构性挫败，
        // 而这种挫败玩家无法靠努力化解，只能在源头保证分布。
        val random = Random(20261016)
        val total = 20_000
        val males = (1..total).count { PetGender.random(random) == PetGender.MALE }
        val ratio = males.toDouble() / total

        assertTrue("随机性别明显偏离 1:1：公占比 $ratio", ratio in 0.47..0.53)
    }

    @Test
    fun `a seeded random is reproducible`() {
        // 便于在其它测试里注入确定序列。
        val a = (1..20).map { PetGender.random(Random(7)) }
        val b = (1..20).map { PetGender.random(Random(7)) }
        assertEquals(a, b)
    }

    // ─────────────── 开蛋即分配，且此后不变 ───────────────

    @Test
    fun `new eggs get both genders across many draws`() {
        val random = Random(20261016)
        val result = BlindBoxResult(
            species = PetSpecies.SNAIL,
            rarity = Rarity.COMMON,
            genes = PetGenes()
        )
        val genders = (1..500)
            .map {
                BlindBoxEngine.createEgg(
                    ownerId = 1L,
                    result = result,
                    batchNo = "B20261016",
                    random = random
                ).gender
            }
            .toSet()

        assertEquals("开蛋必须真的分配性别，而不是永远落在默认值上", 2, genders.size)
    }

    @Test
    fun `hatching does not change the gender`() {
        val egg = BlindBoxEngine.createEgg(
            ownerId = 1L,
            result = BlindBoxResult(PetSpecies.SNAIL, Rarity.COMMON, PetGenes()),
            batchNo = "B20261016",
            random = FixedGenderRandom(PetGender.FEMALE)
        )
        assertEquals(PetGender.FEMALE, egg.gender)

        val hatched = BlindBoxEngine.hatch(egg)

        assertEquals(PetStage.BABY, hatched.stage)
        assertEquals("破壳不得改变性别 —— 变了会出现'同一只宠时而能配时而不能配'",
            PetGender.FEMALE, hatched.gender)
    }

    // ─────────────── 行为契约：一公一母 + 异主 ───────────────

    @Test
    fun `opposite genders are compatible`() {
        val rules = GrowthRules.PairingRules
        assertTrue(rules.gendersCompatible(PetGender.MALE, PetGender.FEMALE))
        assertTrue(rules.gendersCompatible(PetGender.FEMALE, PetGender.MALE))
    }

    @Test
    fun `same gender is rejected`() {
        // G-01 的症结：这条规则曾**完全不存在**，配对前置条件只有"成年 + 亲密度"。
        val rules = GrowthRules.PairingRules
        assertTrue("前置条件：一公一母确实被要求", rules.requireDifferentGender)
        assertFalse(rules.gendersCompatible(PetGender.MALE, PetGender.MALE))
        assertFalse(rules.gendersCompatible(PetGender.FEMALE, PetGender.FEMALE))
    }

    @Test
    fun `two adults of opposite gender from different owners can pair`() {
        val a = pairablePet(id = 1L, ownerId = 10L, gender = PetGender.MALE)
        val b = pairablePet(id = 2L, ownerId = 20L, gender = PetGender.FEMALE)

        assertTrue(a.canPairWith(b))
        assertTrue(b.canPairWith(a))
    }

    @Test
    fun `two parents of the same gender cannot pair`() {
        val a = pairablePet(id = 1L, ownerId = 10L, gender = PetGender.MALE)
        val b = pairablePet(id = 2L, ownerId = 20L, gender = PetGender.MALE)

        assertFalse("这就是 G-01 的核心缺陷：不加性别校验，配对照样成立", a.canPairWith(b))
        assertTrue("但两者各自都是合格的单身候选 —— 缺的是**双方**约束", a.canPair)
        assertTrue(b.canPair)
    }

    @Test
    fun `the same owner cannot pair with itself`() {
        val a = pairablePet(id = 1L, ownerId = 10L, gender = PetGender.MALE)
        val b = pairablePet(id = 2L, ownerId = 10L, gender = PetGender.FEMALE)

        assertFalse("同主人的两只宠不允许配对，防止克隆式自我繁殖", a.canPairWith(b))
    }

    @Test
    fun `an immature pet cannot pair regardless of gender`() {
        val adult = pairablePet(id = 1L, ownerId = 10L, gender = PetGender.MALE)
        val baby = pairablePet(
            id = 2L, ownerId = 20L, gender = PetGender.FEMALE, stage = PetStage.BABY
        )

        assertFalse(adult.canPairWith(baby))
        assertFalse(baby.canPairWith(adult))
    }

    @Test
    fun `low intimacy blocks pairing`() {
        val a = pairablePet(id = 1L, ownerId = 10L, gender = PetGender.MALE)
        val b = pairablePet(
            id = 2L, ownerId = 20L, gender = PetGender.FEMALE,
            intimacy = GrowthRules.PairingRules.requiredIntimacy - 1
        )

        assertFalse(a.canPairWith(b))
    }

    @Test
    fun `gender does not feed into any numeric calculation`() {
        // ADR-021：性别只决定"能否配对"，不得影响数值。
        // 若哪天有人把它接进属性/战力，这一条会立刻变红。
        val male = pairablePet(id = 1L, ownerId = 10L, gender = PetGender.MALE)
        val female = pairablePet(id = 1L, ownerId = 10L, gender = PetGender.FEMALE)

        assertEquals(male.attributes, female.attributes)
        assertEquals(male.power, female.power)
        assertNotEquals(male.gender, female.gender) // 前置：两者确实只在性别上不同
    }

    // ══════════════ 夹具 ══════════════

    private fun pairablePet(
        id: Long,
        ownerId: Long,
        gender: PetGender,
        stage: PetStage = PetStage.ADULT,
        intimacy: Int = GrowthRules.PairingRules.requiredIntimacy
    ): Pet = Pet(
        id = id,
        ownerId = ownerId,
        nickname = "小蜗",
        species = PetSpecies.SNAIL,
        rarity = Rarity.COMMON,
        stage = stage,
        level = 30,
        exp = 0,
        attributes = PetAttributes(intimacy = intimacy),
        genes = PetGenes(),
        bornAt = 1_700_000_000_000L,
        hatchAt = 1_700_000_000_000L,
        isHatched = true,
        spouseId = null,
        familyId = null,
        parentIdA = null,
        parentIdB = null,
        batchNo = "B20261016",
        gender = gender
    )

    /**
     * 固定结果的随机源，用于验证"分配结果确实被写进去了"（而不是刚好撞上默认值）。
     *
     * 注意映射方向：`Random.nextBoolean()` 的实现是 `nextBits(1) != 0`，
     * 而 [PetGender.random] 把 `true` 映射为 [PetGender.MALE]。
     * 所以「公」对应 `1`、「母」对应 `0` —— 写反了测试会假绿。
     */
    private class FixedGenderRandom(private val fixed: PetGender) : Random() {
        override fun nextBits(bitCount: Int): Int =
            if (fixed == PetGender.MALE) 1 else 0
    }
}
