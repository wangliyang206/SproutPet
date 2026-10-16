package com.sproutpet.core.domain.pet

import com.sproutpet.core.domain.incubation.BlindBoxEngine
import com.sproutpet.core.domain.incubation.BlindBoxResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 阶段枚举的**数据契约**（D-02）。
 *
 * ## 为什么要有这个文件
 *
 * `PetStage` 的 `id` 会被原样写进 `pet.stage` 列，是**数据库与领域模型之间的契约**。
 * 它曾经在文档里被整体写错过一次（漏了 `HATCHING`，导致 1~6 全部串位），
 * 那种错误会让**存量宠物被系统性错读成别的阶段**，且不会有任何报错。
 *
 * 所以这里把 id、派生语义、以及"哪些值可以落库"一起钉死。
 */
class PetStageTest {

    @Test
    fun `stage ids are fixed and contiguous from zero`() {
        // ⚠️ 这七个数字不可重排 —— `pet.stage` 列里已经按它们存了历史数据。
        assertEquals(0, PetStage.EGG.id)
        assertEquals(1, PetStage.HATCHING.id)
        assertEquals(2, PetStage.BABY.id)
        assertEquals(3, PetStage.JUVENILE.id)
        assertEquals(4, PetStage.ADULT.id)
        assertEquals(5, PetStage.ELDER.id)
        assertEquals(6, PetStage.RETIRED.id)

        assertEquals((0..6).toList(), PetStage.entries.map { it.id })
    }

    @Test
    fun `fromId round trips and falls back to egg`() {
        for (stage in PetStage.entries) {
            assertEquals(stage, PetStage.fromId(stage.id))
        }
        assertEquals(PetStage.EGG, PetStage.fromId(-1))
        assertEquals(PetStage.EGG, PetStage.fromId(99))
    }

    @Test
    fun `only hatched stages count as hatched`() {
        assertFalse(PetStage.EGG.isHatched)
        assertFalse(PetStage.HATCHING.isHatched)
        assertTrue(PetStage.BABY.isHatched)
        assertTrue(PetStage.ADULT.isHatched)
        assertTrue(PetStage.RETIRED.isHatched)
    }

    @Test
    fun `pairing is allowed for adults and elders only`() {
        for (stage in PetStage.entries) {
            val expected = stage == PetStage.ADULT || stage == PetStage.ELDER
            assertEquals("$stage 的配对资格判断错了", expected, stage.canPair)
        }
    }

    // ══════════════ D-02：HATCHING 是不可落库的保留位 ══════════════

    @Test
    fun `stage of level never returns the transition stage`() {
        for (level in -5..GrowthRules.MAX_LEVEL + 5) {
            val stage = GrowthRules.stageOf(level)
            assertFalse(
                "stageOf($level) 返回了过渡态 HATCHING —— 它不该由等级推导出来",
                stage == PetStage.HATCHING
            )
        }
    }

    @Test
    fun `hatching goes straight from egg to baby`() {
        // 破壳没有"中间落库"的一步：3 秒过渡由渲染层 PetRenderState.Hatching 负责。
        val egg = BlindBoxEngine.createEgg(
            ownerId = 1L,
            result = BlindBoxResult(PetSpecies.SNAIL, Rarity.COMMON, PetGenes()),
            batchNo = "B20261016"
        )
        assertEquals(PetStage.EGG, egg.stage)

        val hatched = BlindBoxEngine.hatch(egg)

        assertEquals(PetStage.BABY, hatched.stage)
        assertTrue("破壳后必须可互动", hatched.stage.isHatched)
    }

    @Test
    fun `every writable stage is reachable`() {
        // 反向守门：除 HATCHING 这个保留位外，其余阶段都必须有可达路径，
        // 否则说明某段生命周期（如隐居）其实进不去。
        val reachable = buildSet {
            add(PetStage.EGG)                                   // createEgg
            add(BlindBoxEngine.hatch(
                BlindBoxEngine.createEgg(
                    ownerId = 1L,
                    result = BlindBoxResult(PetSpecies.SNAIL, Rarity.COMMON, PetGenes()),
                    batchNo = "B20261016"
                )
            ).stage)                                            // hatch -> BABY
            for (level in 1..GrowthRules.MAX_LEVEL) add(GrowthRules.stageOf(level))
            add(PetStage.RETIRED)                               // 隐居，由主动操作进入（Phase 8）
        }

        val writable = PetStage.entries.filter { it != PetStage.HATCHING }.toSet()
        assertEquals(
            "以下阶段没有任何可达路径（只有保留位 HATCHING 允许不可达）：" +
                (writable - reachable),
            writable,
            reachable
        )
    }
}
