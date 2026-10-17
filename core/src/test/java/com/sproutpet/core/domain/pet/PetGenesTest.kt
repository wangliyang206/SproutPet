package com.sproutpet.core.domain.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PetGenes] 基因模型测试：随机生成与遗传混合。
 *
 * ## 测试策略
 *
 * `PetGenes.random()` / `inherit()` 内部用 `(0..1000).random()` 取随机数，
 * 非确定性。所以测试分两层：
 * 1. **确定性断言** —— `inherit(mutationRate = 0f)` 时变异项恒为 0，
 *    子代每个基因严格等于父母均值，可以逐字段精确断言。
 * 2. **值域断言** —— 无论随机种子如何，`random()` 与 `inherit()` 的输出
 *    每个字段都必须落在 `[0, 1]`（由 `coerceIn` 执法）。
 */
class PetGenesTest {

    /** `random()` 六个维度全部落在 [0, 1]。 */
    @Test
    fun random_allFieldsWithinUnitRange() {
        repeat(100) {
            val g = PetGenes.random()
            assertInUnitRange(g.bodySize)
            assertInUnitRange(g.hueShift)
            assertInUnitRange(g.patternDensity)
            assertInUnitRange(g.eyeSize)
            assertInUnitRange(g.tailLength)
            assertInUnitRange(g.liveliness)
        }
    }

    /**
     * `mutationRate = 0` ⇒ 无变异，子代 = 父母均值。
     *
     * 这是 `inherit` 唯一可精确断言的分支（变异项恒为 0），
     * 用来守住「均值混合」这个核心语义。
     */
    @Test
    fun inherit_withZeroMutation_isExactAverageOfParents() {
        val a = PetGenes(
            bodySize = 0.2f,
            hueShift = 0.4f,
            patternDensity = 0.6f,
            eyeSize = 0.8f,
            tailLength = 0.1f,
            liveliness = 0.9f
        )
        val b = PetGenes(
            bodySize = 0.8f,
            hueShift = 0.6f,
            patternDensity = 0.4f,
            eyeSize = 0.2f,
            tailLength = 0.9f,
            liveliness = 0.1f
        )

        val child = PetGenes.inherit(a, b, mutationRate = 0f)

        assertEquals(0.5f, child.bodySize, 0.0001f)
        assertEquals(0.5f, child.hueShift, 0.0001f)
        assertEquals(0.5f, child.patternDensity, 0.0001f)
        assertEquals(0.5f, child.eyeSize, 0.0001f)
        assertEquals(0.5f, child.tailLength, 0.0001f)
        assertEquals(0.5f, child.liveliness, 0.0001f)
    }

    /**
     * 极端父母（0 与 1）+ 满变异率（1.0），输出仍必须钳制在 [0, 1]。
     *
     * `mix` 的变异项是 `(randomUnit - 0.5) * mutationRate * 2`，
     * 当 `mutationRate = 1` 且父母取 0/1 极值时，最容易冲出边界，
     * 正好用来验证 `coerceIn(0f, 1f)` 这条防线。
     */
    @Test
    fun inherit_withFullMutationAndExtremeParents_staysInUnitRange() {
        val a = PetGenes(0f, 0f, 0f, 0f, 0f, 0f)
        val b = PetGenes(1f, 1f, 1f, 1f, 1f, 1f)

        repeat(200) {
            val child = PetGenes.inherit(a, b, mutationRate = 1f)
            assertInUnitRange(child.bodySize)
            assertInUnitRange(child.hueShift)
            assertInUnitRange(child.patternDensity)
            assertInUnitRange(child.eyeSize)
            assertInUnitRange(child.tailLength)
            assertInUnitRange(child.liveliness)
        }
    }

    /** 默认 `mutationRate`（0.15）路径也合法，输出在 [0,1]。 */
    @Test
    fun inherit_withDefaultMutationRate_staysInUnitRange() {
        val a = PetGenes(0.3f, 0.3f, 0.3f, 0.3f, 0.3f, 0.3f)
        val b = PetGenes(0.7f, 0.7f, 0.7f, 0.7f, 0.7f, 0.7f)
        repeat(100) {
            val child = PetGenes.inherit(a, b)
            assertInUnitRange(child.bodySize)
            assertInUnitRange(child.liveliness)
        }
    }

    private fun assertInUnitRange(value: Float) {
        assertTrue("value $value should be >= 0", value >= 0f)
        assertTrue("value $value should be <= 1", value <= 1f)
    }
}
