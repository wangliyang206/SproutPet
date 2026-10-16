package com.sproutpet.core.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 数据库迁移集合。
 *
 * ⚠️ 约定：
 * - 每次修改实体结构，必须同时**提升 [SproutPetDatabase.DB_VERSION]** 并在此补一条迁移
 * - 迁移只做**加列/加表/加索引**，不改已有列语义（避免线上数据不可逆）
 * - 每条迁移都应在 `MigrationTest` 中被覆盖（至少验证关键列存在）
 *
 * 为什么不长期依赖 `fallbackToDestructiveMigration`：那会**清空用户数据**。
 * 开发期可以兜底，但灵兽血统、族谱、世代这些字段一旦丢失无法重建。
 *
 * @see docs/03-数据库设计.md 第五节「迁移策略」
 */
object DbMigrations {

    /**
     * V1 → V2：为 `pet` 表补上「灵血浓度」列。
     *
     * ## 背景（这是一次真实的事故修复）
     *
     * ADR-023「灵血传承」在**领域层**早已实现（[com.sproutpet.core.domain.pet.GrowthRules.ExoticBloodRules]），
     * 但持久化层遗漏了该字段：`PetEntity` 没有对应列、`PetMapper` 也没有映射。
     *
     * 后果非常隐蔽 —— 单元测试全部通过（因为测试直接构造 [com.sproutpet.core.domain.pet.Pet]，
     * 不经过数据库），但**真实运行时**宠物一旦落库再读出来，灵血浓度就回落到默认值 `0`，
     * 「灵兽血脉可经营」的设计目标完全落空。
     *
     * ## 为什么默认值取 0
     *
     * 领域模型中 `Pet.exoticBlood` 默认为 `0`，所以对存量数据而言
     * 「没有这一列」与「这一列等于 0」语义完全等价 —— 迁移不会改变任何既有宠物的行为。
     */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `pet` ADD COLUMN `exotic_blood` INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    /**
     * V2 → V3：为 `pet` 表补上「完整神兽形态层」标记。
     *
     * ## 背景
     *
     * ADR-026 把神兽形态从"稀有度 ≥ 传说**自动**获得"改为
     * 「**灵兽 × 同虫**」独占（见 [com.sproutpet.core.domain.pet.DeityRules]）。
     * 这个判定需要"诞生时的配对类型"这个信息，**宠物自身推不出来**，
     * 因此必须在出生时结算并存下来。
     *
     * ## 为什么默认值取 0（false）

     *
     * 两点理由：
     * 1. 领域模型 `Pet.hasFullDeityForm` 默认 `false`，两者语义一致；
     * 2. 更重要的 —— V2 之前的形态是**由稀有度现算的**，并没有"个体级形态"概念。
     *    存量宠物升级后形态会按新规则重新着色：只有"灵兽 × 同虫"才有完整形态。
     *    这是**规则的统一**，不是数据丢失（形态本来就是可推导的外观，不是玩家资产）。
     */
    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `pet` ADD COLUMN `deity_form` INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    /**
     * V3 → V4：为 `pet` 表补上「性别」列。
     *
     * ## 背景（这是一次**文档反复依赖、代码却完全没有**的补漏）
     *
     * 性别从 v1.0 起就被写进了产品文档，而且是**配对生育的硬前置条件**：
     * - docs/00 §5.2「孵化时随机；配对需要**一公一母**」
     * - docs/06 §2.3「能否配对（只看成年/**公母**/异主）」
     * - docs/04 Phase 6 验收「满足条件才能配对（成年、**公母**、异主、冷却）」
     *
     * 但代码里搜 `gender` / `性别` **零命中**：`Pet` 无字段、`PetEntity` 无列、
     * `PairingRules` 无校验。也就是说配对前置条件实际只校验了"成年 + 亲密度"两条，
     * 族谱的性别展示位也没有数据来源。
     *
     * 这是典型的「**持久化层漏字段**」，与 V1→V2 的 `exotic_blood` 事故同源：
     * 领域层测试全绿（因为测试直接构造 `Pet`，不经过数据库），
     * 但真实运行时字段一落库就丢。
     *
     * ## 为什么存量数据要**随机**分配性别，而不是统一给 `0`
     *
     * 统一给 `0`（公）会带来一个结构性死局：老玩家的宠物**全部是公的**，
     * 从此再也配不上对 —— 而这是不可逆的（用户无法自行改性别，见 docs/00 §十三 Q5）。
     *
     * 随机分配则与"孵化时随机"的既定语义一致，且 1:1 分布能保证
     * 任意两只老宠之间有 50% 概率可以配对。
     *
     * `RANDOM() & 1` 而非 `ABS(RANDOM()) % 2`：后者在 `RANDOM()` 取到
     * 64 位最小值时会因 `ABS` 溢出而返回负数，`% 2` 得到 `-1`，写出非法性别值。
     */
    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `pet` ADD COLUMN `gender` INTEGER NOT NULL DEFAULT 0"
            )
            // 存量宠物按 1:1 随机分配，避免"老玩家全是公的"这种不可逆死局。
            db.execSQL("UPDATE `pet` SET `gender` = (RANDOM() & 1)")
        }
    }

    /** 全部迁移，按版本顺序交给 Room（见 [com.sproutpet.core.di.AppModule]） */
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}
