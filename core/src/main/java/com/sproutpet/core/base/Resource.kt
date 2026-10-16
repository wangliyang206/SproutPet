package com.sproutpet.core.base

/**
 * 统一资源封装，用于区分 加载中 / 成功 / 失败 三态。
 *
 * Repository 层统一返回 `Flow<Resource<T>>` 或 `Resource<T>`，
 * ViewModel 负责转换为 UiState，避免异常穿透到 UI。
 */
sealed interface Resource<out T> {

    /** 加载中，data 为上一次的缓存值（可选） */
    data class Loading<T>(val data: T? = null) : Resource<T>

    /** 成功 */
    data class Success<T>(val data: T) : Resource<T>

    /** 失败，携带用户可读消息与原始异常 */
    data class Error(
        val message: String,
        val code: Int = -1,
        val cause: Throwable? = null
    ) : Resource<Nothing>
}

/** 取成功值，否则返回 null */
fun <T> Resource<T>.dataOrNull(): T? = (this as? Resource.Success)?.data

/** 是否成功 */
val Resource<*>.isSuccess: Boolean get() = this is Resource.Success

/** 是否加载中 */
val Resource<*>.isLoading: Boolean get() = this is Resource.Loading

/**
 * 将 Resource 转换为界面三态，供 XML 布局切换 visibility 使用。
 */
enum class UiStatus { LOADING, SUCCESS, ERROR, EMPTY }

fun <T> Resource<T>.toStatus(isEmpty: Boolean = false): UiStatus = when {
    this is Resource.Loading -> UiStatus.LOADING
    this is Resource.Error -> UiStatus.ERROR
    isEmpty -> UiStatus.EMPTY
    else -> UiStatus.SUCCESS
}
