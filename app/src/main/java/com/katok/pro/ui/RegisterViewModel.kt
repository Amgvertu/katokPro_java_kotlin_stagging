package com.katok.pro.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class RegisterViewModel : ViewModel() {
    private val phone = MutableLiveData<String>()
    private val password = MutableLiveData<String>()
    private val confirmPassword = MutableLiveData<String>()
    private val cityName = MutableLiveData<String>()
    private val cityId = MutableLiveData<Int>()
    private val regionId = MutableLiveData<Int>()
    private val countryId = MutableLiveData(1) // Россия

    fun getPhone(): LiveData<String> = phone
    fun getPassword(): LiveData<String> = password
    fun getConfirmPassword(): LiveData<String> = confirmPassword
    fun getCityName(): LiveData<String> = cityName
    fun getCityId(): LiveData<Int> = cityId
    fun getRegionId(): LiveData<Int> = regionId
    fun getCountryId(): LiveData<Int> = countryId

    fun setPhone(value: String) { phone.value = value }
    fun setPassword(value: String) { password.value = value }
    fun setConfirmPassword(value: String) { confirmPassword.value = value }
    fun setCityName(name: String) { cityName.value = name }
    fun setCityId(id: Int) { cityId.value = id }
    fun setRegionId(id: Int) { regionId.value = id }
    fun setCountryId(id: Int) { countryId.value = id }
}
