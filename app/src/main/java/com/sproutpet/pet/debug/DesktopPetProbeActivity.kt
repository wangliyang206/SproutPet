package com.sproutpet.pet.debug

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import com.sproutpet.core.base.BaseActivity
import com.sproutpet.pet.databinding.ActivityDesktopPetProbeBinding

/**
 * 桌宠技术验证面板（仅调试）。
 *
 * 用法：装上 debug 包 → 授权悬浮窗 → 依次点四个模式的按钮 →
 * 每次按提示操作，然后看底部日志判断事件到底去哪了。
 *
 * 结论要写进 `docs/01-技术架构设计.md` 的 ADR-014（当前该决策仍是纸面推演）。
 *
 * ⚠️ 与正式业务无关，Phase 3 定案后可删除。
 */
class DesktopPetProbeActivity : BaseActivity<ActivityDesktopPetProbeBinding>() {

    private val overlaySettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshPermissionState()
    }

    override fun inflateBinding(inflater: LayoutInflater): ActivityDesktopPetProbeBinding =
        ActivityDesktopPetProbeBinding.inflate(inflater)

    override fun initView() {
        // 必须在任何 log 之前初始化（决定日志文件能否落盘）
        ProbeLog.init(applicationContext)

        binding.btnModeRenderOnly.setOnClickListener { launchMode(ProbeMode.RENDER_ONLY) }
        binding.btnModeFullTouch.setOnClickListener { launchMode(ProbeMode.FULL_TOUCH) }
        binding.btnModeDualLayer.setOnClickListener { launchMode(ProbeMode.DUAL_LAYER) }
        binding.btnModePetWindow.setOnClickListener { launchMode(ProbeMode.PET_WINDOW) }

        binding.btnStop.setOnClickListener {
            ProbeFloatingService.stop(this)
            ProbeLog.log("UI", "已请求停止服务并清除窗口")
        }

        binding.btnGrantPermission.setOnClickListener {
            if (canDrawOverlays()) {
                toast("悬浮窗权限已开启")
                refreshPermissionState()
            } else {
                openOverlaySettings()
            }
        }

        binding.btnClearLog.setOnClickListener { ProbeLog.clear() }

        // ── 人工观察：有些结论代码测不出来（事件根本没经过我们的进程），
        //    点一下即写进日志文件，测试者不需要口述或截图。
        binding.btnObserveDragOk.setOnClickListener {
            ProbeLog.observe("【人工】拖动宠物 → 跟手 ✓")
        }
        binding.btnObserveDragFail.setOnClickListener {
            ProbeLog.observe("【人工】拖动宠物 → 不动 / 无反应 ✗")
        }
        binding.btnObserveTapThroughOk.setOnClickListener {
            ProbeLog.observe("【人工】点空白处 → 桌面图标被点开了，穿透成功 ✓")
        }
        binding.btnObserveTapThroughFail.setOnClickListener {
            ProbeLog.observe("【人工】点空白处 → 桌面图标没反应，被挡住了 ✗")
        }

        binding.btnShareLog.setOnClickListener { shareLog() }
    }

    override fun initData() {
        ProbeLog.lines.collectInLifecycle { lines ->
            binding.logText.text = if (lines.isEmpty()) "（暂无日志）" else lines.joinToString("\n")
            binding.logScroll.post { binding.logScroll.fullScroll(View.FOCUS_DOWN) }
        }
        refreshLogPath()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
        refreshLogPath()
    }

    /** 把日志文件真实路径显示出来，方便用户在没有分享目标时手动取文件 */
    private fun refreshLogPath() {
        binding.logPath.text = ProbeLog.logFilePath()
            ?.let { "日志文件（可分享或直接取用）：\n$it" }
            ?: "日志文件：不可用（外部存储未挂载，请检查存储状态）"
    }

    private fun shareLog() {
        val intent = ProbeLog.shareIntent()
        if (intent == null) {
            toast("还没有日志可分享，请先跑一个模式再试")
            return
        }
        runCatching { startActivity(Intent.createChooser(intent, "分享验证日志")) }
            .onFailure {
                toast("没有可用的分享应用。日志路径：${ProbeLog.logFilePath()}")
            }
    }

    private fun launchMode(mode: ProbeMode) {
        if (!canDrawOverlays()) {
            toast("需要先开启悬浮窗权限")
            openOverlaySettings()
            return
        }
        ProbeLog.log("UI", "启动 ${mode.label}")
        ProbeFloatingService.start(this, mode)
        // 立刻退回桌面，省得用户自己切 —— 顺便让"点桌面图标"这一步可以直接做
        ProbeLog.log("UI", "已退回桌面。测完从通知栏点回来即可")
        moveTaskToBack(true)
    }

    private fun refreshPermissionState() {
        val granted = canDrawOverlays()
        binding.permissionStatus.text = if (granted) {
            "悬浮窗权限：已开启\n可以开始验证。启动任一模式后会自动退回桌面，方便直接观察效果。"
        } else {
            "悬浮窗权限：未开启\n需要先授权，否则宠物不会出现在桌面上。"
        }
        binding.btnGrantPermission.text =
            if (granted) "悬浮窗权限已开启" else "去开启悬浮窗权限"
        binding.btnGrantPermission.isEnabled = !granted
    }

    private fun canDrawOverlays(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }

    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            toast("当前系统版本无需手动授权")
            return
        }
        val withPackage = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        val result = runCatching { overlaySettingsLauncher.launch(withPackage) }
        if (result.isFailure) {
            // 部分厂商 ROM 不接受带包名的 Intent，退回通用入口
            runCatching {
                overlaySettingsLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            }.onFailure { toast("无法打开悬浮窗设置，请手动到系统设置里开启") }
        }
    }
}
