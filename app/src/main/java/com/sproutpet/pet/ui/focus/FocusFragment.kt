package com.sproutpet.pet.ui.focus

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.sproutpet.pet.R

/**
 * 专注陪伴番茄钟页（docs/04 §2.5）。
 *
 * 入口：孵化抽层「专注陪伴」行。支持 15/25/45/60 分钟预设，
 * 开始/暂停/结束；结束或提前停止按**实际陪伴时长**回调
 * `onFocusFinished(eggId, elapsedMs)`，由场景层结算孵化抵扣与陪伴经验。
 */
class FocusFragment : Fragment() {

    /** 陪伴结束回调（eggId, 实际陪伴毫秒） */
    var onFocusFinished: ((Long, Long) -> Unit)? = null

    private var eggId: Long = 0L

    /** 当前选中的专注时长（毫秒） */
    private var selectedMs: Long = DEFAULT_FOCUS_MS

    /** 已累计陪伴时长（毫秒，含暂停前的） */
    private var accumulatedMs: Long = 0L
    private var sessionStartAt: Long = 0L
    private var timer: CountDownTimer? = null
    private var running: Boolean = false

    private lateinit var tvCountdown: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var chips: List<TextView>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        eggId = arguments?.getLong(ARG_EGG_ID) ?: 0L
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_focus, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        tvCountdown = view.findViewById(R.id.tv_countdown)
        btnStart = view.findViewById(R.id.btn_start)
        btnStop = view.findViewById(R.id.btn_stop)
        chips = listOf(
            view.findViewById(R.id.chip_15),
            view.findViewById(R.id.chip_25),
            view.findViewById(R.id.chip_45),
            view.findViewById(R.id.chip_60)
        )
        val presets = listOf(15, 25, 45, 60).map { it * 60 * 1000L }
        chips.forEachIndexed { i, chip ->
            chip.setOnClickListener {
                if (running) return@setOnClickListener
                selectedMs = presets[i]
                accumulatedMs = 0L
                updateCountdown(selectedMs)
                highlightChip(i)
            }
        }
        highlightChip(1) // 默认 25 分

        view.findViewById<TextView>(R.id.tv_back).setOnClickListener { finishSession() }

        btnStart.setOnClickListener {
            if (running) pauseSession() else startSession()
        }
        btnStop.setOnClickListener { finishSession() }

        updateCountdown(selectedMs)
    }

    private fun startSession() {
        running = true
        sessionStartAt = System.currentTimeMillis()
        btnStart.setText(R.string.focus_pause)
        btnStop.visibility = View.VISIBLE
        chips.forEach { it.alpha = 0.4f }
        val remain = (selectedMs - accumulatedMs).coerceAtLeast(0L)
        timer = object : CountDownTimer(remain, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                updateCountdown(millisUntilFinished)
            }
            override fun onFinish() {
                // 自然结束：按选中时长结算
                accumulatedMs = selectedMs
                finishSession()
            }
        }.start()
    }

    private fun pauseSession() {
        running = false
        timer?.cancel()
        timer = null
        accumulatedMs += System.currentTimeMillis() - sessionStartAt
        btnStart.setText(R.string.focus_resume)
        val remain = (selectedMs - accumulatedMs).coerceAtLeast(0L)
        updateCountdown(remain)
    }

    /** 结束陪伴：按实际累计时长结算并退出 */
    private fun finishSession() {
        if (running) {
            accumulatedMs += System.currentTimeMillis() - sessionStartAt
        }
        timer?.cancel()
        timer = null
        running = false
        if (accumulatedMs >= MIN_EFFECTIVE_FOCUS_MS) {
            onFocusFinished?.invoke(eggId, accumulatedMs)
        }
        parentFragmentManager.popBackStack()
    }

    private fun updateCountdown(ms: Long) {
        val totalSec = (ms / 1000).coerceAtLeast(0L)
        val mm = totalSec / 60
        val ss = totalSec % 60
        tvCountdown.text = String.format("%02d:%02d", mm, ss)
    }

    private fun highlightChip(index: Int) {
        chips.forEachIndexed { i, chip ->
            chip.alpha = if (i == index) 1f else 0.5f
        }
    }

    override fun onDestroyView() {
        timer?.cancel()
        super.onDestroyView()
    }

    companion object {
        private const val ARG_EGG_ID = "egg_id"
        private const val DEFAULT_FOCUS_MS = 25 * 60 * 1000L
        /** 低于 5 秒的误触不结算 */
        private const val MIN_EFFECTIVE_FOCUS_MS = 5_000L

        fun newInstance(eggId: Long): FocusFragment =
            FocusFragment().apply {
                arguments = Bundle().apply { putLong(ARG_EGG_ID, eggId) }
            }
    }
}
