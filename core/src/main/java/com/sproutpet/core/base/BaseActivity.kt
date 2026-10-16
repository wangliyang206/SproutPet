package com.sproutpet.core.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewbinding.ViewBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Activity 基类，统一 ViewBinding 与生命周期安全的 Flow 收集。
 *
 * 使用示例：
 * ```
 * class MainActivity : BaseActivity<ActivityMainBinding>() {
 *     override fun inflateBinding(inflater: LayoutInflater) =
 *         ActivityMainBinding.inflate(inflater)
 *     override fun initView() { ... }
 *     override fun initData() { ... }
 * }
 * ```
 */
abstract class BaseActivity<VB : ViewBinding> : AppCompatActivity() {

    private var _binding: VB? = null
    protected val binding: VB get() = requireNotNull(_binding) { "binding 在 onCreate 前不可访问" }

    /** 由子类提供 Binding 的 inflate 方式 */
    protected abstract fun inflateBinding(inflater: LayoutInflater): VB

    /** 初始化视图（设置监听器等） */
    protected open fun initView() {}

    /** 初始化数据（发起请求、订阅状态） */
    protected open fun initData() {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = inflateBinding(layoutInflater)
        setContentView(binding.root)
        initView()
        initData()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    /**
     * 在 STARTED 生命周期内安全收集 Flow，避免泄漏。
     */
    protected fun <T> Flow<T>.collectInLifecycle(block: (T) -> Unit) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                collect { block(it) }
            }
        }
    }

    protected fun toast(msg: CharSequence) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Fragment 基类，统一 ViewBinding 与视图生命周期安全的收集。
 */
abstract class BaseFragment<VB : ViewBinding> : Fragment() {

    private var _binding: VB? = null
    protected val binding: VB get() = requireNotNull(_binding) { "binding 在视图销毁后不可访问" }

    /** 由子类提供 Binding 的 inflate 方式 */
    protected abstract fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?): VB

    protected open fun initView(view: View, savedInstanceState: Bundle?) {}
    protected open fun initData() {}

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = inflateBinding(inflater, container)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initView(view, savedInstanceState)
        initData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * 在视图生命周期内安全收集 Flow。
     * 视图销毁自动取消，避免内存泄漏与重复订阅。
     */
    protected fun <T> Flow<T>.collectInView(block: (T) -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                collect { block(it) }
            }
        }
    }

    protected fun toast(msg: CharSequence) {
        context?.let { Toast.makeText(it, msg, Toast.LENGTH_SHORT).show() }
    }
}
