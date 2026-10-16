package com.sproutpet.pet.debug

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.sproutpet.pet.R
import kotlin.math.abs

/**
 * 桌宠技术验证用的窗口服务（仅调试）。
 *
 * ## 为什么需要这个验证
 *
 * 产品最大的卖点是「宠物跑到桌面上，还能坐在其他 App 的图标上」。
 * 这依赖一个前提：**全屏透明悬浮窗能显示，同时不影响用户点桌面上的其他东西**。
 *
 * 但 Android 的窗口触摸是**窗口级**的，没有"部分区域可穿透"的 API：
 * - 加了 `FLAG_NOT_TOUCHABLE` → 整个窗口都不接收触摸，事件全穿透 → 宠物点不动
 * - 不加 → 整个窗口都接收触摸，事件被吃掉 → 桌面点不动
 *
 * 所以需要用「渲染窗口」和「触摸窗口」分离等办法绕开。这个服务把几种候选方案
 * 都实现出来，让开发者在真机上逐一验证，而不是靠纸上推演（ADR-014 需要实测支撑）。
 *
 * ## 四种模式对应的验证问题
 *
 * | 模式 | 验证什么 |
 * |------|---------|
 * | ① 全屏渲染 · 全穿透 | 宠物能不能显示？全屏透明窗会不会有黑底？ |
 * | ② 全屏可点 | 证明"全屏可点"会挡住整个桌面 |
 * | ③ 全屏渲染 + 触摸小窗 | 分离方案能否同时满足"可点"和"可穿透" |
 * | ④ 独立小窗 | 最简方案是否可行、窗口数增多时的表现 |
 *
 * ⚠️ 本类与正式业务无关。Phase 3 方案定案后，整个 debug 包可删除。
 */
class ProbeFloatingService : Service() {

    private lateinit var windowManager: WindowManager

    /** 已挂载的窗口根视图，用于统一清理 */
    private val attachedViews = mutableListOf<View>()

    /** 全屏窗口里的宠物节点（模式①②③使用） */
    private var fullScreenPetView: View? = null

    /** 独立触摸小窗（模式③④使用） */
    private var hitWindowView: View? = null
    private var hitWindowParams: WindowManager.LayoutParams? = null

    private var petSize = 0
    private var petX = 0
    private var petY = 0

    private var downRawX = 0f
    private var downRawY = 0f
    private var dragging = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Service 可能被系统单独拉起（START_STICKY），所以这里也要保证日志可用
        ProbeLog.init(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        petSize = (PROBE_PET_DP * resources.displayMetrics.density).toInt()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()

        val mode = ProbeMode.fromKey(intent?.getStringExtra(EXTRA_MODE))
        // 开一段新会话：文件里写入分隔头（含时间与设备），界面清屏只看本轮
        ProbeLog.beginSession(mode.label)

        detachAll()
        resetPosition()
        ProbeLog.log("SVC", "宠物尺寸 ${petSize}px，初始位置 ($petX, $petY)")

        when (mode) {
            ProbeMode.RENDER_ONLY -> attachFullScreenWindow(touchable = false)
            ProbeMode.FULL_TOUCH -> attachFullScreenWindow(touchable = true)
            ProbeMode.DUAL_LAYER -> {
                attachFullScreenWindow(touchable = false)
                attachHitWindow(renderPet = false)
            }
            ProbeMode.PET_WINDOW -> attachHitWindow(renderPet = true)
        }

        ProbeLog.log("TEST", mode.hint)
        return START_STICKY
    }

    override fun onDestroy() {
        detachAll()
        ProbeLog.log("SVC", "服务已销毁，窗口已清理")
        super.onDestroy()
    }

    // ═══════════════════ 窗口挂载 ═══════════════════

    /**
     * 全屏透明窗口。
     * @param touchable false 时加 FLAG_NOT_TOUCHABLE，事件全部穿透
     */
    private fun attachFullScreenWindow(touchable: Boolean) {
        val container = FrameLayout(this)
        val pet = createPetView()
        container.addView(
            pet,
            FrameLayout.LayoutParams(petSize, petSize).apply {
                leftMargin = petX
                topMargin = petY
            }
        )

        var windowFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!touchable) {
            windowFlags = windowFlags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayWindowType(),
            windowFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        val label = if (touchable) "全屏窗口(可触摸)" else "全屏窗口(穿透)"
        if (addWindowSafely(container, params, label)) {
            fullScreenPetView = pet
            if (touchable) {
                container.setOnTouchListener { _, event -> handleFullScreenTouch(event) }
                ProbeLog.log("TEST", "点【宠物圆】应该能拖动；点【宠物以外】的空白也会被这个窗口吃掉，到不了桌面")
            } else {
                ProbeLog.log("TEST", "现在连点【宠物圆】都应该穿透到桌面 —— 这是模式①的关键验证点")
            }
        }
    }

