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
     * V6 → V7：`pet` 表补 `fusion_marks` + 新增 `pairing` 表（docs/03 §3.6 冻结结构）。
     *
     * `runMigrationsAndValidate` 会把迁移后的 schema 与导出的 `7.json` 逐列比对 ——
     * 特别是 `pairing` 的 **UNIQUE(pet_id_a, pet_id_b)** 索引：迁移 SQL 里索引名
     * 与 Room 期望名（`index_pairing_pet_a_pet_b`）不一致会在这里直接爆掉。
     */
    @Test
    fun migrate6To7_schemaMatchesExportedV7() {
        val db = helper.createDatabase(TEST_DB, 6)
        db.close()
        helper.runMigrationsAndValidate(TEST_DB, 7, true, DbMigrations.MIGRATION_6_7)
    }

    /**
     * V6 → V7 的**数据语义**：
     * 1. 存量宠物 `fusion_marks` 默认 `0`（= 从未融合过，与领域默认值等价）；
     * 2. `pairing` 的唯一约束生效 —— 同一对宠物（不论 id 先后顺序的两倍）只允许一条记录。
     */
    @Test
    fun migrate6To7_fusionMarksDefaultsToZero_andPairingUniqueHolds() {
        val db = helper.createDatabase(TEST_DB, 6)
        insertPetV4(db, ownerId = 1L, nickname = "a")
        insertPetV4(db, ownerId = 1L, nickname = "b")
        db.close()

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB, 7, true, DbMigrations.MIGRATION_6_7
        )

        // ① fusion_marks 默认 0
        migrated.query("SELECT fusion_marks FROM pet").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
            assertTrue(cursor.moveToNext())
            assertEquals(0, cursor.getInt(0))
        }

        // ② pairing 唯一约束：同对（pet 1 × pet 2）插入第二条必须抛异常
        migrated.execSQL(
            """
            INSERT INTO pairing (
                pet_id_a, pet_id_b, owner_id_a, owner_id_b,
                paired_at, updated_at
            ) VALUES (1, 2, 1, 1, 1000, 1000)
            """.trimIndent()
        )
        var threw = false
        try {
            migrated.execSQL(
                """
                INSERT INTO pairing (
                    pet_id_a, pet_id_b, owner_id_a, owner_id_b,
                    paired_at, updated_at
                ) VALUES (1, 2, 1, 1, 2000, 2000)
                """.trimIndent()
            )
        } catch (expected: android.database.sqlite.SQLiteConstraintException) {
            threw = true
        }
        assertTrue("UNIQUE(pet_id_a, pet_id_b) 应拦截重复配对", threw)
        migrated.close()
    }

    /**
     * V5 → V7 链式迁移：模拟从 V5 直接升到 V7 的老用户，
     * 中间所有迁移（5→6、6→7）都要被正确执行且最终 schema 与 `7.json` 一致。
     */
    @Test
    fun migrate5To7_chained_schemaMatchesExportedV7() {
        val db = helper.createDatabase(TEST_DB, 5)
        db.close()
        helper.runMigrationsAndValidate(
            TEST_DB, 7, true,
            DbMigrations.MIGRATION_5_6, DbMigrations.MIGRATION_6_7
        )
    }

    /**
     * 按 V4 schema 的 `pet` 表结构插入一行（只指定 NOT NULL 且无默认值的列；
     * 带默认值的 `gender`/`is_retired`/`exotic_blood`/`deity_form` 与可空列省略）。
     */
    /**
     * V7 → V8：**纯数据回填**（不改结构）—— 每个 owner 的已破壳未退休宠物按 `id` 升序，
     * 前 6 只占 `home_slot = 0..5`，其余留 `-1`（进院子）。
     *
     * 为什么必须测：破壳路径此前**从不写 `home_slot`**，存量库里除 V5 指定的那一只外
     * 全是 `-1`。若这条迁移漏跑或写错，用户升级后会看到「家里只剩 1 只」——
     * 而崩溃修复（`SceneRoster.onStage` 只渲染 `atHome`）会让这件事立刻可见。
     *
     * 顺带验证：**每个 owner 独立算**，不会把 A 家的 6 个名额算给 B 家。
     */
    @Test
    fun migrate7To8_backfillsHomeSlotsSixPerOwnerOldestFirst() {
        val db = helper.createDatabase(TEST_DB, 7)
        // owner 1：9 只已破壳 → 前 6 只进家，后 3 只进院子
        repeat(9) { insertPetV4(db, ownerId = 1L, nickname = "p$it") }
        // owner 2：2 只 → 都进家（各自独立的名额）
        insertPetV4(db, ownerId = 2L, nickname = "q0")
        insertPetV4(db, ownerId = 2L, nickname = "q1")
        db.close()

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB, 8, true, DbMigrations.MIGRATION_7_8
        )

        migrated.query("SELECT owner_id, home_slot FROM pet ORDER BY owner_id, id").use { c ->
            val owner1 = mutableListOf<Int>()
            val owner2 = mutableListOf<Int>()
            while (c.moveToNext()) {
                if (c.getLong(0) == 1L) owner1 += c.getInt(1) else owner2 += c.getInt(1)
            }
            assertEquals(listOf(0, 1, 2, 3, 4, 5, -1, -1, -1), owner1)
            assertEquals(listOf(0, 1), owner2)
        }
        migrated.close()
    }

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
