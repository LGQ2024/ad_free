package com.example.jingwang.crash

import android.os.Process
import kotlin.system.exitProcess

object LocalCrashHandler {
    fun install(repository: CrashReportRepository) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is Handler) return
        Thread.setDefaultUncaughtExceptionHandler(Handler(repository, previous))
    }

    private class Handler(
        private val repository: CrashReportRepository,
        private val previous: Thread.UncaughtExceptionHandler?,
    ) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            try {
                repository.recordFatal(thread, throwable)
            } catch (_: Throwable) {
                // 崩溃处理器不能遮盖原始异常或阻止系统结束进程。
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }
}