    /**
     * 宠物大小的独立窗口，专门接收触摸。
     * @param renderPet true 时窗口自己画宠物（模式④）；false 时窗口全透明，只负责接事件（模式③）
     */
    private fun attachHitWindow(renderPet: Boolean) {
        val view: View = if (renderPet) createPetView() else View(this)

        val params = WindowManager.LayoutParams(
            petSize,
            petSize,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = petX
            y = petY
        }

        val label = if (renderPet) "独立小窗(自渲染)" else "触摸小窗(透明)"
        if (addWindowSafely(view, params, label)) {
            hitWindowView = view
            hitWindowParams = params
            view.setOnTouchListener { _, event -> handleHitWindowTouch(event) }
            ProbeLog.log("TEST", "拖动宠物看是否跟手；然后点【宠物以外】的地方，应能点到桌面图标")
        }
    }

    // ═══════════════════ 触摸处理 ═══════════════════

    /**
     * 全屏窗口的触摸：需要自己做命中测试。
     * 注意 —— 无论命中与否都必须 return true，否则事件会落给下层，就达不到"证明会挡住桌面"的目的。
     */
    private fun handleFullScreenTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isHitPet(event.rawX, event.rawY)) {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    dragging = false
                    ProbeLog.log("TOUCH", "DOWN 命中宠物 @(${event.rawX.toInt()}, ${event.rawY.toInt()})")
                } else {
                    ProbeLog.log(
                        "TOUCH",
                        "DOWN 落在空白 @(${event.rawX.toInt()}, ${event.rawY.toInt()}) —— " +
                            "事件被窗口吃掉，桌面收不到（这正是模式②的代价）"
                    )
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (downRawX != 0f || downRawY != 0f) {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (abs(dx) >= DRAG_THRESHOLD || abs(dy) >= DRAG_THRESHOLD) {
                        if (!dragging) {
                            dragging = true
                            ProbeLog.log("TOUCH", "开始拖动")
                        }
                        petX += dx.toInt()
                        petY += dy.toInt()
                        applyPetPosition(updateFullScreen = true, updateHitWindow = false)
                        downRawX = event.rawX
                        downRawY = event.rawY
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    ProbeLog.log("TOUCH", "UP 拖动结束，宠物停在 ($petX, $petY)")
                } else if (isHitPet(event.rawX, event.rawY)) {
                    ProbeLog.log("TOUCH", "UP 点了宠物一下（本次点击未传给桌面）")
                }
                downRawX = 0f
                downRawY = 0f
                dragging = false
            }

            MotionEvent.ACTION_CANCEL -> {
                ProbeLog.log("TOUCH", "CANCEL —— 事件被系统抢走")
                downRawX = 0f
                downRawY = 0f
                dragging = false
            }
        }
        return true
    }

    /** 独立小窗的触摸：窗口本身就是宠物，不需要命中测试 */
    private fun handleHitWindowTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                dragging = false
                ProbeLog.log("TOUCH", "DOWN 命中宠物 @(${event.rawX.toInt()}, ${event.rawY.toInt()})")
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (abs(dx) >= DRAG_THRESHOLD || abs(dy) >= DRAG_THRESHOLD) {
                    if (!dragging) {
                        dragging = true
                        ProbeLog.log("TOUCH", "开始拖动")
                    }
                    petX += dx.toInt()
                    petY += dy.toInt()
                    applyPetPosition(updateFullScreen = true, updateHitWindow = true)
                    downRawX = event.rawX
                    downRawY = event.rawY
                }
            }

            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    ProbeLog.log("TOUCH", "UP 拖动结束，宠物停在 ($petX, $petY)")
                } else {
                    ProbeLog.log("TOUCH", "UP 点了宠物一下（本次点击未传给桌面）")
                }
                downRawX = 0f
                downRawY = 0f
                dragging = false
            }

            MotionEvent.ACTION_CANCEL -> {
                ProbeLog.log("TOUCH", "CANCEL —— 事件被系统抢走")
                downRawX = 0f
                downRawY = 0f
                dragging = false
            }
        }
        return true
    }

    // ═══════════════════ 坐标与视图 ═══════════════════

    private fun resetPosition() {
        val dm = resources.displayMetrics
        petX = (dm.widthPixels - petSize) / 2
        petY = (dm.heightPixels * 0.55f).toInt()
    }

    /** 把当前 petX/petY 应用到对应窗口上 */
    private fun applyPetPosition(updateFullScreen: Boolean, updateHitWindow: Boolean) {
        if (updateFullScreen) {
            (fullScreenPetView?.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                lp.leftMargin = petX
                lp.topMargin = petY
                fullScreenPetView?.layoutParams = lp
            }
        }
        if (updateHitWindow) {
            hitWindowParams?.let { p ->
                p.x = petX
                p.y = petY
                hitWindowView?.let { v ->
                    runCatching { windowManager.updateViewLayout(v, p) }
                        .onFailure { ProbeLog.log("ERROR", "移动小窗失败：${it.message}") }
                }
            }
        }
    }

    /** 命中测试：屏幕坐标是否落在宠物矩形内（含少量容差，方便手指按） */
    private fun isHitPet(rawX: Float, rawY: Float): Boolean {
        val tolerance = HIT_TOLERANCE_DP * resources.displayMetrics.density
        return rawX >= petX - tolerance && rawX <= petX + petSize + tolerance &&
            rawY >= petY - tolerance && rawY <= petY + petSize + tolerance
    }

    private fun createPetView(): TextView = TextView(this).apply {
        text = "宠"
        textSize = 26f
        gravity = Gravity.CENTER
        setTextColor(0xFFFFFFFF.toInt())
        setBackgroundResource(R.drawable.bg_probe_pet)
    }

    // ═══════════════════ 基础设施 ═══════════════════

    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun addWindowSafely(
        view: View,
        params: WindowManager.LayoutParams,
        label: String
    ): Boolean = try {
        windowManager.addView(view, params)
        attachedViews += view
        ProbeLog.log(
            "WINDOW",
            "OK   $label  type=${params.type} flags=0x${Integer.toHexString(params.flags)} " +
                "size=${params.width}x${params.height}"
        )
        true
    } catch (t: Throwable) {
        ProbeLog.log("ERROR", "FAIL $label  ${t.javaClass.simpleName}: ${t.message}")
        false
    }

    private fun detachAll() {
        attachedViews.forEach { view ->
            runCatching { windowManager.removeViewImmediate(view) }
        }
        attachedViews.clear()
        fullScreenPetView = null
        hitWindowView = null
        hitWindowParams = null
    }

    private fun goForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "桌宠验证", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }

        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, DesktopPetProbeActivity::class.java),
            pendingFlags
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val notification = builder
            .setContentTitle("桌宠技术验证运行中")
            .setContentText("点此返回验证面板")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val EXTRA_MODE = "probe_mode"

        private const val CHANNEL_ID = "sproutpet_pet_probe"
        private const val NOTIFICATION_ID = 9001
        private const val PROBE_PET_DP = 110
        private const val HIT_TOLERANCE_DP = 12
        private const val DRAG_THRESHOLD = 6f

        fun start(context: Context, mode: ProbeMode) {
            val intent = Intent(context, ProbeFloatingService::class.java)
                .putExtra(EXTRA_MODE, mode.key)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ProbeFloatingService::class.java))
        }
    }
}

