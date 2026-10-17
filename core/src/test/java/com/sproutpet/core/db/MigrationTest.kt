package com.sproutpet.core.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 数据库迁移测试（Room `MigrationTestHelper` + Robolectric，纯 JVM 跑）。
 *
 * ## 为什么用 Robolectric 而不是 androidTest
 *
 * `MigrationTestHelper` 依赖 Android `Context`。传统做法是放 `androidTest`
 * （真机/模拟器），但 CI 跑起来慢且需要设备。Robolectric 提供 JVM 侧的
 * Android 运行时，配合 `testOptions.unitTests.isIncludeAndroidResources = true`
 * 就能让 `MigrationTestHelper` 在 `test` 目录里跑 —— 又快又不用设备。
 *
 * ## 基线从 V4 开始
 *
 * V1~V3 期间未配置 `room.schemaLocation`，`1.json`~`3.json` 不存在且无法补
 * （Room 用 `identityHash` 校验，手写 JSON 必然对不上）。
 * 所以迁移测试的可用基线是 V4、V5、V6。
 *
 * `MIGRATION_1_2` / `MIGRATION_2_3` / `MIGRATION_3_4` 仍**未被自动化覆盖**，
 * 它们的 SQL 很短（仅 `ALTER TABLE ADD COLUMN`），风险靠人工审查兜底。
 *
 * @see DbMigrations
 * @see SproutPetDatabase.DB_VERSION
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SproutPetDatabase::class.java.canonicalName
    )

    /**
     * V4 → V5：`pet` 表加 `is_main` / `home_slot` / `desktop_slot` 三列。
     *
     * `runMigrationsAndValidate` 会把迁移后的 schema 与导出的 `5.json` 逐列比对
     * （列名、类型、NOT NULL、默认值、索引、主键），任何不一致都直接失败。
     */
    @Test
    fun migrate4To5_schemaMatchesExportedV5() {
        val db = helper.createDatabase(TEST_DB, 4)
        db.close()
        helper.runMigrationsAndValidate(TEST_DB, 5, true, DbMigrations.MIGRATION_4_5)
    }

    /**
     * V5 → V6：新增 `user` 表与 `inventory_item` 表（含索引）。
     */
    @Test
    fun migrate5To6_schemaMatchesExportedV6() {
        val db = helper.createDatabase(TEST_DB, 5)
        db.close()
        helper.runMigrationsAndValidate(TEST_DB, 6, true, DbMigrations.MIGRATION_5_6)
    }

    /**
     * V4 → V6 链式迁移：模拟一个从 V4 直接升级到 V6 的老用户，
     * 中间所有迁移（4→5、5→6）都要被正确执行且最终 schema 与 `6.json` 一致。
     */
    @Test
    fun migrate4To6_chained_schemaMatchesExportedV6() {
        val db = helper.createDatabase(TEST_DB, 4)
        db.close()
        helper.runMigrationsAndValidate(
            TEST_DB, 6, true,
            DbMigrations.MIGRATION_4_5, DbMigrations.MIGRATION_5_6
        )
    }

    /**
     * V4 → V5 的**数据迁移**逻辑：
     * 每只主人的**最老一只宠**（`MIN(id)`）应被设为 `is_main = 1`、`home_slot = 0`；
     * 其余宠保持默认（`is_main = 0`、`home_slot = -1`、`desktop_slot = -1`）。
     *
     * 这是 `MIGRATION_4_5` 里那段 `UPDATE ... WHERE id IN (SELECT MIN(id) ...)` 的
     * 核心语义，必须有测试守住 —— 否则一旦 SQL 写错，所有老用户的主宠都会丢。
     */
    @Test
    fun migrate4To5_oldestPetPerOwnerBecomesMainAtHome0() {
        val db = helper.createDatabase(TEST_DB, 4)
        // 同一主人（owner_id = 1）插入两只宠，id 由 AUTOINCREMENT 分配
        insertPetV4(db, ownerId = 1L, nickname = "older")
        insertPetV4(db, ownerId = 1L, nickname = "younger")
        // 另一个主人的一只宠，验证「每只主人各自取最老」
        insertPetV4(db, ownerId = 2L, nickname = "other")
        db.close()

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB, 5, true, DbMigrations.MIGRATION_4_5
        )

        migrated.query(
            "SELECT owner_id, is_main, home_slot, desktop_slot FROM pet ORDER BY owner_id, id"
        ).use { cursor ->
            // owner 1: older (主宠, 家0) → younger (待命)
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals(0, cursor.getInt(2))
            assertEquals(-1, cursor.getInt(3))

            assertTrue(cursor.moveToNext())
            assertEquals(1L, cursor.getLong(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals(-1, cursor.getInt(2))
            assertEquals(-1, cursor.getInt(3))

            // owner 2: 唯一一只 → 主宠, 家0
            assertTrue(cursor.moveToNext())
            assertEquals(2L, cursor.getLong(0))
            assertEquals(1, cursor.getInt(1))
            assertEquals(0, cursor.getInt(2))
            assertEquals(-1, cursor.getInt(3))
        }
        migrated.close()
    }

    /**
     * 按 V4 schema 的 `pet` 表结构插入一行（只指定 NOT NULL 且无默认值的列；
     * 带默认值的 `gender`/`is_retired`/`exotic_blood`/`deity_form` 与可空列省略）。
     */
    private fun insertPetV4(db: androidx.sqlite.db.SupportSQLiteDatabase, ownerId: Long, nickname: String) {
        db.execSQL(
            """
            INSERT INTO pet (
                owner_id, nickname, species_id, rarity, stage,
                level, exp,
                attr_stamina, attr_strength, attr_agility, attr_wisdom, attr_charm, attr_intimacy,
                power,
                gene_body_size, gene_hue_shift, gene_pattern_density, gene_eye_size, gene_tail_length, gene_liveliness,
                born_at, hatch_at, is_hatched,
                generation, batch_no, updated_at, is_synced
            ) VALUES (
                $ownerId, '$nickname', 'turtle', 1, 3,
                1, 0,
                10, 10, 10, 10, 10, 10,
                100,
                1.0, 0.0, 0.5, 1.0, 1.0, 1.0,
                1000, 2000, 1,
                1, 'batch-$ownerId-$nickname', 1000, 0
            )
            """.trimIndent()
        )
    }

    companion object {
        private const val TEST_DB = "migration-test"
    }
}
