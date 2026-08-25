package com.example.jingwang.crash

import android.content.Context
import android.os.Build
import com.example.jingwang.BuildConfig
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CrashReportRepository(context: Context) {
    private val store = CrashReportStore(context.noBackupFilesDir)
    private val mutableReport = MutableStateFlow(store.read())
    val report: StateFlow<String?> = mutableReport.asStateFlow()

    internal fun recordFatal(thread: Thread, throwable: Throwable) {
        store.write(
            formatCrashReport(
                timestampEpochMillis = System.currentTimeMillis(),
                appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                androidVersion = "${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}",
                threadName = thread.name,
                throwable = throwable,
            ),
        )
    }

    fun clear() {
        store.clear()
        mutableReport.value = null
    }

    companion object {
        internal fun formatCrashReport(
            timestampEpochMillis: Long,
            appVersion: String,
            androidVersion: String,
            threadName: String,
            throwable: Throwable,
        ): String = buildString {
            appendLine("净网本地崩溃报告")
            appendLine("时间：${Instant.ofEpochMilli(timestampEpochMillis)}")
            appendLine("应用版本：$appVersion")
            appendLine("Android：$androidVersion")
            appendLine("线程：${threadName.replace('\r', ' ').replace('\n', ' ').take(200)}")
            appendLine()
            appendLine("异常堆栈：")
            append(throwable.stackTraceToString())
        }
    }
}