/**
 * 四种候选方案。每种都在真机上验证一组具体问题。
 */
enum class ProbeMode(
    val key: String,
    val label: String,
    /** 启动后给测试者的操作提示 */
    val hint: String
) {
    RENDER_ONLY(
        "render_only",
        "① 全屏渲染 · 全穿透",
        "请做两件事：1) 看宠物圆是否正常显示、周围有没有不该有的黑框；2) 点宠物圆，再去点桌面图标，两者都应该有反应（说明宠物没吃掉事件）"
    ),

    FULL_TOUCH(
        "full_touch",
        "② 全屏可点 · 挡住桌面",
        "请做两件事：1) 拖动宠物圆，应能跟手；2) 点空白处，桌面图标应该点不动（用来证明全屏可点的代价）"
    ),

    DUAL_LAYER(
        "dual_layer",
        "③ 全屏渲染 + 触摸小窗",
        "请做三件事：1) 拖动宠物圆，是否跟手；2) 点宠物圆，是否有日志但桌面无反应；3) 点宠物以外的空白，桌面图标应能正常点开"
    ),

    PET_WINDOW(
        "pet_window",
        "④ 独立小窗（自渲染自接收）",
        "请做三件事：1) 拖动宠物圆，是否跟手；2) 点宠物圆，是否有日志；3) 点宠物以外的空白，桌面图标应能正常点开"
    );

    companion object {
        fun fromKey(key: String?): ProbeMode =
            entries.firstOrNull { it.key == key } ?: RENDER_ONLY
    }
}
