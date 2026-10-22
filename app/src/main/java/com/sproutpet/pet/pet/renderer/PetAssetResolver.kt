package com.sproutpet.pet.pet.renderer

import android.content.Context
import com.sproutpet.core.domain.pet.PetSpecies
import com.sproutpet.core.domain.pet.PetStage
import com.sproutpet.pet.BuildConfig
import com.sproutpet.pet.R
import java.util.concurrent.ConcurrentHashMap

/**
 * 宠物形象资源解析器 —— 取图的**唯一执法点**（docs/15 §5.1）。
 *
 * ## 四级回落（命中即返回）
 *
 * ```
 * 1. pet_{speciesId}_{stage}          精确匹配        → 命中即用
 * 2. pet_{speciesId}_{fallbackStage}  阶段回落（elder→adult）
 * 3. pet_{familyId}_{stage}           ★ 类群回落     → L1 类群层
 * 4. pet_{familyId}_{fallbackStage}   类群落回阶段
 *    ↓ 全部未命中
 *    DEBUG → 抛 [MissingPetAssetException] / RELEASE → pet_missing_asset 红叉占位
 * ```
 *
 * ## 三条硬规则（docs/15 §5.1）
 *
 * - **R-1 禁止回落到 `pet_sprout_chicken_baby`**：那是「全是鸡」缺陷的直接原因。
 *   兜底必须按**类群**回落，不能按"第一个做好的物种"回落。
 * - **R-2 DEBUG 下资源缺失必须可见**：抛异常，静默 = 缺陷不可见 = 缺陷长期存在。
 * - **R-3 取图必须传 `species`**：调用方（`FramePetRenderer`）不得只按 `stage` 取图。
 *
 * 资源（v1.3 起）为 **AI 生成的 3D 渲染 PNG**：6 类群 × 3 可画阶段 = **18 个**
 * （`res/drawable-nodpi/pet_{family}_{stage}.png`，512×512 透明底），由
 * `tools/prepare_pet_png.py` 从生成原图抠底后落地（docs/15 §十一）。
 * `ELDER` / `RETIRED` 复用 `adult`（docs/15 §四），不出独立资源。
 * ⚠️ 本对象是**唯一取图入口**：任何 `R.drawable.pet_*` 字面量都算缺陷
 * （曾经 `NamingDialogFragment` 硬编码小鸡，导致起名卡永远显示同一只）。
 */
object PetAssetResolver {

    /** 资源缺失占位（红叉 + 提示），只允许出现在 RELEASE 兜底 */
    private const val MISSING = "pet_missing_asset"

    /** SpeciesFamily → 资源名段（与生成脚本的输出文件名对齐） */
    private fun familyIdOf(species: PetSpecies): String = when (species.family) {
        com.sproutpet.core.domain.pet.SpeciesFamily.UNCATEGORIZED -> "uncat"
        else -> species.family.name.lowercase()
    }

    /** 阶段回落链：长者沿用成年（docs/15 §四「换色即可，不单独画」） */
    private fun fallbackStage(stage: PetStage): PetStage? = when (stage) {
        PetStage.ELDER, PetStage.RETIRED -> PetStage.ADULT
        PetStage.HATCHING -> PetStage.EGG
        else -> null
    }

    private fun stageId(stage: PetStage): String = when (stage) {
        PetStage.EGG, PetStage.HATCHING -> "egg"
        PetStage.BABY -> "baby"
        PetStage.JUVENILE -> "juvenile"
        PetStage.ADULT -> "adult"
        PetStage.ELDER, PetStage.RETIRED -> "adult"
    }

    /** (speciesId, familyId) → {资源名: resId} 缓存；包名资源在进程内不变 */
    private val cache = ConcurrentHashMap<String, Int>()

    /**
     * 按 docs/15 §5.1 的四级回落解析 drawable 资源。
     *
     * @throws MissingPetAssetException DEBUG 下四级全部未命中时
     */
    fun resolve(context: Context, species: PetSpecies, stage: PetStage): Int {
        // 蛋对全部物种通用（蛋里是什么虫，破壳时才知道）
        if (stage == PetStage.EGG || stage == PetStage.HATCHING) {
            return if (stage == PetStage.HATCHING) R.drawable.pet_egg_cracked else R.drawable.pet_egg_common
        }

        val speciesId = species.id
        val familyId = familyIdOf(species)
        val primary = stageId(stage)
        val fallback = fallbackStage(stage)?.let { stageId(it) }

        val candidates = buildList {
            add("pet_${speciesId}_$primary")
            if (fallback != null) add("pet_${speciesId}_$fallback")
            add("pet_${familyId}_$primary")
            if (fallback != null) add("pet_${familyId}_$fallback")
        }

        for (name in candidates) {
            val id = drawableId(context, name)
            if (id != 0) return id
        }

        // 四级全部未命中 —— 按 R-2：DEBUG 必须可见，RELEASE 用红叉占位
        val missing = drawableId(context, MISSING)
        if (BuildConfig.DEBUG) {
            throw MissingPetAssetException(species, stage, candidates)
        }
        check(missing != 0) {
            "RELEASE 缺少兜底占位 pet_missing_asset —— 请恢复该资源（它本身就是最后防线）"
        }
        return missing
    }

    private fun drawableId(context: Context, name: String): Int =
        cache.getOrPut(name) {
            context.resources.getIdentifier(name, "drawable", context.packageName)
        }
}

/** DEBUG 下资源四级全部未命中时抛出（docs/15 §5.1 R-2：缺失必须可见） */
class MissingPetAssetException(
    species: PetSpecies,
    stage: PetStage,
    tried: List<String>
) : IllegalStateException(
    "宠物形象资源缺失：${species.displayName}(${species.id}) @ $stage；" +
        "已按四级回落尝试 ${tried.joinToString()}。" +
        "请补资源或运行 tools/prepare_pet_png.py 重新落地 L1 类群层（docs/15 §十一）。"
)
