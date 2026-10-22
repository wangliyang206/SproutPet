package com.sproutpet.pet.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
    private val keyFreeTicketDay = stringPreferencesKey("free_ticket_day")
    private val keyPrivacyAgreed = booleanPreferencesKey("privacy_agreed")
    private val keyTrayCollapsed = booleanPreferencesKey("tray_collapsed")
    private val keyFirstHatchReward = booleanPreferencesKey("first_hatch_reward_claimed")
    private val keyStarterEggPlaced = booleanPreferencesKey("starter_egg_placed")

    /** ⚠️ 试用阶段资源包（2026-10-22 用户拍板）—— **上线前必须回收**（docs/09 `T-01`） */
    private val keyTrialBonusGranted = booleanPreferencesKey("trial_bonus_granted")

    /**
     * 隐私政策同意态（docs/00 §八 红线 5 · design-v2 19 屏 · `16 §三 K-4`）。
     *
     * - 未同意前：`MainActivity` 只展示全屏隐私门，不加载场景；
     *   `SproutPetApplication.privacyAgreed` 保持 false，任何采集类 SDK 不得初始化。
     * - 同意后：写入本键，并触发 `SproutPetApplication.onPrivacyAgreed()`。
     */
    fun observePrivacyAgreed(): Flow<Boolean> = dataStore.data.map { it[keyPrivacyAgreed] ?: false }

    suspend fun readPrivacyAgreed(): Boolean =
        dataStore.data.map { it[keyPrivacyAgreed] ?: false }.first()

    /** 同意隐私政策（幂等；只许从 false → true，撤回同意不在 App 内提供 —— 按设计走卸载） */
    suspend fun setPrivacyAgreed() {
        dataStore.edit { it[keyPrivacyAgreed] = true }
    }

    /** 互动托盘是否收起（真机反馈：底部三卡挡场景 → 可折成右下小药丸，选择要记住） */
    fun observeTrayCollapsed(): Flow<Boolean> = dataStore.data.map { it[keyTrayCollapsed] ?: false }

    suspend fun setTrayCollapsed(collapsed: Boolean) {
        dataStore.edit { it[keyTrayCollapsed] = collapsed }
    }

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

    /**
     * 领取「每日免费盲盒门票」的幂等标记（docs/00 §2.1：每日免费 2 抽）。
     *
     * @param today 本地日期字符串（`yyyy-MM-dd`）
     * @return true = 今天还没领过，调用方应发放；false = 已领过
     *
     * ⚠️ 产品原文是「签到 1 + 任务全清 1」，但签到与任务**界面还不存在**（docs/16 §二 07 屏），
     * 所以先把「每天 2 张」这条**规则**落地，等签到/任务做出来再把发放源换成真实领取。
     * 这样门票经济不会因为 UI 缺位而彻底没有入口。
     */
    suspend fun claimDailyFreeTickets(today: String): Boolean {
        val last = dataStore.data.map { it[keyFreeTicketDay] }.first()
        if (last == today) return false
        dataStore.edit { it[keyFreeTicketDay] = today }
        return true
    }

    /**
     * 领取「首只孵化成功」一次性十连奖励（`docs/00 §2.1` · `docs/14 §2.4`）。
     *
     * @return true = 本次是第一次领取，调用方应发放 10 抽；false = 已领过
     *
     * ⚠️ **读-判-写必须在同一次 `edit` 里**：`edit` 的 lambda 在事务内只跑一次，
     * 所以两次并发调用不可能同时拿到 true（先读再 `edit` 的写法会重复发奖）。
     * 这条奖励是**一次性**的，重复发的后果是直接送出一个十连 —— 不能靠调用方自觉。
     */
    suspend fun claimFirstHatchReward(): Boolean {
        var granted = false
        dataStore.edit { prefs ->
            if (prefs[keyFirstHatchReward] != true) {
                prefs[keyFirstHatchReward] = true
                granted = true
            }
        }
        return granted
    }

    /**
     * 领取**试用阶段资源包**（2026-10-22 用户拍板，仅测试期）：
     * 盲盒门票 ×100 · 金币 ×100,000 · 其余每种道具 ×1000。
     *
     * @return true = 本次是第一次领取，调用方应发放；false = 已发过
     *
     * ⚠️ 与 [claimFirstHatchReward] 同一条纪律：**读-判-写必须在同一次 `edit` 里**。
     * ⚠️⚠️ **上线前必须移除调用点并回收本键** —— 这是给试用体验灌水的临时口径，
     * 记录在 `docs/09-待决问题总表.md` 的 `T-01`，不许悄悄转正。
     */
    suspend fun claimTrialBonus(): Boolean {
        var granted = false
        dataStore.edit { prefs ->
            if (prefs[keyTrialBonusGranted] != true) {
                prefs[keyTrialBonusGranted] = true
                granted = true
            }
        }
        return granted
    }

    /**
     * 「首蛋已自动送入场景」账本（`docs/14 §1.5` · 2026-10-22 用户拍板）。
     *
     * 首日的第一颗蛋由程序直接落下（用户不需要点任何东西），这条标记回答两个问题：
     * - 送过了吗？（进程重启、冷启动补发都不能再送第二颗）
     * - 送成功了吗？（**落库成功才写**，抽的过程断掉就下次重试，不会出现"票扣了蛋没有"）
     *
     * ⚠️ 与 [claimFirstHatchReward] 的区别：那条是**奖励**（先占位再发，绝不能重复发）；
     * 这条是**开场**（先发再记账，最坏情况是重试一次）—— 顺序不同是因为失败代价不同。
     */
    suspend fun isStarterEggPlaced(): Boolean =
        dataStore.data.map { it[keyStarterEggPlaced] ?: false }.first()

    suspend fun markStarterEggPlaced() {
        dataStore.edit { it[keyStarterEggPlaced] = true }
    }
}
