package com.sproutpet.pet.pet.floating

import android.app.Service
import android.content.Intent
import android.os.IBinder
import timber.log.Timber

/**
 * 桌面宠物悬浮窗服务。
 *
 * ⚠️ 当前为骨架占位，Phase 3 完整实现。
 *
 * 职责边界（重要）：
 * - 本服务**只负责渲染与展示宠物、处理用户触摸互动**
 * - 严禁在此服务中读取第三方应用内容、模拟点击、操作其他 App
 * - 严禁申请无障碍服务权限（AccessibilityService）
 *
 * 合规要求：悬浮窗用途必须在隐私政策中明确说明为"桌面宠物展示"。
 */
class PetFloatingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Timber.d("PetFloatingService onCreate")
        // Phase 3 实现：
        // 1. 创建 WindowManager 悬浮视图（含 PetRenderer 渲染容器）
        // 2. 注册触摸监听（单击/双击/长按/拖拽）
        // 3. 订阅 PetRenderState，驱动宠物动画
        // 4. 启动前台通知（Android 8.0+ 要求）
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("PetFloatingService onDestroy")
        // Phase 3 实现：移除悬浮视图，释放 PetRenderer 资源
    }
}
