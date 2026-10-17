package com.sproutpet.core.db.mapper

import com.sproutpet.core.db.entity.UserEntity
import com.sproutpet.core.domain.user.User

/**
 * User Entity ↔ Domain 转换器。
 */
object UserMapper {

    fun toDomain(entity: UserEntity): User = User(
        id = entity.id,
        nickname = entity.nickname,
        avatar = entity.avatar,
        gold = entity.gold,
        gem = entity.gem,
        level = entity.level,
        exp = entity.exp,
        pityCount = entity.pityCount,
        totalDrawCount = entity.totalDrawCount,
        lastLoginAt = entity.lastLoginAt,
        createdAt = entity.createdAt,
        remoteId = entity.remoteId,
        updatedAt = entity.updatedAt,
        isSynced = entity.isSynced
    )

    fun toEntity(user: User, existing: UserEntity? = null): UserEntity = UserEntity(
        id = user.id,
        nickname = user.nickname,
        avatar = user.avatar,
        gold = user.gold,
        gem = user.gem,
        level = user.level,
        exp = user.exp,
        pityCount = user.pityCount,
        totalDrawCount = user.totalDrawCount,
        lastLoginAt = user.lastLoginAt,
        createdAt = user.createdAt,
        remoteId = existing?.remoteId ?: user.remoteId,
        updatedAt = System.currentTimeMillis(),
        isSynced = false
    )
}
