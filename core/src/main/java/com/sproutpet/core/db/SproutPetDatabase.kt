package com.sproutpet.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sproutpet.core.db.entity.PetEntity
import com.sproutpet.core.db.entity.PetDao

/**
 * 应用本地数据库。
 *
 * 当前版本仅包含宠物表，作为骨架验证。
 * 后续阶段按下表逐步增加实体（见 docs/03-数据库设计.md）。
 *
 * ⚠️ 版本号**不按"计划中的 V2/V3/V4"走** —— 实际落地顺序由里程碑决定，
 * 计划里的 V2/V3/V4 编号早已被 `exotic_blood`、`deity_form`、`gender` 占用。
 * 增删实体时**只看 [DB_VERSION] 与 [DbMigrations.ALL] 的实际版本**，不要参考任何
 * "计划版本号"，否则会写出与真实版本对不上的迁移。
 *
 * ⚠️ 迁移约定：
 * - 每新增实体必须提供 Migration，禁止长期使用 fallbackToDestructiveMigration
 *   （现仅 DEBUG 构建挂载兜底，见 [com.sproutpet.core.di.AppModule]）
 * - 版本号在 [DB_VERSION] 统一维护
 */
@Database(
    entities = [
        PetEntity::class
    ],
    version = SproutPetDatabase.DB_VERSION,
    exportSchema = true
)
abstract class SproutPetDatabase : RoomDatabase() {

    abstract fun petDao(): PetDao

    companion object {
        /**
         * 当前数据库版本。
         *
         * 变更历史：
         * - V1：`pet` 表（骨架验证）
         * - V2：`pet` 表补 `exotic_blood`（ADR-023 灵血传承落库，见 [DbMigrations.MIGRATION_1_2]）
         * - V3：`pet` 表补 `deity_form`（ADR-026 神兽形态改为个体级、出生时结算，见 [DbMigrations.MIGRATION_2_3]）
         * - V4：`pet` 表补 `gender`（配对前置条件"一公一母"，见 [DbMigrations.MIGRATION_3_4]）
         * - V5：`pet` 表补 `is_main` / `home_slot` / `desktop_slot`（U-12 三层数量链条，见 [DbMigrations.MIGRATION_4_5]）
         */
        const val DB_VERSION = 5
    }
}
