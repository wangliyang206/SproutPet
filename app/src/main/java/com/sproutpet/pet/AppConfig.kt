package com.sproutpet.pet

import android.content.Context
import timber.log.Timber

/**
 * 全局能力开关与第三方 SDK 初始化统一入口。
 *
 * 存在的意义：把所有"合规敏感"的初始化集中到一处，
 * 便于安全审计、隐私合规检查与上架审核自查。
 */
object AppConfig {

    /** 是否开启调试面板 */
    val DEBUG_PANEL_ENABLED: Boolean get() = BuildConfig.DEBUG

    /** 是否启用云端同步（Phase 6 开启） */
    val cloudEnabled: Boolean get() = BuildConfig.CLOUD_ENABLED

    /** 是否启用悬浮宠物功能 */
    var floatingPetEnabled: Boolean = true

    /** 悬浮宠物的渲染模式，可由远程配置下发 */
    var renderMode: String = "native"

    private var sdkInitialized = false

    /**
     * 初始化第三方 SDK。
     *
     * 调用前置条件：用户已明确同意隐私政策。
     * 若未同意，此处会直接 return 并打日志，防止违规采集。
     */
    fun initThirdPartySdk(context: Context) {
        if (!SproutPetApplication.privacyAgreed) {
            Timber.w("未同意隐私政策，跳过第三方 SDK 初始化")
            return
        }
        if (sdkInitialized) return

        // 在此初始化统计、推送等 SDK
        // 例如：UMConfigure.init(context, appKey, channel, deviceType)
        // 注意：隐私政策中必须逐条公示所集成的 SDK、收集数据类型与用途

        sdkInitialized = true
        Timber.i("第三方 SDK 初始化完成")
    }
}
