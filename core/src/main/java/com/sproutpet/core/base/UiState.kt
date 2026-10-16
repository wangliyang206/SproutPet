package com.sproutpet.core.base

/**
 * 页面状态标记接口。
 *
 * 每个页面的 UiState 都实现此接口，且必须是不可变 data class，
 * 便于 Compose/XML 差分刷新与状态回溯。
 */
interface UiState

/**
 * 一次性事件标记接口（Toast / Snackbar / 弹窗提示）。
 */
interface UiEvent

/**
 * 导航指令标记接口。
 */
interface NavEvent
