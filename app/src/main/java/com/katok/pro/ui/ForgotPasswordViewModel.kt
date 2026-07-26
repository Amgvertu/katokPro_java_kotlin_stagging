package com.katok.pro.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ForgotPasswordViewModel : ViewModel() {
    private val phone = MutableLiveData<String>()
    private val code = MutableLiveData<String>()
    private val newPassword = MutableLiveData<String>()
    private val confirmPassword = MutableLiveData<String>()

    fun getPhone(): LiveData<String> = phone
    fun getCode(): LiveData<String> = code
    fun getNewPassword(): LiveData<String> = newPassword
    fun getConfirmPassword(): LiveData<String> = confirmPassword

    fun setPhone(value: String) { phone.value = value }
    fun setCode(value: String) { code.value = value }
    fun setNewPassword(value: String) { newPassword.value = value }
    fun setConfirmPassword(value: String) { confirmPassword.value = value }
}
