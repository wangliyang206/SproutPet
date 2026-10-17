package com.sproutpet.pet.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地账号与盲盒计数（Phase 1 口径：user 表属 Phase 2，先用 DataStore 落地）。
 *
 * - [ownerId]：本地单机主人 ID，首次启动生成后不变；Phase 7 接入后端后再映射真实账号
 * - [pityCount]：盲盒「连续未出史诗」计数（`BlindBoxEngine.PITY_THRESHOLD` 的输入）
 */
@Singleton
class OwnerLocalDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val keyOwnerId = longPreferencesKey("owner_id")
    private val keyPityCount = intPreferencesKey("blind_box_pity_count")

    /** 本地主人 ID（不存在则生成并落盘） */
    suspend fun getOrCreateOwnerId(): Long {
        val existing = dataStore.data.map { it[keyOwnerId] }.first()
        if (existing != null && existing > 0) return existing
        val created = UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE
        dataStore.edit { it[keyOwnerId] = created }
        return created
    }

    /** 当前保底计数（连续未出高稀有的次数） */
    fun observePityCount(): Flow<Int> = dataStore.data.map { it[keyPityCount] ?: 0 }

    suspend fun readPityCount(): Int = dataStore.data.map { it[keyPityCount] ?: 0 }.first()

    suspend fun writePityCount(count: Int) {
        dataStore.edit { it[keyPityCount] = count }
    }
}
