package com.sproutpet.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sproutpet.core.db.entity.PetEntity
import com.sproutpet.core.db.entity.PetDao

/**
 * 应用本地数据库。
 *
 * 当前版本仅包含宠物表，作为骨架验证。
 * 后续阶段按下表逐步增加实体（见 docs/03-数据库设计.md）：
 * - V2: 加入 UserEntity / InventoryEntity / ItemEntity
 * - V3: 加入 FamilyEntity / FamilyMemberEntity / PairingEntity（v2.0：原 MarriageEntity）
 * - V4: 加入 ActivityLogEntity / DailyStatEntity（用于防刷与统计）
 *
 * ⚠️ 迁移约定：
 * - 每新增实体必须提供 Migration，禁止长期使用 fallbackToDestructiveMigration
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
        const val DB_VERSION = 1
    }
}
