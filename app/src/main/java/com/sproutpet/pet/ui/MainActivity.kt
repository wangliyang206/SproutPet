package com.sproutpet.pet.ui

import android.view.LayoutInflater
import com.sproutpet.pet.databinding.ActivityMainBinding
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
    }

    override fun initData() {
    }
}
