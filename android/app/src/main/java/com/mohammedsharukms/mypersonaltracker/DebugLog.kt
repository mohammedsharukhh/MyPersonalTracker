package com.mohammedsharukms.mypersonaltracker

import android.util.Log

/** The system log, in debug builds only: anything there can be read over USB debugging or by a phone maker's log collector. */
object DebugLog {
    fun d(tag: String, message: String) { if (BuildConfig.DEBUG) Log.d(tag, message) }
    fun i(tag: String, message: String) { if (BuildConfig.DEBUG) Log.i(tag, message) }
    fun e(tag: String, message: String, error: Throwable? = null) { if (BuildConfig.DEBUG) Log.e(tag, message, error) }
}
