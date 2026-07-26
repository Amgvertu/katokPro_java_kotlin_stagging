package com.katok.pro.ui

import androidx.lifecycle.ViewModel
import com.katok.pro.model.admin.AdminMessage
import com.katok.pro.repository.AdminMessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import com.katok.pro.model.NetworkResult
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val repository: AdminMessageRepository
) : ViewModel() {

    private val _messages = MutableStateFlow<List<AdminMessage>>(emptyList())
    val messages: StateFlow<List<AdminMessage>> = _messages.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        // Подписка на локальные изменения
        viewModelScope.launch {
            repository.getMessages().collect { list ->
                _messages.value = list
            }
        }
        viewModelScope.launch {
            repository.getUnreadCount().collect { count ->
                _unreadCount.value = count
            }
        }
        // Загружаем с сервера при создании
        loadMessages()
    }

    fun loadMessages() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                repository.syncMessages()
            } catch (e: Exception) {
                _error.value = e.message
            }
            _isLoading.value = false
        }
    }

    fun markAsRead(id: Long) {
        viewModelScope.launch {
            repository.markAsRead(id)
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            repository.markAllAsRead()
        }
    }

    // Добавление нового сообщения из WebSocket
    fun addNewMessage(message: AdminMessage) {
        viewModelScope.launch {
            repository.addMessage(message)
        }
    }

    // Обновление количества непрочитанных при получении push/websocket
    fun refreshUnreadCount() {
        viewModelScope.launch {
            val count = repository.getUnreadCount().first()
            _unreadCount.value = count
        }
    }

    fun deleteMessage(id: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.deleteMessage(id)
            _isLoading.value = false
            when (result) {
                is NetworkResult.Success -> {
                    // Обновляем список – удаляем элемент локально
                    val currentList = _messages.value.toMutableList()
                    currentList.removeAll { it.id == id }
                    _messages.value = currentList
                    // Обновляем счётчик непрочитанных (если нужно)
                    refreshUnreadCount()
                }
                is NetworkResult.Error -> _error.value = result.message
                else -> {}
            }
        }
    }

    fun deleteAllMessages() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.deleteAllMessages()
            _isLoading.value = false
            when (result) {
                is NetworkResult.Success -> {
                    // Очищаем список
                    _messages.value = emptyList()
                    _unreadCount.value = 0
                    _error.value = null
                }
                is NetworkResult.Error -> {
                    _error.value = result.message
                    // Если ошибка – возможно, стоит перезагрузить список с сервера
                    loadMessages()
                }
                else -> {}
            }
        }
    }
}