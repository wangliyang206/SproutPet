package com.sproutpet.pet.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 互动状态存储（冷却时间戳 + 每日次数 + 每日经验）。
 *
 * 账号级状态，Phase 2 用 DataStore 落地（互动冷却/日限的严格校验）。
 * 日期按本地自然日切分，跨日自动归零。
 */
@Singleton
class InteractionStateStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private fun cdKey(ownerId: Long, typeKey: String) =
        longPreferencesKey("cd_${typeKey}_$ownerId")

    private fun dailyKey(ownerId: Long, typeKey: String) =
        stringPreferencesKey("daily_${typeKey}_$ownerId")

    private fun expKey(ownerId: Long) =
        stringPreferencesKey("daily_exp_$ownerId")

    private fun todayStr(): String = LocalDate.now().toString()

    /** 某互动上次执行时间戳（0 = 从未执行） */
    suspend fun lastInteractionAt(ownerId: Long, typeKey: String): Long =
        dataStore.data.map { it[cdKey(ownerId, typeKey)] ?: 0L }.first()

    suspend fun setLastInteractionAt(ownerId: Long, typeKey: String, ts: Long) {
        dataStore.edit { it[cdKey(ownerId, typeKey)] = ts }
    }

    /** 某互动今日已执行次数（跨日自动归零） */
    suspend fun dailyCount(ownerId: Long, typeKey: String): Int =
        parseDateCount(dataStore.data.map { it[dailyKey(ownerId, typeKey)] }.first())

    suspend fun incrementDailyCount(ownerId: Long, typeKey: String) {
        val current = dailyCount(ownerId, typeKey)
        dataStore.edit { it[dailyKey(ownerId, typeKey)] = "${todayStr()}:${current + 1}" }
    }

    /** 今日已获得经验（跨日自动归零） */
    suspend fun dailyExpGained(ownerId: Long): Int =
        parseDateCount(dataStore.data.map { it[expKey(ownerId)] }.first())

    suspend fun addDailyExp(ownerId: Long, amount: Int) {
        val current = dailyExpGained(ownerId)
        dataStore.edit { it[expKey(ownerId)] = "${todayStr()}:${current + amount}" }
    }

    private fun parseDateCount(raw: String?): Int {
        if (raw.isNullOrEmpty()) return 0
        val parts = raw.split(":")
        if (parts.size != 2) return 0
        return if (parts[0] == todayStr()) parts[1].toIntOrNull() ?: 0 else 0
    }
}
