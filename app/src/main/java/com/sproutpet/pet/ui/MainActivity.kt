package com.sproutpet.pet.ui

import android.content.Intent
import android.view.LayoutInflater
import androidx.lifecycle.lifecycleScope
import com.sproutpet.core.base.BaseActivity
import com.sproutpet.pet.BuildConfig
import com.sproutpet.pet.SproutPetApplication
import com.sproutpet.pet.data.local.OwnerLocalDataSource
import com.sproutpet.pet.debug.DesktopPetProbeActivity
import com.sproutpet.pet.databinding.ActivityMainBinding
import com.sproutpet.pet.ui.privacy.PrivacyGateFragment
import com.sproutpet.pet.ui.scene.SceneFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 单 Activity 宿主（倒置 IA，U-10 拍板）。
 *
 * **桌面场景即主界面**：全屏 [SceneFragment]，无 Tab 栏、无功能宫格。
 * 配对 / 孵化 / 家庭等均为场景内动作或右缘抽屉内容（见 docs/04 §1.1）。
 *
 * 首启分流（`16 §三 K-4`）：未同意隐私政策时先挂 [PrivacyGateFragment]（19 屏，全屏门），
 * 同意后**替换**为场景 —— 不留返回栈，用户不会「退回」隐私门。
 */
@AndroidEntryPoint
class MainActivity : BaseActivity<ActivityMainBinding>() {

    @Inject
    lateinit var ownerLocal: OwnerLocalDataSource

    override fun inflateBinding(inflater: LayoutInflater): ActivityMainBinding =
        ActivityMainBinding.inflate(inflater)

    override fun initView() {
        if (BuildConfig.DEBUG) {
            addProbeEntry()
        }
    }

    override fun initData() {
        // 旋转 / 进程重建时已挂过 Fragment，不再重复分流
        if (supportFragmentManager.findFragmentById(binding.sceneContainer.id) != null) return
        lifecycleScope.launch {
            val agreed = ownerLocal.readPrivacyAgreed()
            if (agreed) {
                // 已同意过：恢复全局标记并补齐 SDK 初始化（幂等），然后直接进场景
                SproutPetApplication.privacyAgreed = true
                SproutPetApplication.instance.onPrivacyAgreed()
                showScene()
            } else {
                showPrivacyGate()
            }
        }
    }

    private fun showPrivacyGate() {
        val gate = PrivacyGateFragment().apply {
            onAgreed = { showScene() }
        }
        supportFragmentManager.beginTransaction()
            .replace(binding.sceneContainer.id, gate, PrivacyGateFragment.TAG)
            .commit()
    }

    private fun showScene() {
        supportFragmentManager.beginTransaction()
            .replace(binding.sceneContainer.id, SceneFragment.newInstance())
            .commit()
    }

    /**
     * 调试入口：桌宠技术验证面板。
     *
     * 动态添加而不写进 layout，是为了不污染正式界面 ——
     * Phase 3 桌宠方案定案后删掉本方法与 debug 包即可。
     * 也可用命令行直达：`adb shell am start -n com.sproutpet.pet.debug/com.sproutpet.pet.debug.DesktopPetProbeActivity`
     */
    private fun addProbeEntry() {
        val entry = com.google.android.material.button.MaterialButton(this).apply {
            text = getString(com.sproutpet.pet.R.string.probe_entry)
            textSize = 11f
            alpha = 0.75f
            setOnClickListener {
                startActivity(Intent(this@MainActivity, DesktopPetProbeActivity::class.java))
            }
        }
        val params = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.END
            topMargin = 64
            marginEnd = 40
        }
        binding.root.addView(entry, params)
    }
}
