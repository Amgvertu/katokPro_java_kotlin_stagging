package com.katok.pro.network

import com.katok.pro.model.ApiResponse
import com.katok.pro.model.NetworkResult
import com.katok.pro.util.GlobalErrorHandler
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

suspend fun <T> safeApiCall(
    execute: suspend () -> Response<ApiResponse<T>>
): NetworkResult<T> {
    return try {
        val response = execute()
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null && body.isSuccess) {
                val data = body.data
                if (data != null) {
                    NetworkResult.Success(data)
                } else {
                    NetworkResult.Error("Данные не получены", response.code())
                }
            } else {
                NetworkResult.Error(body?.message ?: "Ошибка сервера: ${response.code()}", response.code())
            }
        } else {
            val errorResult = GlobalErrorHandler.handleError(HttpException(response))
            NetworkResult.Error(errorResult.message, response.code())
        }
    } catch (e: IOException) {
        val errorResult = GlobalErrorHandler.handleError(e)
        NetworkResult.Error(errorResult.message)
    } catch (e: SocketTimeoutException) {
        val errorResult = GlobalErrorHandler.handleError(e)
        NetworkResult.Error(errorResult.message)
    } catch (e: UnknownHostException) {
        val errorResult = GlobalErrorHandler.handleError(e)
        NetworkResult.Error(errorResult.message)
    } catch (e: Exception) {
        val errorResult = GlobalErrorHandler.handleError(e)
        NetworkResult.Error(errorResult.message)
    }
}

suspend fun <T> safeApiCallWithNullableData(
    execute: suspend () -> Response<ApiResponse<T>>
): NetworkResult<T?> {
    return try {
        val response = execute()
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null && body.isSuccess) {
                NetworkResult.Success(body.data) // data может быть null
            } else {
                NetworkResult.Error(body?.message ?: "Ошибка сервера: ${response.code()}", response.code())
            }
        } else {
            val errorResult = GlobalErrorHandler.handleError(HttpException(response))
            NetworkResult.Error(errorResult.message, response.code())
        }
    } catch (e: IOException) {
        val errorResult = GlobalErrorHandler.handleError(e)
        NetworkResult.Error(errorResult.message)
    } catch (e: SocketTimeoutException) {
        val errorResult = GlobalErrorHandler.handleError(e)
        NetworkResult.Error(errorResult.message)
    } catch (e: UnknownHostException) {
        val errorResult = GlobalErrorHandler.handleError(e)
        NetworkResult.Error(errorResult.message)
    } catch (e: Exception) {
        val errorResult = GlobalErrorHandler.handleError(e)
        NetworkResult.Error(errorResult.message)
    }
}

suspend fun safeApiCallIgnoreNullData(
    execute: suspend () -> Response<ApiResponse<Void>>
): NetworkResult<Unit> {
    return try {
        val response = execute()
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null && body.isSuccess) {
                NetworkResult.Success(Unit)
            } else {
                NetworkResult.Error(body?.message ?: "Ошибка сервера: ${response.code()}", response.code())
            }
        } else {
            val errorResult = GlobalErrorHandler.handleError(HttpException(response))
            NetworkResult.Error(errorResult.message, response.code())
        }
    } catch (e: IOException) {
        val errorResult = GlobalErrorHandler.handleError(e)
        NetworkResult.Error(errorResult.message)
    } catch (e: SocketTimeoutException) {
        val errorResult = GlobalErrorHandler.handleError(e)
        NetworkResult.Error(errorResult.message)
    } catch (e: UnknownHostException) {
        val errorResult = GlobalErrorHandler.handleError(e)
        NetworkResult.Error(errorResult.message)
    } catch (e: Exception) {
        val errorResult = GlobalErrorHandler.handleError(e)
        NetworkResult.Error(errorResult.message)
    }
}