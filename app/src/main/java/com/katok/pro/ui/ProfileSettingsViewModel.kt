package com.katok.pro.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ProfileSettingsViewModel : ViewModel() {
    private val oldPassword = MutableLiveData<String>()
    private val newPassword = MutableLiveData<String>()
    private val confirmPassword = MutableLiveData<String>()
    private val newPhone = MutableLiveData<String>()
    private val passwordForPhone = MutableLiveData<String>()

    fun getOldPassword(): LiveData<String> = oldPassword
    fun getNewPassword(): LiveData<String> = newPassword
    fun getConfirmPassword(): LiveData<String> = confirmPassword
    fun getNewPhone(): LiveData<String> = newPhone
    fun getPasswordForPhone(): LiveData<String> = passwordForPhone

    fun setOldPassword(value: String) { oldPassword.value = value }
    fun setNewPassword(value: String) { newPassword.value = value }
    fun setConfirmPassword(value: String) { confirmPassword.value = value }
    fun setNewPhone(value: String) { newPhone.value = value }
    fun setPasswordForPhone(value: String) { passwordForPhone.value = value }
}
