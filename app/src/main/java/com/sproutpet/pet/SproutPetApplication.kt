package com.sproutpet.pet

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.blankj.utilcode.util.Utils
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * 应用入口。
 *
 * 职责边界（严格遵守）：
 * - 初始化全局基础设施（日志、工具库、崩溃上报）
 * - 不做任何业务逻辑，不发起网络请求，不读数据库
 *
 * 隐私合规要求：用户同意隐私政策前，禁止初始化任何 SDK。
 * 因此 UMeng 等统计 SDK 的初始化必须放在 [onPrivacyAgreed] 中调用。
 */
@HiltAndroidApp
class SproutPetApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 1. 初始化工具库（无隐私采集行为，可提前）
        Utils.init(this)

        // 2. 日志：仅 debug 构建打印
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // 3. 深色模式跟随系统
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    /**
     * 用户同意隐私政策后调用。
     *
     * 所有涉及设备信息采集的第三方 SDK（统计、推送等）必须在此初始化。
     * 由 [AppConfig] 统一管理，便于审计。
     */
    fun onPrivacyAgreed() {
        Timber.i("隐私政策已同意，开始初始化第三方 SDK")
        AppConfig.initThirdPartySdk(this)
    }

    companion object {
        @Volatile
        lateinit var instance: SproutPetApplication
            private set

        /** 隐私政策是否已同意（供全局判断，避免提前采集） */
        @Volatile
        var privacyAgreed: Boolean = false
            internal set
    }
}
