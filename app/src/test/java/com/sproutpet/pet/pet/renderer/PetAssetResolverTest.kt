package com.sproutpet.pet.pet.renderer

import androidx.test.core.app.ApplicationProvider
import com.sproutpet.core.domain.pet.PetSpecies
import com.sproutpet.core.domain.pet.PetStage
import com.sproutpet.core.domain.pet.SpeciesFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [PetAssetResolver] 四级回落测试（docs/15 §5.1 / 验收 B-3 / B-4）。
 *
 * 用 Robolectric 提供的 Context 真实解析 `res/drawable/` ——
 * 这意味着测试**直接反映资源目录的现状**：删掉一个类群资源，这里立刻变红。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PetAssetResolverTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun resName(id: Int): String = context.resources.getResourceEntryName(id)

    private val drawableSpecies = PetSpecies.entries.filter { it.isDrawable }

    // ── B-3：任何物种都不显示为萌芽鸡（除非它真是羽虫） ──

    @Test
    fun `L1 类群层覆盖全部可抽物种的全部可画阶段`() {
        for (species in drawableSpecies) {
            for (stage in listOf(PetStage.BABY, PetStage.JUVENILE, PetStage.ADULT)) {
                val id = PetAssetResolver.resolve(context, species, stage)
                assertTrue(
                    "${species.id} @ $stage 解析失败（四级回落全部未命中）",
                    id != 0
                )
                val name = resName(id)
                // L1 只有类群层 → 非羽虫物种必须落到自己类群的资源，绝不是鸡/萌芽鸡
                if (species.family != com.sproutpet.core.domain.pet.SpeciesFamily.YU) {
                    assertTrue(
                        "$species @ $stage 回落到了羽虫资源 $name —— 类群回落错位",
                        name.startsWith("pet_${familyId(species)}_")
                    )
                }
            }
        }
    }

    @Test
    fun `类群回落命中 L1 资源`() {
        // 蜗蜗是蠃虫，没有任何 pet_snail_* 精确资源 → 必须回落到 pet_luo_baby
        val id = PetAssetResolver.resolve(context, PetSpecies.SNAIL, PetStage.BABY)
        assertEquals("pet_luo_baby", resName(id))
    }

    // ── 阶段回落（docs/15 §四：长者复用成年，不出独立资源） ──

    @Test
    fun `长者与退休阶段回落到成年资源`() {
        val adult = PetAssetResolver.resolve(context, PetSpecies.SNAIL, PetStage.ADULT)
        val elder = PetAssetResolver.resolve(context, PetSpecies.SNAIL, PetStage.ELDER)
        val retired = PetAssetResolver.resolve(context, PetSpecies.SNAIL, PetStage.RETIRED)
        assertEquals(adult, elder)
        assertEquals(adult, retired)
    }

    // ── 蛋对全部物种通用（docs/15 §1.1） ──

    @Test
    fun `蛋阶段不区分物种`() {
        val a = PetAssetResolver.resolve(context, PetSpecies.SNAIL, PetStage.EGG)
        val b = PetAssetResolver.resolve(context, PetSpecies.SNAIL, PetStage.HATCHING)
        assertEquals("pet_egg_common", resName(a))
        assertNotEquals(a, b)
    }

    // ── R-2：RELEASE 兜底占位必须存在 ──

    @Test
    fun `兜底占位 pet_missing_asset 必须存在`() {
        assertTrue(
            "pet_missing_asset 是 RELEASE 的最后防线（docs/15 §5.1），不许删",
            context.resources.getIdentifier("pet_missing_asset", "drawable", context.packageName) != 0
        )
    }

    private fun familyId(species: PetSpecies): String = when (species.family) {
        com.sproutpet.core.domain.pet.SpeciesFamily.UNCATEGORIZED -> "uncat"
        else -> species.family.name.lowercase()
    }
}
