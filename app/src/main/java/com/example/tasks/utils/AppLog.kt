package com.example.tasks.utils

import android.util.Log
import com.example.tasks.data.repositories.LogRepository

/**
 * Global logging facade.
 */
object AppLog {
    private var repository: LogRepository? = null

    fun init(repo: LogRepository) {
        repository = repo
    }

    fun v(tag: String, msg: String) = log(Log.VERBOSE, tag, msg)
    fun d(tag: String, msg: String) = log(Log.DEBUG, tag, msg)
    fun i(tag: String, msg: String) = log(Log.INFO, tag, msg)
    fun w(tag: String, msg: String) = log(Log.WARN, tag, msg)
    fun e(tag: String, msg: String, tr: Throwable? = null) {
        val message = if (tr != null) {
            msg + System.lineSeparator() + Log.getStackTraceString(tr)
        } else {
            msg
        }
        log(Log.ERROR, tag, message)
    }

    private fun log(priority: Int, tag: String, msg: String) {
        val repo = repository
        if (repo != null) {
            repo.log(priority, tag, msg)
        } else {
            // Fallback if not initialized
            Log.println(priority, tag, msg)
        }
    }
}
