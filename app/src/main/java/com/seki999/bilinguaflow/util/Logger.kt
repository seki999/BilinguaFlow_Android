package com.seki999.bilinguaflow.util

import android.util.Log
import com.seki999.bilinguaflow.BuildConfig

/** Thin logging wrapper that stays quiet in release builds and never logs recognized speech content. */
object Logger {
    private const val TAG = "BilinguaFlow"

    fun d(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    fun i(message: String) {
        if (BuildConfig.DEBUG) Log.i(TAG, message)
    }

    fun w(message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.w(TAG, message, throwable)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
    }
}
