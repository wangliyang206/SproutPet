package com.sproutpet.core.base

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import java.io.IOException

/**
 * Repository 基类：统一异常捕获与 Resource 包装。
 *
 * 子类实现 [execute] 执行业务逻辑，异常由基类统一转换为 [Resource.Error]。
 *
 * 使用示例：
 * ```
 * class PetRepositoryImpl(...) : BaseRepository() {
 *     fun getPet(id: Long): Flow<Resource<Pet>> = flowResource {
 *         dao.queryById(id) ?: throw IllegalStateException("宠物不存在")
 *     }
 * }
 * ```
 */
abstract class BaseRepository {

    /**
     * 执行一次可能失败的操作，包装为 Resource 流。
     */
    protected fun <T : Any> flowResource(block: suspend () -> T): Flow<Resource<T>> = flow<Resource<T>> {
        emit(Resource.Success(block()))
    }.onStart {
        emit(Resource.Loading<T>())
    }.catch { e ->
        emit(Resource.Error(e.toUserMessage(), e.toCode(), e))
    }

    /**
     * 同上，但启动时先发射缓存值，用于分页 / 刷新场景避免白屏。
     */
    protected fun <T : Any> flowResourceWithCache(
        cache: () -> T?,
        block: suspend () -> T
    ): Flow<Resource<T>> = flow<Resource<T>> {
        emit(Resource.Success(block()))
    }.onStart {
        emit(Resource.Loading(cache()))
    }.catch { e ->
        emit(Resource.Error(e.toUserMessage(), e.toCode(), e))
    }

    /** 安全执行，不抛异常，返回 Result 包装 */
    protected suspend fun <T> runCatchingResult(block: suspend () -> T): Result<T> =
        runCatching { block() }
}

/** 将异常转换为用户可读文案 */
fun Throwable.toUserMessage(): String = when (this) {
    is IOException -> "网络连接失败，请检查网络后重试"
    is SecurityException -> "权限不足，请检查应用权限设置"
    is IllegalArgumentException -> message ?: "参数错误"
    is IllegalStateException -> message ?: "操作失败，请稍后重试"
    else -> message ?: "未知错误，请稍后重试"
}

/** 将异常转换为错误码 */
fun Throwable.toCode(): Int = when (this) {
    is IOException -> 1001
    is SecurityException -> 1002
    is IllegalArgumentException -> 1003
    is IllegalStateException -> 1004
    else -> -1
}
