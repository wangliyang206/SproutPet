package com.sproutpet.core.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * 所有 ViewModel 的基类。
 *
 * 提供三种向 View 传递信息的方式：
 * - [uiState]：页面状态（Sticky，旋转屏幕后保留）
 * - [events]：一次性事件（如 Toast、导航，不 Sticky）
 * - [navEvents]：导航指令（Channel 实现，确保只消费一次）
 *
 * 使用示例：
 * ```
 * class HomeViewModel @Inject constructor(
 *     private val repo: PetRepository
 * ) : BaseViewModel<HomeUiState, HomeEvent>() {
 *     override fun createInitialState() = HomeUiState()
 * }
 * ```
 */
abstract class BaseViewModel<S : UiState, E : UiEvent> : ViewModel() {

    /** 页面状态 */
    private val _uiState: MutableStateFlow<S> by lazy { MutableStateFlow(createInitialState()) }
    val uiState: StateFlow<S> get() = _uiState.asStateFlow()

    /** 一次性事件（SharedFlow，无重放） */
    private val _events = MutableSharedFlow<E>(extraBufferCapacity = 8)
    val events: SharedFlow<E> get() = _events.asSharedFlow()

    /** 导航事件（Channel，保证只消费一次） */
    private val _navEvents = Channel<NavEvent>(Channel.BUFFERED)
    val navEvents: Flow<NavEvent> = _navEvents.receiveAsFlow()

    /** 子类实现：返回初始状态 */
    protected abstract fun createInitialState(): S

    /** 获取当前状态快照 */
    protected val currentState: S get() = _uiState.value

    /** 更新状态 */
    protected fun setState(reducer: S.() -> S) {
        _uiState.value = _uiState.value.reducer()
    }

    /** 发送一次性事件 */
    protected fun sendEvent(event: E) {
        viewModelScope.launch { _events.emit(event) }
    }

    /** 发送导航事件 */
    protected fun navigate(event: NavEvent) {
        viewModelScope.launch { _navEvents.send(event) }
    }
}
