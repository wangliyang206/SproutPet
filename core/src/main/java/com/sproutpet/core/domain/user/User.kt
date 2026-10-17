package com.sproutpet.core.domain.user

/**
 * 用户领域模型（本地单机账号）。
 *
 * 与 [com.sproutpet.core.domain.pet.Pet] 同一条分层约定：
 * - Domain：承载业务字段与行为（金币增减、保底计数推进等）
 * - Entity：只负责持久化，受 Room 约束
 * - Mapper：两者互转
 *
 * ⚠️ 当前为**本地单机用户**（Phase 2），`id` 由本地生成；
 * Phase 6 接入后端后，`remoteId` 才会被填充。
 */
data class User(
    val id: Long,
    val nickname: String,
    val avatar: String? = null,
    val gold: Int = 0,
    val gem: Int = 0,
    val level: Int = 1,
    val exp: Int = 0,
    /** 盲盒保底计数（连续未出高稀有次数），见 [com.sproutpet.core.domain.incubation.BlindBoxEngine.PITY_THRESHOLD] */
    val pityCount: Int = 0,
    /** 累计抽蛋次数 */
    val totalDrawCount: Int = 0,
    /** 最后登录时间戳（离线收益计算） */
    val lastLoginAt: Long? = null,
    val createdAt: Long,
    val remoteId: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false
) {
    /** 加金币（不低于 0） */
    fun addGold(delta: Int): User = copy(gold = (gold + delta).coerceAtLeast(0))

    /** 加钻石（不低于 0） */
    fun addGem(delta: Int): User = copy(gem = (gem + delta).coerceAtLeast(0))

    /** 推进保底计数（抽出高稀有则归零，否则 +1） */
    fun withPity(afterHighRarity: Boolean): User =
        copy(pityCount = if (afterHighRarity) 0 else pityCount + 1)

    /** 累计抽蛋次数 +1 */
    fun incDrawCount(): User = copy(totalDrawCount = totalDrawCount + 1)
}
