package com.katok.pro.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.katok.pro.model.City
import com.katok.pro.model.NetworkResult
import com.katok.pro.repository.AuthRepository
import com.katok.pro.repository.UserRepository
import com.katok.pro.services.WebSocketForegroundService
import com.katok.pro.util.ProfileCacheManager
import com.katok.pro.util.SessionManager
import com.katok.pro.util.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class MainActivityViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val tokenManager: TokenManager,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    companion object {
        private const val TAG = "MainActivityViewModel"
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _shouldNavigateToLogin = MutableStateFlow(false)
    val shouldNavigateToLogin: StateFlow<Boolean> = _shouldNavigateToLogin

    private val _cityDetected = MutableStateFlow<City?>(null)
    val cityDetected: StateFlow<City?> = _cityDetected

    private var isRefreshingToken = false


    fun saveSelectedCity(cityId: Int, cityName: String) {
        viewModelScope.launch {
            sessionManager.saveSelectedCity(cityId, cityName)
        }
    }

    fun checkTokenAndProceed(onTokenValid: () -> Unit, onTokenInvalid: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = userRepository.getCurrentUser()
            _isLoading.value = false
            when (result) {
                is NetworkResult.Success -> {
                    onTokenValid()
                }
                is NetworkResult.Error -> {
                    if (result.code == 401) {
                        refreshTokenAndProceed(onTokenValid, onTokenInvalid)
                    } else {
                        _error.value = result.message
                        onTokenInvalid()
                    }
                }
                else -> {}
            }
        }
    }

    private fun refreshTokenAndProceed(onSuccess: () -> Unit, onFailure: () -> Unit) {
        if (isRefreshingToken) return
        isRefreshingToken = true
        viewModelScope.launch {
            val refreshToken = tokenManager.getRefreshToken()
            if (refreshToken == null) {
                logoutAndClear()
                onFailure()
                isRefreshingToken = false
                return@launch
            }
            val result = authRepository.refreshToken()
            when (result) {
                is NetworkResult.Success -> {
                    val loginData = result.data
                    tokenManager.saveTokens(loginData.accessToken, loginData.refreshToken)
                    restartWebSocket(loginData.accessToken)
                    onSuccess()
                }
                is NetworkResult.Error -> {
                    if (result.code == 401 || result.code == 400) {
                        logoutAndClear()
                    } else {
                        _error.value = result.message
                    }
                    onFailure()
                }
                else -> {}
            }
            isRefreshingToken = false
        }
    }

    private fun restartWebSocket(token: String?) {
        if (token == null) return
        val intent = Intent(context, WebSocketForegroundService::class.java)
        intent.putExtra("token", token)
        context.startService(intent)
    }

    private fun logoutAndClear() {
        viewModelScope.launch {
            sessionManager.logout()
            tokenManager.clear()
            ProfileCacheManager(context).clear()
            _shouldNavigateToLogin.value = true
        }
    }

    fun startWebSocketService() {
        val token = tokenManager.getAccessToken()
        if (!token.isNullOrEmpty()) {
            WebSocketForegroundService.start(context)
        }
    }

    fun resetNavigationFlag() {
        _shouldNavigateToLogin.value = false
    }

    fun clearError() {
        _error.value = null
    }
}