package com.katok.pro.repository

import android.content.Context
import com.katok.pro.database.AppDatabase
import com.katok.pro.model.NetworkResult
import com.katok.pro.model.admin.AdminMessage
import com.katok.pro.model.admin.AdminMessageRequest
import com.katok.pro.model.admin.AdminMessageListResponse
import com.katok.pro.network.ApiClient
import com.katok.pro.network.ApiService
import com.katok.pro.network.safeApiCall
import com.katok.pro.network.safeApiCallIgnoreNullData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.MultipartBody

class AdminMessageRepository(context: Context) {

    private val dao = AppDatabase.getInstance(context).adminMessageDao()
    private val apiService: ApiService
        get() = ApiClient.getApiService()

    // Получение сообщений из локальной БД (для UI)
    fun getMessages(): Flow<List<AdminMessage>> = dao.getAll()

    // Получение количества непрочитанных (локально)
    fun getUnreadCount(): Flow<Int> = dao.getUnreadCount()

    // Загрузка сообщений с сервера и синхронизация с локальной БД
    suspend fun syncMessages(page: Int = 0, size: Int = 50) {
        val result = safeApiCall { apiService.getMyMessages(page, size) }
        if (result is NetworkResult.Success) {
            val messages = result.data.content ?: emptyList()
            dao.insertAll(messages)
        }
    }

    // Отметить как прочитанное (локально + на сервере)
    suspend fun markAsRead(id: Long) {
        // Сначала локально (для мгновенного обновления UI)
        dao.markAsRead(id)
        // Затем на сервере (в фоне)
        try {
            safeApiCallIgnoreNullData { apiService.markMessageAsRead(id) }
        } catch (e: Exception) {
            // Можно обработать ошибку, но локально уже обновлено
        }
    }

    // Отметить все как прочитанные
    suspend fun markAllAsRead() {
        dao.markAllAsRead()
        try {
            safeApiCallIgnoreNullData { apiService.markAllMessagesAsRead() }
        } catch (e: Exception) {
            // ignore
        }
    }

    // Добавление нового сообщения (из WebSocket) в локальную БД
    suspend fun addMessage(message: AdminMessage) {
        dao.insert(message)
    }

    // Отправка сообщения администратором (реальный вызов API)
    suspend fun sendMessage(request: AdminMessageRequest): NetworkResult<AdminMessage> {
        return safeApiCall { apiService.sendAdminMessage(request) }
    }

    // Загрузка изображения
    suspend fun uploadImage(filePart: MultipartBody.Part): NetworkResult<String> {
        val result = safeApiCall { apiService.uploadMessageImage(filePart) }
        return when (result) {
            is NetworkResult.Success -> {
                val imageUrl = result.data?.imageUrl
                if (imageUrl != null) {
                    NetworkResult.Success(imageUrl)
                } else {
                    NetworkResult.Error("Ссылка на изображение не получена")
                }
            }
            is NetworkResult.Error -> NetworkResult.Error(result.message, result.code)
            is NetworkResult.Loading -> NetworkResult.Loading(result.isLoading)
        }
    }

    // Очистка локальной БД (для тестов)
    suspend fun clearAll() {
        dao.clear()
    }

    // Удаление одного сообщения (локально + на сервере)
    suspend fun deleteMessage(id: Long): NetworkResult<Unit> {
        dao.deleteById(id)
        return safeApiCallIgnoreNullData { apiService.deleteMessage(id) }
    }

    suspend fun deleteAllMessages(): NetworkResult<Unit> {
        dao.deleteAll()
        return safeApiCallIgnoreNullData { apiService.deleteAllMessages() }
    }
}