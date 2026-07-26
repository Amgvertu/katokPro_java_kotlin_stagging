package com.katok.pro.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ProfileInfoViewModel : ViewModel() {
    private val firstName = MutableLiveData<String>()
    private val lastName = MutableLiveData<String>()
    private val birthDate = MutableLiveData<String>()
    private val position = MutableLiveData<String>()
    private val level = MutableLiveData<String>()
    private val number = MutableLiveData<String>()
    private val team = MutableLiveData<String>()
    private val email = MutableLiveData<String>()
    private val cityId = MutableLiveData<Int>()
    private val cityName = MutableLiveData<String>()
    private val regionId = MutableLiveData<Int>()
    private val countryId = MutableLiveData(1) // Россия по умолчанию

    fun getFirstName(): LiveData<String> = firstName
    fun getLastName(): LiveData<String> = lastName
    fun getBirthDate(): LiveData<String> = birthDate
    fun getPosition(): LiveData<String> = position
    fun getLevel(): LiveData<String> = level
    fun getNumber(): LiveData<String> = number
    fun getTeam(): LiveData<String> = team
    fun getEmail(): LiveData<String> = email
    fun getCityId(): LiveData<Int> = cityId
    fun getCityName(): LiveData<String> = cityName
    fun getRegionId(): LiveData<Int> = regionId
    fun getCountryId(): LiveData<Int> = countryId

    fun setFirstName(value: String) { firstName.value = value }
    fun setLastName(value: String) { lastName.value = value }
    fun setBirthDate(value: String) { birthDate.value = value }
    fun setPosition(value: String) { position.value = value }
    fun setLevel(value: String) { level.value = value }
    fun setNumber(value: String) { number.value = value }
    fun setTeam(value: String) { team.value = value }
    fun setEmail(value: String) { email.value = value }
    fun setCityId(id: Int) { cityId.value = id }
    fun setCityName(name: String) { cityName.value = name }
    fun setRegionId(id: Int) { regionId.value = id }
    fun setCountryId(id: Int) { countryId.value = id }

    fun clearAll() {
        setFirstName("")
        setLastName("")
        setBirthDate("")
        setPosition("")
        setLevel("")
        setNumber("")
        setTeam("")
        setEmail("")
        setCityName("")
        setCityId(0)
        setRegionId(0)
        setCountryId(1)
    }
}
