package com.sproutpet.pet.debug

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 桌宠技术验证的事件日志。
 *
 * ## 为什么需要它
 *
 * 悬浮窗的触摸行为在**代码里看不到**：事件到底有没有到达我们的窗口、有没有传给下层，
 * 只能靠窗口侧打点 + 面板侧展示来判断。因此这里是验证的核心观测手段，不是普通日志。
 *
 * ## 为什么必须落盘
 *
 * 最初版本只写内存 `StateFlow`，结果是**日志没法带走** —— 测试者只能对着屏幕念，
 * 或者靠截图，分析者拿不到原始事件流。
 *
 * 现在改成**双写**：
 * - 内存：供面板实时显示（只看最近 [MAX_LINES] 行）
 * - 文件：全部追加写入 `getExternalFilesDir("probe")/probe_session.log`，可一键分享
 *
 * 一次 App 会话对应一个文件；每次启动验证面板时，上一份文件会被**归档**而不是丢弃，
 * 因此反复测试也不会丢历史证据。
 *
 * ⚠️ 仅供调试验证使用，与正式业务无关。Phase 3 定案后可连同 debug 包一起删除。
 */
object ProbeLog {

    private const val MAX_LINES = 500
    private const val DIR_NAME = "probe"
    private const val FILE_NAME = "probe_session.log"
    private const val AUTHORITY_SUFFIX = ".fileprovider"
    private const val MAX_ARCHIVES = 5

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val sessionFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val archiveFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

    private val _lines = MutableStateFlow<List<String>>(emptyList())

    /** 供验证面板订阅（仅本次会话的最近若干行） */
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private var appContext: Context? = null
    private var logFile: File? = null

    /**
     * 幂等初始化。必须在任何 [log] 之前调用（Activity / Service 的 onCreate）。
     *
     * 首次调用时会把上一份日志**归档**为 `probe_<时间戳>.log`，
     * 这样每次打开面板都是一个干净的会话文件，同时保留历史。
     */
    @Synchronized
    fun init(context: Context) {
        if (appContext != null) return
        val ctx = context.applicationContext
        appContext = ctx

        val dir = runCatching { ctx.getExternalFilesDir(DIR_NAME) }.getOrNull()
        if (dir == null) {
            logFile = null
            return
        }

        runCatching {
            dir.mkdirs()
            val current = File(dir, FILE_NAME)
            if (current.exists() && current.length() > 0L) {
                current.renameTo(File(dir, "probe_${archiveFormat.format(Date())}.log"))
            }
            // 只保留最近 MAX_ARCHIVES 份归档，避免无限堆积
            dir.listFiles { f -> f.name.startsWith("probe_") && f.name != FILE_NAME }
                ?.sortedByDescending { it.name }
                ?.drop(MAX_ARCHIVES)
                ?.forEach { it.delete() }
        }

        logFile = File(dir, FILE_NAME)
    }

    /** 日志文件的绝对路径，供面板展示（外部存储不可用时为 null） */
    fun logFilePath(): String? = logFile?.absolutePath

    /** 日志文件是否已有内容 */
    fun hasLogFile(): Boolean = logFile?.let { it.exists() && it.length() > 0L } == true

    /**
     * 开始一段新会话。
     *
     * 每次点击某个模式按钮时调用。文件中写入醒目的分隔头（含设备信息），
     * 界面上则清屏，让测试者只看到本轮事件。
     *
     * 这样跑完四个模式后，**一份文件里就有四段独立记录**，便于横向对比。
     */
    @Synchronized
    fun beginSession(title: String) {
        val divider = "============================================================"
        val header = buildString {
            appendLine()
            appendLine(divider)
            appendLine(" 会话开始 ${sessionFormat.format(Date())}")
            appendLine(" 模式：$title")
            appendLine(
                " 设备：${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}" +
                    " / Android ${android.os.Build.VERSION.RELEASE}" +
                    " (SDK ${android.os.Build.VERSION.SDK_INT})"
            )
            appendLine(divider)
        }
        writeToFile(header.trimEnd())
        _lines.value = listOf("—— 新会话：$title ——")
    }

    @Synchronized
    fun log(tag: String, message: String) {
        val line = "${timeFormat.format(Date())} [$tag] $message"
        _lines.value = (_lines.value + line).takeLast(MAX_LINES)
        writeToFile(line)
    }

    /**
     * 记录测试者的**人工观察结论**。
     *
     * 有些判定无法由代码得出 —— 例如"点空白处，桌面图标有没有被点开"，
     * 那个事件根本不会经过我们的进程。只能靠人看一眼，然后用这个方法记进文件。
     *
     * 这样整份日志就是自解释的：事件流 + 人工结论都在里面，分析者不需要再追问。
     */
    @Synchronized
    fun observe(conclusion: String) {
        log("OBSERVE", conclusion)
    }

    /** 清空**界面显示**（文件记录保留，不会丢证据） */
    @Synchronized
    fun clear() {
        _lines.value = emptyList()
        writeToFile("${timeFormat.format(Date())} [UI] ——— 界面已清空（文件记录保留）———")
    }

    /** 读取完整日志文本（来自文件；文件不可用时退回内存） */
    fun exportText(): String {
        val f = logFile
        if (f == null || !f.exists()) return _lines.value.joinToString("\n")
        return runCatching { f.readText(Charsets.UTF_8) }.getOrDefault("")
    }

    /**
     * 生成分享 Intent（把日志文件发出去）。
     *
     * @return null 表示尚无日志或文件不可用，调用方应提示用户
     */
    fun shareIntent(): Intent? {
        val ctx = appContext ?: return null
        val f = logFile?.takeIf { it.exists() && it.length() > 0L } ?: return null
        val uri = runCatching {
            FileProvider.getUriForFile(ctx, ctx.packageName + AUTHORITY_SUFFIX, f)
        }.getOrNull() ?: return null

        val lineCount = exportText().lineSequence().count()
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "桌宠技术验证日志")
            putExtra(Intent.EXTRA_TEXT, "桌宠技术验证日志（$lineCount 行）")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    @Synchronized
    private fun writeToFile(text: String) {
        val f = logFile ?: return
        runCatching {
            f.parentFile?.mkdirs()
            f.appendText(text + "\n", Charsets.UTF_8)
        }
    }
}
