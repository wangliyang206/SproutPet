package com.sproutpet.core.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 用户表实体（docs/03 §3.2）。
 *
 * 本地单机阶段仅 1 条记录；保底计数 `pity_count` 属账号级而非宠物级，所以放这里。
 */
@Entity(
    tableName = "user",
    indices = [
        Index(value = ["remote_id"])
    ]
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "nickname")
    val nickname: String,

    @ColumnInfo(name = "avatar")
    val avatar: String? = null,

    @ColumnInfo(name = "gold", defaultValue = "0")
    val gold: Int = 0,

    @ColumnInfo(name = "gem", defaultValue = "0")
    val gem: Int = 0,

    @ColumnInfo(name = "level", defaultValue = "1")
    val level: Int = 1,

    @ColumnInfo(name = "exp", defaultValue = "0")
    val exp: Int = 0,

    /** 盲盒保底计数（连续未出高稀有次数） */
    @ColumnInfo(name = "pity_count", defaultValue = "0")
    val pityCount: Int = 0,

    @ColumnInfo(name = "total_draw_count", defaultValue = "0")
    val totalDrawCount: Int = 0,

    @ColumnInfo(name = "last_login_at")
    val lastLoginAt: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "remote_id")
    val remoteId: String? = null,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "is_synced", defaultValue = "0")
    val isSynced: Boolean = false
)
