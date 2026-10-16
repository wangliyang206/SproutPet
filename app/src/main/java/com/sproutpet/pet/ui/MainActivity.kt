package com.sproutpet.pet.ui

import android.content.Intent
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.material.button.MaterialButton
import com.sproutpet.pet.BuildConfig
import com.sproutpet.pet.R
import com.sproutpet.pet.databinding.ActivityMainBinding
import com.sproutpet.pet.debug.DesktopPetProbeActivity
import com.sproutpet.core.base.BaseActivity

/**
 * 单 Activity 架构宿主。
 *
 * 所有页面通过 Navigation 组件的 Fragment 承载，
 * 好处：状态管理集中、转场动画统一、深链支持简单。
 */
class MainActivity : BaseActivity<ActivityMainBinding>() {

    override fun inflateBinding(inflater: LayoutInflater): ActivityMainBinding =
        ActivityMainBinding.inflate(inflater)

    override fun initView() {
        // 底部导航与 NavController 的绑定在 Phase 1 完成

        if (BuildConfig.DEBUG) {
            addProbeEntry()
        }
    }

    override fun initData() {
    }

    /**
     * 调试入口：桌宠技术验证面板。
     *
     * 之所以动态添加而不是写进 layout，是为了**不污染正式界面**——
     * Phase 3 桌宠方案定案后，删掉这个方法和 debug 包即可，layout 无需回滚。
     *
     * 也可以用命令行直接启动，无需经过这里：
     * ```
     * adb shell am start -n com.sproutpet.pet.debug/com.sproutpet.pet.debug.DesktopPetProbeActivity
     * ```
     */
    private fun addProbeEntry() {
        val entry = MaterialButton(this).apply {
            text = getString(R.string.probe_entry)
            textSize = 12f
            setOnClickListener {
                startActivity(Intent(this@MainActivity, DesktopPetProbeActivity::class.java))
            }
        }
        val params = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.WRAP_CONTENT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            topMargin = 16
            marginEnd = 16
        }
        binding.root.addView(entry, params)
    }
}
