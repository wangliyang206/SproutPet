package com.sproutpet.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sproutpet.core.db.entity.InventoryItemDao
import com.sproutpet.core.db.entity.InventoryItemEntity
import com.sproutpet.core.db.entity.PairingDao
import com.sproutpet.core.db.entity.PairingEntity
import com.sproutpet.core.db.entity.PetEntity
import com.sproutpet.core.db.entity.PetDao
import com.sproutpet.core.db.entity.UserDao
import com.sproutpet.core.db.entity.UserEntity

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
        PetEntity::class,
        UserEntity::class,
        InventoryItemEntity::class,
        PairingEntity::class
    ],
    version = SproutPetDatabase.DB_VERSION,
    exportSchema = true
)
abstract class SproutPetDatabase : RoomDatabase() {

    abstract fun petDao(): PetDao

    abstract fun userDao(): UserDao

    abstract fun inventoryItemDao(): InventoryItemDao

    abstract fun pairingDao(): PairingDao

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
         * - V6：新增 `user` 表与 `inventory_item` 表（Phase 2 数据与成长系统，见 [DbMigrations.MIGRATION_5_6]）
         * - V7：`pet` 表补 `fusion_marks`（融合印记落库）+ 新增 `pairing` 表
         *   （docs/03 §3.6 冻结结构；见 [DbMigrations.MIGRATION_6_7]）
         * - V8：**回填 `home_slot`**（不改结构）—— 破壳路径此前从不写该列，存量宠全是 -1；
         *   按 id 升序前 6 只占 0..5、其余进院子（见 [DbMigrations.MIGRATION_7_8]）
         */
        const val DB_VERSION = 8
    }
}
