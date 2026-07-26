package com.katok.pro.util

import android.content.Context

object GlobalErrorHandler {
    private lateinit var context: Context

    fun init(appContext: Context) {
        context = appContext
    }

    fun handleError(throwable: Throwable): ErrorResult {
        val handler = NetworkErrorHandler(context)
        return handler.handleError(throwable)
    }

    fun getErrorMessage(throwable: Throwable): String = handleError(throwable).message
}