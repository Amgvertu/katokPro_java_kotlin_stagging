package com.katok.pro.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class LoginViewModel : ViewModel() {
    private val phone = MutableLiveData<String>()
    private val password = MutableLiveData<String>()

    fun getPhone(): LiveData<String> = phone
    fun getPassword(): LiveData<String> = password

    fun setPhone(phone: String) { this.phone.value = phone }
    fun setPassword(password: String) { this.password.value = password }
}
