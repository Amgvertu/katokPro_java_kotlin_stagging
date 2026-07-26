package com.katok.pro.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.katok.pro.model.FeedbackRequest
import com.katok.pro.model.NetworkResult
import com.katok.pro.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeedbackViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _success = MutableStateFlow(false)
    val success: StateFlow<Boolean> = _success.asStateFlow()

    fun sendFeedback(fullName: String, phone: String, email: String, subject: String, message: String) {
        viewModelScope.launch {
            _isSending.value = true
            _error.value = null
            _success.value = false

            val request = FeedbackRequest(
                fullName = fullName.takeIf { it.isNotBlank() },
                phone = phone.takeIf { it.isNotBlank() },
                email = email.takeIf { it.isNotBlank() },
                subject = subject,
                message = message
            )

            val result = userRepository.sendFeedback(request)
            when (result) {
                is NetworkResult.Success -> {
                    _success.value = true
                }
                is NetworkResult.Error -> {
                    _error.value = result.message
                }
                else -> {}
            }
            _isSending.value = false
        }
    }

    fun resetSuccess() {
        _success.value = false
    }
}