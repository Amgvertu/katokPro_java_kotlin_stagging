package com.katok.pro.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.katok.pro.model.NotificationSettings

class NotificationSettingsViewModel : ViewModel() {
    private val settings = MutableLiveData<NotificationSettings>()
    private val isLoading = MutableLiveData(false)

    fun getSettings(): LiveData<NotificationSettings> = settings
    fun getIsLoading(): LiveData<Boolean> = isLoading

    fun setSettings(settings: NotificationSettings) { this.settings.value = settings }
    fun setLoading(loading: Boolean) { isLoading.value = loading }
}
