package com.sproutpet.pet.ui

import android.content.Intent
import android.view.LayoutInflater
import com.sproutpet.core.base.BaseActivity
import com.sproutpet.pet.BuildConfig
import com.sproutpet.pet.debug.DesktopPetProbeActivity
import com.sproutpet.pet.databinding.ActivityMainBinding
import com.sproutpet.pet.ui.scene.SceneFragment
import dagger.hilt.android.AndroidEntryPoint

/**
 * 单 Activity 宿主（倒置 IA，U-10 拍板）。
 *
 * **桌面场景即主界面**：全屏 [SceneFragment]，无 Tab 栏、无功能宫格。
 * 配对 / 孵化 / 家庭等均为场景内动作或右缘抽屉内容（见 docs/04 §1.1）。
 */
@AndroidEntryPoint
class MainActivity : BaseActivity<ActivityMainBinding>() {

    override fun inflateBinding(inflater: LayoutInflater): ActivityMainBinding =
        ActivityMainBinding.inflate(inflater)

    override fun initView() {
        if (supportFragmentManager.findFragmentById(binding.sceneContainer.id) == null) {
            supportFragmentManager.beginTransaction()
                .replace(binding.sceneContainer.id, SceneFragment.newInstance())
                .commit()
        }
        if (BuildConfig.DEBUG) {
            addProbeEntry()
        }
    }

    override fun initData() {
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
