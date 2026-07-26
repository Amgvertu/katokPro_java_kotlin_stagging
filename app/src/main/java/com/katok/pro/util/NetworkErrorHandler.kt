package com.katok.pro.util

import android.content.Context
import com.katok.pro.R
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class NetworkErrorHandler(private val context: Context) {

    fun handleError(throwable: Throwable): ErrorResult {
        return when (throwable) {
            is HttpException -> {
                when (throwable.code()) {
                    400 -> ErrorResult(context.getString(R.string.error_bad_request), false)
                    401 -> ErrorResult(context.getString(R.string.error_unauthorized), true)
                    403 -> ErrorResult(context.getString(R.string.error_forbidden), false)
                    404 -> ErrorResult(context.getString(R.string.error_not_found), false)
                    500, 502, 503 -> ErrorResult(context.getString(R.string.error_server), false)
                    else -> ErrorResult(context.getString(R.string.error_unknown, throwable.code()), false)
                }
            }
            is IOException, is UnknownHostException, is SocketTimeoutException -> {
                ErrorResult(context.getString(R.string.error_network), false)
            }
            else -> ErrorResult(context.getString(R.string.error_unknown_general, throwable.message), false)
        }
    }

    fun getErrorMessage(throwable: Throwable): String = handleError(throwable).message
}

data class ErrorResult(val message: String, val isUnauthorized: Boolean)